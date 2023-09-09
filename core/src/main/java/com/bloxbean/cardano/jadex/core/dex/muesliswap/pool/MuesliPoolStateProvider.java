
package com.bloxbean.cardano.jadex.core.dex.muesliswap.pool;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.config.DexConfigs;
import com.bloxbean.cardano.jadex.core.pool.DefaultPoolStateProvider;
import com.bloxbean.cardano.jadex.core.pool.PoolState;
import com.bloxbean.cardano.jadex.core.pool.PoolStateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * {@link com.bloxbean.cardano.jadex.core.pool.PoolState PoolState} factory for Muesliswap DEX
 * <p>
 * Since each DEX can use a (slightly) different approach for storing Pool State in UTXOs, this interface allows custom implementation for each dex.
 *
 * @author $stik
 */
@Slf4j
@RequiredArgsConstructor
public class MuesliPoolStateProvider implements PoolStateProvider {
    @Override
    public PoolState fromUtxo(Utxo utxo, ScriptService scriptService) {
        var config = DexConfigs.MUESLI_V3_CONFIG;
        if(config.poolFeePercentage() == null){
            var dataHash = utxo.getDataHash();
            var definition = config.poolDefinitionProvider().fromDatum(dataHash, scriptService);
            var lpFee = ((MuesliPoolDefinition)definition).getLpFee();
            config = new DexConfig(config.network(),
                    config.dexType(),
                    config.poolNftPolicyId(),
                    config.factoryPolicyId(),
                    config.factoryAssetName(),
                    config.lpPolicyId(),
                    new BigDecimal(lpFee).divide(new BigDecimal("10000"), 4, RoundingMode.HALF_DOWN),
                    config.orderAddress(),
                    config.swapFee(),
                    config.outputLovelace(),
                    config.plutusLanguage(),
                    config.poolStateProvider(),
                    config.poolDefinitionProvider(),
                    config.orderDefinitionProvider(),
                    config.metadataProvider());
        }
        return DefaultPoolStateProvider.fromUtxo(utxo, config);
    }
}
