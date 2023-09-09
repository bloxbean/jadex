
package com.bloxbean.cardano.jadex.core.dex.minswap.pool;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.jadex.core.config.DexConfigs;
import com.bloxbean.cardano.jadex.core.pool.DefaultPoolStateProvider;
import com.bloxbean.cardano.jadex.core.pool.PoolState;
import com.bloxbean.cardano.jadex.core.pool.PoolStateProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link com.bloxbean.cardano.jadex.core.pool.PoolState PoolState} factory for Minswap DEX
 * <p>
 * Since each DEX can use a (slightly) different approach for storing Pool State in UTXOs, this interface allows custom implementation for each dex.
 *
 * @author $stik
 */
@Slf4j
public class MinPoolStateProvider implements PoolStateProvider {

    @Override
    public PoolState fromUtxo(Utxo utxo, ScriptService scriptService) {
        return DefaultPoolStateProvider.fromUtxo(utxo, DexConfigs.MIN_CONFIG);
    }
}
