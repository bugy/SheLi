package net.buggy.shoplist.data;

import android.support.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.MissingExternalIdException;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.Settings;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.CollectionUtils;
import net.buggy.shoplist.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryDao implements Dao {

    private Settings settings = new Settings();

    private final AtomicBoolean firstLaunch = new AtomicBoolean(true);
    private final AtomicBoolean showTips = new AtomicBoolean();

    private final InMemoryTable<ShopItem> shopItemsTable = new InMemoryTable<>();
    private final InMemoryTable<Category> categoriesTable = new InMemoryTable<>();
    private final InMemoryTable<Product> productsTable = new InMemoryTable<>();
    private final InMemoryTable<EntitySynchronizationRecord<? extends Entity>> syncRecordsTable = new InMemoryTable<>();

    private Multimap<Class<? extends Entity>, EntityListener<? extends Entity>> listeners = LinkedHashMultimap.create();

    @Override
    public boolean isFirstLaunch() {
        return firstLaunch.get();
    }

    @Override
    public boolean isShowTips() {
        return showTips.get();
    }

    @Override
    public void addShopItem(ShopItem shopItem) {
        shopItemsTable.add(shopItem);

        notifyEntityAdded(shopItem);
    }

    @Override
    public void addProduct(Product product) {
        productsTable.add(product);

        notifyEntityAdded(product);
    }

    @Override
    public List<ShopItem> getShopItems() {
        return new ArrayList<>(shopItemsTable.loadEntities());
    }

    @Override
    public List<Product> getProducts() {
        return new ArrayList<>(productsTable.loadEntities());
    }

    @Override
    public void saveProduct(Product product) {
        productsTable.save(product);

        notifyEntityChanged(product);
    }

    @Override
    public void removeShopItem(ShopItem shopItem) {
        shopItemsTable.remove(shopItem);

        notifyEntityRemoved(shopItem);
    }

    @Override
    public void removeProduct(Product product) {
        if (product != null) {
            final Set<ShopItem> existingItems = shopItemsTable.getRawEntities();
            for (ShopItem existingItem : existingItems) {
                if (Objects.equal(existingItem.getProduct(), product)) {
                    throw new IllegalStateException("Cannot delete product, shopItem is still linked" +
                            ". productId=" + product.getId() +
                            ", name=" + product.getName());
                }
            }
        }

        productsTable.remove(product);

        notifyEntityRemoved(product);
    }

    @Override
    public void saveShopItem(ShopItem shopItem) {
        shopItemsTable.save(shopItem);

        notifyEntityChanged(shopItem);
    }

    @Override
    public void addCategory(Category category) {
        categoriesTable.add(category);

        notifyEntityAdded(category);
    }

    @Override
    public List<Category> getCategories() {
        return new ArrayList<>(categoriesTable.loadEntities());
    }

    @Override
    public void removeCategory(Category category) {
        Set<Product> changedProducts = new LinkedHashSet<>();
        
        if (category != null) {
            final Set<Product> existingProducts = productsTable.getRawEntities();
            for (Product existingProduct : existingProducts) {
                final Set<Category> linkedCategories = new LinkedHashSet<>(existingProduct.getCategories());

                final boolean removed = linkedCategories.remove(category);
                if (removed) {
                    existingProduct.setCategories(linkedCategories);
                    changedProducts.add(existingProduct);
                }
            }
        }

        categoriesTable.remove(category);

        notifyEntityRemoved(category);

        for (Product changedProduct : changedProducts) {
            notifyEntityChanged(changedProduct);
        }
    }

    @Override
    public void saveCategory(Category category) {
        categoriesTable.save(category);

        notifyEntityChanged(category);
    }

    @Override
    public Settings getSettings() {
        return cloneFromStorage(settings);
    }

    @Override
    public void saveSettings(Settings settings) {
        this.settings = cloneToStorage(settings);
    }

    @Override
    public Product findProduct(Long id) {
        return productsTable.find(id);
    }

    @Override
    public Category findCategory(Long id) {
        return categoriesTable.find(id);
    }

    @Override
    public ShopItem findShopItem(Long id) {
        return shopItemsTable.find(id);
    }

    @Override
    public void clearFirstLaunch() {
        firstLaunch.set(false);
    }

    @Override
    public void setShowTips(boolean showTips) {
        this.showTips.set(showTips);
    }

    @Nullable
    @Override
    public Product findProductByExternalId(String externalId, String listId) {
        return findEntityByExternalId(externalId, listId, this.productsTable);
    }

    @Nullable
    private <T extends Entity> T findEntityByExternalId(String externalId, String listId, InMemoryTable<T> table) {
        EntitySynchronizationRecord foundRecord = null;

        final Set<EntitySynchronizationRecord<? extends Entity>> records = syncRecordsTable.getRawEntities();
        for (EntitySynchronizationRecord<? extends Entity> record : records) {
            if (record.getExternalId().equals(externalId) && record.getListId().equals(listId)) {
                foundRecord = record;
                break;
            }
        }

        if (foundRecord == null) {
            return null;
        }

        return table.find(foundRecord.getInternalId());
    }

    @Override
    public <E extends Entity> EntitySynchronizationRecord<E> findSynchronizationRecord(
            Long entityId, String listId, Class<E> clazz) {

        final Set<EntitySynchronizationRecord<? extends Entity>> records = syncRecordsTable.loadEntities();

        for (EntitySynchronizationRecord<? extends Entity> record : records) {
            if (record.getEntityClass().equals(clazz)
                    && record.getInternalId().equals(entityId)
                    && record.getListId().equals(listId)) {
                return (EntitySynchronizationRecord<E>) record;
            }
        }

        return null;
    }

    @Override
    public <E extends Entity> EntitySynchronizationRecord<E> findSynchronizationByExternalId(
            String externalId, String listId, Class<E> clazz) {

        final Set<EntitySynchronizationRecord<? extends Entity>> records = syncRecordsTable.loadEntities();

        for (EntitySynchronizationRecord<? extends Entity> record : records) {
            if (record.getEntityClass().equals(clazz)
                    && record.getExternalId().equals(externalId)
                    && record.getListId().equals(listId)) {
                return (EntitySynchronizationRecord<E>) record;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Product findProductByName(String name) {
        final String searchedName = ModelHelper.normalizeName(name);

        final Set<Product> products = productsTable.loadEntities();
        for (Product product : products) {
            final String productName = ModelHelper.normalizeName(product.getName());
            if (StringUtils.equalIgnoreCase(productName, searchedName)) {
                return product;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Category findCategoryByExternalId(String externalId, String listId) {
        return findEntityByExternalId(externalId, listId, categoriesTable);
    }

    @Nullable
    @Override
    public ShopItem findShopItemByExternalId(String externalId, String listId) {
        return findEntityByExternalId(externalId, listId, shopItemsTable);
    }

    @Nullable
    @Override
    public ShopItem findShopItemByProductName(String productName) {
        final String searchedName = ModelHelper.normalizeName(productName);

        final Set<ShopItem> shopItems = shopItemsTable.loadEntities();
        for (ShopItem shopItem : shopItems) {
            final String currentName = ModelHelper.normalizeName(shopItem.getProduct().getName());
            if (StringUtils.equalIgnoreCase(currentName, searchedName)) {
                return shopItem;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Category findCategoryByName(String name) {
        final String searchedName = ModelHelper.normalizeName(name);

        final Set<Category> categories = categoriesTable.loadEntities();
        for (Category category : categories) {
            final String currentName = ModelHelper.normalizeName(category.getName());
            if (StringUtils.equalIgnoreCase(currentName, searchedName)) {
                return category;
            }
        }

        return null;
    }

    @Override
    public List<ShopItem> findLinkedItems(Product product) {
        List<ShopItem> result = new ArrayList<>();

        final Set<ShopItem> shopItems = shopItemsTable.loadEntities();
        for (ShopItem shopItem : shopItems) {
            if (Objects.equal(shopItem.getProduct(), product)) {
                result.add(shopItem);
            }
        }

        return result;
    }

    @Override
    public <T extends Entity> void addEntityListener(Class<T> clazz, EntityListener<T> listener) {
        listeners.put(clazz, listener);
    }

    @Override
    public <T extends Entity> void removeEntityListener(Class<T> entityClass, EntityListener<T> listener) {
        listeners.remove(entityClass, listener);
    }

    @Override
    public <T extends Entity> void addSynchronizationRecord(EntitySynchronizationRecord<T> record) {
        syncRecordsTable.add(record);

        notifyEntityAdded(record);
    }

    @Override
    public <T extends Entity> void removeSynchronizationRecord(EntitySynchronizationRecord<T> record) {
        syncRecordsTable.remove(record);

        notifyEntityRemoved(record);
    }

    @Override
    public <T extends Entity> void updateSynchronizationRecords(
            Long internalId, Class<T> entityClass, Date changeDate, boolean deleted) {
        final List<EntitySynchronizationRecord<T>> changedRecords = new ArrayList<>();

        final Set<EntitySynchronizationRecord<? extends Entity>> records = syncRecordsTable.getRawEntities();
        for (EntitySynchronizationRecord<? extends Entity> record : records) {
            if (record.getEntityClass().equals(entityClass) && record.getInternalId().equals(internalId)) {
                record.setLastChangeDate(changeDate);
                record.setDeleted(deleted);

                changedRecords.add(clone((EntitySynchronizationRecord<T>) record));
            }
        }

        for (EntitySynchronizationRecord<T> changedRecord : changedRecords) {
            notifyEntityChanged(changedRecord);
        }
    }

    @Override
    public <T extends Entity> List<EntitySynchronizationRecord<T>> loadSynchronizationRecords(
            @Nullable Class<T> entityClass, String listId) {
        List<EntitySynchronizationRecord<T>> result = new ArrayList<>();

        final Set<EntitySynchronizationRecord<? extends Entity>> records = syncRecordsTable.loadEntities();
        for (EntitySynchronizationRecord<? extends Entity> record : records) {
            if (((entityClass == null) || (record.getEntityClass().equals(entityClass)))
                    && record.getListId().equals(listId)) {
                result.add((EntitySynchronizationRecord<T>) record);
            }
        }

        return result;
    }

    @Override
    public <T extends Entity> Map<String, T> mapExternalIds(Set<T> entities, String listId) throws MissingExternalIdException {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyMap();
        }

        final Map<String, T> result = new LinkedHashMap<>();
        for (T entity : entities) {
            final EntitySynchronizationRecord<? extends Entity> record =
                    findSynchronizationRecord(entity.getId(), listId, entity.getClass());

            if (record == null) {
                throw new IllegalStateException("Entity doesn't have sync record: " + entity);
            }

            result.put(record.getExternalId(), entity);
        }

        return result;
    }

    private <T extends Entity> void notifyEntityAdded(T entity) {
        final Collection<EntityListener<? extends Entity>> listeners = this.listeners.get(entity.getClass());

        for (EntityListener<? extends Entity> listener : listeners) {
            ((EntityListener<T>) listener).entityAdded(entity);
        }
    }

    private <T extends Entity> void notifyEntityChanged(T entity) {
        final Collection<EntityListener<? extends Entity>> listeners = this.listeners.get(entity.getClass());

        for (EntityListener<? extends Entity> listener : listeners) {
            ((EntityListener<T>) listener).entityChanged(entity);
        }
    }

    private <T extends Entity> void notifyEntityRemoved(T entity) {
        final Collection<EntityListener<? extends Entity>> listeners = this.listeners.get(entity.getClass());

        for (EntityListener<? extends Entity> listener : listeners) {
            ((EntityListener<T>) listener).entityRemoved(entity);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> T cloneToStorage(T entity) {
        if (entity instanceof Category) {
            final Category originalCategory = (Category) entity;
            return (T) clone(originalCategory);

        } else if (entity instanceof Product) {
            return (T) clone((Product) entity, categoriesTable.getRawEntities());

        } else if (entity instanceof ShopItem) {
            return (T) clone((ShopItem) entity, productsTable.getRawEntities());

        } else if (entity instanceof EntitySynchronizationRecord) {
            return (T) clone((EntitySynchronizationRecord) entity);
        } else if (entity == null) {
            return null;
        }

        throw new IllegalStateException("Class is not supported: " + entity.getClass());
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> T cloneFromStorage(T entity) {
        if (entity instanceof Category) {
            final Category originalCategory = (Category) entity;
            return (T) clone(originalCategory);

        } else if (entity instanceof Product) {
            return (T) clone((Product) entity, null);

        } else if (entity instanceof ShopItem) {
            return (T) clone((ShopItem) entity, null);

        } else if (entity instanceof EntitySynchronizationRecord) {
            return (T) clone((EntitySynchronizationRecord) entity);
        } else if (entity == null) {
            return null;
        }

        throw new IllegalStateException("Class is not supported: " + entity.getClass());
    }


    private Category clone(Category originalCategory) {
        if (originalCategory == null) {
            return null;
        }

        final Category category = new Category();
        category.setColor(originalCategory.getColor());
        category.setName(originalCategory.getName());
        category.setId(originalCategory.getId());
        return category;
    }

    private Product clone(Product originalProduct, @Nullable Set<Category> clonedCategories) {
        if (originalProduct == null) {
            return null;
        }

        final Product product = new Product();
        product.setName(originalProduct.getName());
        product.setId(originalProduct.getId());
        product.setDefaultUnits(originalProduct.getDefaultUnits());
        product.setPeriodCount(originalProduct.getPeriodCount());
        product.setLastBuyDate(originalProduct.getLastBuyDate());
        product.setPeriodType(originalProduct.getPeriodType());

        List<Category> productCategories = new ArrayList<>();
        if (clonedCategories == null) {
            for (Category category : originalProduct.getCategories()) {
                productCategories.add(clone(category));
            }
        } else {
            final Map<Long, Category> categoryMap = ModelHelper.mapIds(clonedCategories);
            for (Category category : originalProduct.getCategories()) {
                final Category clonedCategory = categoryMap.get(category.getId());
                if (clonedCategory == null) {
                    throw new IllegalStateException("Cloned category cache doesn't contain category" +
                            ". Id=" + category.getId());
                }
                productCategories.add(clonedCategory);
            }
        }
        product.setCategories(productCategories);

        return product;
    }

    private ShopItem clone(ShopItem originalItem, @Nullable Set<Product> clonedProducts) {
        if (originalItem == null) {
            return null;
        }

        final ShopItem shopItem = new ShopItem();
        shopItem.setChecked(originalItem.isChecked());
        shopItem.setId(originalItem.getId());
        shopItem.setUnitOfMeasure(originalItem.getUnitOfMeasure());
        shopItem.setComment(originalItem.getComment());
        shopItem.setQuantity(originalItem.getQuantity());

        final Product product;
        final Product originalProduct = originalItem.getProduct();
        if (originalProduct == null) {
            product = null;
        } else if (clonedProducts == null) {
            product = clone(originalProduct, null);
        } else {
            final Map<Long, Product> productMap = ModelHelper.mapIds(clonedProducts);
            final Product clonedProduct = productMap.get(originalProduct.getId());
            if (clonedProduct == null) {
                throw new IllegalStateException("Cloned product cache doesn't contain product" +
                        ". Id=" + originalProduct.getId());
            }
            product = clonedProduct;
        }
        shopItem.setProduct(product);

        return shopItem;
    }

    private <T extends Entity> EntitySynchronizationRecord<T> clone(EntitySynchronizationRecord<T> originalRecord) {
        final EntitySynchronizationRecord<T> clonedRecord = new EntitySynchronizationRecord<>();
        clonedRecord.setId(originalRecord.getId());
        clonedRecord.setLastChangeDate(originalRecord.getLastChangeDate());
        clonedRecord.setExternalId(originalRecord.getExternalId());
        clonedRecord.setDeleted(originalRecord.isDeleted());
        clonedRecord.setListId(originalRecord.getListId());
        clonedRecord.setEntityClass(originalRecord.getEntityClass());
        clonedRecord.setInternalId(originalRecord.getInternalId());

        return clonedRecord;
    }


    private final class InMemoryTable<T extends Entity> {
        private final AtomicLong idCounter = new AtomicLong(1);
        private final ConcurrentHashMap<Long, T> rows = new ConcurrentHashMap<>();

        public long add(T entity) {
            if (entity.getId() != null) {
                throw new IllegalStateException("Adding entities with existing id is not allowed");
            }

            final long id = idCounter.getAndIncrement();
            entity.setId(id);

            final T clonedEntity = cloneToStorage(entity);
            rows.put(id, clonedEntity);

            return id;
        }

        public Set<T> getRawEntities() {
            return new LinkedHashSet<>(rows.values());
        }

        public Set<T> loadEntities() {
            final LinkedHashSet<T> result = new LinkedHashSet<>();

            final TreeMap<Long, T> sortedEntities = new TreeMap<>(rows);
            for (Long id : sortedEntities.keySet()) {
                final T entity = rows.get(id);

                if (entity != null) {
                    final T clonedEntity = cloneFromStorage(entity);
                    result.add(clonedEntity);
                }
            }

            return result;
        }

        public void save(T entity) {
            Preconditions.checkNotNull(entity, "Cannot store null entity");
            Preconditions.checkNotNull(entity.getId(), "Cannot store entity without id");

            final T clonedEntity = cloneToStorage(entity);
            final T removed = rows.put(clonedEntity.getId(), entity);

            if (removed == null) {
                throw new IllegalStateException("Saved entity, which is not stored in DB" +
                        ". id=" + entity.getId() +
                        ", class=" + entity.getClass() +
                        ", toString=" + entity);
            }
        }

        public void remove(T entity) {
            Preconditions.checkNotNull(entity, "Cannot delete null entity");
            Preconditions.checkNotNull(entity.getId(), "Cannot delete entity without id");

            rows.remove(entity.getId());
        }

        public T find(Long id) {
            final T result = rows.get(id);

            if (result == null) {
                return null;
            }

            return cloneFromStorage(result);
        }
    }
}
