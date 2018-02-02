package net.buggy.shoplist.data;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Delete;
import com.activeandroid.query.From;
import com.activeandroid.query.Select;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.Language;
import net.buggy.shoplist.model.MissingExternalIdException;
import net.buggy.shoplist.model.PeriodType;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.Settings;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;
import net.buggy.shoplist.utils.CollectionUtils;
import net.buggy.shoplist.utils.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("HardCodedStringLiteral")
public class SqlliteDao implements Serializable, Dao {

    private StoredMetadata metadata;

    private Multimap<Class<? extends Entity>, EntityListener<? extends Entity>> listeners = LinkedHashMultimap.create();

    public SqlliteDao() {
        initMetadata();

        cleanDb();
    }

    private void initMetadata() {
        metadata = getMetadataInstance();
        if (metadata.getId() == null) {
            metadata.save();
        }
    }

    private StoredMetadata getMetadataInstance() {
        final List<StoredMetadata> metadataList = new Select().from(StoredMetadata.class).execute();
        if (metadataList.isEmpty()) {
            final StoredMetadata newMetadata = new StoredMetadata();
            newMetadata.firstLaunch = true;
            newMetadata.showTips = true;
            return newMetadata;
        }

        return metadataList.get(0);
    }

    private void cleanDb() {
        final List<StoredShopItem> items = new Select().from(StoredShopItem.class).execute();
        for (StoredShopItem item : items) {
            if (item.product == null) {
                StoredShopItem.delete(StoredShopItem.class, item.getId());
            }
        }
    }

    @Override
    public boolean isFirstLaunch() {
        return metadata.firstLaunch;
    }

    @Override
    public boolean isShowTips() {
        return metadata.showTips;
    }

    private Map<Long, ShopItem> loadShopItems() {
        final Map<Long, Product> products = loadProducts();

        final Map<Long, ShopItem> result = new LinkedHashMap<>();

        final List<StoredShopItem> storedItems = new Select().from(StoredShopItem.class).execute();
        for (StoredShopItem storedItem : storedItems) {
            final Product product = products.get(storedItem.product.getId());

            final ShopItem shopItem = storedItem.toModel(product);

            result.put(shopItem.getId(), shopItem);
        }

        return result;
    }

    private Map<Long, Product> loadProducts() {
        final Map<Long, Category> categoryMap = loadCategories();

        final Map<Long, Product> result = new LinkedHashMap<>();

        final List<StoredProduct> storedProducts = new Select().from(StoredProduct.class).execute();
        for (StoredProduct storedProduct : storedProducts) {
            final Product product = storedProduct.toProduct(categoryMap);

            result.put(product.getId(), product);
        }

        return result;
    }

    private Map<Long, Category> loadCategories() {
        Map<Long, Category> result = new ConcurrentHashMap<>();

        final List<StoredCategory> storedCategories = new Select().from(StoredCategory.class).execute();
        for (StoredCategory storedCategory : storedCategories) {
            final Category category = storedCategory.toModel();

            result.put(category.getId(), category);
        }

        return result;
    }

    @Override
    public void addShopItem(ShopItem shopItem) {
        final StoredShopItem storedShopItem = StoredShopItem.create(shopItem);
        final Long id = storedShopItem.save();
        shopItem.setId(id);

        notifyEntityAdded(shopItem);
    }

    @Override
    public void addProduct(Product product) {
        final StoredProduct existingProduct = findEntityByName(
                product.getName(), StoredProduct.class);
        if (existingProduct != null) {
            throw new IllegalStateException(
                    "Product with the name " + product.getName() + " already exists");
        }

        final StoredProduct storedProduct = StoredProduct.create(product);
        final Long id = storedProduct.customSave();
        product.setId(id);

        notifyEntityAdded(product);
    }

    @Override
    public List<ShopItem> getShopItems() {
        final Map<Long, ShopItem> shopItems = loadShopItems();
        final ArrayList<ShopItem> result = new ArrayList<>(shopItems.values());
        Collections.sort(result, new IdComparator<ShopItem>());

        return result;
    }

    @Override
    public List<Product> getProducts() {
        final Map<Long, Product> products = loadProducts();
        return ImmutableList.copyOf(products.values());
    }

