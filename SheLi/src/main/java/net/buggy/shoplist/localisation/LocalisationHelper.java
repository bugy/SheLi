package net.buggy.shoplist.localisation;


import android.content.Context;

import net.buggy.components.ViewUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LocalisationHelper {

    private final static Locale DEF_LOCALE = new Locale("en");

    public static final Locale RU_LOCALE = new Locale("ru");
    public static final Locale EN_LOCALE = new Locale("en");

    private final static List<Locale> SUPPORTED_LOCALES = Arrays.asList(
            EN_LOCALE,
            RU_LOCALE);

    public static Locale getLocale(Context context) {
        final Locale locale = ViewUtils.getAppLocale(context);

        if (!SUPPORTED_LOCALES.contains(locale)) {
            return DEF_LOCALE;
        }

        return locale;
    }
}
