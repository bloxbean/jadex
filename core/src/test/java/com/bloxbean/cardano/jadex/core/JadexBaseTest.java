package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.dex.minswap.order.MinOrderDefinition;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JadexBaseTest{

    public static String getBlockfrostMainProjectId(){
        String bfProjectId = System.getProperty("MAIN_BF_PROJECT_ID");
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            bfProjectId = System.getenv("MAIN_BF_PROJECT_ID");
        }
        return bfProjectId;
    }
    public static String getBlockfrostTestProjectId(){
        String bfProjectId = System.getProperty("TST_BF_PROJECT_ID");
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            bfProjectId = System.getenv("TST_BF_PROJECT_ID");
        }
        return bfProjectId;
    }

    protected final BigInteger lovelacePerTest = BigInteger.valueOf(4000000);

    public String getBlockfrostProjectId(Network network){
        return Networks.mainnet().getNetworkId() == network.getNetworkId()
                ? getBlockfrostMainProjectId()
                : getBlockfrostTestProjectId();
    }
    public static String blockfrostUrl(Network network){
        return Networks.mainnet().getNetworkId() == network.getNetworkId()
                ? Constants.BLOCKFROST_MAINNET_URL
                : Constants.BLOCKFROST_PREPROD_URL;

    }
    public String getBlockfrostUrl(Network network){
        return blockfrostUrl(network);
    }
    public BackendService getBackendService(Network network) {
        return new BFBackendService(getBlockfrostUrl(network), getBlockfrostProjectId(network));
    }
    public TransactionContent waitForTransaction(String txId, BackendService backendService) {
        if(StringUtils.isBlank(txId)){
            throw new IllegalArgumentException("TxId is mandatory");
        }
        try {
            int count = 0;
            while (count < 60) {
                Result<TransactionContent> txnResult = backendService.getTransactionService().getTransaction(txId);
                if (txnResult.isSuccessful()) {
                    return txnResult.getValue();
                } else {
                    log.debug("Waiting for transaction to be included in a block ....");
                }

                count++;
                Thread.sleep(4000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("No tx found for [" + txId + "]");
    }
    protected List<Utxo> getAllUtxos(String address, String unit, BackendService backendService){
        try{
            List<Utxo> result = new ArrayList<>();
            int page = 1;
            while(true){
                var pageUtxos = StringUtils.equals(CardanoConstants.LOVELACE, unit)
                        ? backendService.getUtxoService().getUtxos(address, "", 100, page, OrderEnum.desc)
                        : backendService.getUtxoService().getUtxos(address, unit, 100, page, OrderEnum.desc);
                if(pageUtxos == null || pageUtxos.getValue() == null || pageUtxos.getValue().isEmpty()){
                    return result;
                }
                result.addAll(pageUtxos.getValue());
                page += 1;
            }
        }catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    protected Account getSender(Network network){
        String senderMnemonic = "expire birth alone drip snake smart swift scout grow orbit always calm father oval execute ring spice imitate slot vague blur fatigue news actress";
        return new Account(network, senderMnemonic);
    }

    /**
     * swap funds in specified unit against ADA in preparation of test
     */
    protected void requestFunds(String unit, BigInteger lovelaceToSpend, DexConfig dexConfig, Dex dex){
        try{
            log.info("requesting funds [" + unit + "]");
            var sender = getSender(dexConfig.network());
            var policyIdA = TokenUtil.getPolicyId(unit);
            var tokenNameA = TokenUtil.getTokenName(unit);
            var tokenNameB = TokenUtil.getTokenName(CardanoConstants.LOVELACE);
            var price = dex.getPrice(policyIdA, tokenNameA, null, tokenNameB)._1;
            price = price.multiply(new BigDecimal("1.5"));
            Assertions.assertNotNull(price);

            var swapFee = dexConfig.swapFee();
            var returnLovelace = dexConfig.outputLovelace();

            BigInteger nonDecimalPrice = BigIntegerUtil.toBigInteger(price, dex.getAssetDecimals(null, CardanoConstants.LOVELACE));

            log.debug("lovelaceToSpend: " + lovelaceToSpend);

            var pool = dex.getPool(policyIdA, tokenNameA, null, CardanoConstants.LOVELACE);
            var minTokenAmount = pool.getAmountOut(CardanoConstants.LOVELACE, lovelaceToSpend);
            log.debug("minTokenAmount: " + minTokenAmount);

            MinOrderDefinition definition = MinOrderDefinition.builder()
                    .assetInTokenName(CardanoConstants.LOVELACE)
                    .assetOutPolicyId(policyIdA)
                    .assetOutTokenName(tokenNameA)
                    .isLimitOrder(true)
                    .amountIn(lovelaceToSpend)
                    .swapFee(swapFee)
                    .returnLovelace(returnLovelace)
                    .minimumAmountOut(minTokenAmount.amount())
                    .paymentKeyHash(sender.hdKeyPair().getPublicKey().getKeyHash())
                    .stakeKeyHash(sender.stakeHdKeyPair().getPublicKey().getKeyHash())
                    .build();

            // place buy order for half of price (will not get filled)
            var orderTxId = dex.swap(sender, definition, Duration.ofSeconds(240));
            log.debug("funding orderTxId: " + orderTxId);
            Assertions.assertNotNull(orderTxId);
            waitForTransaction(orderTxId, getBackendService(dexConfig.network()));
            Thread.sleep(1000 * 60);
        }catch(Exception e){
            throw new IllegalStateException("Failed to fund", e);
        }
    }
}
