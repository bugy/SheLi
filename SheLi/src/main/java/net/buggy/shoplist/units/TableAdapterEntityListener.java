package net.buggy.shoplist.units;

import android.app.Activity;

import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.shoplist.data.UiThreadEntityListener;
import net.buggy.shoplist.model.Entity;


public class TableAdapterEntityListener<T extends Entity> extends UiThreadEntityListener<T> {
    private final FactoryBasedAdapter<T> adapter;

    public TableAdapterEntityListener(FactoryBasedAdapter<T> adapter, Activity activity) {
        super(activity);

        this.adapter = adapter;
    }

    @Override
    public void entityAddedUi(T newEntity) {
        if (!adapter.getAllItems().contains(newEntity)) {
            adapter.add(newEntity);
        }
    }

    @Override
    public void entityChangedUi(T changedEntity) {
        adapter.update(changedEntity);
    }

    @Override
    public void entityRemovedUi(T removedEntity) {
        adapter.remove(removedEntity);
    }
}