    public static List<Class<? extends Model>> getModelClasses() {
        return Arrays.asList(
                StoredProduct.class,
                StoredShopItem.class,
                StoredCategory.class,
                StoredProductCategoryLink.class,
                StoredSettings.class,
                StoredMetadata.class,
                StoredSynchronizationRecord.class);
    }

    @Override
    public void saveProduct(Product product) {
        final StoredProduct storedProduct = StoredProduct.load(StoredProduct.class, product.getId());
        if (storedProduct == null) {
            throw new IllegalStateException("Trying to save unexisting product");
        }

        storedProduct.fillFrom(product);
        storedProduct.customSave();

        notifyEntityChanged(product);
    }

    @Override
    public void removeShopItem(ShopItem shopItem) {
        StoredShopItem.delete(StoredShopItem.class, shopItem.getId());

        notifyEntityRemoved(shopItem);
    }

    @Override
    public void removeProduct(Product product) {
        new Delete()
                .from(StoredProductCategoryLink.class)
                .where("product = ? ", product.getId())
                .execute();

        StoredProduct.delete(StoredProduct.class, product.getId());

        notifyEntityRemoved(product);
    }

    @Override
    public void saveShopItem(ShopItem shopItem) {
        final StoredShopItem storedShopItem = StoredShopItem.load(StoredShopItem.class, shopItem.getId());

        if (storedShopItem == null) {
            throw new IllegalStateException("Trying to save unexisting shopItem");
        }

        storedShopItem.fillFrom(shopItem);
        storedShopItem.save();

        notifyEntityChanged(shopItem);
    }

    @Override
    public void addCategory(Category category) {
        final StoredCategory existingCategory = findEntityByName(
                category.getName(), StoredCategory.class);
        if (existingCategory != null) {
            throw new IllegalStateException("Category with name " + category.getName() + " already exists");
        }

        final StoredCategory storedCategory = StoredCategory.create(category);
        final Long id = storedCategory.save();

        category.setId(id);

        notifyEntityAdded(category);
    }

    @Override
    public List<Category> getCategories() {
        final Map<Long, Category> categories = loadCategories();
        return ImmutableList.copyOf(categories.values());
    }

    @Override
    public void removeCategory(Category category) {
        final List<StoredProductCategoryLink> removedLinks = new Select()
                .from(StoredProductCategoryLink.class)
                .where("category = ? ", category.getId())
                .execute();

        new Delete()
                .from(StoredProductCategoryLink.class)
                .where("category = ? ", category.getId())
                .execute();

        StoredCategory.delete(StoredCategory.class, category.getId());

        notifyEntityRemoved(category);

        if (!CollectionUtils.isEmpty(removedLinks)) {
            final Map<Long, Category> categoryMap = loadCategories();
            for (StoredProductCategoryLink removedLink : removedLinks) {
                final Product unlinkedProduct = removedLink.getProduct().toProduct(categoryMap);
                notifyEntityChanged(unlinkedProduct);
            }
        }
    }

    @Override
    public void saveCategory(Category category) {
        final StoredCategory storedCategory = StoredCategory.load(StoredCategory.class, category.getId());

        if (storedCategory == null) {
            throw new IllegalStateException("Trying to save unexisting category");
        }

        storedCategory.fillFrom(category);
        storedCategory.save();

        notifyEntityChanged(category);
    }

    @Override
    public Settings getSettings() {

        StoredSettings storedSettings = loadSettingsInstance();
        if (storedSettings == null) {
            storedSettings = new StoredSettings();
            storedSettings.fillFrom(new Settings());
            storedSettings.save();
        }

        final Settings settings = new Settings();
        settings.setId(storedSettings.getId());
        settings.setLanguage(storedSettings.language);

        return settings;
    }

    private StoredSettings loadSettingsInstance() {
        List<StoredSettings> storedSettingsList = new Select().from(StoredSettings.class).execute();

        if (storedSettingsList.size() == 1) {
            return storedSettingsList.get(0);

        } else if (storedSettingsList.isEmpty()) {
            return null;

        } else {
            final int lastIndex = storedSettingsList.size() - 1;
            List<StoredSettings> invalidSettings = storedSettingsList.subList(0, lastIndex);
            Log.w("SqlliteDao", "getSettings: more than 1 settings instance found. " +
                    "Deleting " + invalidSettings.size());
            for (StoredSettings invalidSetting : invalidSettings) {
                invalidSetting.delete();
            }

            return storedSettingsList.get(lastIndex);
        }
    }

