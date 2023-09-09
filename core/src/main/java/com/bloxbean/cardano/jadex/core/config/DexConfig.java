
package com.bloxbean.cardano.jadex.core.config;

import com.bloxbean.cardano.client.api.util.CostModelUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.jadex.core.Dex;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinitionProvider;
import com.bloxbean.cardano.jadex.core.order.metadata.MetadataProvider;
import com.bloxbean.cardano.jadex.core.pool.PoolStateProvider;
import com.bloxbean.cardano.jadex.core.pool.definition.PoolDefinitionProvider;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Dex-specific configuration. Each supported DEX has its own distinct configuration.
 * Used to initialize {@link Dex Dex}.
 * Supported configurations are available in {@link DexConfigs DexConfigs}.
 *
 * @param network the Cardano network environment this config is intended for
 * @param dexType label for the dex this config is intended for
 * @param poolNftPolicyId the policyId of the pool's NFT. Each pool must contain an asset with this policyId
 * @param factoryPolicyId the policyId of the pool's asset. Each UTXO containing the pool asset is considered a pool UTXO
 * @param factoryAssetName the asset name of the pool's asset, used in combination with the factoryPolicyId
 * @param lpPolicyId the policyId of the pool LP
 * @param poolFeePercentage optional pool fee percentage, if not provided, should be set dynamically from pool datum in {@link PoolStateProvider PoolStateProvider}
 * @param orderAddress the address of the order script. Must match `orderScript` hash
 * @param swapFee the (batcher) fee applicable for this DEX
 * @param outputLovelace the amount of lovelace which will be returned on swaps
 * @param plutusLanguage the plutus language version used for cancelling swaps
 * @param poolStateProvider the DEX-specific {@link PoolStateProvider PoolStateProvider} implementation. A {@link com.bloxbean.cardano.jadex.core.pool.DefaultPoolStateProvider DefaultPoolStateProvider} is available
 * @param poolDefinitionProvider the DEX-specific {@link PoolDefinitionProvider PoolDefinitionProvider} implementation
 * @param orderDefinitionProvider the DEX-specific {@link OrderDefinitionProvider OrderDefinitionProvider} implementation
 * @param metadataProvider the DEX-specific {@link MetadataProvider MetadataProvider} implementation
 *
 * @author $stik
 */
@Builder
public record DexConfig(Network network,
                        String dexType,
                        String poolNftPolicyId,
                        String factoryPolicyId,
                        String factoryAssetName,
                        String lpPolicyId,
                        BigDecimal poolFeePercentage,
                        String orderAddress,
                        BigInteger swapFee,
                        BigInteger outputLovelace,
                        Language plutusLanguage,
                        PoolStateProvider poolStateProvider,
                        PoolDefinitionProvider poolDefinitionProvider,
                        OrderDefinitionProvider orderDefinitionProvider,
                        MetadataProvider metadataProvider) {
    public String getPoolAssetId() {
        return factoryPolicyId + factoryAssetName;
    }

    public CostModel costModel() {
        return Language.PLUTUS_V1.equals(plutusLanguage)
                ? CostModelUtil.PlutusV1CostModel
                : CostModelUtil.PlutusV2CostModel;
    }
}