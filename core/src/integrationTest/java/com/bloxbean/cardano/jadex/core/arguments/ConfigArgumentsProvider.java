package com.bloxbean.cardano.jadex.core.arguments;

import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.jadex.core.Dex;
import com.bloxbean.cardano.jadex.core.DexImpl;
import com.bloxbean.cardano.jadex.core.DexTestConfigs;
import com.bloxbean.cardano.jadex.core.JadexBaseTest;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.stream.Stream;

@Slf4j
public class ConfigArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return DexTestConfigs.all().stream()
                .filter(it -> {
                    if(StringUtils.isBlank(it.blockfrostProjectId())){
                        log.warn("Skipping test for [" + it.dexConfig().dexType() + "] since blockfrostProjectId is not set...");
                        return false;
                    }
                    return true;
                })
                .map(testConfig -> {
                    var backendService = new BFBackendService(JadexBaseTest.blockfrostUrl(testConfig.dexConfig().network()), testConfig.blockfrostProjectId());
                    var dex = new DexImpl(backendService, testConfig.dexConfig());
                    return toArguments(testConfig.dexConfig(), dex);
                });
    }
    private Arguments toArguments(DexConfig dexConfig, Dex dex){
        return Arguments.of(dexConfig, dex);
    }
}
