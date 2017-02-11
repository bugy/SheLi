package net.buggy.shoplist.filters;

import com.android.internal.util.Predicate;
import com.google.common.base.Strings;

import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;

import java.util.List;

public class ProductsFilter implements Predicate<Product> {

    private final String text;
    private final List<Category> categories;

    public ProductsFilter(String text, List<Category> categories) {
        this.text = text;
        this.categories = categories;
    }

    @Override
    public boolean apply(Product product) {
        if (!Strings.isNullOrEmpty(text)) {
            final String name = product.getName().toLowerCase();
            final String text = this.text.toLowerCase();

            if (!name.contains(text)) {
                return false;
            }
        }

        if (categories == null) {
            return true;
        }

        return product.getCategories().containsAll(categories);
    }
}
