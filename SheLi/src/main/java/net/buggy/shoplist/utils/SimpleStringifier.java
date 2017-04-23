package net.buggy.shoplist.utils;

import com.google.common.base.Function;

public class SimpleStringifier<T> implements Function<T, String> {

    public final static SimpleStringifier INSTANCE = new SimpleStringifier();

    @Override
    public String apply(T object) {
        if (object == null) {
            return null;
        }

        return object.toString();
    }
}
