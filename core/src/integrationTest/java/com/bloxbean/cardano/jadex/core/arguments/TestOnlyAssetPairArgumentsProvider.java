package com.bloxbean.cardano.jadex.core.arguments;

import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.jadex.core.*;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.stream.Stream;

public class TestOnlyAssetPairArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return DexTestConfigs.testOnly().stream()
                .flatMap(this::toArguments);
    }
    private Stream<? extends Arguments> toArguments(DexTestConfig testConfig){
        var backendService = new BFBackendService(JadexBaseTest.blockfrostUrl(testConfig.dexConfig().network()), testConfig.blockfrostProjectId());
        var dex = new DexImpl(backendService, testConfig.dexConfig());
        return testConfig.testAssetPairs().stream()
                .map(assetPair -> toArguments(assetPair._1, assetPair._2, testConfig.dexConfig(), dex));
    }
    private Arguments toArguments(String assetA, String assetB, DexConfig dexConfig, Dex dex){
        return Arguments.of(assetA, assetB, dexConfig, dex);
    }
}
