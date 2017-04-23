package net.buggy.shoplist.utils;

import android.content.Context;

import com.google.common.base.Function;

import net.buggy.shoplist.model.Language;

public class LanguageEnumStringifier implements Function<Language, String> {
    private final Context context;

    public LanguageEnumStringifier(Context context) {
        this.context = context;
    }

    @Override
    public String apply(Language language) {
        return context.getString(language.getNameKey());
    }
}
