
package com.bloxbean.cardano.jadex.core.util;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.pool.Pool;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Util class for {@link OrderDefinition OrderDefinition} calculations
 *
 * @author $stik
 */
@UtilityClass
@Slf4j
public class OrderUtil {

    public Tuple<BigDecimal, BigDecimal> getPrice(OrderDefinition orderDefinition,
                                                  String assetA,
                                                  int decimalsA,
                                                  String assetB,
                                                  int decimalsB){
        if(!BigIntegerUtil.isPositive(orderDefinition.getAmountIn())
            || !BigIntegerUtil.isPositive(orderDefinition.getMinimumAmountOut())){
            return null;
        }
        if(StringUtils.equals(TokenUtil.getUnit(orderDefinition.getAssetInPolicyId(), orderDefinition.getAssetInTokenName()), assetA)
                && StringUtils.equals(TokenUtil.getUnit(orderDefinition.getAssetOutPolicyId(), orderDefinition.getAssetOutTokenName()), assetB)){
            return getBuyPrice(BigIntegerUtil.toBigDecimal(orderDefinition.getAmountIn(), decimalsA),BigIntegerUtil.toBigDecimal(orderDefinition.getMinimumAmountOut(), decimalsB));
        }else if(StringUtils.equals(TokenUtil.getUnit(orderDefinition.getAssetInPolicyId(), orderDefinition.getAssetInTokenName()), assetB)
                && StringUtils.equals(TokenUtil.getUnit(orderDefinition.getAssetOutPolicyId(), orderDefinition.getAssetOutTokenName()), assetA)){
            return getSellPrice(BigIntegerUtil.toBigDecimal(orderDefinition.getAmountIn(), decimalsB), BigIntegerUtil.toBigDecimal(orderDefinition.getMinimumAmountOut(), decimalsA));
        }
        throw new IllegalStateException("Invalid order for pool " + assetA + " - " + assetB);
    }

    /**
     * Get order price.
     * @param amountIn - The amount provided for the buy
     * @param minimumAmountOut - The minimum amount to receive
     * @return Returns a pair of asset A/B price and B/A price, adjusted to decimals.
     */
    private Tuple<BigDecimal, BigDecimal> getBuyPrice(BigDecimal amountIn,
                                                      BigDecimal minimumAmountOut){

        var amountA = amountIn;
        var amountB = minimumAmountOut;

        return new Tuple<>(
                amountA.divide(amountB, amountA.precision(), RoundingMode.HALF_DOWN),
                amountB.divide(amountA, amountB.precision(), RoundingMode.HALF_DOWN));
    }

    public Tuple<BigDecimal, BigDecimal> getSellPrice(BigDecimal amountIn,
                                                      BigDecimal minimumAmountOut){

        var amountA = minimumAmountOut;
        var amountB = amountIn;

        return new Tuple<>(
                amountA.divide(amountB, amountA.precision(), RoundingMode.HALF_DOWN),
                amountB.divide(amountA, amountB.precision(), RoundingMode.HALF_DOWN));
    }

    public static Amount getAmountIn(Utxo utxo, String assetOutPolicyId, String assetOutTokenName, BigInteger returnLovelace, BigInteger swapFee){
        Amount amountIn;
        var minAda = BigIntegerUtil.sum(returnLovelace, swapFee);
        var amounts = utxo.getAmount();
        var potentialInAmounts = amounts.stream()
                .filter(it -> !StringUtils.equals(it.getUnit(), TokenUtil.getUnit(assetOutPolicyId, assetOutTokenName)))
                .filter(amount -> !TokenUtil.equals(TokenUtil.getUnit(null, CardanoConstants.LOVELACE), amount.getUnit())
                        || amount.getQuantity().compareTo(minAda) > 0)
                .toList();
        if(potentialInAmounts.isEmpty()){
            throw new IllegalStateException("No potential amount IN found in [" + amounts + "] w unit [" + TokenUtil.getUnit(assetOutPolicyId, assetOutTokenName) + "]");
        }
        // if potentialInAmounts == 1
        // found
        if(potentialInAmounts.size() == 1){
            amountIn = potentialInAmounts.get(0);
        }else if(potentialInAmounts.size() == 2){
            // else if potentialInAmounts == 2
            // find non lovelace (returnLovelace)
            var selected = potentialInAmounts.stream()
                    .filter(it -> StringUtils.isNotBlank(TokenUtil.getPolicyId(it.getUnit()))
                            && !TokenUtil.equals(TokenUtil.getTokenName(it.getUnit()), CardanoConstants.LOVELACE))
                    .toList();
            if(selected.isEmpty()){
                throw new IllegalStateException("No amount IN found in [" + amounts + "] w potential ["+potentialInAmounts+"] w unit [" + TokenUtil.getUnit(assetOutPolicyId, assetOutTokenName) + "]");
            }
            if(selected.size() > 1){
                throw new IllegalStateException("Multiple amounts IN ["+selected+"] found in [" + amounts + "] w potential ["+potentialInAmounts+"] w unit [" + TokenUtil.getUnit(assetOutPolicyId, assetOutTokenName) + "]");
            }
            amountIn = selected.get(0);
        }else{
            throw new IllegalStateException("Multiple amounts IN ["+potentialInAmounts+"] found in [" + amounts + "] w potential ["+potentialInAmounts+"] w unit [" + TokenUtil.getUnit(assetOutPolicyId, assetOutTokenName) + "]");
        }
        if(amountIn != null && TokenUtil.equals(TokenUtil.getUnit(null, CardanoConstants.LOVELACE), amountIn.getUnit())){
            amountIn = new Amount(amountIn.getUnit(), amountIn.getQuantity().subtract(returnLovelace).subtract(swapFee));
        }
        return amountIn;
    }

