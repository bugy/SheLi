package net.buggy.shoplist.utils;

import android.content.Context;

import com.google.common.base.Function;

import net.buggy.shoplist.model.PeriodType;

public class PeriodTypeStringifier implements Function<PeriodType, String> {
    private final Context context;
    private Integer count;

    public PeriodTypeStringifier(Context context) {
        this.context = context;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Override
    public String apply(PeriodType type) {
        if (count == null) {
            return context.getString(type.getFullNameKey());
        }

        return context.getResources().getQuantityString(type.getPluralKey(), count);
    }
}
