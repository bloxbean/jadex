
package com.bloxbean.cardano.jadex.core.config;

import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.jadex.core.dex.minswap.order.MinOrderDefinitionProvider;
import com.bloxbean.cardano.jadex.core.dex.minswap.order.MinMetadataProvider;
import com.bloxbean.cardano.jadex.core.dex.minswap.pool.MinPoolDefinitionProvider;
import com.bloxbean.cardano.jadex.core.dex.minswap.pool.MinPoolStateProvider;
import com.bloxbean.cardano.jadex.core.dex.muesliswap.order.MuesliMetadataProvider;
import com.bloxbean.cardano.jadex.core.dex.muesliswap.order.MuesliOrderDefinitionProvider;
import com.bloxbean.cardano.jadex.core.dex.muesliswap.pool.MuesliPoolDefinitionProvider;
import com.bloxbean.cardano.jadex.core.dex.muesliswap.pool.MuesliPoolStateProvider;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A static set of supported {@link DexConfig DexConfig}
 *
 * @author $stik
 */
public class DexConfigs {
    /**
     * Minswap mainnet configuration
     */
    public static final DexConfig MIN_CONFIG = new DexConfig(Networks.mainnet(),
            "MINSWAP",
            "0be55d262b29f564998ff81efe21bdc0022621c12f15af08d0f2ddb1",
            "13aa2accf2e1561723aa26871e071fdf32c867cff7e7d50ad470d62f",
            "4d494e53574150",
            "e4214b7cce62ac6fbba385d164df48e157eae5863521b4b67ca71d86",
            new BigDecimal("0.003"),
            "addr1zxn9efv2f6w82hagxqtn62ju4m293tqvw0uhmdl64ch8uw6j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq6s3z70",
            BigInteger.valueOf(2_000_000),
            BigInteger.valueOf(2_000_000),
            Language.PLUTUS_V1,
            new MinPoolStateProvider(),
            new MinPoolDefinitionProvider(),
            new MinOrderDefinitionProvider(),
            new MinMetadataProvider());

    /**
     * Muesliswap mainnet configuration
     */
    public static final DexConfig MUESLI_V3_CONFIG = new DexConfig(Networks.mainnet(),
            "MUESLISWAP_V3",
            "909133088303c49f3a30f1cc8ed553a73857a29779f6c6561cd8093f",
            "de9b756719341e79785aa13c164e7fe68c189ed04d61c9876b2fe53f",
            "4d7565736c69537761705f414d4d",
            "af3d70acf4bd5b3abb319a7d75c89fb3e56eafcdd46b2e9b57a2557f",
            null,
            "addr1zyq0kyrml023kwjk8zr86d5gaxrt5w8lxnah8r6m6s4jp4g3r6dxnzml343sx8jweqn4vn3fz2kj8kgu9czghx0jrsyqqktyhv",
            BigInteger.valueOf(950_000),
            BigInteger.valueOf(1_700_000),
            Language.PLUTUS_V2,
            new MuesliPoolStateProvider(),
            new MuesliPoolDefinitionProvider(),
            new MuesliOrderDefinitionProvider(),
            new MuesliMetadataProvider(Networks.mainnet()));
}
