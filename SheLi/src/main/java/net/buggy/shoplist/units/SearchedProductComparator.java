package net.buggy.shoplist.units;

import net.buggy.shoplist.compare.CompareUtils;
import net.buggy.shoplist.components.SearchProductCellFactory;
import net.buggy.shoplist.model.Product;

import java.util.Comparator;


public class SearchedProductComparator implements Comparator<SearchProductCellFactory.SearchedProduct> {

    private final String searchStringLower;

    public SearchedProductComparator(String searchString) {
        this.searchStringLower = searchString.toLowerCase();
    }

    @Override
    public int compare(SearchProductCellFactory.SearchedProduct o1, SearchProductCellFactory.SearchedProduct o2) {
        final Product p1 = o1.getProduct();
        final Product p2 = o2.getProduct();

        if (p1.getId() == null) {
            return -1;
        } else if (p2.getId() == null) {
            return 1;
        }

        if (o1.isExists()) {
            if (!o2.isExists()) {
                return 1;
            }
        } else if (o2.isExists()) {
            return -1;
        }

        final String name1 = p1.getName();
        final String name2 = p2.getName();

        if (name1.toLowerCase().startsWith(searchStringLower)) {
            if (!name2.toLowerCase().startsWith(searchStringLower)) {
                return -1;
            }
        } else if (name2.toLowerCase().startsWith(searchStringLower)) {
            return 1;
        }

        return CompareUtils.safeCompare(name1, name2);
    }
}
