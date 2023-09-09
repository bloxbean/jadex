
package com.bloxbean.cardano.jadex.core.dex.minswap.pool;

import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.jadex.core.pool.definition.PoolDefinition;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;

/**
 * {@link PoolDefinition PoolDefinition} for Minswap DEX
 *
 * @author $stik
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
public class MinPoolDefinition extends PoolDefinition {
    private final BigInteger totalLiquidity;
    private final BigInteger rootKLast;

    public MinPoolDefinition(String assetA, String assetB, BigInteger totalLiquidity, BigInteger rootKLast) {
        super(assetA, assetB);
        this.totalLiquidity = totalLiquidity;
        this.rootKLast = rootKLast;
    }

    public PlutusData toPlutusData(){
        return ConstrPlutusData.of(0,
                assetToPlutusData(getAssetA()),
                assetToPlutusData(getAssetB()),
                BigIntPlutusData.of(totalLiquidity),
                BigIntPlutusData.of(rootKLast),
                ConstrPlutusData.of(1, ListPlutusData.of()));
    }
    private PlutusData assetToPlutusData(String asset) {
        return StringUtils.isBlank(asset) || CardanoConstants.LOVELACE.equals(asset)
                ? ConstrPlutusData.of(0,
                    BytesPlutusData.of(""),
                    BytesPlutusData.of(""))
                : ConstrPlutusData.of(0,
                    StringUtils.isNotBlank(TokenUtil.getPolicyId(asset))
                        ? BytesPlutusData.of(HexUtil.decodeHexString(TokenUtil.getPolicyId(asset)))
                        : BytesPlutusData.of(""),
                    StringUtils.isNotBlank(TokenUtil.getTokenName(asset))
                        ? BytesPlutusData.of(HexUtil.decodeHexString(TokenUtil.getTokenName(asset)))
                        : BytesPlutusData.of(""));
    }

    public static MinPoolDefinition fromPlutusData(PlutusData plutusData){
        var source = (ConstrPlutusData)plutusData;
        var assetA = assetFromPlutusData(source.getData().getPlutusDataList().get(0));
        var assetB = assetFromPlutusData(source.getData().getPlutusDataList().get(1));
        var totalLiquidity = ((BigIntPlutusData)source.getData().getPlutusDataList().get(2)).getValue();
        var rootKLast = ((BigIntPlutusData)source.getData().getPlutusDataList().get(3)).getValue();
        return new MinPoolDefinition(assetA, assetB, totalLiquidity, rootKLast);
    }
    private static String assetFromPlutusData(PlutusData plutusData){
        var constr = (ConstrPlutusData) plutusData;
        var policyIdByte = ((BytesPlutusData)constr.getData().getPlutusDataList().get(0)).getValue();
        var policyIdExtracted = HexUtil.encodeHexString(policyIdByte);
        var tokenNameByte = ((BytesPlutusData)constr.getData().getPlutusDataList().get(1)).getValue();
        var tokenNameExtracted = HexUtil.encodeHexString(tokenNameByte);
        return StringUtils.isNotBlank(policyIdExtracted) || StringUtils.isNotBlank(tokenNameExtracted) ? TokenUtil.getUnit(policyIdExtracted, tokenNameExtracted) : CardanoConstants.LOVELACE;
    }
}
