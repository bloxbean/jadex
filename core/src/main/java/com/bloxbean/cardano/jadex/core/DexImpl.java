
package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.CostModelUtil;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.impl.RandomImproveUtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.plutus.util.ScriptDataHashGenerator;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.order.book.OrderBook;
import com.bloxbean.cardano.jadex.core.order.book.UtxoOrder;
import com.bloxbean.cardano.jadex.core.order.collateral.CollateralProvider;
import com.bloxbean.cardano.jadex.core.order.collateral.DefaultCollateralProvider;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.pool.DefaultPoolStateProvider;
import com.bloxbean.cardano.jadex.core.pool.PoolState;
import com.bloxbean.cardano.jadex.core.util.AddressUtil;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generic implementation offering features to interact with a range of Cardano DEXes.
 * Each DEX instance represents 1 Cardano DEX, configured using {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}.
 * <p>
 * Custom DEXes are supported by providing a custom {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}.
 *
 * @author $stik
 */
@Slf4j
public class DexImpl implements Dex {

    private final UtxoService utxoService;
    private final AssetService assetService;
    private final TransactionService transactionService;
    private final ScriptService scriptService;
    private final MetadataService metadataService;
    private final Set<String> poolAddresses = new HashSet<>();
    private final Map<String, Integer> assetDecimals = new HashMap<>();
    private final DexConfig dexConfig;
    private final BackendService backendService;
    private final CollateralProvider collateralProvider;
    private final BlockService blockService;

