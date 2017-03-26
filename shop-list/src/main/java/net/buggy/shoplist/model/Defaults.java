package net.buggy.shoplist.model;


import android.content.Context;
import android.graphics.Color;

import net.buggy.shoplist.R;
import net.buggy.shoplist.localisation.LocalisationHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.buggy.shoplist.localisation.LocalisationHelper.EN_LOCALE;
import static net.buggy.shoplist.localisation.LocalisationHelper.RU_LOCALE;

public class Defaults {

    private interface Translatable {
        int getStringId();

        Set<Locale> getLocales();
    }

    private enum DefaultProduct implements Translatable {
        APPLES(R.string.default_product_apples, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        BACON(R.string.default_product_bacon, DefaultCategory.MEAT, EN_LOCALE),
        BANANAS(R.string.default_product_bananas, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        BEANS(R.string.default_product_beans, null, RU_LOCALE, EN_LOCALE),
        BEER(R.string.default_product_beer, DefaultCategory.BEVERAGES, RU_LOCALE, EN_LOCALE),
        BEEF(R.string.default_product_beef, DefaultCategory.MEAT, RU_LOCALE, EN_LOCALE),
        BREAD(R.string.default_product_bread, null, RU_LOCALE, EN_LOCALE),
        BUCKWHEAT(R.string.default_product_buckwheat, DefaultCategory.CEREALS, RU_LOCALE),
        BUTTER(R.string.default_product_butter, DefaultCategory.DAIRY_PRODUCTS, RU_LOCALE, EN_LOCALE),
        CABBAGE(R.string.default_product_cabbage, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE),
        CARROTS(R.string.default_product_carrots, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        CHEESE(R.string.default_product_cheese, DefaultCategory.DAIRY_PRODUCTS, RU_LOCALE, EN_LOCALE),
        CHICKEN(R.string.default_product_chicken, DefaultCategory.MEAT, RU_LOCALE, EN_LOCALE),
        CHIPS(R.string.default_product_chips, DefaultCategory.CEREALS, EN_LOCALE),
        CHOCOLATE(R.string.default_product_chocolate, DefaultCategory.SWEETS, RU_LOCALE, EN_LOCALE),
        COFFEE(R.string.default_product_coffee, null, RU_LOCALE, EN_LOCALE),
        CORN(R.string.default_product_corn, null, RU_LOCALE, EN_LOCALE),
        CUCUMBER(R.string.default_product_cucumber, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        EGGS(R.string.default_product_eggs, DefaultCategory.MEAT, RU_LOCALE, EN_LOCALE),
        FISH(R.string.default_product_fish, DefaultCategory.MEAT, RU_LOCALE, EN_LOCALE),
        FLOUR(R.string.default_product_flour, DefaultCategory.CEREALS, RU_LOCALE, EN_LOCALE),
        GARLIC(R.string.default_product_garlic, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        HONEY(R.string.default_product_honey, DefaultCategory.SWEETS, EN_LOCALE),
        JUICE(R.string.default_product_juice, DefaultCategory.BEVERAGES, RU_LOCALE, EN_LOCALE),
        KETCHUP(R.string.default_product_ketchup, null, EN_LOCALE),
        LEMON(R.string.default_product_lemon, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        MAYO(R.string.default_product_mayo, null, RU_LOCALE, EN_LOCALE),
        MILK(R.string.default_product_milk, DefaultCategory.DAIRY_PRODUCTS, RU_LOCALE, EN_LOCALE),
        MUSHROOMS(R.string.default_product_mushrooms, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        ONION(R.string.default_product_onion, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        OIL(R.string.default_product_oil, null, RU_LOCALE, EN_LOCALE),
        ORANGES(R.string.default_product_oranges, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        PEPPER(R.string.default_product_pepper, DefaultCategory.SPICES, RU_LOCALE, EN_LOCALE),
        PORK(R.string.default_product_pork, DefaultCategory.MEAT, RU_LOCALE, EN_LOCALE),
        POTATO(R.string.default_product_potato, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        RICE(R.string.default_product_rice, DefaultCategory.CEREALS, RU_LOCALE, EN_LOCALE),
        SALT(R.string.default_product_salt, DefaultCategory.SPICES, RU_LOCALE, EN_LOCALE),
        SHAMPOO(R.string.default_product_shampoo, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE),
        SOAP(R.string.default_product_soap, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE),
        SPAGHETTI(R.string.default_product_spaghetti, DefaultCategory.CEREALS, RU_LOCALE, EN_LOCALE),
        SUGAR(R.string.default_product_sugar, null, RU_LOCALE, EN_LOCALE),
        SWEETS(R.string.default_product_sweets, DefaultCategory.SWEETS, RU_LOCALE, EN_LOCALE),
        TEA(R.string.default_product_tea, null, RU_LOCALE, EN_LOCALE),
        TOILET_PAPER(R.string.default_product_toilet_paper, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE),
        TOMATOES(R.string.default_product_tomatoes, DefaultCategory.FRUITS_AND_VEGETABLES, RU_LOCALE, EN_LOCALE),
        TOOTHPASTE(R.string.default_product_wine, DefaultCategory.BEVERAGES, RU_LOCALE, EN_LOCALE),
        WINE(R.string.default_product_toothpaste, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE);

        private final int stringId;
        private final DefaultCategory category;
        private final Set<Locale> locales;

        DefaultProduct(int stringId, DefaultCategory category, Locale... locales) {
            this.stringId = stringId;
            this.category = category;

            this.locales = new LinkedHashSet<>(Arrays.asList(locales));
        }

        public int getStringId() {
            return stringId;
        }

        public Set<Locale> getLocales() {
            return locales;
        }

        public DefaultCategory getCategory() {
            return category;
        }
    }

    private enum DefaultCategory implements Translatable {
        FRUITS_AND_VEGETABLES(R.string.default_category_fruits_and_vegetables, Color.GREEN,
                RU_LOCALE, EN_LOCALE),
        DAIRY_PRODUCTS(R.string.default_category_dairy_products, Color.WHITE,
                RU_LOCALE, EN_LOCALE),
        MEAT(R.string.default_category_meat, Color.rgb(255, 50, 50), RU_LOCALE, EN_LOCALE),
        HOUSEHOLD_CHEMICALS(R.string.default_category_household_chemicals, Color.MAGENTA, RU_LOCALE, EN_LOCALE),
        BEVERAGES(R.string.default_category_beverages, Color.CYAN, RU_LOCALE, EN_LOCALE),
        CEREALS(R.string.default_category_cereals, Color.rgb(206, 128, 12), RU_LOCALE, EN_LOCALE),
        SWEETS(R.string.default_category_sweets, Color.YELLOW, RU_LOCALE, EN_LOCALE),
        SPICES(R.string.default_category_spices, Color.rgb(220, 0, 0), RU_LOCALE, EN_LOCALE);

        private final int stringId;
        private final int color;
        private final Set<Locale> locales;

        DefaultCategory(int stringId, int color, Locale... locales) {
            this.stringId = stringId;
            this.color = color;

            this.locales = new LinkedHashSet<>(Arrays.asList(locales));
        }

        public int getStringId() {
            return stringId;
        }

        public Set<Locale> getLocales() {
            return locales;
        }

        public int getColor() {
            return color;
        }
    }


    public static List<Product> createDefaultProducts(Context context, List<Category> categories) {
        List<Product> result = new ArrayList<>();

        List<DefaultProduct> localeSpecificProducts = getLocaleSpecific(
                DefaultProduct.values(), context);
        Map<String, Category> categoryMap = new LinkedHashMap<>();
        for (Category category : categories) {
            categoryMap.put(category.getName(), category);
        }

        for (DefaultProduct defaultProduct : localeSpecificProducts) {
            final String name = context.getString(defaultProduct.getStringId());

            final Product product = new Product();
            product.setName(name);

            if (defaultProduct.getCategory() != null) {
                final String categoryName = context.getString(
                        defaultProduct.getCategory().getStringId());

                final Category category = categoryMap.get(categoryName);
                if (category != null) {
                    product.setCategories(Collections.singletonList(category));
                }
            }

            result.add(product);
        }

        return result;
    }

    public static List<Category> createDefaultCategories(Context context) {
        List<Category> result = new ArrayList<>();

        List<DefaultCategory> localeSpecificCategories = getLocaleSpecific(
                DefaultCategory.values(), context);

        for (DefaultCategory defaultCategory : localeSpecificCategories) {
            final String name = context.getString(defaultCategory.getStringId());

            final Category category = new Category();
            category.setName(name);
            category.setColor(defaultCategory.getColor());

            result.add(category);
        }

        return result;
    }

    private static <T extends Translatable> List<T> getLocaleSpecific(T[] values, Context context) {
        final Locale locale = LocalisationHelper.getLocale(context);

        List<T> result = new ArrayList<>();
        for (T value : values) {
            if (value.getLocales().contains(locale)) {
                result.add(value);
            }
        }

        return result;
    }

}
