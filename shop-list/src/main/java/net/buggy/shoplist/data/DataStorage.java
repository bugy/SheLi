package net.buggy.shoplist.data;


import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataStorage implements Serializable {

    public DataStorage() {
        cleanDb();
    }

    private void cleanDb() {
        final List<StoredShopItem> items = new Select().from(StoredShopItem.class).execute();
        for (StoredShopItem item : items) {
            if (item.product == null) {
                StoredShopItem.delete(StoredShopItem.class, item.getId());
            }
        }
    }

    private Map<Long, ShopItem> loadShopItems() {
        final Map<Long, Product> products = loadProducts();

        final Map<Long, ShopItem> result = new LinkedHashMap<>();

        final List<StoredShopItem> storedItems = new Select().from(StoredShopItem.class).execute();
        for (StoredShopItem storedItem : storedItems) {
            final Product product = products.get(storedItem.product.getId());

            final ShopItem shopItem = new ShopItem();
            shopItem.setId(storedItem.getId());
            shopItem.setProduct(product);
            shopItem.setQuantity(storedItem.quantity);
            shopItem.setComment(storedItem.comment);
            shopItem.setUnitOfMeasure(storedItem.unitOfMeasure);
            shopItem.setChecked((storedItem.checked != null) ? storedItem.checked : false);

            result.put(shopItem.getId(), shopItem);
        }

        return result;
    }

    private Map<Long, Product> loadProducts() {
        final Map<Long, Category> categoryMap = loadCategories();

        final Map<Long, Product> result = new LinkedHashMap<>();

        final List<StoredProduct> storedProducts = new Select().from(StoredProduct.class).execute();
        for (StoredProduct storedProduct : storedProducts) {
            final Product product = new Product();
            product.setName(storedProduct.name);
            product.setNote(storedProduct.note);
            product.setId(storedProduct.getId());
            product.setDefaultUnits(storedProduct.unitOfMeasure);

            Set<Category> productCategories = new LinkedHashSet<>();
            for (StoredCategory storedCategory : storedProduct.getCategories()) {
                final Category category = categoryMap.get(storedCategory.getId());
                productCategories.add(category);
            }
            product.setCategories(productCategories);

            result.put(product.getId(), product);
        }

        return result;
    }

    private Map<Long, Category> loadCategories() {
        Map<Long, Category> result = new ConcurrentHashMap<>();

        final List<StoredCategory> storedCategories = new Select().from(StoredCategory.class).execute();
        for (StoredCategory storedCategory : storedCategories) {
            final Category category = new Category();
            category.setName(storedCategory.name);
            category.setId(storedCategory.getId());
            category.setColor(storedCategory.color);

            result.put(category.getId(), category);
        }

        return result;
    }

    public void addShopItem(ShopItem shopItem) {
        final StoredShopItem storedShopItem = StoredShopItem.create(shopItem);
        final Long id = storedShopItem.save();
        shopItem.setId(id);
    }

    public void addProduct(Product product) {
        final List<Model> existingProducts = new Select()
                .from(StoredProduct.class)
                .where("name = ?  COLLATE NOCASE", product.getName())
                .execute();
        if (!existingProducts.isEmpty()) {
            throw new IllegalStateException(
                    "Product with the name " + product.getName() + " already exists");
        }


        final StoredProduct storedProduct = StoredProduct.create(product);
        final Long id = storedProduct.customSave();
        product.setId(id);
    }

    public List<ShopItem> getShopItems() {
        final Map<Long, ShopItem> shopItems = loadShopItems();
        final ArrayList<ShopItem> result = new ArrayList<>(shopItems.values());
        Collections.sort(result, new IdComparator<ShopItem>());

        return result;
    }

    public List<Product> getProducts() {
        final Map<Long, Product> products = loadProducts();
        return ImmutableList.copyOf(products.values());
    }

    public static List<Class<? extends Model>> getModelClasses() {
        return Arrays.asList(
                StoredProduct.class,
                StoredShopItem.class,
                StoredCategory.class,
                StoredProductCategoryLink.class);
    }

    public void saveProduct(Product product) {
        final StoredProduct storedProduct = StoredProduct.load(StoredProduct.class, product.getId());
        if (storedProduct == null) {
            throw new IllegalStateException("Trying to save unexisting product");
        }

        storedProduct.fillFrom(product);
        storedProduct.customSave();
    }

    public void removeShopItem(ShopItem shopItem) {
        StoredShopItem.delete(StoredShopItem.class, shopItem.getId());
    }

    public void removeProduct(Product product) {
        new Delete()
                .from(StoredProductCategoryLink.class)
                .where("product = ? ", product.getId())
                .execute();

        StoredProduct.delete(StoredProduct.class, product.getId());
    }

    public void saveShopItem(ShopItem shopItem) {
        final StoredShopItem storedShopItem = StoredShopItem.load(StoredShopItem.class, shopItem.getId());

        if (storedShopItem == null) {
            throw new IllegalStateException("Trying to save unexisting shopItem");
        }

        storedShopItem.fillFrom(shopItem);
        storedShopItem.save();
    }

    public void addCategory(Category category) {
        final List<Model> existingCategories = new Select()
                .from(StoredCategory.class)
                .where("name = ?  COLLATE NOCASE", category.getName())
                .execute();
        if (!existingCategories.isEmpty()) {
            throw new IllegalStateException("Category with name " + category.getName() + " already exists");
        }

        final StoredCategory storedCategory = StoredCategory.create(category);
        final Long id = storedCategory.save();

        category.setId(id);
    }

    public List<Category> getCategories() {
        final Map<Long, Category> categories = loadCategories();
        return ImmutableList.copyOf(categories.values());
    }

    public void removeCategory(Category category) {
        new Delete()
                .from(StoredProductCategoryLink.class)
                .where("category = ? ", category.getId())
                .execute();

        StoredCategory.delete(StoredCategory.class, category.getId());
    }

    public void saveCategory(Category category) {
        final StoredCategory storedCategory = StoredCategory.load(StoredCategory.class, category.getId());

        if (storedCategory == null) {
            throw new IllegalStateException("Trying to save unexisting category");
        }

        storedCategory.fillFrom(category);
        storedCategory.save();
    }

    @Table(name = "Categories")
    public static class StoredCategory extends Model {
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
    public static class StoredProduct extends Model {
        @Column(name = "Name")
        private String name;

        @Column(name = "Note")
        private String note;

        @Column(name = "UnitOfMeasure")
        private UnitOfMeasure unitOfMeasure;

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
            this.note = product.getNote();
            this.unitOfMeasure = product.getDefaultUnits();

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

            final String idsString = Joiner.on(",").join(newIds);
            final List<StoredCategory> storedCategories = new Select()
                    .from(StoredCategory.class)
                    .where("Id IN (" + idsString + ")")
                    .execute();
            if (storedCategories.size() != newIds.size()) {
                throw new IllegalStateException("Not all categories were loaded");
            }
            for (StoredCategory storedCategory : storedCategories) {
                final StoredProductCategoryLink link = new StoredProductCategoryLink();
                link.setProduct(this);
                link.setCategory(storedCategory);
                newLinks.add(link);
            }
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
    }

    private static class IdComparator<T extends Entity> implements Comparator<T> {
        @Override
        public int compare(T o1, T o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }
}
