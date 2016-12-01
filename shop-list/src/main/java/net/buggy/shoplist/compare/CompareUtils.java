package net.buggy.shoplist.compare;

public class CompareUtils {

    public static <T> Integer compareNulls(T value1, T value2) {
        if (value1 == null) {
            if (value2 == null) {
                return 0;
            }

            return -1;
        } else if (value2 == null) {
            return 1;
        }

        return null;
    }

    public static int safeCompare(Comparable value1, Comparable value2) {
        final Integer compareNulls = compareNulls(value1, value2);
        if (compareNulls != null) {
            return compareNulls;
        }

        return value1.compareTo(value2);
    }

    public static int safeCompare(String value1, String value2) {
        final Integer compareNulls = compareNulls(value1, value2);
        if (compareNulls != null) {
            return compareNulls;
        }

        return value1.toLowerCase().compareTo(value2.toLowerCase());
    }
}
