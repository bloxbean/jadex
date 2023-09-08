
package com.bloxbean.cardano.jadex.core.order.book;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.util.OrderUtil;

import java.math.BigDecimal;

/**
 * DTO containing the raw utxo together with the {@link OrderDefinition OrderDefinition} extracted from metadata and datum hash
 *
 * @author $stik
 */
public record UtxoOrder(Utxo utxo,
                        OrderDefinition orderDefinition) {

    public Tuple<BigDecimal, BigDecimal> getPrice(String assetA, int decimalsA, String assetB, int decimalsB){
        return OrderUtil.getPrice(this.orderDefinition, assetA, decimalsA, assetB, decimalsB);
    }
}
