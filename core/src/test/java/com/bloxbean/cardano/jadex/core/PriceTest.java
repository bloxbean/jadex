package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.jadex.core.arguments.AssetPairArgumentsProvider;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import com.bloxbean.cardano.jadex.core.util.OrderUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@Slf4j
public class PriceTest extends JadexBaseTest{

    private BigInteger getAssetValue(String asset, BigInteger lovelaceValue, DexConfig dexConfig, Dex dex){
        if(CardanoConstants.LOVELACE.equals(asset)){
            return lovelaceValue;
        }else{
            var policyIdA = "";
            var tokenNameA = CardanoConstants.LOVELACE;
            var policyIdB = TokenUtil.getPolicyId(asset);
            var tokenNameB = TokenUtil.getTokenName(asset);

            // find price of B/Ada
            var adaPrice = dex.getPrice(policyIdA, tokenNameA, policyIdB, tokenNameB)._1;
            log.debug("adaPrice: " + adaPrice);
            BigInteger nonDecimalPrice = BigIntegerUtil.toBigInteger(adaPrice, dex.getAssetDecimals(policyIdA, tokenNameA));
            // check how much required
            return BigIntegerUtil.toBigInteger(new BigDecimal(lovelaceValue).divide(new BigDecimal(nonDecimalPrice), 8, RoundingMode.HALF_DOWN), dex.getAssetDecimals(policyIdB, tokenNameB));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testPoolPriceOut(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);

        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);

        var dexDecimalPrice = dex.getPrice(pool.id());
        Assertions.assertNotNull(dexDecimalPrice);
        Assertions.assertTrue(dexDecimalPrice._1.compareTo(BigDecimal.ZERO) > 0);
        Assertions.assertTrue(dexDecimalPrice._2.compareTo(BigDecimal.ZERO) > 0);

        // covert AB price to BA price
        var priceAB = dexDecimalPrice._1; // price in ADA (1 min = ? ADA)
        var priceBA = dexDecimalPrice._2; // price in MIN (1 ADA = ? min)

        var assetAProvided = getAssetValue(assetA, lovelacePerTest, dexConfig, dex); //BigInteger.valueOf(6000000);
        // BUY (min tokens to receive)
        // priceBA (bigint) * assetAProvided (bigInt) / 1 (bigdec w assetA decimals)
        var assetBToReceive = BigIntegerUtil.toBigInteger(priceBA, dex.getAssetDecimals(policyIdB, tokenNameB))
                .multiply(assetAProvided).divide(new BigInteger("10").pow(dex.getAssetDecimals(policyIdA, tokenNameA)));

        // priceAB (bigint) * assetBProvided (bigInt) / 1 (bigdec w assetB decimals)
        var assetAToReceive = BigIntegerUtil.toBigInteger(priceAB, dex.getAssetDecimals(policyIdA, tokenNameA))
                .multiply(assetBToReceive).divide(new BigInteger("10").pow(dex.getAssetDecimals(policyIdB, tokenNameB)));

        Assertions.assertTrue(assetAToReceive.multiply(new BigInteger("101")).divide(new BigInteger("100")).compareTo(assetAProvided)  >= 0);
        Assertions.assertTrue(assetAToReceive.multiply(new BigInteger("99")).divide(new BigInteger("100")).compareTo(assetAProvided)  <= 0);

        var swapAmountOutB = pool.getAmountOut(TokenUtil.getUnit(policyIdA, tokenNameA), assetAProvided);

        var poolFeePercentage = new BigDecimal("0.003");
        var assetBToReceiveWithPoolFee = new BigDecimal(assetBToReceive).multiply(BigDecimal.ONE.subtract(poolFeePercentage)).setScale(0, RoundingMode.HALF_DOWN).toBigInteger();
        Assertions.assertTrue(assetBToReceiveWithPoolFee.multiply(new BigInteger("110")).divide(new BigInteger("100")).compareTo(swapAmountOutB.amount())  >= 0);
        Assertions.assertTrue(assetBToReceiveWithPoolFee.multiply(new BigInteger("90")).divide(new BigInteger("100")).compareTo(swapAmountOutB.amount())  <= 0);

        var swapAmountOutA = pool.getAmountOut(TokenUtil.getUnit(policyIdB, tokenNameB), swapAmountOutB.amount());

        var assetAToReceiveWithPoolFee = new BigDecimal(assetAToReceive).multiply(BigDecimal.ONE.subtract(poolFeePercentage)).setScale(0, RoundingMode.HALF_DOWN).toBigInteger();

        Assertions.assertTrue(assetAToReceiveWithPoolFee.multiply(new BigInteger("110")).divide(new BigInteger("100")).compareTo(swapAmountOutA.amount())  >= 0);
        Assertions.assertTrue(assetAToReceiveWithPoolFee.multiply(new BigInteger("90")).divide(new BigInteger("100")).compareTo(swapAmountOutA.amount())  <= 0);

        // test price impact
        var assetAHalfPool = pool.reserveA().divide(BigInteger.TWO);
        var swapAmountOutBHalfPool = pool.getAmountOut(TokenUtil.getUnit(policyIdA, tokenNameA), assetAHalfPool);
        Assertions.assertEquals(new BigDecimal("0.33"), swapAmountOutBHalfPool.priceImpact().setScale(2, RoundingMode.DOWN));

        var assetBHalfPool = pool.reserveB().divide(BigInteger.TWO);
        var swapAmountOutAHalfPool = pool.getAmountOut(TokenUtil.getUnit(policyIdB, tokenNameB), assetBHalfPool);
        Assertions.assertEquals(new BigDecimal("0.33"), swapAmountOutAHalfPool.priceImpact().setScale(2, RoundingMode.DOWN));
    }

    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testPoolPriceIn(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);

        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);

        var assetAExpected = getAssetValue(assetA, lovelacePerTest, dexConfig, dex);//BigInteger.valueOf(6000000);
        var swapAmountInB = pool.getAmountIn(TokenUtil.getUnit(policyIdA, tokenNameA), assetAExpected);

        var swapAmountOutA = pool.getAmountOut(TokenUtil.getUnit(policyIdB, tokenNameB), swapAmountInB.amount());

        Assertions.assertTrue(assetAExpected.multiply(new BigInteger("101")).divide(new BigInteger("100")).compareTo(swapAmountOutA.amount())  >= 0);
        Assertions.assertTrue(assetAExpected.multiply(new BigInteger("99")).divide(new BigInteger("100")).compareTo(swapAmountOutA.amount())  <= 0);

        // test price impact
        var assetAHalfPool = pool.reserveA().divide(BigInteger.TWO);
        var swapAmountInBHalfPool = pool.getAmountIn(TokenUtil.getUnit(policyIdA, tokenNameA), assetAHalfPool);
        Assertions.assertEquals(new BigDecimal("0.49"), swapAmountInBHalfPool.priceImpact().setScale(2, RoundingMode.DOWN));

        var assetBHalfPool = pool.reserveB().divide(BigInteger.TWO);
        var swapAmountInAHalfPool = pool.getAmountIn(TokenUtil.getUnit(policyIdB, tokenNameB), assetBHalfPool);
        Assertions.assertEquals(new BigDecimal("0.49"), swapAmountInAHalfPool.priceImpact().setScale(2, RoundingMode.DOWN));
    }

    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testGetOrderBuyPrice(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);

        var dexDecimalPrice = dex.getPrice(policyIdA, tokenNameA, policyIdB, tokenNameB);
        Assertions.assertNotNull(dexDecimalPrice);
        Assertions.assertTrue(dexDecimalPrice._1.compareTo(BigDecimal.ZERO) > 0);
        Assertions.assertTrue(dexDecimalPrice._2.compareTo(BigDecimal.ZERO) > 0);

        var amountIn = getAssetValue(assetA, lovelacePerTest, dexConfig, dex);

        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);

        var minTokenAmount = pool.getAmountOut(TokenUtil.getUnit(policyIdA, tokenNameA), amountIn);

        var calculatedAmountIn = pool.getAmountIn(TokenUtil.getUnit(policyIdB, tokenNameB), minTokenAmount.amount());

        Assertions.assertTrue(new BigDecimal(calculatedAmountIn.amount()).multiply(new BigDecimal("1.01")).compareTo(new BigDecimal(amountIn)) >= 0);
        Assertions.assertTrue(new BigDecimal(calculatedAmountIn.amount()).multiply(new BigDecimal("0.99")).compareTo(new BigDecimal(amountIn)) <= 0);

        var spend = BigIntegerUtil.toBigInteger(new BigDecimal(minTokenAmount.amount()).divide(new BigDecimal(BigIntegerUtil.toBigInteger(dexDecimalPrice._2, dex.getAssetDecimals(policyIdB, tokenNameB))), 8, RoundingMode.HALF_DOWN), dex.getAssetDecimals(policyIdA, tokenNameA));

        Assertions.assertTrue(spend.compareTo(amountIn.multiply(BigInteger.valueOf(890)).divide(BigInteger.valueOf(1000))) >= 0);
        Assertions.assertTrue(spend.compareTo(amountIn) <= 0);

        var returnLovelace = BigInteger.valueOf(2_000_000);
        var swapFee = BigInteger.valueOf(2_000_000);
        var calculatedPrice = OrderUtil.getPrice(new OrderDefinition(policyIdA, tokenNameA,
                amountIn, policyIdB, tokenNameB, minTokenAmount.amount(), null, null, returnLovelace, swapFee), assetA, dex.getAssetDecimals(policyIdA, tokenNameA), assetB, dex.getAssetDecimals(policyIdB, tokenNameB));

        Assertions.assertTrue(dexDecimalPrice._1.multiply(new BigDecimal("1.05")).compareTo(calculatedPrice._1) >= 0);
        Assertions.assertTrue(dexDecimalPrice._1.multiply(new BigDecimal("0.95")).compareTo(calculatedPrice._1) <= 0);

        Assertions.assertTrue(dexDecimalPrice._2.multiply(new BigDecimal("1.05")).compareTo(calculatedPrice._2) >= 0);
        Assertions.assertTrue(dexDecimalPrice._2.multiply(new BigDecimal("0.95")).compareTo(calculatedPrice._2) <= 0);
    }

    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testGetOrderSellPrice(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);
        var dexDecimalPrice = dex.getPrice(policyIdA, tokenNameA, policyIdB, tokenNameB);
        Assertions.assertNotNull(dexDecimalPrice);
        Assertions.assertTrue(dexDecimalPrice._1.compareTo(BigDecimal.ZERO) > 0);
        Assertions.assertTrue(dexDecimalPrice._2.compareTo(BigDecimal.ZERO) > 0);

        var amountIn = getAssetValue(assetB, lovelacePerTest, dexConfig, dex);//BigInteger.valueOf(5890);

        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);

        var calculatedAmountOut = pool.getAmountOut(assetB, amountIn);
        var minTokenAmount = pool.getAmountOut(TokenUtil.getUnit(policyIdB, tokenNameB), amountIn);

        Assertions.assertTrue(new BigDecimal(calculatedAmountOut.amount()).multiply(new BigDecimal("1.01")).compareTo(new BigDecimal(minTokenAmount.amount())) >= 0);
        Assertions.assertTrue(new BigDecimal(calculatedAmountOut.amount()).multiply(new BigDecimal("0.99")).compareTo(new BigDecimal(minTokenAmount.amount())) <= 0);

        var minTokenAmountBD = BigIntegerUtil.toBigDecimal(minTokenAmount.amount(), dex.getAssetDecimals(policyIdA, tokenNameA));

        var receivedBd = BigIntegerUtil.toBigDecimal(minTokenAmount.amount(), dex.getAssetDecimals(policyIdA, tokenNameA)).divide(dexDecimalPrice._1, 8, RoundingMode.HALF_DOWN);
        var received = BigIntegerUtil.toBigInteger(receivedBd, dex.getAssetDecimals(policyIdB, tokenNameB));

        Assertions.assertTrue(received.compareTo(amountIn.multiply(BigInteger.valueOf(950)).divide(BigInteger.valueOf(1000))) >= 0);
        Assertions.assertTrue(received.compareTo(amountIn.multiply(BigInteger.valueOf(1050)).divide(BigInteger.valueOf(1000))) <= 0);

        var returnLovelace = BigInteger.valueOf(2_000_000);
        var swapFee = BigInteger.valueOf(2_000_000);
        var calculatedPrice = OrderUtil.getPrice(new OrderDefinition(policyIdB, tokenNameB, amountIn, policyIdA, tokenNameA, minTokenAmount.amount(), null, null, returnLovelace, swapFee), TokenUtil.getUnit(policyIdA, tokenNameA), dex.getAssetDecimals(policyIdA, tokenNameA), TokenUtil.getUnit(policyIdB, tokenNameB), dex.getAssetDecimals(policyIdB, tokenNameB));

        Assertions.assertTrue(dexDecimalPrice._1.multiply(new BigDecimal("1.05")).compareTo(calculatedPrice._1) >= 0);
        Assertions.assertTrue(dexDecimalPrice._1.multiply(new BigDecimal("0.95")).compareTo(calculatedPrice._1) <= 0);

        Assertions.assertTrue(dexDecimalPrice._2.multiply(new BigDecimal("1.05")).compareTo(calculatedPrice._2) >= 0);
        Assertions.assertTrue(dexDecimalPrice._2.multiply(new BigDecimal("0.95")).compareTo(calculatedPrice._2) <= 0);
    }
}