    public DexImpl(BackendService backendService, DexConfig dexConfig) {
        this.utxoService = backendService.getUtxoService();
        this.assetService = backendService.getAssetService();
        this.transactionService = backendService.getTransactionService();
        this.scriptService = backendService.getScriptService();
        this.metadataService = backendService.getMetadataService();
        this.backendService = backendService;
        this.blockService = backendService.getBlockService();
        this.collateralProvider = new DefaultCollateralProvider(backendService);
        this.dexConfig = dexConfig;
    }
    public Set<String> getPoolAddress() {
        if(!poolAddresses.isEmpty()){
            return poolAddresses;
        }
        try{
            var allAssets = assetService.getAllAssetAddresses(dexConfig.getPoolAssetId());
            var result = allAssets.getValue().stream()
                    .map(AssetAddress::getAddress)
                    .collect(Collectors.toSet());
            log.debug("There are " + result.size() + " pool addresses");
            poolAddresses.clear();
            poolAddresses.addAll(result);
            return result;
        }catch(Exception e){
            log.error("Failed to load pool addresses", e);
            throw new IllegalStateException("Failed to load pool address", e);
        }
    }
    @Override
    public PoolState getPool(String poolId) {
        try{
            if(StringUtils.isBlank(poolId)){
                return null;
            }
            var nft = dexConfig.poolNftPolicyId() + poolId;
            var nftTxs = this.assetService.getTransactions(nft, 1, 1, OrderEnum.desc);
            if(nftTxs.getValue() == null || nftTxs.getValue().isEmpty()){
                log.debug("Failed to load txs for " + nft + " - " + nftTxs);
                return null;
            }
            var nftTx = nftTxs.getValue().get(0);

            var txUtxos = transactionService.getTransactionUtxos(nftTx.getTxHash());
            if(txUtxos.getValue() == null){
                throw new IllegalStateException("Failed to load pool tx [" + nftTx + "]");
            }
            var poolUtxo = txUtxos.getValue().getOutputs().stream()
                    .map(utxo -> utxo.toUtxos(nftTx.getTxHash()))
                    .filter(this::isValidPoolOutput)
                    .filter(utxo -> StringUtils.isNotBlank(utxo.getDataHash()))
                    .findFirst();

            return poolUtxo
                    .map(utxo -> dexConfig.poolStateProvider().fromUtxo(utxo, scriptService))
                    .orElse(null);
        }catch(Exception e){
            log.error("Failed to load pool for id " + poolId, e);
            throw new IllegalStateException("Failed to load pool for id " + poolId, e);
        }
    }
    @Override
    public List<PoolState> getAllPools() {
        return getAllPools(null);
    }
    private List<PoolState> getAllPools(Predicate<PoolState> predicate) {
        var allPools = new ArrayList<PoolState>();
        var poolAddresses = getPoolAddress();
        for(var poolAddress : poolAddresses){
            var page = 1;
            while(true){
                var poolsOpt = getPools(page, 100, poolAddress);
                if(poolsOpt.isEmpty()){
                    break;
                }
                allPools.addAll(poolsOpt.get().stream()
                        .filter(it -> predicate == null || predicate.test(it))
                        .toList());
                page += 1;
            }
        }
        return allPools;
    }
    private Optional<List<PoolState>> getPools(int page, int count, String poolAddress){
        try{
            var result = utxoService.getUtxos(poolAddress, dexConfig.getPoolAssetId(), count, page, OrderEnum.asc).getValue();
            if(result == null || result.isEmpty()){
                return Optional.empty();
            }

            return Optional.of(result.stream()
                    .filter(this::isValidPoolOutput)
                    .map(it -> dexConfig.poolStateProvider().fromUtxo(it, scriptService))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));

        }catch(Exception e){
            log.error("Failed to get pools", e);
            throw new IllegalStateException("Failed to get pools", e);
        }
    }
    private boolean isValidPoolOutput(Utxo utxo){
        var poolAddresses = getPoolAddress();
        if(poolAddresses == null || !poolAddresses.contains(utxo.getAddress())){
            log.debug("Skipping pool output... utxo [" + utxo + "] not valid, wrong pool address");
            return false;
        }
        var amounts = utxo.getAmount();

        if(amounts == null
                || amounts.stream()
                .filter(it -> StringUtils.equals(it.getUnit(), dexConfig.getPoolAssetId()))
                .count() != 1){
            log.debug("Skipping pool output... utxo [" + utxo + "] not valid, missing asset id");
            return false;
        }
        var nft = amounts.stream().filter(it -> StringUtils.startsWith(it.getUnit(), dexConfig.poolNftPolicyId()))
                .findFirst();
        if(nft.isEmpty()){
            log.debug("Skipping pool output... utxo [" + utxo + "] not valid, missing NFT");
            return false;
        }
//        var poolId = StringUtils.substring(nft.get().getUnit(), 56);
//        var relevantAssets = amounts.stream()
//                .filter(it -> !StringUtils.startsWith(it.getUnit(), dexConfig.factoryPolicyId())
//                        && !StringUtils.endsWith(it.getUnit(), poolId)) // NFT and LP tokens from profit sharing
//                .toList();
        var relevantAssets = amounts.stream()
                .filter(it -> !StringUtils.equals(it.getUnit(), dexConfig.getPoolAssetId())
                        && !StringUtils.startsWith(it.getUnit(), dexConfig.lpPolicyId())
                        && !StringUtils.startsWith(it.getUnit(), dexConfig.poolNftPolicyId()))
                .toList();
        if(relevantAssets.size() < 2){
            log.debug("Skipping pool output... utxo [" + utxo + "] not valid, " + relevantAssets.size() + " relevant assets...");
            return false;
        }
        if(StringUtils.isBlank(utxo.getDataHash())){
            log.debug("Skipping pool output... utxo [" + utxo + "] not valid, missing data hash");
            return false;
        }
        return true;
    }
    @Override
    public Tuple<BigDecimal, BigDecimal> getPrice(String poolId) {
        var pool = getPool(poolId);
        return pool != null
                    ? getPoolPrice(pool, null, null)
                    : null;
    }
    @Override
    public Tuple<BigDecimal, BigDecimal> getPrice(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName) {
        var normalized = DefaultPoolStateProvider.normalizeAssets(TokenUtil.getUnit(assetAPolicyId, assetATokenName), TokenUtil.getUnit(assetBPolicyId, assetBTokenName));
        var possiblePools = getAllPools(pool -> (StringUtils.equals(pool.getAssetA(), normalized._1)
                && StringUtils.equals(pool.getAssetB(), normalized._2))
        );

        return possiblePools.stream()
                .filter(it -> it.liquidity() != null)
                .sorted((it1, it2) -> it2.liquidity().compareTo(it1.liquidity()))
                .findFirst()
                .flatMap(pool -> {
                    var poolPrice = getPoolPrice(pool, null, null);
                    return Optional.ofNullable(poolPrice);
                }).orElse(null);
    }
    /**
     * Get pool price.
     * @param pool - The pool we want to get price.
     * @param decimalsA - The decimals of assetA in pool, if undefined then query from Blockfrost.
     * @param decimalsB - The decimals of assetB in pool, if undefined then query from Blockfrost.
     * @return Returns a pair of asset A/B price and B/A price, adjusted to decimals.
     */
    private Tuple<BigDecimal, BigDecimal> getPoolPrice(PoolState pool,
                                                       Integer decimalsA,
                                                       Integer decimalsB){
        if(decimalsA == null){
            decimalsA = getAssetDecimals(pool.getAssetA());
        }
        if(decimalsB == null){
            decimalsB = getAssetDecimals(pool.getAssetB());
        }

        return pool.getPrice(decimalsA, decimalsB);
    }
    @Override
    public int getAssetDecimals(String policyId, String tokenName) {
        var asset = TokenUtil.getUnit(policyId, tokenName);
        return getAssetDecimals(asset);
    }
    private int getAssetDecimals(String asset){
        if(StringUtils.equals(asset, CardanoConstants.LOVELACE)){
            return 6;
        }
        if(this.assetDecimals.containsKey(asset)){
            return this.assetDecimals.get(asset);
        }
        try{
            var assetAInfo = assetService.getAsset(asset);
            if(assetAInfo == null || assetAInfo.getValue() == null){
                throw new IllegalStateException("No asset found for [" + asset + "]");
            }
            if(!Networks.mainnet().equals(this.dexConfig.network())
                && (assetAInfo.getValue().getMetadata() == null || !assetAInfo.getValue().getMetadata().has("decimals"))){
                return 6;
            }
            var result = assetAInfo.getValue().getMetadata() != null && assetAInfo.getValue().getMetadata().has("decimals")
                    ? assetAInfo.getValue().getMetadata().get("decimals").intValue()
                    : 0;
            this.assetDecimals.put(asset, result);
            return result;
        }catch(Exception e){
            log.error("Failed to get asset decimals for [" + asset + "]", e);
            throw new IllegalStateException(e);
        }
    }
    @Override
    public PoolState getPool(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName) {
        var normalized = DefaultPoolStateProvider.normalizeAssets(TokenUtil.getUnit(assetAPolicyId, assetATokenName), TokenUtil.getUnit(assetBPolicyId, assetBTokenName));
        var possiblePools = getAllPools(pool -> (StringUtils.equals(pool.getAssetA(), normalized._1)
                && StringUtils.equals(pool.getAssetB(), normalized._2))
        );
        return possiblePools.stream()
                .sorted((it1, it2) -> it2.liquidity().compareTo(it1.liquidity()))
                .findFirst()
                .orElse(null);
    }
    private String swap(Account sender, String amountInPolicyId, String amountInTokenName, BigInteger amountIn, Long ttl, PlutusData datum, Metadata metadata) {
        try{
            var lovelaceAmount = Amount.lovelace(BigIntegerUtil.sum(StringUtils.isBlank(amountInPolicyId) && StringUtils.equals(amountInTokenName, CardanoConstants.LOVELACE) ? amountIn : BigInteger.ZERO, dexConfig.outputLovelace(), dexConfig.swapFee()));
            var otherAmount = StringUtils.isNotBlank(amountInPolicyId) && !StringUtils.equals(amountInTokenName, CardanoConstants.LOVELACE)
                    ? Amount.asset(TokenUtil.getUnit(amountInPolicyId, amountInTokenName), amountIn)
                    : null;
            var amounts = new ArrayList<Amount>();
            amounts.add(lovelaceAmount);
            if(otherAmount != null){
                amounts.add(otherAmount);
            }

            Tx tx = new Tx()
                    .payToContract(dexConfig.orderAddress(), amounts, datum.getDatumHash())
                    .attachMetadata(metadata)
                    .from(sender.baseAddress());

            byte[] scriptDataHash = ScriptDataHashGenerator.generate(Collections.emptyList(),
                    Collections.singletonList(datum), CostModelUtil.getLanguageViewsEncoding(dexConfig.costModel()));

            QuickTxBuilder builder = new QuickTxBuilder(backendService);
            Result<String> result = builder.compose(tx)
                    .withUtxoSelectionStrategy(new RandomImproveUtxoSelectionStrategy(new DefaultUtxoSupplier(backendService.getUtxoService()), false))
                    .withSigner(SignerProviders.signerFrom(sender))
                    .preBalanceTx((txBuilderContext, transaction) -> {
                        transaction.getWitnessSet().getPlutusDataList().add(datum);
                        transaction.getBody().setScriptDataHash(scriptDataHash);
                        if(ttl != null){
                            transaction.getBody().setTtl(ttl);
                        }
                    })
                    .mergeOutputs(false)
                    .withTxInspector(txn -> log.debug("tx: " + JsonUtil.getPrettyJson(txn)))
                    .completeAndWait(log::debug);

            log.debug("Result: " + result);
            return result.getValue();
        }catch(Exception e){
            log.error("Failed to swap", e);
            throw new IllegalStateException("Failed to swap", e);
        }
    }
    @Override
    public String swap(Account sender, OrderDefinition orderDefinition, Duration invalidAfter) {
        return swap(sender,
                orderDefinition.getAssetInPolicyId(),
                orderDefinition.getAssetInTokenName(),
                orderDefinition.getAmountIn(),
                invalidAfter != null ? getTtl(invalidAfter) : null,
                dexConfig.orderDefinitionProvider().toDatum(orderDefinition),
                dexConfig.metadataProvider().toMetadata(orderDefinition));
    }
    @Override
    public UtxoOrder getOrder(String transactionId) {
        try{
            var txUtxoContent = transactionService.getTransactionUtxos(transactionId);
            if(!txUtxoContent.isSuccessful()){
                throw new IllegalStateException("Failed to fetch tx UTXOs for " + transactionId + " - " + txUtxoContent.getResponse());
            }
            return txUtxoContent.getValue()
                    .getOutputs().stream()
                    .filter(utxo -> StringUtils.equals(utxo.getAddress(), dexConfig.orderAddress()))
                    .map(output -> output.toUtxos(transactionId))
                    .findFirst()
                    .flatMap(utxo -> Optional.ofNullable(buildOrder(utxo)))
                    .orElse(null);
        }catch(Exception e){
            log.warn("Failed to get order for " + transactionId, e);
            throw new IllegalStateException("Failed to get order for " + transactionId, e);
        }
    }
    @Override
    public OrderBook getOpenOrders(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName){
        try{
            var swapFee = dexConfig.swapFee();
            var returnLovelace = dexConfig.outputLovelace();
            var minAda = BigIntegerUtil.sum(swapFee, returnLovelace);

            var utxos = getUtxos(dexConfig.orderAddress(), utxo -> {
                if(StringUtils.isBlank(utxo.getDataHash())){
                    return false;
                }
                var checkSell = utxo.getAmount().stream()
                        .filter(amount -> TokenUtil.equals(TokenUtil.getUnit(assetBPolicyId, assetBTokenName), amount.getUnit()))
                        .filter(amount -> amount.getQuantity() != null && BigIntegerUtil.isPositive(amount.getQuantity()))
                        .filter(amount -> !TokenUtil.equals(TokenUtil.getUnit(null, CardanoConstants.LOVELACE), amount.getUnit())
                                || amount.getQuantity().compareTo(minAda) > 0)
                        .findFirst()
                        .isPresent();
                var checkBuy = !checkSell
                        && utxo.getAmount().stream()
                            .filter(amount -> TokenUtil.equals(TokenUtil.getUnit(assetAPolicyId, assetATokenName), amount.getUnit()))
                            .filter(amount -> amount.getQuantity() != null && BigIntegerUtil.isPositive(amount.getQuantity()))
                            .filter(amount -> !TokenUtil.equals(TokenUtil.getUnit(null, CardanoConstants.LOVELACE), amount.getUnit())
                                    || amount.getQuantity().compareTo(minAda) > 0)
                            .findFirst()
                            .isPresent();
                return checkBuy || checkSell;
            });

            log.debug("Selected [" + utxos.size() + "] UTXOs");

            var utxoOrders = utxos.stream().map(this::buildOrder)
                    .filter(it -> it.orderDefinition() != null)
                    .toList();

            log.debug("converted [" + utxoOrders.size() + "] UTXOs to orders");

            var buyUtxos = utxoOrders.stream()
                    .filter(it -> TokenUtil.equals(it.orderDefinition().getAssetInPolicyId(), assetAPolicyId)
                            && TokenUtil.equals(it.orderDefinition().getAssetInTokenName(), assetATokenName)
                            && TokenUtil.equals(it.orderDefinition().getAssetOutPolicyId(), assetBPolicyId)
                            && TokenUtil.equals(it.orderDefinition().getAssetOutTokenName(), assetBTokenName))
                    .collect(Collectors.toList());
            var sellUtxos = utxoOrders.stream()
                    .filter(it -> TokenUtil.equals(it.orderDefinition().getAssetInPolicyId(), assetBPolicyId)
                            && TokenUtil.equals(it.orderDefinition().getAssetInTokenName(), assetBTokenName)
                            && TokenUtil.equals(it.orderDefinition().getAssetOutPolicyId(), assetAPolicyId)
                            && TokenUtil.equals(it.orderDefinition().getAssetOutTokenName(), assetATokenName))
                    .collect(Collectors.toList());

            log.debug("converted [" + utxoOrders.size() + "] UTXOs to " + buyUtxos.size() + " buy orders and " + sellUtxos.size() + " sell orders...");

            return new OrderBook(assetAPolicyId, assetATokenName, assetBPolicyId, assetBTokenName, buyUtxos, sellUtxos, getAssetDecimals(TokenUtil.getUnit(assetAPolicyId, assetATokenName)), getAssetDecimals(TokenUtil.getUnit(assetBPolicyId, assetBTokenName)));

        }catch(Exception e){
            log.warn("Failed to get open orders for " + assetAPolicyId + " - " + assetATokenName + " vs " + assetBPolicyId + " - " + assetBTokenName, e);
            throw new IllegalStateException("Failed to get open orders for " + assetAPolicyId + " - " + assetATokenName + " vs " + assetBPolicyId + " - " + assetBTokenName, e);
        }
    }
    private UtxoOrder buildOrder(Utxo utxo){
        var definition = buildOrderDefinition(utxo);
        return new UtxoOrder(utxo, definition);
    }
    private OrderDefinition buildOrderDefinition(Utxo utxo){
        try{
            if(StringUtils.isBlank(utxo.getDataHash())){
                throw new IllegalArgumentException("Data hash not set");
            }

            var datum = getDatum(utxo.getDataHash());
            var definition = this.dexConfig.orderDefinitionProvider().fromUtxo(utxo, datum);
            if(definition == null){
                log.debug("Invalid pool order [" + utxo + "], definition not available...");
                return null;
            }
            return  definition;
        }catch(Exception e){
            log.debug("Failed to build order for " + utxo + " - " + ExceptionUtils.getMessage(e));
            return null;
        }
    }
    private List<Utxo> getUtxos(String address, Predicate<Utxo> predicate){
        return getUtxos(address, predicate, 1, new ArrayList<>());
    }
    private List<Utxo> getUtxos(String address, Predicate<Utxo> predicate, int page, List<Utxo> fetched){
        try{
            var pageUtxos = utxoService.getUtxos(address, 100, page);
            if(pageUtxos == null || pageUtxos.getValue() == null || pageUtxos.getValue().isEmpty()){
                return fetched;
            }
            // add to fetched + invoke again
            fetched.addAll(pageUtxos.getValue()
                    .stream()
                    .filter(it -> predicate == null || predicate.test(it))
                    .toList());
            return getUtxos(address, predicate, page + 1, fetched);
        }catch(Exception e){
            log.warn("Failed to fetch UTXOs for [" + address + "] w page [" + page + "]");
            return fetched;
        }
    }
    private Map<String, String> getMetadata(String txHash){
        try{
            List<MetadataJSONContent> content = metadataService.getJSONMetadataByTxnHash(txHash).getValue();
            return content.stream()
                    .map(it -> new Tuple<>(it.getLabel(), it.getJsonMetadata().has("msg")
                            ? it.getJsonMetadata().get("msg").get(0).asText()
                            : it.getJsonMetadata().asText()))
                    .collect(Collectors.toMap(it -> it._1, it -> it._2));
        }catch(Exception e){
            throw new IllegalStateException("Failed to get metadata for hash [" + txHash + "]", e);
        }
    }
    private PlutusData getDatum(String datumHash){
        JsonNode json = null;
        try{
            json = scriptService.getScriptDatum(datumHash).getValue().getJsonValue();
            return PlutusDataJsonConverter.toPlutusData(json);
        }catch(Exception e){
            throw new IllegalStateException("Failed to get datum for hash [" + datumHash + "] w json [" + (json != null ? json.asText() : null), e);
        }
    }
    private String getScriptData(String scriptAddress){
        try{
            var scriptHash = AddressUtil.getScriptHashFromAddress(scriptAddress);
            return scriptService.getPlutusScriptCbor(scriptHash).getValue();
        }catch (Exception e){
            throw new IllegalStateException("Failed to get script data for " + scriptAddress, e);
        }
    }
    @Override
    public String cancelSwap(Account sender, Utxo orderUtxo, Duration invalidAfter) {
        try{
            log.info("UTXO [" + orderUtxo + "] will be cancelled...");
            var definition = buildOrderDefinition(orderUtxo);
            if(definition == null){
                throw new IllegalArgumentException("Failed to build definition for [" + orderUtxo + "]");
            }
            var orderDatum = dexConfig.orderDefinitionProvider().toDatum(definition);
            var redeemerDatum = dexConfig.orderDefinitionProvider().toRedeemerDatum(definition);

            var scriptData = getScriptData(dexConfig.orderAddress());
            var dexScript = StringUtils.isNotBlank(scriptData)
                    ? Language.PLUTUS_V1.equals(dexConfig.plutusLanguage())
                        ? PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(scriptData, PlutusVersion.v1)
                        : PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(scriptData, PlutusVersion.v2)
                : null;
            log.debug("dex script: " + dexScript);

            ScriptTx scriptTx = new ScriptTx()
                    .collectFrom(orderUtxo, redeemerDatum)
                    .payToAddress(sender.baseAddress(), orderUtxo.getAmount())
                    .attachMetadata(MessageMetadata.create().add("JADEX: cancel order"))
                    .attachSpendingValidator(dexScript);

            var collateral = this.collateralProvider.create(sender);

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            var builder = quickTxBuilder.compose(scriptTx)
                    .withUtxoSelectionStrategy(new RandomImproveUtxoSelectionStrategy(new DefaultUtxoSupplier(backendService.getUtxoService()), false))
                    .feePayer(sender.baseAddress())
                    .withSigner(SignerProviders.signerFrom(sender.hdKeyPair()))
                    .withCollateralInputs(TransactionInput.builder().transactionId(collateral.utxoHash()).index(collateral.utxoIndex()).build())
                    .withRequiredSigners(new Address(sender.baseAddress()))
                    .preBalanceTx((txBuilderContext, transaction) -> {
//                        transaction.getBody().setRequiredSigners(List.of(new Address(sender.baseAddress()).getPaymentCredentialHash().orElseThrow()));
                        transaction.getWitnessSet().getPlutusDataList().add(orderDatum);
                        if(invalidAfter != null){
                            transaction.getBody().setTtl(getTtl(invalidAfter));
                        }
                    })
                    .mergeOutputs(false)
                    .withTxInspector(txn -> log.debug("tx: " + JsonUtil.getPrettyJson(txn)));

            var result = builder.completeAndWait(log::debug);
            log.debug("result: " + result);
            return result.getValue();

        }catch(Exception e){
            throw new IllegalStateException("Failed to cancel swap for utxo [" + orderUtxo + "]", e);
        }
    }
    private long getTtl(Duration duration) {
        try{
            Block block = blockService.getLatestBlock().getValue();
            long slot = block.getSlot();
            return slot + duration.getSeconds();
        }catch(ApiException e){
            throw new IllegalStateException("Failed to fetch latest block", e);
        }
    }
}
