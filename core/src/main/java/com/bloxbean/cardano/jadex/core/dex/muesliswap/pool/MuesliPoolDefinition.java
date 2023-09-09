
package com.bloxbean.cardano.jadex.core.dex.muesliswap.pool;

import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.jadex.core.pool.definition.PoolDefinition;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;

/**
 * {@link PoolDefinition PoolDefinition} for Muesliswap DEX
 *
 * @author $stik
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@ToString(callSuper = true)
public class MuesliPoolDefinition extends PoolDefinition {
    private final BigInteger totalLpTokens;
    private final BigInteger lpFee;

    public MuesliPoolDefinition(String assetA, String assetB, BigInteger totalLpTokens, BigInteger lpFee) {
        super(assetA, assetB);
        this.totalLpTokens = totalLpTokens;
        this.lpFee = lpFee;
    }

    public PlutusData toPlutusData(){
        return ConstrPlutusData.of(0,
                assetToPlutusData(getAssetA()),
                assetToPlutusData(getAssetB()),
                BigIntPlutusData.of(totalLpTokens),
                BigIntPlutusData.of(lpFee));
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

    public static MuesliPoolDefinition fromPlutusData(PlutusData plutusData){
        var source = (ConstrPlutusData)plutusData;
        var assetA = assetFromPlutusData(source.getData().getPlutusDataList().get(0));
        var assetB = assetFromPlutusData(source.getData().getPlutusDataList().get(1));
        var totalLpTokens = ((BigIntPlutusData)source.getData().getPlutusDataList().get(2)).getValue();
        var lpFee = ((BigIntPlutusData)source.getData().getPlutusDataList().get(3)).getValue();
        return new MuesliPoolDefinition(assetA, assetB, totalLpTokens, lpFee);
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
