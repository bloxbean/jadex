[![Clean, Build](https://github.com/bloxbean/jadex/actions/workflows/build.yml/badge.svg)](https://github.com/bloxbean/jadex/actions/workflows/build.yml) [![Integration Tests](https://github.com/bloxbean/jadex/actions/workflows/integrationTest.yml/badge.svg)](https://github.com/bloxbean/jadex/actions/workflows/integrationTest.yml) 
# JADEX - Cardano DEX aggregator

Jadex is a Java-based DEX aggregation library for Cardano. It simplifies interactions with Decentralized Exchanges (DEX) on the Cardano blockchain from a Java application. Built as an abstraction layer on top of [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib).

Please note that this software is currently in beta phase and is provided "as-is." We strongly recommend conducting thorough testing before use and [reporting any issues](https://github.com/bloxbean/jadex/issues).

If you find value in using this library or wish to support its development, consider donating to $JADEX or staking with [BLOXB](https://www.bloxbean.com/cardano-staking/).

## Use as a library in a Java Project

### For release binaries

**For Maven, add the following dependencies to project's pom.xml**

```xml
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>jadex</artifactId>
            <version>{version}</version>
        </dependency>
```
**For Gradle, add the following dependencies to build.gradle**

```
implementation 'com.bloxbean.cardano:jadex:{version}'
```


## Usage

### Prerequisites

Jadex is build as an abstraction layer on top of [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib).
To initialize Jadex, you need a `BackendService`. Refer to [the Cardano Client Lib Documentation](https://github.com/bloxbean/cardano-client-lib#create-blockfrost-backend-service-and-get-other-services) for instructions on initializing a `BackendService`.


### DEX Initialization

This library offers a generic way to interact with each DEX based on a `DexConfig`. To initialize a DEX implementation, provide a Cardano Client Lib `BackendService` and a `DexConfig`.

```java
var dex = new DexImpl(backendService, DexConfigs.MIN_CONFIG);
```

### Custom DEX Configs

The library's modular architecture allows developers to extend and customize its functionality easily. While predefined Dex configurations are available in the `DexConfigs` class, you can construct `DexImpl` with custom `DexConfig` instances if needed. Consider contributing custom configurations for inclusion in the library (learn [how to contribute](#how-to-contribute)).

### DEX Functionality

The `Dex` interface offers core functionalities:

- `getAllPools`: Retrieve all pools for a specific DEX.
- `getPool`: Retrieve a pool by assets or pool ID (asset name of a pool's NFT and LP tokens).
- `getPrice`: Retrieve current prices for a specific pool.
- `getOpenOrders`: Retrieve all open orders for a specific pool.
- `getOrder`: Retrieve order details for a specific transaction.
- `swap`: Initiate a new swap order using the provided `OrderDefinition`.
- `cancelSwap`: Cancel an existing swap order identified by the provided UTXO.

#### Get Pools

Pools are UTXOs containing factory assets. The most efficient way to fetch pools is by using the pool ID (asset name of a pool's NFT and LP tokens) once retrieved from `PoolState`.

```java
// Fetch a pool by assets (expensive)
PoolState pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);

// Store the pool ID for efficient re-fetching
String poolId = pool.id();

// Fetch a pool by pool ID (efficient)
PoolState pool = dex.getPool(poolId);


String assetA = pool.getAssetA();
String assetB = pool.getAssetB();
BigInteger liquidity = pool.liquidity();

// Get the input amount needed for a certain token amount in a pair from swapping and the price impact of this order
SwapAmount amountIn = pool.getAmountIn(assetOut, exactAmountOut);

// Get the output amount for a certain token amount in a pair from swapping and the price impact of this order
SwapAmount amountOut = pool.getAmountOut(assetIn, amountIn); 
```


#### Get Prices

Prices can be retrieved from `PoolState` directly or via the `Dex` interface, with the most efficient method being by using the pool ID.

A price is represented as a pair of asset A/B price and B/A price, adjusted to decimals.

```java
// retrieve price from Pool
PoolState pool = dex.getPool(poolId);
Tuple<BigDecimal, BigDecimal> pricePair = pool.getPrice(dex.getAssetDecimals(policyIdA, tokenNameA), dex.getAssetDecimals(policyIdB, tokenNameB));

// retrieve price from Dex
Tuple<BigDecimal, BigDecimal> pricePair = dex.getPrice(poolId);

BigDecimal priceAB = pricePair._1;
BigDecimal priceBA = pricePair._2;
```

#### Get Open Orders

Open orders represent unspent UTXOs associated with a DEX order address and can be retrieved using the `Dex` interface. Please note that retrieving open orders is an expensive operation due to datum resolution.

```java
// Retrieve open orders for an asset pair
OrderBook orderBook = dex.getOpenOrders(policyIdA, tokenNameA, policyIdB, tokenNameB);
// list of buy orders with price
List<Tuple<UtxoOrder, BigDecimal>> buyOrders = orderBook.getBuyOrder();
// list of sell orders with price
List<Tuple<UtxoOrder, BigDecimal>> sellOrders = orderBook.getSellOrder();


// get 1 specific order based on swap txId
UtxoOrder order = dex.getOrder(orderTxId);

```


#### Place Swap Order

To place a swap order, provide the sender account ([part of the Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib#account-api-usage)) and the `OrderDefinition`.

The `OrderDefinition` is used for producing the tx datum and metadata. Each DEX may use slightly different versions of datum/metadata; `DexConfig` provides convenient ways to add custom parsers.

```java
BigInteger minTokenAmount = pool.getAmountOut(TokenUtil.getUnit(policyIdA, tokenNameA), assetAToSpend).amount();

OrderDefinition definition = OrderDefinition.builder()
        .assetInPolicyId(policyIdA)
        .assetInTokenName(tokenNameA)
        .assetOutPolicyId(policyIdB)
        .assetOutTokenName(tokenNameB)
        .amountIn(assetAToSpend)
        .swapFee(swapFee)
        .returnLovelace(returnLovelace)
        .minimumAmountOut(minTokenAmount)
        .paymentKeyHash(sender.hdKeyPair().getPublicKey().getKeyHash())
        .stakeKeyHash(sender.stakeHdKeyPair().getPublicKey().getKeyHash())
        .build();

String orderTxId = dex.swap(sender, definition, Duration.ofHours(2));
```


#### Cancel Swap Order

Canceling a swap order involves redeeming non-spent outputs from a script. Provide the sender account and the script address UTXO containing the amount to redeem.

```java
UtxoOrder fetchedOrder = dex.getOrder(orderTxId);
String cancelTxId = dex.cancelSwap(sender, fetchedOrder.utxo(), Duration.ofHours(2));
```

## Known Issues / Outstanding Tasks

### Smart Order Splitter
We are currently developing a multi-DEX liquidity aggregator and smart order splitter, which will be available soon.

### Limited DEX config support
Jadex currently supports Minswap and Muesliswap DEXes, with integration for VyFinance, WingRiders, and SundaeSwap in progress. Contributions for additional DEX support are welcome.

### Swap exact out support
The current `OrderDefinition` accommodates swap exact in orders, but support for swap exact out orders is pending.

### Support for Inline Datums
Jadex currently supports UTXO outputs with datum hashes but not [inline datums](https://cips.cardano.org/cips/cip32/). Adding support for inline datums is more efficient and requires additional `DexConfig` values indicating supported datum types.
Ideally, at least 1 dex supporting inline datums should be available for testing purposes.

### No pre-prod version of Muesliswap
Currently, there is no operational preprod version of Muesliswap. Efforts are underway to deploy it, but no timeline is available. Automated tests for placing and canceling swap orders with Muesliswap DEX are pending until its availability on preprod.


## Build
```
git clone https://github.com/bloxbean/jadex.git

./gradlew clean build
```

## Run Integration Tests

Jadex includes a parameterized test framework to execute tests against `DexTestConfig` instances.

A `DexTestConfig` can be used for preprod or mainnet configurations.
Tests which modify account state (swap and cancel) are only executed against preprod configurations... 


```
export TST_BF_PROJECT_ID=<Blockfrost Preprod network Project Id>
export MAIN_BF_PROJECT_ID=<Blockfrost Main network Project Id>
./gradlew integrationTest -PTST_BF_PROJECT_ID=${TST_BF_PROJECT_ID} -PMAIN_BF_PROJECT_ID=${MAIN_BF_PROJECT_ID}
```

## How to contribute

Your contributions to this project are highly appreciated. You can contribute by reporting issues, making feature requests, and submitting pull requests.

- Create a [Discussion](https://github.com/bloxbean/jadex/discussions)
- Create an [Issue](https://github.com/bloxbean/jadex/issues)
- Create a [Fork](https://github.com/bloxbean/jadex/fork)

Feel free to join the conversation on our [Discord Server](https://discord.gg/JtQ54MSw6p)

When contributing new `DexConfig` records, please provide matching `DexTestConfig` records for mainnet and preprod (if available).

## Version History



## License

This project is licensed under the MIT License - see the LICENSE.md file for details

## Acknowledgments

* [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)
* [Minswap](https://minswap.org)
* [Muesliswap](https://muesliswap.com)
