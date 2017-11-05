package net.buggy.shoplist.data;


import android.support.annotation.Nullable;

import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.MissingExternalIdException;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.Settings;
import net.buggy.shoplist.model.ShopItem;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Dao {
    boolean isFirstLaunch();

    boolean isShowTips();

    void addShopItem(ShopItem shopItem);

    void addProduct(Product product);

    List<ShopItem> getShopItems();

    List<Product> getProducts();

    void saveProduct(Product product);

    void removeShopItem(ShopItem shopItem);

    void removeProduct(Product product);

    void saveShopItem(ShopItem shopItem);

    void addCategory(Category category);

    List<Category> getCategories();

    void removeCategory(Category category);

    void saveCategory(Category category);

    Settings getSettings();

    void saveSettings(Settings settings);

    Product findProduct(Long id);

    Category findCategory(Long id);

    ShopItem findShopItem(Long id);

    void clearFirstLaunch();

    void setShowTips(boolean showTips);

    @Nullable
    Product findProductByExternalId(String externalId, String listId);

    <E extends Entity> EntitySynchronizationRecord<E> findSynchronizationRecord(
            Long entityId, String listId, Class<E> clazz);

    <E extends Entity> EntitySynchronizationRecord<E> findSynchronizationByExternalId(
            String externalId, String listId, Class<E> clazz);

    @Nullable
    Product findProductByName(String name);

    @Nullable
    Category findCategoryByExternalId(String externalId, String listId);

    @Nullable
    ShopItem findShopItemByExternalId(String externalId, String listId);

    @Nullable
    ShopItem findShopItemByProductName(String productName);

    @Nullable
    Category findCategoryByName(String name);

    List<ShopItem> findLinkedItems(Product product);

    <T extends Entity> void addEntityListener(Class<T> clazz, EntityListener<T> listener);

    <T extends Entity> void removeEntityListener(Class<T> entityClass, EntityListener<T> listener);

    <T extends Entity> void addSynchronizationRecord(EntitySynchronizationRecord<T> record);

    <T extends Entity> void removeSynchronizationRecord(EntitySynchronizationRecord<T> record);

    <T extends Entity> void updateSynchronizationRecords(Long internalId, Class<T> entityClass, Date changeDate, boolean deleted);

    <T extends Entity> List<EntitySynchronizationRecord<T>> loadSynchronizationRecords(
            @Nullable Class<T> entityClass, String listId);

    <T extends Entity> Map<String, T> mapExternalIds(Set<T> entities, String listId) throws MissingExternalIdException;
}

