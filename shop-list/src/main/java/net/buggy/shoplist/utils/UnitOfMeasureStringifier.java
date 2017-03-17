package net.buggy.shoplist.utils;

import com.google.common.base.Function;

import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.UnitOfMeasure;

public class UnitOfMeasureStringifier implements Function<UnitOfMeasure, String> {
    private final ShopListActivity activity;

    public UnitOfMeasureStringifier(ShopListActivity activity) {
        this.activity = activity;
    }

    @Override
    public String apply(UnitOfMeasure unitOfMeasure) {
        final String fullName = activity.getString(unitOfMeasure.getFullNameKey());
        final String shortName = activity.getString(unitOfMeasure.getShortNameKey());

        return fullName + ", " + shortName;
    }
}
