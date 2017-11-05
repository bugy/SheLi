package net.buggy.shoplist.data;

import net.buggy.shoplist.model.Entity;

public interface EntityListener<T extends Entity> {
    void entityAdded(T newEntity);

    void entityChanged(T changedEntity);

    void entityRemoved(T removedEntity);
}
