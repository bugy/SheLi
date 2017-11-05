package net.buggy.shoplist.data;


import android.app.Activity;

import net.buggy.shoplist.model.Entity;

public abstract class UiThreadEntityListener<T extends Entity> implements EntityListener<T> {

    private final Activity activity;

    protected UiThreadEntityListener(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void entityAdded(final T newEntity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                entityAddedUi(newEntity);
            }
        });
    }

    @Override
    public void entityChanged(final T changedEntity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                entityChangedUi(changedEntity);
            }
        });

    }

    @Override
    public void entityRemoved(final T removedEntity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                entityRemovedUi(removedEntity);
            }
        });
    }

    public abstract void entityAddedUi(T newEntity);

    public abstract void entityChangedUi(T changedEntity);

    public abstract void entityRemovedUi(T removedEntity);
}
