
package com.bloxbean.cardano.jadex.core;


import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.order.book.OrderBook;
import com.bloxbean.cardano.jadex.core.order.book.UtxoOrder;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.pool.PoolState;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Interface representing the supported features for each Decentralized Exchange (DEX).
 * Each DEX implementation represents 1 Cardano DEX, configured using {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
public interface Dex {

    /**
     * Retrieve all Pools for 1 DEX.
     * Note: this operation can incur high rate of BackendService requests
     *
     * @return a list of all pools
     */
    List<PoolState> getAllPools();

    /**
     * Retrieve the pool with the highest liquidity for given assets
     * Lovelace pools have empty assetAPolicyId and `lovelace` as assetATokenName
     * <p>
     * Note: this operation can incur high rate of BackendService requests
     *
     * @param assetAPolicyId the policy ID of assetA
     * @param assetATokenName the token name of assetA
     * @param assetBPolicyId the policy ID of assetB
     * @param assetBTokenName the token name of assetB
     * @return the state of a pool UTxO.
     */
    PoolState getPool(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName);

    /**
     * Fetch a pool by its Pool ID.
     * This is the most efficient way to fetch a pool.
     * It is recommended to store poolIds, retrieved using `getAllPools` or `getPool(assetAPolicyId, assetATokenName, assetBPolicyId, assetBTokenName)` and use those PoolIds for optimised fetching of PoolState
     *
     * @param poolId The pool ID. This is the asset name of a pool's NFT and LP tokens. It can also be acquired by calling pool.id.
     * @return the state of a pool UTxO.
     */
    PoolState getPool(String poolId);

    /**
     * Retrieve the current price for a specific pool
     * Lovelace pools have empty assetAPolicyId and `lovelace` as assetATokenName
     * <p>
     * Note: this operation can incur high rate of BackendService requests
     *
     * @param assetAPolicyId the policy ID of assetA
     * @param assetATokenName the token name of assetA
     * @param assetBPolicyId the policy ID of assetB
     * @param assetBTokenName the token name of assetB
     * @return a pair of asset A/B price and B/A price, adjusted to decimals
     */
    Tuple<BigDecimal, BigDecimal> getPrice(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName);

    /**
     * A more efficient way to fetch the price of a pool
     *
     * @param poolId The pool ID. This is the asset name of a pool's NFT and LP tokens. It can also be acquired by calling pool.id.
     * @return a pair of asset A/B price and B/A price, adjusted to decimals
     */
    Tuple<BigDecimal, BigDecimal> getPrice(String poolId);

    /**
     * Provides the number of decimals used by an asset.
     * Asset decimals are used for interacting with prices
     *
     * @param policyId the policyId of the asset
     * @param tokenName the token name of the asset
     * @return the number of decimals for the given asset
     */
    int getAssetDecimals(String policyId, String tokenName);

    /**
     * Fetch all open orders for a given asset pair
     *
     * @param assetAPolicyId the policy ID of assetA
     * @param assetATokenName the token name of assetA
     * @param assetBPolicyId the policy ID of assetB
     * @param assetBTokenName the token name of assetB
     * @return an orderbook contains buy and sell orders
     */
    OrderBook getOpenOrders(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName);

    /**
     * Get 1 specific order by its swap transaction id
     * @param txId the transaction id of the swap transaction
     * @return raw UTXO enriched with {@link OrderDefinition OrderDefinition}
     */
    UtxoOrder getOrder(String txId);

    /**
     * Initiate a new swap order, based on the provided {@link OrderDefinition OrderDefinition}
     *
     * @param sender the wallet of the account requesting the swap. Funds will be transferred from this wallet to the DEX.
     * @param orderDefinition order-specific parameters used for building datum and metadata
     * @param invalidAfter the duration after which the transaction will be removed from the mempool if it hasn't been included in a block yet. Recommended value is >2 hours.
     * @return the transaction ID of the swap transaction
     */
    String swap(Account sender, OrderDefinition orderDefinition, Duration invalidAfter);

    /**
     * Cancel an existing swap order, identified by the provided {@link Utxo UTXO}.
     * A cancel can only be successful if the order is not fully matched
     *
     * @param sender the wallet of the account requesting the cancel. Refunds will be paid into the wallets base address.
     * @param orderUtxo the UTXO containing the swap to cancel. This UTXO must belong to the contract address and contain the datum hash and funds to be refunded
     * @param invalidAfter the duration after which the transaction will be removed from the mempool if it hasn't been included in a block yet. Recommended value is >2 hours.
     * @return the transaction ID of the cancel transaction
     */
    String cancelSwap(Account sender, Utxo orderUtxo, Duration invalidAfter);
}
