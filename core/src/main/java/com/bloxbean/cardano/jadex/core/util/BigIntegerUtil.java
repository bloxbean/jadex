
package com.bloxbean.cardano.jadex.core.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

/**
 * Util class for interacting with {@link BigInteger BigInteger} in a null-safe way.
 *
 * @author $stik
 */
@UtilityClass
public class BigIntegerUtil {

    public static BigInteger sum(BigInteger ... values){
        if(values == null || values.length == 0){
            return null;
        }
        return Arrays.stream(values)
                     .filter(Objects::nonNull)
                     .reduce(BigInteger::add)
                     .orElse(null);
    }

    public static boolean isPositive(BigInteger value){
        return  value != null && BigInteger.ZERO.compareTo(value) < 0;
    }

    public static BigDecimal toBigDecimal(BigInteger source, int nrOfDecimals){
        return source != null
                ? nrOfDecimals > 0
                    ? new BigDecimal(source).divide(new BigDecimal("10").pow(nrOfDecimals), nrOfDecimals, RoundingMode.UNNECESSARY)
                    : new BigDecimal(source)
                : null;
    }
    public static BigInteger toBigInteger(BigDecimal source, int nrOfDecimals){
        return source != null
                ? nrOfDecimals > 0
                    ? source.multiply(new BigDecimal("10").pow(nrOfDecimals)).toBigInteger()
                    : source.toBigInteger()
                : null;
    }

    public static boolean equals(BigInteger bi1, BigInteger bi2){
        if(bi1 == null && bi2 == null){
            return true;
        }
        if(bi1 == null || bi2 == null){
            return false;
        }
        return bi1.compareTo(bi2) == 0;
    }
}
