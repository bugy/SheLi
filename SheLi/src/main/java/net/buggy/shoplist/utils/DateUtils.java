package net.buggy.shoplist.utils;


import com.google.common.base.Preconditions;

import java.util.Date;

public class DateUtils {

    public static final long MS_IN_SEC = 1000;
    public static final long SEC_IN_MIN = 60;
    public static final long MS_IN_MIN = MS_IN_SEC * SEC_IN_MIN;
    public static final long MIN_IN_HOUR = 60;
    public static final long MS_IN_HOUR = MS_IN_MIN * MIN_IN_HOUR;
    public static final long HOURS_IN_DAY = 24;
    public static final long MS_IN_DAY = MS_IN_HOUR * HOURS_IN_DAY;

    public static double daysDiff(Date from, Date to) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(to);

        final long differenceMs = to.getTime() - from.getTime();

        return (double) differenceMs / MS_IN_DAY;
    }

}
