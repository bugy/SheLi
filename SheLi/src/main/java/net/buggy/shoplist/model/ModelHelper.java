package net.buggy.shoplist.model;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.utils.DateUtils;
import net.buggy.shoplist.utils.StringUtils;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ModelHelper {

    public static final int MIN_OVERDUE_AGE_PERCENT = 75;

    public static int getColor(Category category) {
        if (category.getColor() != null) {
            return category.getColor();
        }

        return Color.WHITE;
    }

    public static Multiset<Integer> getColors(Collection<Category> categories) {
        Multiset<Integer> result = LinkedHashMultiset.create();

        for (Category category : categories) {
            final int color = getColor(category);
            result.add(color);
        }

        return result;
    }

    public static void saveCategoryLinkedProducts(
            Category category, Set<Product> linkedProducts, Dao dao) {

        final List<Product> allProducts = dao.getProducts();

        for (Product product : allProducts) {
            final boolean unlinked = !linkedProducts.contains(product)
                    && product.getCategories().contains(category);
            final boolean linked = linkedProducts.contains(product)
                    && !product.getCategories().contains(category);

            if (unlinked) {
                product.removeCategory(category);
                dao.saveProduct(product);
            } else if (linkedProducts.contains(product) && linked) {
                product.addCategory(category);
                dao.saveProduct(product);
            }
        }
    }

    public static boolean isUnique(Category category, String name, ShopListActivity activity) {
        final Dao dao = activity.getDao();
        final List<Category> categories = dao.getCategories();

        for (Category anotherCategory : categories) {
            if (Objects.equal(anotherCategory, category)) {
                continue;
            }

            if (StringUtils.equalIgnoreCase(anotherCategory.getName(), name)) {
                return false;
            }
        }

        return true;
    }

    public static Category createCategory(String name) {
        final Category category = new Category();
        category.setName(name);

        final Random random = new Random();
        final int color = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
        category.setColor(color);

        return category;
    }

    public static String buildStringQuantity(ShopItem shopItem, Context context) {
        if (shopItem.getQuantity() == null) {
            return "";
        }

        final String quantityString = StringUtils.toString(shopItem.getQuantity());

        final UnitOfMeasure unitOfMeasure = getUnitOfMeasure(shopItem);
        if (unitOfMeasure == null) {
            return quantityString;
        }

        final String unitsString = context.getString(unitOfMeasure.getShortNameKey());
        return quantityString + " " + unitsString;
    }

    public static String getAgeText(Date date, Context context) {
        if (date == null) {
            return "";
        }

        final double daysDiff = DateUtils.daysDiff(date, new Date());
        final long daysDiffRounded = Math.round(daysDiff);

        if (daysDiffRounded <= 0) {
            return context.getString(R.string.today);
        }

        if (daysDiffRounded <= 6) {
            return context.getString(R.string.days_ago, String.valueOf(daysDiffRounded));
        }

        int weeksRounded = (int) Math.round(daysDiff / 7);
        if (weeksRounded <= 4) {
            return context.getString(R.string.weeks_ago, String.valueOf(weeksRounded));
        }

        int monthsRounded = (int) Math.round(daysDiff / 30);
        if (monthsRounded < 10) {
            return context.getString(R.string.months_ago, String.valueOf(monthsRounded));
        }

        int yearsRounded = (int) Math.round(daysDiff / 365);
        if (yearsRounded <= 5) {
            return context.getString(R.string.years_ago, String.valueOf(yearsRounded));
        }

        return context.getString(R.string.long_ago);
    }

    public static int ageToPercent(Product product) {
        final Integer periodCount = product.getPeriodCount();
        if (periodCount == null) {
            return 0;
        }

        if (product.getPeriodType() == null) {
            return 0;
        }

        final Date lastBuyDate = product.getLastBuyDate();
        if (lastBuyDate == null) {
            return 0;
        }

        final PeriodType periodType = product.getPeriodType();
        final int periodDays = periodType.getDays() * periodCount;
        final double ageDays = DateUtils.daysDiff(lastBuyDate, new Date());

        return (int) Math.round(ageDays / periodDays * 100);
    }

    public static UnitOfMeasure getUnitOfMeasure(ShopItem shopItem) {
        if (shopItem.getUnitOfMeasure() != null) {
            return shopItem.getUnitOfMeasure();
        }

        return shopItem.getProduct().getDefaultUnits();
    }

    @Nullable
    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }

        return name.trim().toLowerCase();
    }

    public static <T extends Entity> Map<Long, T> mapIds(Collection<T> entities) {
        final Map<Long, T> result = new LinkedHashMap<>();

        for (T entity : entities) {
            result.put(entity.getId(), entity);
        }

        return result;
    }

    @NonNull
    public static <T extends Entity> EntitySynchronizationRecord<T> createSyncRecord(
            Long internalId, String externalId, String listId, Date modificationDate, Class<T> entityClass) {

        final EntitySynchronizationRecord<T> synchronizationRecord = new EntitySynchronizationRecord<>();
        synchronizationRecord.setLastChangeDate(modificationDate);
        synchronizationRecord.setInternalId(internalId);
        synchronizationRecord.setExternalId(externalId);
        synchronizationRecord.setListId(listId);
        synchronizationRecord.setDeleted(false);
        synchronizationRecord.setEntityClass(entityClass);
        return synchronizationRecord;
    }
}
