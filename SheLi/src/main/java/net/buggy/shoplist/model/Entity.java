package net.buggy.shoplist.model;


import java.io.Serializable;

public class Entity implements Serializable {

    private Long id;
    private Integer hashCode = null;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Entity entity = (Entity) o;

        if ((id == null) || (entity.id == null)) {
            return false;
        }

        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        if (hashCode == null) {
            hashCode = (id != null) ? id.hashCode() : (int) System.currentTimeMillis();
        }

        return hashCode;
    }
}
