package net.buggy.shoplist.model;


import android.content.Context;

import net.buggy.shoplist.R;
import net.buggy.shoplist.localisation.LocalisationHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.buggy.shoplist.localisation.LocalisationHelper.EN_LOCALE;
import static net.buggy.shoplist.localisation.LocalisationHelper.RU_LOCALE;

public class Defaults {

    private enum DefaultProduct {
        APPLES(R.string.default_product_apples, RU_LOCALE, EN_LOCALE),
        BACON(R.string.default_product_bacon, EN_LOCALE),
        BANANAS(R.string.default_product_bananas, RU_LOCALE, EN_LOCALE),
        BEANS(R.string.default_product_beans, RU_LOCALE, EN_LOCALE),
        BEER(R.string.default_product_beer, RU_LOCALE, EN_LOCALE),
        BEEF(R.string.default_product_beef, RU_LOCALE, EN_LOCALE),
        BREAD(R.string.default_product_bread, RU_LOCALE, EN_LOCALE),
        BUCKWHEAT(R.string.default_product_buckwheat, RU_LOCALE),
        BUTTER(R.string.default_product_butter, RU_LOCALE, EN_LOCALE),
        CABBAGE(R.string.default_product_cabbage, RU_LOCALE),
        CARROTS(R.string.default_product_carrots, RU_LOCALE, EN_LOCALE),
        CHEESE(R.string.default_product_cheese, RU_LOCALE, EN_LOCALE),
        CHICKEN(R.string.default_product_chicken, RU_LOCALE, EN_LOCALE),
        CHIPS(R.string.default_product_chips, EN_LOCALE),
        CHOCOLATE(R.string.default_product_chocolate, RU_LOCALE, EN_LOCALE),
        COFFEE(R.string.default_product_coffee, RU_LOCALE, EN_LOCALE),
        CORN(R.string.default_product_corn, RU_LOCALE, EN_LOCALE),
        CUCUMBER(R.string.default_product_cucumber, RU_LOCALE, EN_LOCALE),
        EGGS(R.string.default_product_eggs, RU_LOCALE, EN_LOCALE),
        FISH(R.string.default_product_fish, RU_LOCALE, EN_LOCALE),
        FLOUR(R.string.default_product_flour, RU_LOCALE, EN_LOCALE),
        GARLIC(R.string.default_product_garlic, RU_LOCALE, EN_LOCALE),
        HONEY(R.string.default_product_honey, EN_LOCALE),
        JUICE(R.string.default_product_juice, RU_LOCALE, EN_LOCALE),
        KETCHUP(R.string.default_product_ketchup, EN_LOCALE),
        LEMON(R.string.default_product_lemon, RU_LOCALE, EN_LOCALE),
        MAYO(R.string.default_product_mayo, RU_LOCALE, EN_LOCALE),
        MILK(R.string.default_product_milk, RU_LOCALE, EN_LOCALE),
        MUSHROOMS(R.string.default_product_mushrooms, RU_LOCALE, EN_LOCALE),
        ONION(R.string.default_product_onion, RU_LOCALE, EN_LOCALE),
        OIL(R.string.default_product_oil, RU_LOCALE, EN_LOCALE),
        ORANGES(R.string.default_product_oranges, RU_LOCALE, EN_LOCALE),
        PEPPER(R.string.default_product_pepper, RU_LOCALE, EN_LOCALE),
        PORK(R.string.default_product_pork, RU_LOCALE, EN_LOCALE),
        POTATO(R.string.default_product_potato, RU_LOCALE, EN_LOCALE),
        RICE(R.string.default_product_rice, RU_LOCALE, EN_LOCALE),
        SALT(R.string.default_product_salt, RU_LOCALE, EN_LOCALE),
        SHAMPOO(R.string.default_product_shampoo, RU_LOCALE, EN_LOCALE),
        SOAP(R.string.default_product_soap, RU_LOCALE, EN_LOCALE),
        SPAGHETTI(R.string.default_product_spaghetti, RU_LOCALE, EN_LOCALE),
        SUGAR(R.string.default_product_sugar, RU_LOCALE, EN_LOCALE),
        SWEETS(R.string.default_product_sweets, RU_LOCALE, EN_LOCALE),
        TEA(R.string.default_product_tea, RU_LOCALE, EN_LOCALE),
        TOILET_PAPER(R.string.default_product_toilet_paper, RU_LOCALE, EN_LOCALE),
        TOMATOES(R.string.default_product_tomatoes, RU_LOCALE, EN_LOCALE),
        TOOTHPASTE(R.string.default_product_wine, RU_LOCALE, EN_LOCALE),
        WINE(R.string.default_product_toothpaste, RU_LOCALE, EN_LOCALE);

        private final int stringId;
        private final Set<Locale> locales;

        DefaultProduct(int stringId, Locale... locales) {
            this.stringId = stringId;

            this.locales = new LinkedHashSet<>(Arrays.asList(locales));
        }

        public int getStringId() {
            return stringId;
        }

        public Set<Locale> getLocales() {
            return locales;
        }
    }


    public static List<Product> createDefaultProducts(Context context) {
        final Locale locale = LocalisationHelper.getLocale(context);

        List<Product> result = new ArrayList<>();

        for (DefaultProduct defaultProduct : DefaultProduct.values()) {
            if (defaultProduct.getLocales().contains(locale)) {
                final String name = context.getString(defaultProduct.getStringId());

                final Product product = new Product();
                product.setName(name);

                result.add(product);
            }
        }

        return result;
    }

}
