package net.buggy.shoplist.compare;

import net.buggy.shoplist.model.ShopItem;

import java.util.Comparator;

public class ShopItemComparator implements Comparator<ShopItem> {
    @Override
    public int compare(ShopItem i1, ShopItem i2) {
        final Integer productCompare = CompareUtils.compareNulls(i1.getProduct(), i2.getProduct());
        if (productCompare != null) {
            return productCompare;
        }

        final String name1 = i1.getProduct().getName();
        final String name2 = i2.getProduct().getName();

        return CompareUtils.safeCompare(name1, name2);
    }
}
