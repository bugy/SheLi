package net.buggy.shoplist.model;


import net.buggy.shoplist.R;

public enum PeriodType {
    DAYS(R.string.period_type_days, R.plurals.period_type_days, 1),
    WEEKS(R.string.period_type_weeks, R.plurals.period_type_weeks, 7),
    MONTHS(R.string.period_type_months, R.plurals.period_type_months, 30),
    YEARS(R.string.period_type_years, R.plurals.period_type_years, 365);

    private final int fullNameKey;
    private final int pluralKey;
    private final int days;

    PeriodType(int fullNameKey, int pluralKey, int days) {
        this.fullNameKey = fullNameKey;
        this.pluralKey = pluralKey;
        this.days = days;
    }

    public int getFullNameKey() {
        return fullNameKey;
    }

    public int getPluralKey() {
        return pluralKey;
    }

    public int getDays() {
        return days;
    }
}
