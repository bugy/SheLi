package net.buggy.shoplist.model;

import android.graphics.Color;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

import java.util.Collection;

public class ModelHelper {

    public static int getColor(Category category) {
        if (category.getColor() != null) {
            return category.getColor();
        }

        return Color.WHITE;
    }

    public static Multiset<Integer> getColors(Collection<Category> categories) {
        Multiset<Integer> result = LinkedHashMultiset.create();

        for (Category category : categories) {
            final int color = getColor(category);
            result.add(color);
        }

        return result;
    }
}
