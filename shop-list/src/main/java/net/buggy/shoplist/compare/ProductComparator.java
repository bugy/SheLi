package net.buggy.shoplist.compare;

import net.buggy.shoplist.model.Product;

import java.util.Comparator;

public class ProductComparator implements Comparator<Product> {

    public static final ProductComparator INSTANCE = new ProductComparator();

    @Override
    public int compare(Product p1, Product p2) {
        final String name1 = p1.getName();
        final String name2 = p2.getName();

        return CompareUtils.safeCompare(name1, name2);
    }
}
