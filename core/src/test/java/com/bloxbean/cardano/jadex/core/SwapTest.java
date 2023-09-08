package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.jadex.core.arguments.ConfigArgumentsProvider;
import com.bloxbean.cardano.jadex.core.arguments.TestOnlyAssetPairArgumentsProvider;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.order.book.UtxoOrder;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.util.AddressUtil;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
public class SwapTest extends JadexBaseTest{

    // TODO add test for swap exact out order

    @ParameterizedTest
    @ArgumentsSource(ConfigArgumentsProvider.class)
    void testOrderScriptParsing(DexConfig dexConfig, Dex dex) throws Exception{
        var scriptHash = AddressUtil.getScriptHashFromAddress(dexConfig.orderAddress());

        var cbor = getBackendService(dexConfig.network()).getScriptService()
                .getPlutusScriptCbor(scriptHash).getValue();
        log.debug("Script CBOR: " + cbor);

        var expected = DexTestConfigs.all().stream()
                        .filter(it -> it.dexConfig().equals(dexConfig))
                        .findFirst()
                        .map(DexTestConfig::orderScript)
                        .orElse(null);

        log.debug("expected: " + expected);

        Assertions.assertEquals(expected, cbor);
    }

    @ParameterizedTest
    @ArgumentsSource(TestOnlyAssetPairArgumentsProvider.class)
    void testPlaceBuyAndSellOrder(String assetA, String assetB, DexConfig dexConfig, Dex dex) throws Exception{
        // first swap ada for min
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);
        var pricePair = dex.getPrice(policyIdA, tokenNameA, policyIdB, tokenNameB);
        log.debug("pricePair: " + pricePair);
        var price = pricePair._1;
        log.debug("price: " + price);
        Assertions.assertNotNull(price);

        Account sender = getSender(dexConfig.network());
        log.debug("sender: " + sender.baseAddress());

        var returnLovelace = dexConfig.outputLovelace();
        var swapFee = dexConfig.swapFee();

