package net.buggy.shoplist.utils;

import com.google.common.base.Function;

import java.math.BigDecimal;

public class BigDecimalStringifier implements Function<BigDecimal, String> {

    public final static BigDecimalStringifier INSTANCE = new BigDecimalStringifier();

    @Override
    public String apply(BigDecimal bigDecimal) {
        return StringUtils.toString(bigDecimal);
    }
}
