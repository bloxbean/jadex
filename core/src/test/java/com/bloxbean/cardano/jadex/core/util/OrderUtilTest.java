package com.bloxbean.cardano.jadex.core.util;

import com.bloxbean.cardano.jadex.core.pool.Pool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OrderUtilTest {

    @Test
    public void testSmartSplitWithSinglePool() {
        String assetIn = "AssetA";
        BigInteger amountIn = BigInteger.valueOf(500);
        int decimalsA = 18;
        int decimalsB = 18;
        BigDecimal slippagePercentage = BigDecimal.valueOf(0.1);

        // Create a list with a single pool
        List<Pool> allPools = new ArrayList<>();
        Pool pool1 = new Pool("pool1", assetIn, "AssetB", BigInteger.valueOf(16000), BigInteger.valueOf(15000), BigDecimal.ZERO);
        allPools.add(pool1);

        List<OrderUtil.AmountInPerPool> poolAmountsIn = OrderUtil.smartSplit(assetIn, amountIn, allPools, slippagePercentage, decimalsA, decimalsB);

        Assertions.assertEquals(1, poolAmountsIn.size());
        Assertions.assertEquals(amountIn, poolAmountsIn.get(0).getAmountIn());
    }

    @Test
    public void testSmartSplitWithMultiplePools() {
        String assetIn = "AssetA";
        BigInteger amountIn = BigInteger.valueOf(1000);
        int decimalsA = 18;
        int decimalsB = 18;
        BigDecimal slippagePercentage = BigDecimal.valueOf(0.1);

        // Create three pools with different prices
        List<Pool> allPools = new ArrayList<>();
        Pool pool1 = new Pool("pool1", assetIn, "AssetB", BigInteger.valueOf(16000), BigInteger.valueOf(15000), BigDecimal.ZERO);
        Pool pool2 = new Pool("pool2", assetIn, "AssetB", BigInteger.valueOf(15500), BigInteger.valueOf(15000), BigDecimal.ZERO);
        Pool pool3 = new Pool("pool3", assetIn, "AssetB", BigInteger.valueOf(20000), BigInteger.valueOf(20000), BigDecimal.ZERO);
        allPools.add(pool1);
        allPools.add(pool2);
        allPools.add(pool3);

        List<OrderUtil.AmountInPerPool> poolAmountsIn = OrderUtil.smartSplit(assetIn, amountIn, allPools, slippagePercentage, decimalsA, decimalsB);

        Assertions.assertEquals(3, poolAmountsIn.size());

        // Check that the sum of amounts in poolOrders equals the original amountIn
        BigInteger totalAmountIn = BigInteger.ZERO;
        for (OrderUtil.AmountInPerPool order : poolAmountsIn) {
            totalAmountIn = totalAmountIn.add(order.getAmountIn());
        }
        Assertions.assertEquals(amountIn, totalAmountIn);
    }

    @Test
    public void testSmartSplitWithNoBetterPool() {
        String assetIn = "AssetA";
        BigInteger amountIn = BigInteger.valueOf(1000);
        int decimalsA = 6;
        int decimalsB = 6;
        BigDecimal slippagePercentage = BigDecimal.valueOf(0.1);

        // Create three pools with the same or worse prices
        List<Pool> allPools = new ArrayList<>();
        Pool pool1 = new Pool("pool1", assetIn, "AssetB", BigInteger.valueOf(16000), BigInteger.valueOf(15000), BigDecimal.ZERO);
        Pool pool2 = new Pool("pool2", assetIn, "AssetB", BigInteger.valueOf(15500), BigInteger.valueOf(15500), BigDecimal.ZERO);
        Pool pool3 = new Pool("pool3", assetIn, "AssetB", BigInteger.valueOf(2000000), BigInteger.valueOf(2200000), BigDecimal.ZERO);
        allPools.add(pool1);
        allPools.add(pool2);
        allPools.add(pool3);

        List<OrderUtil.AmountInPerPool> poolAmountsIn = OrderUtil.smartSplit(assetIn, amountIn, allPools, slippagePercentage, decimalsA, decimalsB);

        Assertions.assertEquals(1, poolAmountsIn.size());
        Assertions.assertEquals(amountIn, poolAmountsIn.get(0).getAmountIn());
    }

    @Test
    public void testSmartSplitWithSlippage() {
        String assetIn = "AssetA";
        BigInteger amountIn = BigInteger.valueOf(1000);
        int decimalsA = 6;
        int decimalsB = 6;
        BigDecimal slippagePercentage = BigDecimal.valueOf(0.05); // Set a lower slippage

        // Create three pools with different prices
        List<Pool> allPools = new ArrayList<>();
        Pool pool1 = new Pool("pool1", assetIn, "AssetB", BigInteger.valueOf(16000), BigInteger.valueOf(15000), BigDecimal.ZERO);
        Pool pool2 = new Pool("pool2", assetIn, "AssetB", BigInteger.valueOf(15500), BigInteger.valueOf(15000), BigDecimal.ZERO);
        Pool pool3 = new Pool("pool3", assetIn, "AssetB", BigInteger.valueOf(20000), BigInteger.valueOf(20000), BigDecimal.ZERO);
        allPools.add(pool1);
        allPools.add(pool2);
        allPools.add(pool3);

        List<OrderUtil.AmountInPerPool> poolAmountsIn = OrderUtil.smartSplit(assetIn, amountIn, allPools, slippagePercentage, decimalsA, decimalsB);

        Assertions.assertEquals(2, poolAmountsIn.size());
        Assertions.assertEquals(BigInteger.valueOf(610), poolAmountsIn.get(0).getAmountIn().add(poolAmountsIn.get(1).getAmountIn()));
    }
}