    /**
     * Split an amountIn for most efficient filling over the provided Pools
     *
     * @param assetIn the asset used as input for the swap
     * @param amountIn the amount of assetIn that will be divided over different pools
     * @param allPools all pools to check
     * @param slippagePercentage the max amount of price change that can be tolerated (0 = 0%, 1 = 100%). If slippage is reached not all `amountIn` will be used
     * @param decimalsA - The decimals of assetA in pool.
     * @param decimalsB - The decimals of assetB in pool.
     * @return a list of `AmountInPerPool` representing the best amount of assetIn to swap per pool
     */
    public static List<AmountInPerPool> smartSplit(String assetIn, BigInteger amountIn, List<Pool> allPools, BigDecimal slippagePercentage, int decimalsA, int decimalsB){
        var pools = allPools.stream()
                .filter(pool -> TokenUtil.equals(pool.getAssetA(), assetIn) || TokenUtil.equals(pool.getAssetB(), assetIn))
                .map(pool -> new Pool(pool.getId(), pool.getAssetA(), pool.getAssetB(), pool.getReserveA(), pool.getReserveB(), pool.getPoolFeePercentage()))
                .toList();
        var poolOrders = new ArrayList<AmountInPerPool>();
        var bestPool = pools.stream()
                .map(pool -> new Tuple<>(pool, pool.getPrice(decimalsA, decimalsB)))
                .map(tuple -> TokenUtil.equals(assetIn, tuple._1.getAssetA())
                        ? new Tuple<>(tuple._1, tuple._2._1)
                        : new Tuple<>(tuple._1, tuple._2._2))
                .sorted((it1, it2) -> it1._2.compareTo(it2._2))
                .map(tuple -> tuple._1)
                .findFirst()
                .orElseThrow();

        var slippagePrice = slippagePercentage != null
                ? (TokenUtil.equals(assetIn, bestPool.getAssetA())
                ? bestPool.getPrice(decimalsA, decimalsB)._1
                : bestPool.getPrice(decimalsA, decimalsB)._2)
                          .multiply(BigDecimal.ONE.add(slippagePercentage))
                : null;

        var amountInAssigned = BigInteger.ONE;
        var amountInAssignedToBestPool = BigInteger.ONE;

        while (amountInAssigned.compareTo(amountIn) <= 0){

            var priceBestPool = TokenUtil.equals(assetIn, bestPool.getAssetA())
                    ? bestPool.getPrice(decimalsA, decimalsB)._1
                    : bestPool.getPrice(decimalsA, decimalsB)._2;
            var bestPoolTmp = bestPool;

            var betterPool = pools.stream()
                    .filter(it -> !it.getId().equals(bestPoolTmp.getId()))
                    .filter(it -> (TokenUtil.equals(assetIn, bestPoolTmp.getAssetA())
                            ? it.getPrice(decimalsA, decimalsB)._1
                            : it.getPrice(decimalsA, decimalsB)._2)
                            .compareTo(priceBestPool) < 0)
                    .sorted((it1, it2) -> it2.liquidity().compareTo(it1.liquidity()))
                    .findFirst();
            if(betterPool.isPresent()){
                var existing = poolOrders.stream().filter(it -> it.getPoolId().equals(bestPoolTmp.getId())).findFirst();
                if(existing.isPresent()){
                    poolOrders.remove(existing.get());
                    poolOrders.add(new AmountInPerPool(bestPool.getId(), existing.get().getAmountIn().add(amountInAssignedToBestPool.subtract(BigInteger.ONE))));
                }else{
                    poolOrders.add(new AmountInPerPool(bestPool.getId(), amountInAssignedToBestPool.subtract(BigInteger.ONE)));
                }
                amountInAssignedToBestPool = BigInteger.ONE;
                bestPool = betterPool.get();
            }else{
                if(slippagePrice != null && slippagePrice.compareTo(priceBestPool) <= 0){
                    break;
                }

                amountInAssigned = amountInAssigned.add(BigInteger.ONE);
                amountInAssignedToBestPool = amountInAssignedToBestPool.add(BigInteger.ONE);

                if(TokenUtil.equals(assetIn, bestPoolTmp.getAssetA())){
                    bestPool.addA();
                }else{
                    bestPool.addB();
                }
            }
        }
        if(amountInAssignedToBestPool.compareTo(BigInteger.ONE) > 0){
            var bestPoolTmp = bestPool;
            var existing = poolOrders.stream().filter(it -> it.getPoolId().equals(bestPoolTmp.getId())).findFirst();
            if(existing.isPresent()){
                poolOrders.remove(existing.get());
                poolOrders.add(new AmountInPerPool(bestPool.getId(), existing.get().getAmountIn().add(amountInAssignedToBestPool.subtract(BigInteger.ONE))));
            }else{
                poolOrders.add(new AmountInPerPool(bestPool.getId(), amountInAssignedToBestPool.subtract(BigInteger.ONE)));
            }
        }
        return poolOrders;
    }

    @Value
    public static class AmountInPerPool {
        private final String poolId;
        private final BigInteger amountIn;
    }
}
