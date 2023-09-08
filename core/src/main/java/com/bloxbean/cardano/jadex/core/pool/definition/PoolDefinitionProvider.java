
package com.bloxbean.cardano.jadex.core.pool.definition;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.backend.api.ScriptService;

/**
 * {@link PoolDefinition PoolDefinition} factory, responsible for constructing {@link PoolDefinition PoolDefinitions} from {@link PlutusData PlutusData}
 * <p>
 * Since each DEX can have its own pool definition, this definition needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
public interface PoolDefinitionProvider {

    default PoolDefinition fromDatum(String datumHash, ScriptService scriptService){
        try{
            var json = scriptService.getScriptDatum(datumHash).getValue().getJsonValue();
            var data = PlutusDataJsonConverter.toPlutusData(json);
            return fromDatum(data);
        }catch(Exception e){
            throw new IllegalStateException("Failed to read from datum hash [" + datumHash + "]", e);
        }

    }
    PoolDefinition fromDatum(PlutusData datumHash);
}
