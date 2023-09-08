
package com.bloxbean.cardano.jadex.core.dex.minswap.order;

import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.order.metadata.MetadataProvider;

/**
 * {@link Metadata Metadata} factory for Minswap DEX
 * <p>
 * Since each DEX can have its own metadata provider, this provider needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
public class MinMetadataProvider implements MetadataProvider {

    @Override
    public Metadata toMetadata(OrderDefinition orderDefinition) {
        var limitOrder = true;
        if(orderDefinition instanceof MinOrderDefinition){
            limitOrder = ((MinOrderDefinition)orderDefinition).getIsLimitOrder() != null
                    ? ((MinOrderDefinition)orderDefinition).getIsLimitOrder()
                    : true;
        }
        return MessageMetadata.create()
                .add(limitOrder ? "Minswap: Swap Exact In Limit Order" : "Minswap: Swap Exact In Order");
    }
}
