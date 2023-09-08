
package com.bloxbean.cardano.jadex.core.order.definition;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.jadex.core.Dex;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;

/**
 * Base class for requesting swap from {@link Dex Dex} implementation
 * <p>
 * OrderDefinition can be extended to add additional features, usable in {@link OrderDefinition orderDefinition} implementations.
 *
 * @author $stik
 */
@RequiredArgsConstructor
@EqualsAndHashCode
@Getter
@ToString
@SuperBuilder(toBuilder = true)
public class OrderDefinition {
        /**
         * policy id of the asset you want to swap
         */
        private final String assetInPolicyId;
        /**
         * token name of the asset you want to swap
         */
        private final String assetInTokenName;
        /**
         * amount of the asset you want to swap
         */
        private final BigInteger amountIn;
        /**
         * policy id of the asset you want to receive
         */
        private final String assetOutPolicyId;
        /**
         * token name of the asset you want to receive
         */
        private final String assetOutTokenName;
        /**
         * the minimum Amount of Asset Out you can accept after order is executed
         */
        private final BigInteger minimumAmountOut;
        /**
         * the senders public stake key hash
         */
        private final byte[] stakeKeyHash;
        /**
         * the senders public payment key hash
         */
        private final byte[] paymentKeyHash;
        /**
         * The lovelace amount which will be returned when the order completes
         */
        private final BigInteger returnLovelace;
        /**
         * The fixed fee for the swap executor
         */
        private final BigInteger swapFee;

        public String getSenderAddress(Network network){
                return AddressProvider.getBaseAddress(Credential.fromKey(paymentKeyHash), Credential.fromKey(stakeKeyHash), network)
                        .getAddress();
        }
}