    @Override
    public void saveSettings(Settings settings) {
        StoredSettings storedSettings = loadSettingsInstance();
        if (storedSettings == null) {
            storedSettings = new StoredSettings();
        }

        storedSettings.fillFrom(settings);
        storedSettings.save();

        if (settings.getId() == null) {
            settings.setId(storedSettings.getId());
        }

        notifyEntityChanged(settings);
    }

    @Override
    public Product findProduct(Long id) {
        final StoredProduct storedProduct = new Select()
                .from(StoredProduct.class)
                .where("Id = ?", id)
                .executeSingle();

        if (storedProduct == null) {
            return null;
        }

        return storedProduct.toProduct();
    }

    @Override
    public Category findCategory(Long id) {
        final StoredCategory storedCategory = new Select()
                .from(StoredCategory.class)
                .where("Id = ?", id)
                .executeSingle();

        if (storedCategory == null) {
            return null;
        }

        return storedCategory.toModel();
    }

    @Override
    public ShopItem findShopItem(Long id) {
        final StoredShopItem storedShopItem = new Select()
                .from(StoredShopItem.class)
                .where("Id = ?", id)
                .executeSingle();

        if (storedShopItem == null) {
            return null;
        }

        final Long productId = storedShopItem.product.getId();
        final Product product;
        if (productId != null) {
            product = findProduct(productId);
        } else {
            product = null;
        }

        return storedShopItem.toModel(product);
    }


    @Override
    public void clearFirstLaunch() {
        if (metadata.firstLaunch) {
            metadata.firstLaunch = false;

            final StoredMetadata metadata = getMetadataInstance();
            metadata.firstLaunch = false;
            metadata.save();
        }
    }

    @Override
    public void setShowTips(boolean showTips) {
        if (metadata.showTips != showTips) {
            metadata.showTips = showTips;
            metadata.save();
        }
    }

    @Nullable
    @Override
    public Product findProductByExternalId(String externalId, String listId) {
        final StoredSynchronizationRecord record = findSynchronizationRecord(externalId, listId, Product.class);

        if (record == null) {
            return null;
        }

        return findProduct(record.entityInternalId);
    }

    @Override
    public <E extends Entity> EntitySynchronizationRecord<E> findSynchronizationRecord(
            Long entityId, String listId, Class<E> clazz) {

        final StoredSynchronizationRecord storedRecord = new Select().from(StoredSynchronizationRecord.class)
                .where("EntityInternalId = ?", entityId)
                .and("EntityClass = ?", clazz.getName())
                .and("ListId = ?", listId)
                .executeSingle();

        if (storedRecord == null) {
            return null;
        }

        return storedRecord.toModel();
    }

    @Override
    public <E extends Entity> EntitySynchronizationRecord<E> findSynchronizationByExternalId(
            String externalId, String listId, Class<E> clazz) {
        final StoredSynchronizationRecord record = findSynchronizationRecord(externalId, listId, clazz);

        if (record == null) {
            return null;
        }

        return record.toModel();
    }

    private StoredSynchronizationRecord findSynchronizationRecord(
            String externalId, String listId, Class<? extends Entity> clazz) {

        return new Select()
                .from(StoredSynchronizationRecord.class)
                .where("EntityExternalId = ?", externalId)
                .and("ListId = ?", listId)
                .and("EntityClass = ?", clazz.getName())
                .executeSingle();
    }

    @Nullable
    @Override
    public Product findProductByName(String name) {
        final StoredProduct storedProduct = findEntityByName(name, StoredProduct.class);

        return storedProduct != null ? storedProduct.toProduct() : null; 
    }

    @Nullable
    @Override
    public Category findCategoryByExternalId(String externalId, String listId) {
        if (externalId == null) {
            return null;
        }

        final StoredSynchronizationRecord record = findSynchronizationRecord(externalId, listId, Category.class);

        if (record == null) {
            return null;
        }

        return findCategory(record.entityInternalId);
    }

    @Nullable
    @Override
    public ShopItem findShopItemByExternalId(String externalId, String listId) {
        if (externalId == null) {
            return null;
        }

        final StoredSynchronizationRecord record = findSynchronizationRecord(externalId, listId, ShopItem.class);
        if (record == null) {
            return null;
        }

        return findShopItem(record.entityInternalId);
    }

