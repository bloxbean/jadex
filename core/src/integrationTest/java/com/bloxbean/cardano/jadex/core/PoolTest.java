package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.jadex.core.config.DexConfig;
import com.bloxbean.cardano.jadex.core.arguments.AssetPairArgumentsProvider;
import com.bloxbean.cardano.jadex.core.arguments.ConfigArgumentsProvider;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigDecimal;
import java.util.Random;

@Slf4j
public class PoolTest extends JadexBaseTest{


    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testPoolDefinitionSerialization(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);

        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);
        var definition = pool.getDefinition(getBackendService(dexConfig.network()).getScriptService());
        var actual = dexConfig.poolDefinitionProvider().fromDatum(definition.toPlutusData());
        Assertions.assertEquals(definition, actual);
    }

    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testGetPrice(String assetA, String assetB, DexConfig dexConfig, Dex dex) {
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);
        var price = dex.getPrice(policyIdA, tokenNameA, policyIdB, tokenNameB);
        Assertions.assertNotNull(price);
        Assertions.assertTrue(price._1.compareTo(BigDecimal.ZERO) > 0);
        Assertions.assertTrue(price._2.compareTo(BigDecimal.ZERO) > 0);
    }

    @ParameterizedTest
    @ArgumentsSource(ConfigArgumentsProvider.class)
    void testGetPoolById(DexConfig dexConfig, Dex dex){
        var allPools = dex.getAllPools();

        // select random pool from allPools
        Random rand = new Random();
        var randomPool = allPools.get(rand.nextInt(allPools.size()));
        var randomPoolId = randomPool.id();

        var fetched = dex.getPool(randomPoolId);

        Assertions.assertEquals(randomPool.getAssetA(), fetched.getAssetA());
        Assertions.assertEquals(randomPool.getAssetB(), fetched.getAssetB());
        Assertions.assertEquals(randomPool.getDataHash(), fetched.getDataHash());
        Assertions.assertEquals(randomPool.getAmounts(), fetched.getAmounts());
        Assertions.assertEquals(randomPool.getTxHash(), fetched.getTxHash());
        Assertions.assertEquals(randomPool.reserveA(), fetched.reserveA());
        Assertions.assertEquals(randomPool.reserveB(), fetched.reserveB());
    }

    @ParameterizedTest
    @ArgumentsSource(ConfigArgumentsProvider.class)
    void testGetMinPools(DexConfig dexConfig, Dex dex){
        var allPools = dex.getAllPools();
        log.info("All: " + allPools);
        log.info(allPools.size() + " pools detected...");
        Assertions.assertTrue(allPools.size() > 50);
    }

    @ParameterizedTest
    @ArgumentsSource(AssetPairArgumentsProvider.class)
    void testGetPool(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        // first swap ada for min
        var policyIdA = TokenUtil.getPolicyId(assetA);
        var tokenNameA = TokenUtil.getTokenName(assetA);
        var policyIdB = TokenUtil.getPolicyId(assetB);
        var tokenNameB = TokenUtil.getTokenName(assetB);

        var pool = dex.getPool(policyIdA, tokenNameA, policyIdB, tokenNameB);
        Assertions.assertNotNull(pool);
        Assertions.assertEquals(TokenUtil.getUnit(policyIdA, tokenNameA), pool.getAssetA());
        Assertions.assertEquals(TokenUtil.getUnit(policyIdB, tokenNameB), pool.getAssetB());
        Assertions.assertNotNull(pool.getDataHash());

        var definition = pool.getDefinition(getBackendService(dexConfig.network()).getScriptService());
        Assertions.assertNotNull(definition);
        Assertions.assertEquals(pool.getAssetA(), definition.getAssetA());
        Assertions.assertEquals(pool.getAssetB(), definition.getAssetB());
    }

}
