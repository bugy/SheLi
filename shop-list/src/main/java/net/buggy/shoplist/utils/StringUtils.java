package net.buggy.shoplist.utils;


public class StringUtils {
    public static boolean equalIgnoreCase(String s1, String s2) {
        //noinspection StringEquality
        if (s1 == s2) {
            return true;
        }

        if (s1 == null) {
            return false;
        }

        return s1.equalsIgnoreCase(s2);
    }
}
