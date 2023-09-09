package com.bloxbean.cardano.jadex.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

class BigIntegerUtilTest {
    @Test
    void testBigDecimalConversion(){
        var bigIntegerSource = "1018";
        var bigDecimalSource = "0.001018";
        Assertions.assertEquals(new BigDecimal(bigDecimalSource), BigIntegerUtil.toBigDecimal(new BigInteger(bigIntegerSource), 6));
        Assertions.assertEquals(new BigInteger(bigIntegerSource), BigIntegerUtil.toBigInteger(new BigDecimal(bigDecimalSource), 6));
    }
}