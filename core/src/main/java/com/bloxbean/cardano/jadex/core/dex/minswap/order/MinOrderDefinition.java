
package com.bloxbean.cardano.jadex.core.dex.minswap.order;

import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;

/**
 * Minswap-specific implementation provider of the {@link OrderDefinition OrderDefinition}
 *
 * @author $stik
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public class MinOrderDefinition extends OrderDefinition {
        /**
         * Define this order is Limit Order or not
         */
        private final Boolean isLimitOrder;

        public MinOrderDefinition(String assetInPolicyId, String assetInTokenName, BigInteger amountIn, String assetOutPolicyId, String assetOutTokenName, BigInteger minimumAmountOut, byte[] stakeKeyHash, byte[] paymentKeyHash, BigInteger returnLovelace, BigInteger batcherFee, Boolean isLimitOrder) {
                super(assetInPolicyId, assetInTokenName, amountIn, assetOutPolicyId, assetOutTokenName, minimumAmountOut, stakeKeyHash, paymentKeyHash, returnLovelace, batcherFee);
                this.isLimitOrder = isLimitOrder;
        }
}