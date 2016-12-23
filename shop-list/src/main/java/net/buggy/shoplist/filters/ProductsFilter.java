package net.buggy.shoplist.filters;

import com.android.internal.util.Predicate;
import com.google.common.base.Strings;

import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;

public class ProductsFilter implements Predicate<Product> {

    private final String text;
    private final Category category;

    public ProductsFilter(String text, Category category) {
        this.text = text;
        this.category = category;
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

        if (category == null) {
            return true;
        }

        return product.getCategories().contains(category);
    }
}
