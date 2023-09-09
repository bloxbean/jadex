
package com.bloxbean.cardano.jadex.core.order.metadata;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;

/**
 * {@link Metadata Metadata} factory, responsible for constructing {@link Metadata Metadata} from {@link OrderDefinition OrderDefinition}
 * <p>
 * Since each DEX can have its own metadata provider, this provider needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
public interface MetadataProvider {
    /**
     * converts an {@link OrderDefinition orderDefinition} into a {@link Metadata metadata} representation of a swap metadata
     *
     * @param orderDefinition the input required for constructing the {@link Metadata metadata}
     * @return the raw {@link Metadata Metadata} usable by the low-level Cardano Client Lib
     */
    Metadata toMetadata(OrderDefinition orderDefinition);
}
