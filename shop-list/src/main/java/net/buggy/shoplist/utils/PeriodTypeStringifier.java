package net.buggy.shoplist.utils;

import com.google.common.base.Function;

import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.PeriodType;

public class PeriodTypeStringifier implements Function<PeriodType, String> {
    private final ShopListActivity activity;

    public PeriodTypeStringifier(ShopListActivity activity) {
        this.activity = activity;
    }

    @Override
    public String apply(PeriodType type) {
        return activity.getString(type.getFullNameKey());
    }
}
