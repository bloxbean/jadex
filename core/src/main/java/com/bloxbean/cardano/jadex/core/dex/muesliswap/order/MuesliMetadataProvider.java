
package com.bloxbean.cardano.jadex.core.dex.muesliswap.order;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.order.metadata.MetadataProvider;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;

/**
 * {@link Metadata Metadata} factory for Muesliswap DEX
 * <p>
 * Since each DEX can have its own metadata provider, this provider needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
@RequiredArgsConstructor
public class MuesliMetadataProvider implements MetadataProvider {

    private final Network network;

    @Override
    public Metadata toMetadata(OrderDefinition orderDefinition) {
        return MessageMetadata.create()
                .add("MuesliSwap Place Order")
                .put(1000, new Address(orderDefinition.getSenderAddress(network)).getBytes())
                .put(1002, StringUtils.isNotBlank(orderDefinition.getAssetOutPolicyId()) ? orderDefinition.getAssetOutPolicyId() : "")
                .put(1003, StringUtils.isNotBlank(orderDefinition.getAssetOutTokenName()) && !StringUtils.equalsIgnoreCase(orderDefinition.getAssetOutTokenName(), CardanoConstants.LOVELACE) ? orderDefinition.getAssetOutTokenName() : "")
                .put(1004, orderDefinition.getMinimumAmountOut())
                .put(1005, BigIntegerUtil.sum(orderDefinition.getReturnLovelace(), orderDefinition.getSwapFee()))
                .put(1007, BigInteger.valueOf(1))
                .put(1008, StringUtils.isNotBlank(orderDefinition.getAssetInPolicyId()) ? orderDefinition.getAssetInPolicyId() : "")
                .put(1009, StringUtils.isNotBlank(orderDefinition.getAssetInTokenName()) && !StringUtils.equalsIgnoreCase(orderDefinition.getAssetInTokenName(), CardanoConstants.LOVELACE) ? orderDefinition.getAssetInTokenName() : "");
    }
}
