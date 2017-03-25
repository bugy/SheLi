package net.buggy.shoplist.model;


import net.buggy.shoplist.R;

public enum Language {

    EN("en", R.string.language_en), RU("ru", R.string.language_ru);

    private final String locale;
    private final int nameKey;

    Language(String locale, int nameKey) {
        this.locale = locale;
        this.nameKey = nameKey;
    }

    public String getLocale() {
        return locale;
    }

    public int getNameKey() {
        return nameKey;
    }
}