    @Nullable
    @Override
    public ShopItem findShopItemByProductName(String productName) {
        if (productName == null) {
            return null;
        }

        final Product product = findProductByName(productName);
        if (product == null) {
            return null;
        }

        final StoredShopItem storedShopItem = new Select()
                .from(StoredShopItem.class)
                .where("Product = ?", product.getId())
                .executeSingle();

        if (storedShopItem == null) {
            return null;
        }

        return storedShopItem.toModel(product);
    }

    @Nullable
    @Override
    public Category findCategoryByName(String name) {
        final StoredCategory storedCategory = findEntityByName(name, StoredCategory.class);

        return storedCategory != null ? storedCategory.toModel() : null;
    }

    @Nullable
    private <T extends Model & Named> T findEntityByName(String name, Class<T> clazz) {
        boolean canSearchIgnoreCase = canSqlSearchIgnoreCase(name);

        if (canSearchIgnoreCase) {
            return new Select()
                    .from(clazz)
                    .where("name = ? COLLATE NOCASE", name)
                    .executeSingle();
        }

        final List<T> existingCategories = new Select()
                .from(clazz)
                .execute();
        for (T entity : existingCategories) {
            if (StringUtils.equalIgnoreCase(entity.getName(), name)) {
                return entity;
            }
        }

        return null;
    }

    private boolean canSqlSearchIgnoreCase(String value) {
        return (value == null) || CharMatcher.ascii().matchesAllOf(value);
    }

    @Override
    public List<ShopItem> findLinkedItems(Product product) {
        final List<ShopItem> shopItems = getShopItems();
        final List<ShopItem> linkedItems = new ArrayList<>();
        for (ShopItem shopItem : shopItems) {
            if (Objects.equal(product, shopItem.getProduct())) {
                linkedItems.add(shopItem);
            }
        }
        return linkedItems;
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
        final StoredSynchronizationRecord storedRecord = StoredSynchronizationRecord.create(record);
        final Long id = storedRecord.save();

        record.setId(id);

        notifyEntityAdded(record);
    }

    @Override
    public <T extends Entity> void removeSynchronizationRecord(EntitySynchronizationRecord<T> record) {
        StoredSynchronizationRecord.delete(StoredSynchronizationRecord.class, record.getId());

        notifyEntityRemoved(record);
    }

    @Override
    public <T extends Entity> void updateSynchronizationRecords(
            Long internalId, Class<T> entityClass, Date changeDate, boolean deleted) {

        final List<StoredSynchronizationRecord> storedRecords = new Select()
                .from(StoredSynchronizationRecord.class)
                .where("EntityInternalId = ? AND EntityClass = ?", internalId, entityClass.getName())
                .execute();

        for (StoredSynchronizationRecord storedRecord : storedRecords) {
            storedRecord.deleted = deleted;
            storedRecord.lastChangeDate = changeDate;
            storedRecord.save();

            notifyEntityChanged(storedRecord.toModel());
        }
    }

    @Override
    public <T extends Entity> List<EntitySynchronizationRecord<T>> loadSynchronizationRecords(@Nullable Class<T> entityClass, String listId) {
        final From statement = new Select()
                .from(StoredSynchronizationRecord.class)
                .where("ListId = ?", listId);

        if (entityClass != null) {
            statement.and("EntityClass = ?", entityClass.getName());
        }

        final List<StoredSynchronizationRecord> records = statement.execute();

        List<EntitySynchronizationRecord<T>> result = new ArrayList<>(records.size());
        for (StoredSynchronizationRecord record : records) {
            result.add(record.<T>toModel());
        }

        return result;
    }

