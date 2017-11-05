package net.buggy.shoplist.sharing;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.buggy.shoplist.sharing.FirebaseHelper.getBigDecimalValue;
import static net.buggy.shoplist.sharing.FirebaseHelper.getEnumValue;
import static net.buggy.shoplist.sharing.FirebaseHelper.serializeBigDecimal;
import static net.buggy.shoplist.sharing.FirebaseHelper.serializeDate;
import static net.buggy.shoplist.sharing.FirebaseHelper.serializeEnum;


public class ShopItemSynchronizer extends DaoEntitySynchronizer<ShopItem> {

    public ShopItemSynchronizer(Dao dao) {
        super(dao);
    }

    @Override
    public Class<ShopItem> getEntityClass() {
        return ShopItem.class;
    }

    @Override
    public String getFirebaseListName() {
        return "shopItems";
    }

    @Override
    public List<ShopItem> loadEntities() {
        return getDao().getShopItems();
    }

    @Override
    public String getNaturalId(ShopItem clientEntity) {
        if (clientEntity.getProduct() != null) {
            return ModelHelper.normalizeName(clientEntity.getProduct().getName());
        }

        return null;
    }

    @Override
    public String getNaturalId(DataSnapshot serverEntity) {
        return getProductName(serverEntity);
    }

    private String getProductName(DataSnapshot serverEntity) {
        final String productName = serverEntity.child("naturalId").getValue(String.class);

        return ModelHelper.normalizeName(productName);
    }

    @Override
    public void removeFromClient(ShopItem entity) {
        getDao().removeShopItem(entity);
    }

    @Override
    public void createServerEntity(ShopItem clientEntity, DatabaseReference parentNode) {
        final String listId = getListIdFromEntitiesList(parentNode);

        final DatabaseReference serverItem = parentNode.push();

        final EntitySynchronizationRecord<ShopItem> synchronizationRecord =
                addSynchronizationRecord(clientEntity.getId(), serverItem.getKey(), listId, new Date());

        updateServerEntity(clientEntity, serverItem, synchronizationRecord);
    }

    @Override
    public void updateServerEntity(ShopItem clientEntity, DatabaseReference serverEntity) {
        final String listId = getListIdFromEntity(serverEntity);

        final EntitySynchronizationRecord<ShopItem> itemRecord =
                getDao().findSynchronizationRecord(clientEntity.getId(), listId, ShopItem.class);

        if (itemRecord != null) {
            updateServerEntity(clientEntity, serverEntity, itemRecord);
        } else {
            Log.w("ShopItemSynchronizer", "updateServerEntity: sync record not found" +
                    ". listId=" + listId +
                    ", externalId=" + serverEntity.getKey() +
                    ", shopItem=" + clientEntity);
        }
    }

    @Override
    public ShopItem findOnClient(Long internalId) {
        return getDao().findShopItem(internalId);
    }

    private void updateServerEntity(
            ShopItem clientEntity, DatabaseReference serverEntity, EntitySynchronizationRecord<ShopItem> itemRecord) {

        Map<String, Object> valuesMap = new LinkedHashMap<>();
        valuesMap.put("lastChangeDate", serializeDate(itemRecord.getLastChangeDate()));
        valuesMap.put("naturalId", ModelHelper.normalizeName(clientEntity.getProduct().getName()));
        valuesMap.put("comment", clientEntity.getComment());
        valuesMap.put("quantity", serializeBigDecimal(clientEntity.getQuantity()));
        valuesMap.put("unitOfMeasure", serializeEnum(clientEntity.getUnitOfMeasure()));
        valuesMap.put("checked", clientEntity.isChecked());

        final EntitySynchronizationRecord<Product> productRecord = getDao().findSynchronizationRecord(
                clientEntity.getProduct().getId(), itemRecord.getListId(), Product.class);
        if (productRecord != null) {
            valuesMap.put("product", productRecord.getExternalId());
        } else {
            Log.w("ShopItemSynchronizer", "updateServerEntity: product has no externalId, won't save" +
                    ". productName=" + clientEntity.getProduct().getName());
            return;
        }

        serverEntity.updateChildren(valuesMap);
    }

    @Override
    public ShopItem findOnClientByExternalId(String externalId, String listId) {
        return getDao().findShopItemByExternalId(externalId, listId);
    }

    @Override
    public ShopItem findOnClientByNaturalId(DataSnapshot serverEntity) {
        final String productName = getProductName(serverEntity);

        if (productName == null) {
            return null;
        }

        return getDao().findShopItemByProductName(ModelHelper.normalizeName(productName));
    }

    @Override
    public ShopItem createClientEntityInstance(DataSnapshot serverEntity) {
        final ShopItem shopItem = new ShopItem();
        fillClientEntity(shopItem, serverEntity);

        return shopItem;
    }

    @Override
    public ShopItem saveNewClientEntity(ShopItem clientEntity) {
        getDao().addShopItem(clientEntity);

        return getDao().findShopItem(clientEntity.getId());
    }

    @Override
    public void updateClientEntity(ShopItem clientEntity, DataSnapshot serverEntity) {
        fillClientEntity(clientEntity, serverEntity);

        getDao().saveShopItem(clientEntity);
    }

    private String fillClientEntity(ShopItem clientEntity, DataSnapshot serverEntity) {
        final String listId = getListIdFromEntity(serverEntity.getRef());

        clientEntity.setComment(serverEntity.child("comment").getValue(String.class));
        clientEntity.setQuantity(getBigDecimalValue(serverEntity.child("quantity")));
        clientEntity.setUnitOfMeasure(
                getEnumValue(serverEntity.child("unitOfMeasure"), UnitOfMeasure.class));

        final Boolean checked = serverEntity.child("checked").getValue(Boolean.class);
        if (checked != null) {
            clientEntity.setChecked(checked);
        }

        final String productExternalId = serverEntity.child("product").getValue(String.class);
        Product product = null;
        if (productExternalId != null) {
            product = getDao().findProductByExternalId(productExternalId, listId);

            if (product == null) {
                throw new IllegalStateException("Couldn't find product by external id " + productExternalId);
            }
        }
        clientEntity.setProduct(product);
        return listId;
    }
}
