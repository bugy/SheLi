package net.buggy.shoplist.sharing;

import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;

import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.MissingExternalIdException;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.PeriodType;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.buggy.shoplist.sharing.FirebaseHelper.getDateValue;
import static net.buggy.shoplist.sharing.FirebaseHelper.getEnumValue;
import static net.buggy.shoplist.sharing.FirebaseHelper.serializeDate;
import static net.buggy.shoplist.sharing.FirebaseHelper.serializeEnum;

public class ProductSynchronizer extends DaoEntitySynchronizer<Product> {

    public ProductSynchronizer(Dao dao) {
        super(dao);
    }

    @Override
    public Class<Product> getEntityClass() {
        return Product.class;
    }

    @Override
    public String getFirebaseListName() {
        return "products";
    }

    @Override
    public List<Product> loadEntities() {
        return getDao().getProducts();
    }

    @Override
    public String getNaturalId(Product clientEntity) {
        if (clientEntity == null) {
            return null;
        }

        return ModelHelper.normalizeName(clientEntity.getName());
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
    public void removeFromClient(Product product) {
        final List<ShopItem> linkedItems = getDao().findLinkedItems(product);
        for (ShopItem linkedItem : linkedItems) {
            getDao().removeShopItem(linkedItem);
        }

        getDao().removeProduct(product);
    }

    @Override
    public void createServerEntity(Product clientProduct, DatabaseReference parentNode) {
        final String listId = getListIdFromEntitiesList(parentNode);

        final DatabaseReference serverProduct = parentNode.push();

        final EntitySynchronizationRecord<Product> synchronizationRecord =
                addSynchronizationRecord(clientProduct.getId(), serverProduct.getKey(), listId, new Date());

        updateServerEntity(clientProduct, serverProduct, synchronizationRecord);
    }

    @Override
    public void updateServerEntity(Product clientProduct, DatabaseReference serverEntity) {
        final String listId = getListIdFromEntity(serverEntity);

        final EntitySynchronizationRecord<Product> productRecord = getDao().findSynchronizationRecord(
                clientProduct.getId(), listId, Product.class);

        if (productRecord != null) {
            updateServerEntity(clientProduct, serverEntity, productRecord);
        } else {
            Log.w("ProductSynchronizer", "updateServerEntity: record not found" +
                    ". listId=" + listId +
                    ", externalId=" + serverEntity.getKey() +
                    ", product=" + clientProduct);
        } 
    }

    @Override
    public Product findOnClient(Long internalId) {
        return getDao().findProduct(internalId);
    }

    private void updateServerEntity(
            Product clientProduct, DatabaseReference serverEntity, EntitySynchronizationRecord<Product> productRecord) {
        Map<String, Object> valuesMap = new LinkedHashMap<>();
        valuesMap.put("name", clientProduct.getName());
        valuesMap.put("naturalId", ModelHelper.normalizeName(clientProduct.getName()));
        valuesMap.put("lastBuyDate", serializeDate(clientProduct.getLastBuyDate()));
        valuesMap.put("lastChangeDate", serializeDate(productRecord.getLastChangeDate()));
        valuesMap.put("periodType", serializeEnum(clientProduct.getPeriodType()));
        valuesMap.put("periodCount", clientProduct.getPeriodCount());
        valuesMap.put("defaultUnits", serializeEnum(clientProduct.getDefaultUnits()));

        if (!clientProduct.getCategories().isEmpty()) {
            final Map<String, Category> categoriesMap;

            try {
                categoriesMap = getDao().mapExternalIds(clientProduct.getCategories(), productRecord.getListId());
            } catch (MissingExternalIdException e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            valuesMap.put("categories", ImmutableList.copyOf(categoriesMap.keySet()));
        } else {
            valuesMap.put("categories", null);
        }

        serverEntity.updateChildren(valuesMap);
    }

    @Override
    public Product findOnClientByExternalId(String externalId, String listId) {
        return getDao().findProductByExternalId(externalId, listId);
    }

    @Override
    public Product findOnClientByNaturalId(DataSnapshot serverEntity) {
        final String name = getNormalizedName(serverEntity);
        if (name == null) {
            return null;
        }

        return getDao().findProductByName(name);
    }

    @Override
    public Product createClientEntityInstance(DataSnapshot serverEntity) {
        Product product = new Product();
        fillClientEntity(product, serverEntity);

        return product;
    }

    @Override
    public Product saveNewClientEntity(Product clientEntity) {
        getDao().addProduct(clientEntity);

        return getDao().findProduct(clientEntity.getId());
    }

    @Override
    public void updateClientEntity(Product clientProduct, DataSnapshot serverProduct) {
        fillClientEntity(clientProduct, serverProduct);

        getDao().saveProduct(clientProduct);
    }

    private void fillClientEntity(Product clientProduct, DataSnapshot serverProduct) {
        final String listId = getListIdFromEntity(serverProduct.getRef());

        clientProduct.setName(serverProduct.child("name").getValue(String.class));
        clientProduct.setLastBuyDate(getDateValue(serverProduct.child("lastBuyDate")));
        clientProduct.setPeriodCount(serverProduct.child("periodCount").getValue(Integer.class));
        clientProduct.setPeriodType(
                getEnumValue(serverProduct.child("periodType"), PeriodType.class));
        clientProduct.setDefaultUnits(
                getEnumValue(serverProduct.child("defaultUnits"), UnitOfMeasure.class));

        GenericTypeIndicator<List<String>> typeIndicator = new GenericTypeIndicator<List<String>>() {
        };
        final List<String> categoryExternalIds = serverProduct.child("categories").getValue(typeIndicator);
        final Set<Category> categories = Sets.newLinkedHashSet();
        if (categoryExternalIds != null) {
            for (String categoryExternalId : categoryExternalIds) {
                final Category category = getDao().findCategoryByExternalId(categoryExternalId, listId);
                if (category == null) {
                    Log.w("ProductSynchronizer", "updateClientEntity: client category not found" +
                            ". productName=" + clientProduct.getName() +
                            ", categoryId=" + categoryExternalId);
                } else {
                    categories.add(category);
                }
            }
        }

        clientProduct.setCategories(categories);
    }
}
