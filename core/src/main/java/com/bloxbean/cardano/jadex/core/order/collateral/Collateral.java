
package com.bloxbean.cardano.jadex.core.order.collateral;

/**
 * Type-safe representation of a Collateral hash and index
 *
 * @author $stik
 */
public record Collateral(String utxoHash, int utxoIndex) {
}