        BigInteger assetAToSpend;
        if(CardanoConstants.LOVELACE.equals(assetA)){
            assetAToSpend = lovelacePerTest;

            var available = getAllUtxos(sender.baseAddress(), assetA, getBackendService(dexConfig.network()))
                    .stream()
                    .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(assetA, amt.getUnit())))
                    .map(Amount::getQuantity)
                    .reduce(BigIntegerUtil::sum)
                    .orElse(BigInteger.ZERO);
            log.debug("available: " + available);
        }else{
            // find price of B/Ada
            var adaPrice = dex.getPrice(policyIdA, tokenNameA, null, CardanoConstants.LOVELACE)._1;
            log.debug("adaPrice: " + adaPrice);
            BigInteger nonDecimalPrice = BigIntegerUtil.toBigInteger(adaPrice, dex.getAssetDecimals(null, CardanoConstants.LOVELACE));
            // check how much required
            assetAToSpend = new BigDecimal(lovelacePerTest).divide(new BigDecimal(nonDecimalPrice), 0, RoundingMode.DOWN).toBigInteger();
            log.debug("assetAToSpend: " + assetAToSpend);
            // check if available
            var available = getAllUtxos(sender.baseAddress(), assetA, getBackendService(dexConfig.network()))
                    .stream()
                    .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(assetA, amt.getUnit())))
                    .map(Amount::getQuantity)
                    .reduce(BigIntegerUtil::sum)
                    .orElse(BigInteger.ZERO);
            log.debug("available: " + available);
            if(available.compareTo(assetAToSpend) <= 0){
                // request
                requestFunds(assetA, lovelacePerTest.multiply(BigInteger.TWO), dexConfig, dex);
                Thread.sleep(30 * 1000);
            }
        }

        BigInteger nonDecimalPrice = BigIntegerUtil.toBigInteger(price, dex.getAssetDecimals(policyIdA, tokenNameA));

        log.debug("assetAToSpend: " + assetAToSpend);


        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);
        // BUY: A/B price
        var minTokenAmount = pool.getAmountOut(TokenUtil.getUnit(policyIdA, tokenNameA), assetAToSpend).amount();
        log.debug("minTokenAmount: " + minTokenAmount);
        minTokenAmount = minTokenAmount.divide(BigInteger.TWO); // ask half as much
        log.debug("adjusted minTokenAmount: " + minTokenAmount);

        OrderDefinition definition = OrderDefinition.builder()
                .assetInPolicyId(policyIdA)
                .assetInTokenName(tokenNameA)
                .assetOutPolicyId(policyIdB)
                .assetOutTokenName(tokenNameB)
                .amountIn(assetAToSpend)
                .swapFee(swapFee)
                .returnLovelace(returnLovelace)
                .minimumAmountOut(minTokenAmount)
                .paymentKeyHash(sender.hdKeyPair().getPublicKey().getKeyHash())
                .stakeKeyHash(sender.stakeHdKeyPair().getPublicKey().getKeyHash())
                .build();

        var minAmountBeforeSwap = getAllUtxos(sender.baseAddress(), TokenUtil.getUnit(policyIdB, tokenNameB), getBackendService(dexConfig.network()))
                .stream()
                .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(TokenUtil.getUnit(policyIdB, tokenNameB), amt.getUnit())))
                .map(Amount::getQuantity)
                .reduce(BigInteger::add)
                .orElse(BigInteger.ZERO);
        log.debug("minAmountBeforeSwap: " + minAmountBeforeSwap);

        // place buy order for half of price (will not get filled)
        var orderTxId = dex.swap(sender, definition, Duration.ofSeconds(240));
        log.debug("orderTxId: " + orderTxId);
        Assertions.assertNotNull(orderTxId);
        waitForTransaction(orderTxId, getBackendService(dexConfig.network()));
        Thread.sleep(1000 * 120);

        // verify incoming tx
        BigInteger tokenReceived = null;
        int index = 0;
        while(index <= 20){
            var minAmountAfterSwap = getAllUtxos(sender.baseAddress(), TokenUtil.getUnit(policyIdB, tokenNameB), getBackendService(dexConfig.network()))
                    .stream()
                    .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(TokenUtil.getUnit(policyIdB, tokenNameB), amt.getUnit())))
                    .map(Amount::getQuantity)
                    .reduce(BigInteger::add)
                    .orElse(BigInteger.ZERO);
            log.debug("minAmountAfterSwap: " + minAmountAfterSwap);

            if(minAmountAfterSwap.compareTo(minAmountBeforeSwap) > 0){
                // find UTXO
                log.debug("found received UTXO: " + minAmountAfterSwap);
                tokenReceived = minAmountAfterSwap.subtract(minAmountBeforeSwap);
                break;
            }
            Thread.sleep(20000);
            index += 1;
        }
        Assertions.assertNotNull(tokenReceived);

        log.debug("tokenReceived: " + tokenReceived);
        Assertions.assertNotNull(tokenReceived);
        Assertions.assertTrue(tokenReceived.compareTo(minTokenAmount) >= 0);

        Thread.sleep(1000 * 60);

        // then swap min for ada
        var minToSpend = tokenReceived;

        var minAssetAAmount = pool.getAmountOut(TokenUtil.getUnit(policyIdB, tokenNameB), minTokenAmount).amount();
        log.debug("minAssetAAmount: " + minAssetAAmount);
        minAssetAAmount = minAssetAAmount.divide(BigInteger.TWO);
        log.debug("adjusted minAssetAAmount: " + minAssetAAmount);

        OrderDefinition sellDefinition = OrderDefinition.builder()
                .assetInPolicyId(policyIdB)
                .assetInTokenName(tokenNameB)
                .assetOutPolicyId(policyIdA)
                .assetOutTokenName(tokenNameA)
                .amountIn(minToSpend)
                .swapFee(swapFee)
                .returnLovelace(returnLovelace)
                .minimumAmountOut(minAssetAAmount)
                .paymentKeyHash(sender.hdKeyPair().getPublicKey().getKeyHash())
                .stakeKeyHash(sender.stakeHdKeyPair().getPublicKey().getKeyHash())
                .build();

        log.debug("sellDefinition: " + sellDefinition);

        var assetAAmountBeforeSwap = getAllUtxos(sender.baseAddress(), assetA, getBackendService(dexConfig.network()))
                .stream()
                .peek(it -> log.debug("Checking assetA UTXO " + it))
                .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(assetA, amt.getUnit())))
                .map(Amount::getQuantity)
                .reduce(BigInteger::add)
                .orElse(BigInteger.ZERO);
        log.debug("assetAAmountBeforeSwap: " + assetAAmountBeforeSwap);
        Assertions.assertTrue(BigIntegerUtil.isPositive(assetAAmountBeforeSwap));

        var sellOrderTxId = dex.swap(sender, sellDefinition, Duration.ofSeconds(240));
        log.debug("sellOrderTxId: " + sellOrderTxId);
        Assertions.assertNotNull(sellOrderTxId);
        waitForTransaction(sellOrderTxId, getBackendService(dexConfig.network()));

        // verify ada is received in wallet
        BigInteger assetAReceived = null;
        index = 0;
        while(index <= 20){
            var assetAAmountAfterSwap = getAllUtxos(sender.baseAddress(), assetA, getBackendService(dexConfig.network()))
                    .stream()
                    .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(assetA, amt.getUnit())))
                    .map(Amount::getQuantity)
                    .reduce(BigInteger::add)
                    .orElse(BigInteger.ZERO);
            log.debug("assetAAmountAfterSwap: " + assetAAmountAfterSwap);

            if(assetAAmountAfterSwap.compareTo(assetAAmountBeforeSwap) > 0){
                // find UTXO
                log.debug("found received sell UTXO: " + assetAAmountAfterSwap);
                assetAReceived = assetAAmountAfterSwap.subtract(assetAAmountBeforeSwap);
                break;
            }
            Thread.sleep(20000);
            index += 1;
        }
        Assertions.assertNotNull(assetAReceived);

        log.debug("assetAReceived: " + assetAReceived);
        Assertions.assertNotNull(assetAReceived);
        Assertions.assertTrue(assetAReceived.compareTo(minAssetAAmount) >= 0);
    }

    @ParameterizedTest
    @ArgumentsSource(TestOnlyAssetPairArgumentsProvider.class)
    void testPlaceAndCancelSwapInOrder(String assetA, String assetB, DexConfig dexConfig, Dex dex) throws Exception{
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);
        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);

        var price = dex.getPrice(pool.id())._1;
        log.debug("price: " + price);
        Assertions.assertNotNull(price);

        Account sender = getSender(dexConfig.network());

        var swapFee = dexConfig.swapFee();
        var returnLovelace = dexConfig.outputLovelace();

        BigInteger assetAToSpend;
        if(CardanoConstants.LOVELACE.equals(assetA)){
            assetAToSpend = lovelacePerTest;
        }else{
            // find price of B/Ada
            var adaPrice = dex.getPrice(pool.id())._1;
            log.debug("adaPrice: " + adaPrice);
            BigInteger nonDecimalPrice = BigIntegerUtil.toBigInteger(adaPrice, dex.getAssetDecimals(policyIdA, tokenNameA));
            // check how much required
            assetAToSpend = new BigDecimal(lovelacePerTest).divide(new BigDecimal(nonDecimalPrice), 0, RoundingMode.DOWN).toBigInteger();
            log.debug("assetAToSpend: " + assetAToSpend);
            // check if available
            var available = getAllUtxos(sender.baseAddress(), assetA, getBackendService(dexConfig.network()))
                    .stream()
                    .flatMap(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(assetA, amt.getUnit())))
                    .map(Amount::getQuantity)
                    .reduce(BigIntegerUtil::sum)
                    .orElse(BigInteger.ZERO);
            log.debug("available: " + available);
            if(available.compareTo(assetAToSpend) <= 0){
                // request
                requestFunds(assetA, lovelacePerTest.multiply(BigInteger.TWO), dexConfig, dex);
                Thread.sleep(30 * 1000);
            }
        }

        BigInteger nonDecimalPrice = BigIntegerUtil.toBigInteger(price, dex.getAssetDecimals(policyIdB, tokenNameB));

        log.debug("assetAToSpend: " + assetAToSpend);

        var minTokenAmount = pool.getAmountOut(TokenUtil.getUnit(policyIdA, tokenNameA), assetAToSpend).amount().multiply(BigInteger.TWO); // ask twice as much;
        log.debug("minTokenAmount: " + minTokenAmount);

        OrderDefinition definition = OrderDefinition.builder()
                .assetInPolicyId(policyIdA)
                .assetInTokenName(tokenNameA)
                .assetOutPolicyId(policyIdB)
                .assetOutTokenName(tokenNameB)
                .amountIn(assetAToSpend)
                .swapFee(swapFee)
                .returnLovelace(returnLovelace)
                .minimumAmountOut(minTokenAmount)
                .paymentKeyHash(sender.hdKeyPair().getPublicKey().getKeyHash())
                .stakeKeyHash(sender.stakeHdKeyPair().getPublicKey().getKeyHash())
                .build();

        // place buy order for half of price (will not get filled)
        var orderTxId = dex.swap(sender, definition, Duration.ofSeconds(240));
        log.debug("orderTxId: " + orderTxId);
        Assertions.assertNotNull(orderTxId);
        waitForTransaction(orderTxId, getBackendService(dexConfig.network()));

        // find open order
        Thread.sleep(1000 * 60);

        // fetch directly
        UtxoOrder fetchedOrder = null;
        {
            int count = 0;
            while(count <= 10){
                log.info("Fetching order for tx [" + orderTxId + "]");
                fetchedOrder = dex.getOrder(orderTxId);
                if(fetchedOrder != null){
                    log.info("order encountered: " + fetchedOrder);
                    break;
                }
                Thread.sleep(1000 * 30);
                count++;
            }
        }
        Assertions.assertNotNull(fetchedOrder);
        Assertions.assertNotNull(fetchedOrder.orderDefinition());
        Assertions.assertEquals(definition.getAmountIn(), fetchedOrder.orderDefinition().getAmountIn());
        Assertions.assertEquals(definition.getSwapFee(), fetchedOrder.orderDefinition().getSwapFee());
        Assertions.assertEquals(definition.getAssetInPolicyId(), fetchedOrder.orderDefinition().getAssetInPolicyId());
        Assertions.assertEquals(definition.getAssetInTokenName(), fetchedOrder.orderDefinition().getAssetInTokenName());
        Assertions.assertEquals(definition.getAssetOutPolicyId(), fetchedOrder.orderDefinition().getAssetOutPolicyId());
        Assertions.assertEquals(definition.getAssetOutTokenName(), fetchedOrder.orderDefinition().getAssetOutTokenName());
        Assertions.assertEquals(definition.getMinimumAmountOut(), fetchedOrder.orderDefinition().getMinimumAmountOut());
        Assertions.assertEquals(definition.getReturnLovelace(), fetchedOrder.orderDefinition().getReturnLovelace());
        Assertions.assertEquals(definition.getSenderAddress(dexConfig.network()), fetchedOrder.orderDefinition().getSenderAddress(dexConfig.network()));

        Assertions.assertNotNull(fetchedOrder.utxo());
        var expectedAssetASpend = assetAToSpend;
        if(CardanoConstants.LOVELACE.equals(tokenNameA)){
            expectedAssetASpend = expectedAssetASpend.add(swapFee).add(returnLovelace);
        }
        Assertions.assertEquals(expectedAssetASpend,
                fetchedOrder.utxo().getAmount().stream().filter(it -> StringUtils.equals(assetA, it.getUnit())).map(Amount::getQuantity).findFirst().orElse(null));

        // fetch via order book
        log.info("Fetching open orders for asset [" + assetA + " - " + assetB + "]");
        var orderBook = dex.getOpenOrders(policyIdA, tokenNameA, policyIdB, tokenNameB);
        var filteredBuy = orderBook.getBuyOrders(sender.baseAddress());
        var filterBuyOpt = filteredBuy.stream()
                .map(tuple -> tuple._1)
                .filter(utxoOrder -> utxoOrder.utxo().getTxHash().equals(orderTxId))
                .findFirst();

        Assertions.assertTrue(filterBuyOpt.isPresent());
        Assertions.assertEquals(fetchedOrder, filterBuyOpt.get());

        var cancelTxId = dex.cancelSwap(sender, fetchedOrder.utxo(), Duration.ofSeconds(10 * 60));
        log.debug("cancelTxId: " + cancelTxId);
        Assertions.assertNotNull(cancelTxId);
        waitForTransaction(cancelTxId, getBackendService(dexConfig.network()));

        Thread.sleep(30 * 1000);

        // check received UTXO
        var cancelUtxos = getBackendService(dexConfig.network()).getTransactionService().getTransactionUtxos(cancelTxId);
        log.info("cancelUtxos: " + cancelUtxos);
        Assertions.assertNotNull(cancelUtxos.getValue());
        var expectedAssetAReturned = assetAToSpend.add(CardanoConstants.LOVELACE.equals(tokenNameA) ? returnLovelace : BigInteger.ZERO);

        Assertions.assertTrue(cancelUtxos.getValue().getOutputs()
                .stream()
                .filter(it -> it.getAddress().equals(sender.baseAddress()))
                .filter(it -> it.getAmount().stream().filter(amt -> StringUtils.equals(amt.getUnit(), assetA)
                        && new BigInteger(amt.getQuantity()).compareTo(expectedAssetAReturned) >= 0).findFirst().isPresent())
                .findFirst().isPresent());

        // verify order is no longer in open orders
        var orderBookAfterCancel = dex.getOpenOrders(policyIdA, tokenNameA, policyIdB, tokenNameB);
        var filteredBuyAfterCancel = orderBookAfterCancel.getBuyOrders(sender.baseAddress());
        var filterBuyAfterCancelOpt = filteredBuyAfterCancel.stream()
                .map(tuple -> tuple._1)
                .filter(utxoOrder -> utxoOrder.utxo().getTxHash().equals(orderTxId))
                .findFirst();
        log.info("filterBuyAfterCancelOpt: " + filterBuyAfterCancelOpt);
        Assertions.assertTrue(filterBuyAfterCancelOpt.isEmpty());
    }
}
