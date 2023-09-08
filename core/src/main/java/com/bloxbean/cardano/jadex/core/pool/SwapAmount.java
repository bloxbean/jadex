
package com.bloxbean.cardano.jadex.core.pool;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * DTO representing a swap amount and associated price impact.
 * Constructed by {@link PoolState PoolState}.
 *
 * @author $stik
 */
public record SwapAmount(BigInteger amount, BigDecimal priceImpact) {
}