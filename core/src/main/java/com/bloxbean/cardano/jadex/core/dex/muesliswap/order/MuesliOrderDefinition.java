
package com.bloxbean.cardano.jadex.core.dex.muesliswap.order;

import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;

/**
 * Muesliswap-specific implementation provider of the {@link OrderDefinition OrderDefinition}
 *
 * @author $stik
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class MuesliOrderDefinition extends OrderDefinition {
        /**
         * Define this order allows partial fills
         */
        private final Boolean allowPartialFill;

        public MuesliOrderDefinition(String assetInPolicyId, String assetInTokenName, BigInteger amountIn, String assetOutPolicyId, String assetOutTokenName, BigInteger minimumAmountOut, byte[] stakeKeyHash, byte[] paymentKeyHash, BigInteger returnLovelace, BigInteger batcherFee, boolean allowPartialFill) {
                super(assetInPolicyId, assetInTokenName, amountIn, assetOutPolicyId, assetOutTokenName, minimumAmountOut, stakeKeyHash, paymentKeyHash, returnLovelace, batcherFee);
                this.allowPartialFill = allowPartialFill;
        }
}