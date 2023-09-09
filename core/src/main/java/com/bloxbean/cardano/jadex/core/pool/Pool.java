
package com.bloxbean.cardano.jadex.core.pool;

import com.bloxbean.cardano.client.util.Tuple;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Contains the pools calculation logic.
 *
 * @author $stik
 */
@Getter
@ToString
public class Pool {
    private final String id;
    private final String assetA;
    private final String assetB;
    private BigInteger reserveA;
    private BigInteger reserveB;
    private final BigDecimal poolFeePercentage;

    public Pool(String id, String assetA, String assetB, BigInteger reserveA, BigInteger reserveB, BigDecimal poolFeePercentage) {
        this.id = id;
        this.assetA = assetA;
        this.assetB = assetB;
        this.reserveA = reserveA;
        this.reserveB = reserveB;
        this.poolFeePercentage = poolFeePercentage;
    }

    public void addA(){
        reserveA = reserveA.add(BigInteger.ONE);
        reserveB = reserveB.subtract(BigInteger.ONE);
    }
    public void addB(){
        reserveA = reserveA.subtract(BigInteger.ONE);
        reserveB = reserveB.add(BigInteger.ONE);
    }

    public BigInteger reserveA(){
        return this.reserveA;
    }
    public BigInteger reserveB(){
        return this.reserveB;
    }
    /**
     * Get the output amount if we swap a certain amount of a token in the pair
     * @param assetIn The asset that we want to swap from
     * @param amountIn The amount that we want to swap from
     * @return The amount of the other token that we get from the swap and its price impact
     */
    public SwapAmount getAmountOut(String assetIn, BigInteger amountIn){
        var poolFeePercent = this.poolFeePercentage;
        var poolFeeMultiplier = BigInteger.valueOf(10000);
        var poolFeeModifier = poolFeeMultiplier.subtract(poolFeePercent.multiply(new BigDecimal(poolFeeMultiplier)).setScale(0, RoundingMode.HALF_DOWN).toBigInteger());

        if(!StringUtils.equals(this.assetA, assetIn)
            && !StringUtils.equals(this.assetB, assetIn)){
            throw new IllegalArgumentException("asset doesn't exist in pool");
        }
        var reserveIn = StringUtils.equals(assetIn, this.assetA)
                ? this.reserveA()
                : this.reserveB();
        var reserveOut = StringUtils.equals(assetIn, this.assetA)
                ? this.reserveB()
                : this.reserveA();
        var amtOutNumerator = amountIn.multiply(poolFeeModifier).multiply(reserveOut);
        var amtOutDenominator = (amountIn.multiply(poolFeeModifier)).add(reserveIn.multiply(poolFeeMultiplier));

        var priceImpactNumerator = (reserveOut.multiply(amountIn).multiply(amtOutDenominator).multiply(poolFeeModifier))
                .subtract(amtOutNumerator.multiply(reserveIn).multiply(poolFeeMultiplier));
        var priceImpactDenominator = reserveOut.multiply(amountIn).multiply(amtOutDenominator).multiply(poolFeeMultiplier);
        var amountOut = amtOutNumerator.divide(amtOutDenominator);
        var priceImpact = new BigDecimal(priceImpactNumerator).divide(new BigDecimal(priceImpactDenominator), 4, RoundingMode.HALF_DOWN);
        return new SwapAmount(amountOut, priceImpact);
    }

    /**
     * Get the input amount needed if we want to get a certain amount of a token in the pair from swapping
     * @param assetOut The asset that we want to get from the pair
     * @param exactAmountOut The amount of assetOut that we want get from the swap
     * @return The amount needed of the input token for the swap and its price impact
     */
    public SwapAmount getAmountIn(String assetOut, BigInteger exactAmountOut){
        if(!StringUtils.equals(this.assetA, assetOut)
                && !StringUtils.equals(this.assetB, assetOut)){
            throw new IllegalArgumentException("asset doesn't exist in pool");
        }
        var reserveIn = StringUtils.equals(assetOut, this.assetB)
                ? this.reserveA()
                : this.reserveB();
        var reserveOut = StringUtils.equals(assetOut, this.assetB)
                ? this.reserveB()
                : this.reserveA();

        var poolFeePercent = this.poolFeePercentage;
        var poolFeeMultiplier = BigInteger.valueOf(10000);
        var poolFeeModifier = poolFeeMultiplier.subtract(poolFeePercent.multiply(new BigDecimal(poolFeeMultiplier)).setScale(0, RoundingMode.HALF_DOWN).toBigInteger());

        var amtInNumerator = reserveIn.multiply(exactAmountOut).multiply(poolFeeMultiplier);
        var amtInDenominator = (reserveOut.subtract(exactAmountOut)).multiply(poolFeeModifier);

        var priceImpactNumerator = (reserveOut.multiply(amtInNumerator).multiply(poolFeeModifier))
                .subtract(exactAmountOut.multiply(amtInDenominator).multiply(reserveIn).multiply(poolFeeMultiplier));
        var priceImpactDenominator = reserveOut.multiply(amtInNumerator).multiply(poolFeeMultiplier);

        var amountIn = (amtInNumerator.divide(amtInDenominator)).add(BigInteger.ONE);
        var priceImpact = new BigDecimal(priceImpactNumerator).divide(new BigDecimal(priceImpactDenominator), 4, RoundingMode.HALF_DOWN);
        return new SwapAmount(amountIn, priceImpact);
    }

    /**
     * Get pool price.
     * @param decimalsA - The decimals of assetA in pool, if undefined then query from Blockfrost.
     * @param decimalsB - The decimals of assetB in pool, if undefined then query from Blockfrost.
     * @return Returns a pair of asset A/B price and B/A price, adjusted to decimals.
     */
    public Tuple<BigDecimal, BigDecimal> getPrice(int decimalsA,
                                                  int decimalsB){
        var adjustedReserveA = new BigDecimal(this.reserveA()).divide(
                new BigDecimal("10").pow(decimalsA), decimalsA, RoundingMode.HALF_DOWN
        );
        var adjustedReserveB = new BigDecimal(this.reserveB()).divide(
                new BigDecimal("10").pow(decimalsB), decimalsB, RoundingMode.HALF_DOWN
        );
        var priceAB = adjustedReserveA.divide(adjustedReserveB, RoundingMode.HALF_DOWN);
        var priceBA = adjustedReserveB.divide(adjustedReserveA, RoundingMode.HALF_DOWN);
        return new Tuple<>(priceAB, priceBA);
    }

    public BigInteger liquidity(){
        return this.reserveA().multiply(this.reserveB());
    }
}
