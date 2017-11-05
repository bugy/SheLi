package net.buggy.shoplist.units;


import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.EntityListener;
import net.buggy.shoplist.model.Entity;

public class UnitsHelper {
    public static <T extends Entity> void addTemporalDaoListener(
            final Dao dao,
            final Class<T> clazz,
            final EntityListener<T> listener,
            final Unit unit) {
        dao.addEntityListener(clazz, listener);
        unit.addStateListener(new Unit.StateListener() {
            @Override
            public void hidden() {
                dao.removeEntityListener(clazz, listener);
                unit.removeStateListener(this);
            }

            @Override
            public void stopped() {
                dao.removeEntityListener(clazz, listener);
                unit.removeStateListener(this);
            }
        });
    }

}
