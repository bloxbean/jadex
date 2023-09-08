
package com.bloxbean.cardano.jadex.core.pool;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Generic provider of {@link PoolState PoolState} instances.
 *
 * @author $stik
 */
@UtilityClass
public class DefaultPoolStateProvider {

    public static PoolState fromUtxo(Utxo utxo, DexConfig config) {
        var amounts = utxo.getAmount();
        if(amounts == null || amounts.isEmpty()){
            throw new IllegalArgumentException("Amounts is mandatory");
        }
        var txHash = utxo.getTxHash();
        var outputIndex = utxo.getOutputIndex();
        var dataHash = utxo.getDataHash();

        // check NFT
        var nft = amounts.stream().filter(it -> StringUtils.startsWith(it.getUnit(), config.poolNftPolicyId()))
                .findFirst();
        if(nft.isEmpty()){
            throw new IllegalArgumentException("pool doesn't have NFT");
        }
        var relevantAssets = amounts.stream()
                .filter(it -> !StringUtils.equals(it.getUnit(), config.getPoolAssetId())
                        && !StringUtils.startsWith(it.getUnit(), config.lpPolicyId())
                        && !StringUtils.startsWith(it.getUnit(), config.poolNftPolicyId()))
                .toList();
        if(relevantAssets.size() < 2){
            throw new IllegalArgumentException("Invalid UXTO for pool. " + relevantAssets.size() + " relevant assets...");
        }

        String assetA = null;
        String assetB = null;
        if(relevantAssets.size() == 2){
            // ADA/A pool
            assetA = CardanoConstants.LOVELACE;
            var nonADAAssets = relevantAssets.stream()
                    .filter(it -> !StringUtils.equals(it.getUnit(), CardanoConstants.LOVELACE))
                    .toList();
            if(nonADAAssets.size() != 1){
                throw new IllegalArgumentException("pool must have 1 non-ADA asset");
            }
            assetB = nonADAAssets.get(0).getUnit();

        }else if(relevantAssets.size() == 3){
            // A/B pool
            var nonADAAssets = relevantAssets.stream()
                    .filter(it -> !StringUtils.equals(it.getUnit(), CardanoConstants.LOVELACE))
                    .toList();
            if(nonADAAssets.size() != 2){
                throw new IllegalArgumentException("pool must have 2 non-ADA asset");
            }
            var normalized = normalizeAssets(nonADAAssets.get(0).getUnit(), nonADAAssets.get(1).getUnit());
            assetA = normalized._1;
            assetB = normalized._2;
        }else{
            throw new IllegalArgumentException("pool must have 2 or 3 assets except factory, NFT and LP tokens");
        }
        return new PoolState(txHash, outputIndex, amounts, dataHash, assetA, assetB, config);
    }

    public static Tuple<String, String> normalizeAssets(String asset1, String asset2){
        if(StringUtils.equals(asset1, CardanoConstants.LOVELACE)){
            return new Tuple<>(asset1, asset2);
        }
        if(StringUtils.equals(asset2, CardanoConstants.LOVELACE)){
            return new Tuple<>(asset2, asset1);
        }
        // sort lexicographically
        if(StringUtils.compare(asset1, asset2) < 0){
            return new Tuple<>(asset1, asset2);
        }else{
            return new Tuple<>(asset2, asset1);
        }
    }
}
