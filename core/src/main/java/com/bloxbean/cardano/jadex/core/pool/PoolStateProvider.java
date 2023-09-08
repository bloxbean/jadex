
package com.bloxbean.cardano.jadex.core.pool;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.ScriptService;


/**
 * Provider of {@link PoolState PoolState} instances.
 * Since each DEX can use a (slightly) different approach for storing Pool State in UTXOs, this interface allows custom fetching for each dex.
 * Default implementation can be found in {@link DefaultPoolStateProvider DefaultPoolStateProvider}
 *
 * @author $stik
 */
public interface PoolStateProvider {

    /**
     * Construct a `PoolState` object from the pool UTXO.
     *
     * @param utxo the pool UTXO
     * @param scriptService a backend service for resolving pool datum
     * @return a UTXO represented as `PoolState`
     */
    PoolState fromUtxo(Utxo utxo, ScriptService scriptService);
}
