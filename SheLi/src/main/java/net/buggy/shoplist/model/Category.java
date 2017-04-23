package net.buggy.shoplist.model;

import java.io.Serializable;


public class Category extends Entity implements Serializable {

    private String name;
    private Integer color;

    public Category() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return name;
    }
}
