
package com.bloxbean.cardano.jadex.core.pool;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.pool.definition.PoolDefinition;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Represents the state of a pool UTxO.
 * Could be the latest state or a historical state.
 * <p>
 * The state is helpful in preparation of swapping and can be used to determine expected swap in and out amounts as well as price impact.
 *
 * @author $stik
 */
@Getter
@ToString
public class PoolState {

    private final String txHash;
    private final int outputIndex;
    private final List<Amount> amounts;
    private final String dataHash;
    private final Pool pool;
    private final DexConfig config;

    public PoolState(String txHash, int outputIndex, List<Amount> amounts, String dataHash, String assetA, String assetB, DexConfig config) {
        this.txHash = txHash;
        this.outputIndex = outputIndex;
        this.amounts = amounts;
        this.dataHash = dataHash;
        this.config = config;
        this.pool = new Pool(
                StringUtils.substring(this.amounts.stream().filter(it -> StringUtils.startsWith(it.getUnit(), config.poolNftPolicyId()))
                    .findFirst()
                    .map(Amount::getUnit)
                    .orElse(null), config.poolNftPolicyId().length()),
                assetA,
                assetB,
                this.amounts.stream()
                        .filter(it -> StringUtils.equals(it.getUnit(), assetA))
                        .map(Amount::getQuantity)
                        .findFirst()
                        .orElse(BigInteger.ZERO),
                this.amounts.stream()
                        .filter(it -> StringUtils.equals(it.getUnit(), assetB))
                        .map(Amount::getQuantity)
                        .findFirst()
                        .orElse(BigInteger.ZERO),
                config.poolFeePercentage());
    }

    public String nft(){
        return amounts.stream().filter(it -> StringUtils.startsWith(it.getUnit(), config.poolNftPolicyId()))
                .findFirst()
                .map(Amount::getUnit)
                .orElse(null);
    }

    /*
     * a pool's ID is the NFT's asset name
     */
    public String id(){
        return StringUtils.substring(this.nft(), config.poolNftPolicyId().length());
    }

    public String assetLP(){
        return config.lpPolicyId() + this.id();
    }

    public String getAssetA() {
        return pool.getAssetA();
    }

    public String getAssetB() {
        return pool.getAssetB();
    }

    public BigInteger reserveA(){
        return pool.getReserveA();
    }
    public BigInteger reserveB(){
        return pool.getReserveB();
    }
    /**
     * Get the output amount if we swap a certain amount of a token in the pair
     * @param assetIn The asset that we want to swap from
     * @param amountIn The amount that we want to swap from
     * @return The amount of the other token that we get from the swap and its price impact
     */
    public SwapAmount getAmountOut(String assetIn, BigInteger amountIn){
        return pool.getAmountOut(assetIn, amountIn);
    }

    /**
     * Get the input amount needed if we want to get a certain amount of a token in the pair from swapping
     * @param assetOut The asset that we want to get from the pair
     * @param exactAmountOut The amount of assetOut that we want get from the swap
     * @return The amount needed of the input token for the swap and its price impact
     */
    public SwapAmount getAmountIn(String assetOut, BigInteger exactAmountOut){
        return pool.getAmountIn(assetOut, exactAmountOut);
    }

    /**
     * Get pool price.
     * @param decimalsA - The decimals of assetA in pool, if undefined then query from Blockfrost.
     * @param decimalsB - The decimals of assetB in pool, if undefined then query from Blockfrost.
     * @return Returns a pair of asset A/B price and B/A price, adjusted to decimals.
     */
    public Tuple<BigDecimal, BigDecimal> getPrice(int decimalsA,
                                                  int decimalsB){
        return pool.getPrice(decimalsA, decimalsB);
    }

    public BigInteger liquidity(){
        return pool.liquidity();
    }

    public PoolDefinition getDefinition(ScriptService scriptService){
        return this.config.poolDefinitionProvider().fromDatum(this.dataHash, scriptService);
    }
}
