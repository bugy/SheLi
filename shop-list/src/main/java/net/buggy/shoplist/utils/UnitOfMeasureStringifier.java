package net.buggy.shoplist.utils;

import android.content.Context;

import com.google.common.base.Function;

import net.buggy.shoplist.model.UnitOfMeasure;

public class UnitOfMeasureStringifier implements Function<UnitOfMeasure, String> {
    private final Context context;

    public UnitOfMeasureStringifier(Context context) {
        this.context = context;
    }

    @Override
    public String apply(UnitOfMeasure unitOfMeasure) {
        final String fullName = context.getString(unitOfMeasure.getFullNameKey());
        final String shortName = context.getString(unitOfMeasure.getShortNameKey());

        return fullName + ", " + shortName;
    }
}
