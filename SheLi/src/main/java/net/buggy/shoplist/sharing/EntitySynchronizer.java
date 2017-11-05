package net.buggy.shoplist.sharing;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.Date;
import java.util.List;

public abstract class EntitySynchronizer<T> {

    public abstract Class<T> getEntityClass();

    public abstract String getFirebaseListName();

    public abstract List<T> loadEntities();

    public abstract String getNaturalId(T clientEntity);

    public abstract String getNaturalId(DataSnapshot serverEntity);

    public abstract void removeFromClient(T entity);

    public abstract void createServerEntity(T clientEntity, DatabaseReference parentNode);

    public abstract void updateServerEntity(T clientEntity, DatabaseReference serverEntity);

    public abstract T findOnClient(Long internalId);

    public abstract T findOnClientByExternalId(String externalId, String listId);

    public abstract T findOnClientByNaturalId(DataSnapshot serverEntity);

    public abstract T createClientEntityInstance(DataSnapshot serverEntity);

    public abstract T saveNewClientEntity(T clientEntity);

    public abstract void updateClientEntity(T clientEntity, DataSnapshot serverEntity);

    public static Date getLastChangeDate(DataSnapshot serverEntity) {
        final DataSnapshot dateNode = serverEntity.child("lastChangeDate");
        return FirebaseHelper.getDateValue(dateNode);
    }

    protected static String getListIdFromEntity(DatabaseReference entityNode) {
        final DatabaseReference entitiesList = entityNode.getParent();
        return getListIdFromEntitiesList(entitiesList);
    }

    protected static String getListIdFromEntitiesList(DatabaseReference entitiesListNode) {
        final DatabaseReference userList = entitiesListNode.getParent();
        return userList.getKey();
    }

}
