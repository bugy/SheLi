package net.buggy.shoplist.compare;

import net.buggy.shoplist.model.Category;

import java.util.Comparator;

public class CategoryComparator implements Comparator<Category> {

    public static final CategoryComparator INSTANCE = new CategoryComparator();

    @Override
    public int compare(Category c1, Category c2) {
        final String name1 = c1.getName();
        final String name2 = c2.getName();

        return CompareUtils.safeCompare(name1, name2);
    }
}
