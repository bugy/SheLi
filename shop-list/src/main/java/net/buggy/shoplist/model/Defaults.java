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
        APPLES(R.string.default_product_apples, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        BACON(R.string.default_product_bacon, DefaultCategory.FOOD, EN_LOCALE),
        BANANAS(R.string.default_product_bananas, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        BEANS(R.string.default_product_beans, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        BEEF(R.string.default_product_beef, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        BREAD(R.string.default_product_bread, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        BUCKWHEAT(R.string.default_product_buckwheat, DefaultCategory.FOOD, RU_LOCALE),
        BUTTER(R.string.default_product_butter, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        CABBAGE(R.string.default_product_cabbage, DefaultCategory.FOOD, RU_LOCALE),
        CARROTS(R.string.default_product_carrots, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        CHEESE(R.string.default_product_cheese, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        CHICKEN(R.string.default_product_chicken, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        CHIPS(R.string.default_product_chips, DefaultCategory.FOOD, EN_LOCALE),
        CHOCOLATE(R.string.default_product_chocolate, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        COFFEE(R.string.default_product_coffee, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        CORN(R.string.default_product_corn, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        CUCUMBER(R.string.default_product_cucumber, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        EGGS(R.string.default_product_eggs, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        FISH(R.string.default_product_fish, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        FLOUR(R.string.default_product_flour, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        GARLIC(R.string.default_product_garlic, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        HONEY(R.string.default_product_honey, DefaultCategory.FOOD, EN_LOCALE),
        JUICE(R.string.default_product_juice, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        KETCHUP(R.string.default_product_ketchup, DefaultCategory.FOOD, EN_LOCALE),
        LEMON(R.string.default_product_lemon, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        MAYO(R.string.default_product_mayo, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        MILK(R.string.default_product_milk, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        MUSHROOMS(R.string.default_product_mushrooms, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        ONION(R.string.default_product_onion, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        OIL(R.string.default_product_oil, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        ORANGES(R.string.default_product_oranges, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        PEPPER(R.string.default_product_pepper, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        PORK(R.string.default_product_pork, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        POTATO(R.string.default_product_potato, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        RICE(R.string.default_product_rice, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        SALT(R.string.default_product_salt, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        SHAMPOO(R.string.default_product_shampoo, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE),
        SOAP(R.string.default_product_soap, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE),
        SPAGHETTI(R.string.default_product_spaghetti, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        SUGAR(R.string.default_product_sugar, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        SWEETS(R.string.default_product_sweets, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        TEA(R.string.default_product_tea, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        TOILET_PAPER(R.string.default_product_toilet_paper, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE),
        TOMATOES(R.string.default_product_tomatoes, DefaultCategory.FOOD, RU_LOCALE, EN_LOCALE),
        TOOTHPASTE(R.string.default_product_toothpaste, DefaultCategory.HOUSEHOLD_CHEMICALS, RU_LOCALE, EN_LOCALE);

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
        FOOD(R.string.default_category_food, Color.GREEN,
                RU_LOCALE, EN_LOCALE),
        HOUSEHOLD_CHEMICALS(R.string.default_category_household_chemicals, Color.MAGENTA, RU_LOCALE, EN_LOCALE);

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
            categoryMap.put(category.getName().toLowerCase(), category);
        }

        for (DefaultProduct defaultProduct : localeSpecificProducts) {
            final String name = context.getString(defaultProduct.getStringId());

            final Product product = new Product();
            product.setName(name);

            if (defaultProduct.getCategory() != null) {
                final String categoryName = context.getString(
                        defaultProduct.getCategory().getStringId());

                final Category category = categoryMap.get(categoryName.toLowerCase());
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
