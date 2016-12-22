package net.buggy.shoplist.utils;


import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;

public class NumberUtils {

    private static final DecimalFormat BIG_DECIMAL_FORMAT;

    static {
        BIG_DECIMAL_FORMAT = new DecimalFormat();
        BIG_DECIMAL_FORMAT.setParseBigDecimal(true);
        BIG_DECIMAL_FORMAT.setMaximumFractionDigits(5);
        BIG_DECIMAL_FORMAT.setMinimumFractionDigits(0);
        BIG_DECIMAL_FORMAT.setGroupingUsed(false);
    }

    public static BigDecimal toBigDecimal(String string) {
        try {
            return (BigDecimal) BIG_DECIMAL_FORMAT.parse(string);
        } catch (ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
