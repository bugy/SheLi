package net.buggy.shoplist.utils;


import com.google.common.base.Objects;
import com.google.common.collect.Iterables;

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

    public static <T> boolean isEmpty(Iterable<T> iterable) {
        if (iterable == null) {
            return true;
        }

        return Iterables.isEmpty(iterable);
    }
}
