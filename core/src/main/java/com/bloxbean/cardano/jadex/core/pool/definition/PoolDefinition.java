
package com.bloxbean.cardano.jadex.core.pool.definition;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;


/**
 * Base pool definition defining the minimum expected items in each Dex-Specific pool datum
 *
 * @author $stik
 */
@RequiredArgsConstructor
@EqualsAndHashCode
@Getter
@ToString
public abstract class PoolDefinition {
    private final String assetA;
    private final String assetB;

    public abstract PlutusData toPlutusData();
}
