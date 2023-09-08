
package com.bloxbean.cardano.jadex.core.order.definition;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

/**
 * {@link OrderDefinition OrderDefinition} factory, responsible for constructing {@link OrderDefinition OrderDefinitions} from {@link PlutusData PlutusData}
 * <p>
 * Since each DEX can have its own order definition, this definition needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
public interface OrderDefinitionProvider {

    /**
     * converts an {@link OrderDefinition orderDefinition} into a {@link PlutusData plutusData} representation of a swap datum
     *
     * @param orderDefinition the input required for constructing the {@link PlutusData plutusData}
     * @return a {@link PlutusData plutusData} representation of a swap datum, based on the provided {@link OrderDefinition orderDefinition}
     */
    PlutusData toDatum(OrderDefinition orderDefinition);

    /**
     * converts an {@link OrderDefinition orderDefinition} into a {@link PlutusData plutusData} representation of a redeemer datum.
     * the redeemer is used when cancelling a swap order.
     *
     * @param orderDefinition the input required for constructing the {@link PlutusData plutusData}
     * @return a {@link PlutusData plutusData} representation of a redeemer datum, based on the provided {@link OrderDefinition orderDefinition}
     */
    PlutusData toRedeemerDatum(OrderDefinition orderDefinition);

    /**
     * Constructs an order definition from data provided in the {@link Utxo utxo}, {@link PlutusData order datum} and {@link Metadata order metadata}
     *
     * @param utxo the output UTXO send to the order address when placing a swap order
     * @param datum a {@link PlutusData plutusData} representation of a swap datum
     * @return an order definition constructed from the provided input
     */
    OrderDefinition fromUtxo(Utxo utxo, PlutusData datum);

}
