
package com.bloxbean.cardano.jadex.core.order.collateral;

import com.bloxbean.cardano.client.account.Account;

/**
 * Utility class to get, verify and create {@link Collateral collateral} UTXOs
 *
 * @author $stik
 */
public interface CollateralProvider {

    Collateral get(String address);

    boolean verify(String address, String collateralUtxoHash);

    Collateral create(Account sender);

}