    @Override
    public <T extends Entity> Map<String, T> mapExternalIds(Set<T> entities, String listId) throws MissingExternalIdException {
        Map<String, T> result = new LinkedHashMap<>();

        for (T entity : entities) {
            final StoredSynchronizationRecord record = new Select()
                    .from(StoredSynchronizationRecord.class)
                    .where("EntityClass = ?", entity.getClass().getName())
                    .and("ListId = ?", listId)
                    .and("EntityInternalId = ?", entity.getId())
                    .executeSingle();
            if (record == null) {
                throw new MissingExternalIdException("Entity (" + entity + ") " +
                        "has no sync record (listId=" + listId + "");
            }

            result.put(record.entityExternalId, entity);
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

    @Table(name = "Categories")
    public static class StoredCategory extends Model implements Named {
        @Column(name = "Name")
        private String name;

        @Column(name = "Color")
        private Integer color;

        public StoredCategory() {
            super();
        }

        private static StoredCategory create(Category category) {
            final StoredCategory storedCategory = new StoredCategory();
            storedCategory.fillFrom(category);

            return storedCategory;
        }

        public void fillFrom(Category category) {
            this.name = category.getName();
            this.color = category.getColor();
        }

        public Category toModel() {
            final Category category = new Category();
            category.setId(getId());
            category.setName(name);
            category.setColor(color);
            return category;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @Table(name = "ProductCategoryLinks")
    public static class StoredProductCategoryLink extends Model {

        @Column(name = "product")
        private StoredProduct product;

        @Column(name = "category")
        private StoredCategory category;

        public StoredProductCategoryLink() {
        }

        public void setProduct(StoredProduct product) {
            this.product = product;
        }

        public void setCategory(StoredCategory category) {
            this.category = category;
        }

        public StoredProduct getProduct() {
            return product;
        }

        public StoredCategory getCategory() {
            return category;
        }
    }

    @Table(name = "Products")
    public static class StoredProduct extends Model implements Named {
        @Column(name = "Name")
        private String name;

        @Column(name = "UnitOfMeasure")
        private UnitOfMeasure unitOfMeasure;

        @Column(name = "PeriodCount")
        private Integer periodCount;

        @Column(name = "PeriodType")
        private PeriodType periodType;

        @Column(name = "LastBuyDate")
        private Date lastBuyDate;

        private final List<StoredProductCategoryLink> deletedLinks = new ArrayList<>();
        private final List<StoredProductCategoryLink> newLinks = new ArrayList<>();


        public StoredProduct() {
            super();
        }

        private static StoredProduct create(Product product) {
            final StoredProduct storedProduct = new StoredProduct();
            storedProduct.fillFrom(product);

            return storedProduct;
        }

        public void fillFrom(Product product) {
            this.name = product.getName();
            this.unitOfMeasure = product.getDefaultUnits();
            this.periodCount = product.getPeriodCount();
            this.periodType = product.getPeriodType();
            this.lastBuyDate = product.getLastBuyDate();

            Set<Long> categoryIds = new LinkedHashSet<>();
            for (Category category : product.getCategories()) {
                categoryIds.add(category.getId());
            }
            Set<Long> newIds = new LinkedHashSet<>(categoryIds);

            if (product.getId() != null) {
                final List<StoredProductCategoryLink> links = getMany(StoredProductCategoryLink.class, "product");
                for (StoredProductCategoryLink link : links) {
                    final Long categoryId = link.getCategory().getId();

                    if (!categoryIds.contains(categoryId)) {
                        deletedLinks.add(link);
                    } else {
                        newIds.remove(categoryId);
                    }
                }
            }

            final List<StoredCategory> storedCategories = findCategories(newIds);
            for (StoredCategory storedCategory : storedCategories) {
                final StoredProductCategoryLink link = new StoredProductCategoryLink();
                link.setProduct(this);
                link.setCategory(storedCategory);
                newLinks.add(link);
            }
        }

        public Product toProduct() {
            return this.toProduct(null);
        }

        public Product toProduct(@Nullable Map<Long, Category> categoryMap) {
            Product product = new Product();
            product.setName(this.name);
            product.setId(this.getId());
            product.setDefaultUnits(this.unitOfMeasure);
            product.setPeriodCount(this.periodCount);
            product.setPeriodType(this.periodType);
            product.setLastBuyDate(this.lastBuyDate);

            Set<Category> productCategories = new LinkedHashSet<>();
            for (StoredCategory storedCategory : this.getCategories()) {
                final Category category;
                if (categoryMap != null) {
                    category = categoryMap.get(storedCategory.getId());
                } else {
                    category = storedCategory.toModel();
                }

                productCategories.add(category);
            }

            product.setCategories(productCategories);

            return product;
        }

        public Long customSave() {
            for (StoredProductCategoryLink deletedLink : deletedLinks) {
                deletedLink.delete();
            }

            final Long id = save();

            for (StoredProductCategoryLink newLink : newLinks) {
                newLink.save();
            }

            return id;
        }

        public Set<StoredCategory> getCategories() {
            final List<StoredProductCategoryLink> links = getMany(StoredProductCategoryLink.class, "product");

            Set<StoredCategory> result = new LinkedHashSet<>();
            for (StoredProductCategoryLink link : links) {
                result.add(link.getCategory());
            }

            return result;
        }


        @Override
        public String getName() {
            return name;
        }
    }

    @NonNull
    private static List<StoredCategory> findCategories(Collection<Long> ids) {
        final String idsString = Joiner.on(",").join(ids);
        final List<StoredCategory> storedCategories = new Select()
                .from(StoredCategory.class)
                .where("Id IN (" + idsString + ")")
                .execute();
        if (storedCategories.size() != ids.size()) {
            throw new IllegalStateException("Not all categories were loaded");
        }
        return storedCategories;
    }

    private interface Named {
        String getName();
    }
    
    @Table(name = "ShopItems")
    public static class StoredShopItem extends Model {
        @Column(name = "Product")
        private StoredProduct product;

        @Column(name = "Quantity")
        private BigDecimal quantity;

        @Column(name = "Comment")
        private String comment;

        @Column(name = "UnitOfMeasure")
        private UnitOfMeasure unitOfMeasure;

        @Column(name = "Checked")
        private Boolean checked;

        public StoredShopItem() {
            super();
        }

        private static StoredShopItem create(ShopItem shopItem) {
            final StoredShopItem storedShopItem = new StoredShopItem();
            storedShopItem.fillFrom(shopItem);

            return storedShopItem;
        }

        public void fillFrom(ShopItem shopItem) {
            quantity = shopItem.getQuantity();
            comment = shopItem.getComment();
            unitOfMeasure = shopItem.getUnitOfMeasure();
            checked = shopItem.isChecked();

            final StoredProduct storedProduct = new Select()
                    .from(StoredProduct.class)
                    .where("Id = ?", shopItem.getProduct().getId())
                    .executeSingle();
            product = storedProduct;
        }

        public ShopItem toModel(Product product) {
            final ShopItem shopItem = new ShopItem();
            shopItem.setId(getId());
            shopItem.setProduct(product);
            shopItem.setQuantity(quantity);
            shopItem.setComment(comment);
            shopItem.setUnitOfMeasure(unitOfMeasure);
            shopItem.setChecked((checked != null) ? checked : false);

            return shopItem;
        }
    }

    @Table(name = "Settings")
    public static class StoredSettings extends Model {

        @Column(name = "Language")
        private Language language;

        public void fillFrom(Settings settings) {
            language = settings.getLanguage();
        }
    }

    @Table(name = "Metadata")
    public static class StoredMetadata extends Model {

        @Column(name = "FirstLaunch")
        private Boolean firstLaunch;


        @Column(name = "ShowTips")
        private Boolean showTips;

    }

    @Table(name = "StoredSynchronizationRecords")
    public static class StoredSynchronizationRecord extends Model {

        @Column(name = "EntityExternalId", unique = true, notNull = true)
        private String entityExternalId;

        @Column(name = "EntityInternalId", notNull = true)
        private Long entityInternalId;

        @Column(name = "Deleted")
        private Boolean deleted;

        @Column(name = "LastChangeDate")
        private Date lastChangeDate;

        @Column(name = "EntityClass", notNull = true)
        private String entityClass;

        @Column(name = "ListId")
        private String listId;

        private static StoredSynchronizationRecord create(EntitySynchronizationRecord record) {
            final StoredSynchronizationRecord storedRecord = new StoredSynchronizationRecord();
            storedRecord.fillFrom(record);

            return storedRecord;
        }

        public void fillFrom(EntitySynchronizationRecord record) {
            entityClass = record.getEntityClass().getName();
            entityExternalId = record.getExternalId();
            entityInternalId = record.getInternalId();
            deleted = record.isDeleted();
            lastChangeDate = record.getLastChangeDate();
            listId = record.getListId();
        }

        private <T extends Entity> EntitySynchronizationRecord<T> toModel() {
            final EntitySynchronizationRecord<T> record = new EntitySynchronizationRecord<>();
            record.setId(getId());
            record.setExternalId(entityExternalId);
            record.setInternalId(entityInternalId);
            record.setDeleted(deleted);
            record.setLastChangeDate(lastChangeDate);
            record.setListId(listId);

            try {
                record.setEntityClass((Class<T>) Class.forName(entityClass));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            return record;
        }
    }

    private static class IdComparator<T extends Entity> implements Comparator<T> {
        @Override
        public int compare(T o1, T o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }
}
