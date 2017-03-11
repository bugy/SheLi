package net.buggy.shoplist.model;

import android.content.Context;
import android.graphics.Color;

import com.google.common.base.Objects;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.utils.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    public static void saveCategoryLinkedProducts(
            Category category, Set<Product> linkedProducts, DataStorage dataStorage) {

        final List<Product> allProducts = dataStorage.getProducts();

        for (Product product : allProducts) {
            final boolean unlinked = !linkedProducts.contains(product)
                    && product.getCategories().contains(category);
            final boolean linked = linkedProducts.contains(product)
                    && !product.getCategories().contains(category);

            if (unlinked) {
                product.getCategories().remove(category);
                dataStorage.saveProduct(product);
            } else if (linkedProducts.contains(product) && linked) {
                product.getCategories().add(category);
                dataStorage.saveProduct(product);
            }
        }
    }

    public static boolean isUnique(Category category, String name, ShopListActivity activity) {
        final DataStorage dataStorage = activity.getDataStorage();
        final List<Category> categories = dataStorage.getCategories();

        for (Category anotherCategory : categories) {
            if (Objects.equal(anotherCategory, category)) {
                continue;
            }

            if (StringUtils.equalIgnoreCase(anotherCategory.getName(), name)) {

                return false;
            }
        }

        return true;
    }

    public static Category createCategory(String name) {
        final Category category = new Category();
        category.setName(name);

        final Random random = new Random();
        final int color = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
        category.setColor(color);

        return category;
    }

    public static String buildStringQuantity(ShopItem shopItem, Context context) {
        if (shopItem.getQuantity() == null) {
            return "";
        }

        final String quantityString = StringUtils.toString(shopItem.getQuantity());

        UnitOfMeasure unitOfMeasure = shopItem.getUnitOfMeasure();
        if (unitOfMeasure == null) {
            unitOfMeasure = shopItem.getProduct().getDefaultUnits();
        }
        if (unitOfMeasure == null) {
            return quantityString;
        }

        final String unitsString = context.getString(unitOfMeasure.getShortNameKey());
        return quantityString + " " + unitsString;
    }
}
