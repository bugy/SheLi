package net.buggy.shoplist.sharing;

import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.ModelHelper;

import java.util.Date;

public abstract class DaoEntitySynchronizer<T extends Entity> extends EntitySynchronizer<T> {

    private final Dao dao;

    protected DaoEntitySynchronizer(Dao dao) {
        this.dao = dao;
    }

    protected EntitySynchronizationRecord<T> addSynchronizationRecord(
            Long internalId, String externalId, String listId, Date modificationDate) {

        final EntitySynchronizationRecord<T> synchronizationRecord =
                ModelHelper.createSyncRecord(internalId, externalId, listId, modificationDate, getEntityClass());
        dao.addSynchronizationRecord(synchronizationRecord);

        return synchronizationRecord;
    }

    protected void markRecordUpdated(Long internalId, String externalId, String listId, Date changeDate) {
        final EntitySynchronizationRecord<T> record = getDao().findSynchronizationRecord(
                internalId, listId, getEntityClass());

        if (record == null) {
            addSynchronizationRecord(internalId, externalId, listId, changeDate);
        }

        dao.updateSynchronizationRecords(internalId, getEntityClass(), changeDate, false);
    }

    protected Dao getDao() {
        return dao;
    }
}
