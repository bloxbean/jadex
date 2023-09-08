package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.config.DexConfig;
import lombok.Builder;

import java.util.List;

@Builder
public record DexTestConfig(String blockfrostProjectId,
                            DexConfig dexConfig,
                            String orderScript,
                            List<Tuple<String, String>> testAssetPairs){
}