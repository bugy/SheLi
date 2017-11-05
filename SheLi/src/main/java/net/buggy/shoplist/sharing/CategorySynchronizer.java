package net.buggy.shoplist.sharing;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.ModelHelper;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.buggy.shoplist.sharing.FirebaseHelper.serializeDate;


public class CategorySynchronizer extends DaoEntitySynchronizer<Category> {

    public CategorySynchronizer(Dao dao) {
        super(dao);
    }

    @Override
    public Class<Category> getEntityClass() {
        return Category.class;
    }

    @Override
    public String getFirebaseListName() {
        return "categories";
    }

    @Override
    public List<Category> loadEntities() {
        return getDao().getCategories();
    }

    @Override
    public String getNaturalId(Category clientEntity) {
        if (clientEntity == null) {
            return null;
        }

        final String name = clientEntity.getName();
        return ModelHelper.normalizeName(name);
    }

    @Override
    public String getNaturalId(DataSnapshot serverEntity) {
        return getNormalizedName(serverEntity);
    }

    private String getNormalizedName(DataSnapshot serverEntity) {
        final String name = serverEntity.child("name").getValue(String.class);

        return ModelHelper.normalizeName(name);
    }

    @Override
    public void removeFromClient(Category entity) {
        getDao().removeCategory(entity);
    }

    @Override
    public void createServerEntity(Category clientCategory, DatabaseReference parentNode) {
        final DatabaseReference serverCategory = parentNode.push();

        final String listId = getListIdFromEntity(serverCategory);
        final EntitySynchronizationRecord<Category> synchronizationRecord = addSynchronizationRecord(
                clientCategory.getId(), serverCategory.getKey(), listId, new Date());

        updateServerEntity(clientCategory, serverCategory, synchronizationRecord);
    }

    @Override
    public void updateServerEntity(Category clientEntity, DatabaseReference serverEntity) {
        final String listId = getListIdFromEntity(serverEntity);
        final EntitySynchronizationRecord<Category> synchronizationRecord = getDao().findSynchronizationRecord(
                clientEntity.getId(), listId, Category.class);

        updateServerEntity(clientEntity, serverEntity, synchronizationRecord);
    }

    @Override
    public Category findOnClient(Long internalId) {
        return getDao().findCategory(internalId);
    }

    private void updateServerEntity(
            Category clientEntity,
            DatabaseReference serverEntity,
            EntitySynchronizationRecord<Category> synchronizationRecord) {

        Map<String, Object> valuesMap = new LinkedHashMap<>();
        valuesMap.put("name", clientEntity.getName());
        valuesMap.put("naturalId", ModelHelper.normalizeName(clientEntity.getName()));
        valuesMap.put("lastChangeDate", serializeDate(synchronizationRecord.getLastChangeDate()));
        valuesMap.put("color", clientEntity.getColor());

        serverEntity.updateChildren(valuesMap);
    }

    @Override
    public Category findOnClientByExternalId(String externalId, String listId) {
        return getDao().findCategoryByExternalId(externalId, listId);
    }

    @Override
    public Category findOnClientByNaturalId(DataSnapshot serverEntity) {
        final String name = getNormalizedName(serverEntity);
        if (name == null) {
            return null;
        }

        return getDao().findCategoryByName(name);
    }

    @Override
    public Category createClientEntityInstance(DataSnapshot serverEntity) {
        Category category = new Category();
        fillClientEntity(category, serverEntity);

        return category;
    }

    @Override
    public Category saveNewClientEntity(Category clientEntity) {
        getDao().addCategory(clientEntity);

        return getDao().findCategory(clientEntity.getId());
    }

    @Override
    public void updateClientEntity(Category category, DataSnapshot serverEntity) {
        fillClientEntity(category, serverEntity);

        getDao().saveCategory(category);
    }

    private void fillClientEntity(Category category, DataSnapshot serverEntity) {
        category.setName(serverEntity.child("name").getValue(String.class));
        category.setColor(serverEntity.child("color").getValue(Integer.class));
    }
}
