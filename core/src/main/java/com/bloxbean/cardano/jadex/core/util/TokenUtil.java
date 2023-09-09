
package com.bloxbean.cardano.jadex.core.util;

import com.bloxbean.cardano.client.common.CardanoConstants;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Util class for converting unit, policyId and tokenName values
 *
 * @author $stik
 */
@UtilityClass
public class TokenUtil {

    public static boolean equals(String unit1, String unit2){
        return StringUtils.equals(StringUtils.trimToNull(unit1), StringUtils.trimToNull(unit2));
    }
    public static String getPolicyId(String unit){
        return StringUtils.equals(unit, CardanoConstants.LOVELACE)
                ? null
                : StringUtils.substring(unit, 0, 56);
    }
    public static String getTokenName(String unit){
        return StringUtils.equals(unit, CardanoConstants.LOVELACE)
                ? CardanoConstants.LOVELACE
                : StringUtils.substring(unit, 56);
    }
    public static String getUnit(String policyId, String tokenName){
        if(!StringUtils.equals(tokenName, CardanoConstants.LOVELACE)
            && StringUtils.isBlank(policyId)){
            throw new IllegalArgumentException("PolicyId is mandatory for non lovelace tokens. policyId ["+policyId+"] token [" + tokenName + "]");
        }
        return StringUtils.isNotBlank(policyId)
                ? policyId + tokenName
                : CardanoConstants.LOVELACE;
    }
}
