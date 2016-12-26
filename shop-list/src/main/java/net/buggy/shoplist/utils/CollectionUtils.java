package net.buggy.shoplist.utils;


import com.google.common.base.Objects;

import java.util.Collection;

public class CollectionUtils {

    public static <T> T findSame(Collection<T> collection, T element) {
        for (T anotherElement : collection) {
            if (Objects.equal(anotherElement, element)) {
                return anotherElement;
            }
        }

        return null;
    }
}
