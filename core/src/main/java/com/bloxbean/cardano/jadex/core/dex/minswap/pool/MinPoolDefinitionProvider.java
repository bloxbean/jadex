
package com.bloxbean.cardano.jadex.core.dex.minswap.pool;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.jadex.core.pool.definition.PoolDefinition;
import com.bloxbean.cardano.jadex.core.pool.definition.PoolDefinitionProvider;

/**
 * {@link PoolDefinition PoolDefinition} factory for Minswap DEX
 * <p>
 * Since each DEX can have its own pool definition, this definition needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
public class MinPoolDefinitionProvider implements PoolDefinitionProvider {

    public PoolDefinition fromDatum(PlutusData datumHash){
        return MinPoolDefinition.fromPlutusData(datumHash);
    }
}
