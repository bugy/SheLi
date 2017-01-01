package net.buggy.shoplist.model;


import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class Product extends Entity implements Serializable {

    private String name;
    private String note;
    private final Set<Category> categories = new LinkedHashSet<>();

    public Product() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Collection<Category> categories) {
        this.categories.clear();

        if (categories != null) {
            this.categories.addAll(categories);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
