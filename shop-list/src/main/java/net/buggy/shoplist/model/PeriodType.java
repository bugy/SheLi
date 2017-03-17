package net.buggy.shoplist.model;


import net.buggy.shoplist.R;

public enum PeriodType {
    DAYS(R.string.period_type_days, 1),
    WEEKS(R.string.period_type_weeks, 7),
    MONTHS(R.string.period_type_months, 30),
    YEARS(R.string.period_type_years, 365);

    private final int fullNameKey;
    private final int days;

    PeriodType(int fullNameKey, int days) {
        this.fullNameKey = fullNameKey;
        this.days = days;
    }

    public int getFullNameKey() {
        return fullNameKey;
    }

    public int getDays() {
        return days;
    }
}
