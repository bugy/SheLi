package net.buggy.shoplist.sharing;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;

import net.buggy.shoplist.data.FirebaseDatabaseMocker;
import net.buggy.shoplist.data.InMemoryDao;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.PeriodType;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static net.buggy.shoplist.model.PeriodType.DAYS;
import static net.buggy.shoplist.model.PeriodType.WEEKS;
import static net.buggy.shoplist.model.UnitOfMeasure.BOTTLE;
import static net.buggy.shoplist.model.UnitOfMeasure.GRAM;
import static net.buggy.shoplist.model.UnitOfMeasure.LITER;
import static net.buggy.shoplist.model.UnitOfMeasure.PACK;
import static net.buggy.shoplist.sharing.EntitySynchronizer.getListIdFromEntity;
import static net.buggy.shoplist.sharing.FirebaseHelper.getChildrenValues;
import static net.buggy.shoplist.sharing.FirebaseHelper.getDateValue;
import static net.buggy.shoplist.sharing.FirebaseHelper.getEnumValue;
import static net.buggy.shoplist.sharing.FirebaseSynchronizer.State.SUBSCRIBED;
import static net.buggy.shoplist.sharing.FirebaseSynchronizer.State.UNSUBSCRIBED;
import static net.buggy.shoplist.utils.CollectionUtils.isEmpty;
import static net.buggy.shoplist.utils.DateUtils.date;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
public class FirebaseSynchronizerTest {

    private static final int DEFAULT_TIMEOUT = 10000;
    private static final String MAIN_USER = "UserX";

    private static final boolean DEBUG_LOGGING_ENABLED = false;

    private FirebaseDatabase firebaseDatabase;
    private FirebaseAuth firebaseAuth;
    private InMemoryDao dao;
    private FirebaseSynchronizer firebaseSynchronizer;

    private final AtomicReference<FirebaseUser> synchronizerUserReference = new AtomicReference<>();
    private final AtomicReference<FirebaseUser> unitTestUserReference = new AtomicReference<>();

    private final List<FirebaseAuth.AuthStateListener> authListeners = new CopyOnWriteArrayList<>();
    private FirebaseDatabaseMocker firebaseMocker;

    private final List<Throwable> uncaughtExceptions = new CopyOnWriteArrayList<>();
    private final List<String> logWarnings = new CopyOnWriteArrayList<>();
    private final List<String> logErrors = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        mockLogs();

        mockFirebaseDatabase();
        mockFirebaseAuth();

        dao = new InMemoryDao();

        firebaseSynchronizer = new FirebaseSynchronizer(
                dao, firebaseAuth, firebaseDatabase, new TestFailureCallback());
        firebaseSynchronizer.start();

        setUnitTestFirebaseUser(MAIN_USER);
    }

    @After
    public void tearDown() throws Exception {
        disconnect();
        firebaseSynchronizer.stop();

        waitBackgroundTasks();

        final List<Exception> backgroundExceptions = firebaseMocker.getBackgroundExceptions();
        uncaughtExceptions.addAll(backgroundExceptions);

        if (!uncaughtExceptions.isEmpty()) {
            for (Throwable exception : uncaughtExceptions) {
                exception.printStackTrace();
            }

            Assert.fail("Unexpected exceptions occurred (see stdErr)");
        }

        if (!logErrors.isEmpty()) {
            final String logsString = Joiner.on('\n').join(logErrors);
            Assert.fail("Following error were logged:\n" + logsString);
        }

        if (!logWarnings.isEmpty()) {
            final String logsString = Joiner.on('\n').join(logWarnings);
            Assert.fail("Following warnings were logged:\n" + logsString);
        }
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testAuthSubscription() {
        assertFalse("Synchronized didn't subscribe on authentication", authListeners.isEmpty());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testAuthUnsubscription() {
        firebaseSynchronizer.stop();

        assertTrue("Synchronized didn't Unsubscribe from authentication", authListeners.isEmpty());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testUnsubscribedWithoutAuth() {
        final FirebaseSynchronizer.State state = firebaseSynchronizer.getState();
        assertEquals(FirebaseSynchronizer.State.UNSUBSCRIBED, state);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSubscribedAfterAuthentication() {
        authenticate();

        final FirebaseSynchronizer.State state = firebaseSynchronizer.waitState(SUBSCRIBED, 2000);
        assertEquals(SUBSCRIBED, state);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testUnsubscribedAfterLogout() {
        authenticate();
        firebaseSynchronizer.waitState(SUBSCRIBED, 2000);

        disconnect();

        final FirebaseSynchronizer.State state = firebaseSynchronizer.waitState(UNSUBSCRIBED, 2000);
        assertEquals(FirebaseSynchronizer.State.UNSUBSCRIBED, state);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testListCreatedOnFirstStart() {
        authenticate();
        firebaseSynchronizer.waitState(SUBSCRIBED, 2000);

        final DataSnapshot users = firebaseMocker.getDataSnapshot("users");
        final DataSnapshot listIdNode = users.child(synchronizerUserReference.get().getUid()).child("listId");

        final String listId = listIdNode.getValue(String.class);
        assertNotNull(listId);

        final DataSnapshot lists = firebaseMocker.getDataSnapshot("lists");
        final DataSnapshot listNode = lists.child(listId);
        assertNotNull(listNode);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddClientCategory() {
        final Category category1 = addCategory("category1", 12345);

        authenticateAndWaitSynchronizer();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
        assertCategoryEquals(category1, categoriesList.getChildren().iterator().next());

        final EntitySynchronizationRecord<Category> record = findSyncRecord(category1);
        assertNotNull("Sync record for category #" + category1 + " not found", record);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleClientCategories() {
        final Category category1 = addCategory("category1", 12345);
        final Category category2 = addCategory("category2", null);
        final Category category3 = addCategory("category3", 100);

        authenticateAndWaitSynchronizer();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(3, categoriesList.getChildrenCount());

        assertCategoryEquals(category1, findServerEntity(category1));
        assertCategoryEquals(category2, findServerEntity(category2));
        assertCategoryEquals(category3, findServerEntity(category3));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddClientProduct() {
        final Product product = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);

        authenticateAndWaitSynchronizer();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
        assertProductEquals(product, productsList.getChildren().iterator().next());

        final EntitySynchronizationRecord<Product> record = findSyncRecord(product);
        assertNotNull("Sync record for product " + product + " not found", record);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleClientProducts() {
        final Product product1 = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final Product product2 = addProduct("product2", UnitOfMeasure.GRAM, 6, PeriodType.YEARS);
        final Product product3 = addProduct("product3", null, null, null);

        authenticateAndWaitSynchronizer();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(3, productsList.getChildrenCount());

        assertProductEquals(product1, findServerEntity(product1));
        assertProductEquals(product2, findServerEntity(product2));
        assertProductEquals(product3, findServerEntity(product3));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleClientProductsWithCategories() {
        final Category category1 = addCategory("category1", 100);
        final Category category2 = addCategory("category2", 5);

        final Product product1 = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        product1.setCategories(Arrays.asList(category1, category2));
        dao.saveProduct(product1);

        final Product product2 = addProduct("product2", UnitOfMeasure.GRAM, 6, PeriodType.YEARS);
        product2.setCategories(Collections.singletonList(category1));
        dao.saveProduct(product2);

        final Product product3 = addProduct("product3", null, null, null);
        product3.setCategories(Collections.singletonList(category2));
        dao.saveProduct(product3);

        authenticateAndWaitSynchronizer();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(3, productsList.getChildrenCount());

        assertProductEquals(product1, findServerEntity(product1));
        assertProductEquals(product2, findServerEntity(product2));
        assertProductEquals(product3, findServerEntity(product3));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddClientShopItem() {
        final Product product = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final ShopItem shopItem = addShopItem(product, BigDecimal.ONE, "test comment", LITER);

        authenticateAndWaitSynchronizer();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemsList.getChildrenCount());
        assertShopItemEquals(shopItem, shopItemsList.getChildren().iterator().next());

        final EntitySynchronizationRecord<ShopItem> record = findSyncRecord(shopItem);
        assertNotNull("Sync record for shopItem " + shopItem + " not found", record);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleClientShopItems() {
        final Product product1 = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final Product product2 = addProduct("product2", null, null, null);
        final Product product3 = addProduct("product3", UnitOfMeasure.PACK, 1, PeriodType.MONTHS);

        final ShopItem shopItem1 = addShopItem(product1, BigDecimal.ONE, "test comment", LITER);
        final ShopItem shopItem2 = addShopItem(product2, null, null, null);
        final ShopItem shopItem3 = addShopItem(product3, new BigDecimal(0.5), "123", UnitOfMeasure.BOTTLE);

        authenticateAndWaitSynchronizer();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(3, shopItemsList.getChildrenCount());

        assertShopItemEquals(shopItem1, findServerEntity(shopItem1));
        assertShopItemEquals(shopItem2, findServerEntity(shopItem2));
        assertShopItemEquals(shopItem3, findServerEntity(shopItem3));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddServerCategory() {
        final DataSnapshot serverCategory = addServerCategory("category1", 12345);

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());
        assertCategoryEquals(categories.get(0), serverCategory);

        final EntitySynchronizationRecord<Category> record = findSyncRecord(serverCategory);
        assertNotNull("Sync record for category #" + serverCategory.getKey() + " not found", record);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleServerCategories() {
        final DataSnapshot serverCategory1 = addServerCategory("category1", 12345);
        final DataSnapshot serverCategory2 = addServerCategory("category2", null);
        final DataSnapshot serverCategory3 = addServerCategory("category3", 100);

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(3, categories.size());

        assertCategoryEquals((Category) findClientEntity(serverCategory1), serverCategory1);
        assertCategoryEquals((Category) findClientEntity(serverCategory2), serverCategory2);
        assertCategoryEquals((Category) findClientEntity(serverCategory3), serverCategory3);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddServerProduct() {
        final DataSnapshot serverProduct = addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);

        authenticateAndWaitSynchronizer();

        final List<Product> products = dao.getProducts();
        assertEquals(1, products.size());
        assertProductEquals(products.get(0), serverProduct);

        final EntitySynchronizationRecord<Product> record = findSyncRecord(serverProduct);
        assertNotNull("Sync record for product #" + serverProduct.getKey() + " not found", record);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleServerProducts() {
        final DataSnapshot serverProduct1 = addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final DataSnapshot serverProduct2 = addServerProduct("product2", null, null, null);
        final DataSnapshot serverProduct3 = addServerProduct("product3", LITER, 1, WEEKS);

        authenticateAndWaitSynchronizer();

        final List<Product> products = dao.getProducts();
        assertEquals(3, products.size());

        assertProductEquals((Product) findClientEntity(serverProduct1), serverProduct1);
        assertProductEquals((Product) findClientEntity(serverProduct2), serverProduct2);
        assertProductEquals((Product) findClientEntity(serverProduct3), serverProduct3);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleServerProductsWithCategories() {
        final DataSnapshot serverCategory1 = addServerCategory("category1", 100);
        final DataSnapshot serverCategory2 = addServerCategory("category2", null);

        final DataSnapshot serverProduct1 = addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS,
                serverCategory1.getKey(), serverCategory2.getKey());

        final DataSnapshot serverProduct2 = addServerProduct("product2", null, null, null, serverCategory1.getKey());
        final DataSnapshot serverProduct3 = addServerProduct("product3", LITER, 1, WEEKS,
                serverCategory2.getKey());

        authenticateAndWaitSynchronizer();

        final List<Product> products = dao.getProducts();
        assertEquals(3, products.size());

        assertProductEquals((Product) findClientEntity(serverProduct1), serverProduct1);
        assertProductEquals((Product) findClientEntity(serverProduct2), serverProduct2);
        assertProductEquals((Product) findClientEntity(serverProduct3), serverProduct3);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddServerShopItem() {
        final DataSnapshot serverProduct = addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);

        final DataSnapshot serverShopItem = addServerShopItem(
                serverProduct, BigDecimal.ONE, "test", UnitOfMeasure.PACK);

        authenticateAndWaitSynchronizer();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(1, shopItems.size());
        assertShopItemEquals(shopItems.get(0), serverShopItem);

        final EntitySynchronizationRecord<ShopItem> record = findSyncRecord(serverShopItem);
        assertNotNull("Sync record for shopItem #" + serverShopItem.getKey() + " not found", record);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncAddMultipleServerShopItems() {
        final DataSnapshot serverProduct1 = addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final DataSnapshot serverProduct2 = addServerProduct("product2", null, null, null);
        final DataSnapshot serverProduct3 = addServerProduct("product3", UnitOfMeasure.PACK, 1, PeriodType.MONTHS);

        final DataSnapshot serverShopItem1 = addServerShopItem(
                serverProduct1, BigDecimal.ONE, "test", UnitOfMeasure.PACK);
        final DataSnapshot serverShopItem2 = addServerShopItem(
                serverProduct2, null, null, null);
        final DataSnapshot serverShopItem3 = addServerShopItem(
                serverProduct3, new BigDecimal("1.5"), "some comment", UnitOfMeasure.BOTTLE);

        authenticateAndWaitSynchronizer();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(3, shopItems.size());

        assertShopItemEquals((ShopItem) findClientEntity(serverShopItem1), serverShopItem1);
        assertShopItemEquals((ShopItem) findClientEntity(serverShopItem2), serverShopItem2);
        assertShopItemEquals((ShopItem) findClientEntity(serverShopItem3), serverShopItem3);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddClientCategory() {
        addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        final Category category2 = addCategory("category2", 100);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(2, categoriesList.getChildrenCount());
        assertCategoryEquals(category2, findServerEntity(category2));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddClientProduct() {
        addProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        final Product product2 = addProduct("product2", null, null, null);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(2, productsList.getChildrenCount());
        assertProductEquals(product2, findServerEntity(product2));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddClientShopItem() {
        final Product product1 = addProduct("product1", null, null, null);
        addShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        final Product product2 = addProduct("product2", null, null, null);
        final ShopItem shopItem2 = addShopItem(product2, null, null, null);

        waitBackgroundTasks();

        final DataSnapshot shopItemList = getEntityListSnapshot("shopItems");
        assertEquals(2, shopItemList.getChildrenCount());
        assertShopItemEquals(shopItem2, findServerEntity(shopItem2));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncChangeClientCategory() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        category1.setName("changedName");
        category1.setColor(100);
        dao.saveCategory(category1);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
        assertCategoryEquals(category1, findServerEntity(category1));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncChangeClientProduct() {
        final Product product1 = addProduct("product1", UnitOfMeasure.PACK, 1, PeriodType.DAYS);

        authenticateAndWaitSynchronizer();

        final Category category = addCategory("categoryX", 100);
        product1.setCategories(Collections.singletonList(category));
        product1.setName("changedName");
        product1.setPeriodCount(100);
        product1.setDefaultUnits(UnitOfMeasure.GRAM);
        product1.setLastBuyDate(date(2017, 5, 5));
        product1.setPeriodType(WEEKS);
        dao.saveProduct(product1);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
        assertProductEquals(product1, findServerEntity(product1));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncChangeClientShopItem() {
        final Product product1 = addProduct("product1", null, null, null);
        final ShopItem shopItem = addShopItem(product1, BigDecimal.TEN, "test", UnitOfMeasure.KILOGRAM);

        authenticateAndWaitSynchronizer();

        shopItem.setChecked(true);
        shopItem.setUnitOfMeasure(UnitOfMeasure.GRAM);
        shopItem.setQuantity(BigDecimal.ONE);
        shopItem.setComment("new comment");
        dao.saveShopItem(shopItem);

        waitBackgroundTasks();

        final DataSnapshot shopItemList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemList.getChildrenCount());
        assertShopItemEquals(shopItem, findServerEntity(shopItem));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteClientCategory() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        dao.removeCategory(category1);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(0, categoriesList.getChildrenCount());
        assertEquals(0, loadSyncRecords(Category.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteClientProduct() {
        final Product product = addProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        dao.removeProduct(product);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(0, productsList.getChildrenCount());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteClientShopItem() {
        final Product product1 = addProduct("product1", null, null, null);
        final ShopItem shopItem = addShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        dao.removeShopItem(shopItem);

        waitBackgroundTasks();

        final DataSnapshot shopItemList = getEntityListSnapshot("shopItems");
        assertEquals(0, shopItemList.getChildrenCount());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddServerCategory() {
        addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        final DataSnapshot category2 = addServerCategory("category2", 100);

        waitBackgroundTasks();

        final List<Category> clientCategories = dao.getCategories();
        assertEquals(2, clientCategories.size());
        assertCategoryEquals((Category) findClientEntity(category2), category2);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddServerProduct() {
        addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        final DataSnapshot product2 = addServerProduct("product2", null, null, null);

        waitBackgroundTasks();

        final List<Product> clientProducts = dao.getProducts();
        assertEquals(2, clientProducts.size());
        assertProductEquals((Product) findClientEntity(product2), product2);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddServerShopItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        final DataSnapshot product2 = addServerProduct("product2", null, null, null);
        final DataSnapshot shopItem2 = addServerShopItem(product2, null, null, null);

        waitBackgroundTasks();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(2, shopItems.size());
        assertShopItemEquals((ShopItem) findClientEntity(shopItem2), shopItem2);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncChangeServerCategory() {
        final DataSnapshot serverCategory = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("name", "changedName");
        newValues.put("naturalId", "changedName".toLowerCase());
        newValues.put("color", 1);
        final Date changeDate = new Date();
        newValues.put("lastChangeDate", toFirebaseDate(changeDate));
        serverCategory.getRef().updateChildren(newValues);

        waitBackgroundTasks();

        final DataSnapshot updatedCategory = getUpdatedSnapshot(serverCategory);
        assertCategoryEquals(
                (Category) findClientEntity(serverCategory),
                updatedCategory);
        assertEquals(getDate(updatedCategory, "lastChangeDate"), changeDate);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncChangeServerProduct() {
        final DataSnapshot category1 = addServerCategory("category1", 1);
        final DataSnapshot category2 = addServerCategory("category2", 1);
        final DataSnapshot category3 = addServerCategory("category3", 1);

        final DataSnapshot serverProduct = addServerProduct(
                "product", UnitOfMeasure.GRAM, 1, WEEKS, category1.getKey());

        authenticateAndWaitSynchronizer();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("name", "changedName");
        newValues.put("naturalId", "changedName".toLowerCase());
        newValues.put("defaultUnits", toFirebaseEnum(UnitOfMeasure.BOTTLE));
        newValues.put("periodCount", 3);
        newValues.put("periodType", toFirebaseEnum(PeriodType.MONTHS));
        newValues.put("lastBuyDate", toFirebaseDate(date(2017, 5, 5)));
        newValues.put("categories", Arrays.asList(category2.getKey(), category3.getKey()));
        final Date changeDate = new Date();
        newValues.put("lastChangeDate", toFirebaseDate(changeDate));
        serverProduct.getRef().updateChildren(newValues);

        waitBackgroundTasks();

        final DataSnapshot updatedSnapshot = getUpdatedSnapshot(serverProduct);
        assertProductEquals(
                (Product) findClientEntity(serverProduct),
                updatedSnapshot);
        assertEquals(getDate(updatedSnapshot, "lastChangeDate"), changeDate);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncChangeServerShopItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot product2 = addServerProduct("product2", null, null, null);
        final DataSnapshot serverShopItem = addServerShopItem(product1, new BigDecimal(3), "test comment", UnitOfMeasure.GRAM);

        authenticateAndWaitSynchronizer();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("quantity", toFirebaseBigDecimal(5));
        newValues.put("checked", true);
        newValues.put("product", product2.getKey());
        newValues.put("naturalId", getString(product2, "naturalId"));
        newValues.put("comment", "new c");
        newValues.put("unitOfMeasure", toFirebaseEnum(LITER));
        final Date changeDate = new Date();
        newValues.put("lastChangeDate", toFirebaseDate(changeDate));
        serverShopItem.getRef().updateChildren(newValues);

        waitBackgroundTasks();

        final DataSnapshot updatedSnapshot = getUpdatedSnapshot(serverShopItem);
        assertShopItemEquals(
                (ShopItem) findClientEntity(serverShopItem),
                updatedSnapshot);
        assertEquals(getDate(updatedSnapshot, "lastChangeDate"), changeDate);
    }


    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteServerCategory() {
        final DataSnapshot category = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        category.getRef().removeValue();

        waitBackgroundTasks();

        assertEquals(0, dao.getCategories().size());
        assertEquals(0, loadSyncRecords(Category.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteServerProduct() {
        final DataSnapshot product = addServerProduct("product", UnitOfMeasure.GRAM, 1, WEEKS);

        authenticateAndWaitSynchronizer();

        product.getRef().removeValue();

        waitBackgroundTasks();

        assertEquals(0, dao.getProducts().size());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteServerShopItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot shopItem = addServerShopItem(product1, new BigDecimal(3), "test comment", UnitOfMeasure.GRAM);

        authenticateAndWaitSynchronizer();

        shopItem.getRef().removeValue();

        waitBackgroundTasks();

        assertEquals(0, dao.getShopItems().size());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncDeleteServerProductWithLinkedItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        addServerShopItem(product1, new BigDecimal(3), "test comment", UnitOfMeasure.GRAM);

        authenticateAndWaitSynchronizer();

        product1.getRef().removeValue();

        waitBackgroundTasks();

        final List<EntitySynchronizationRecord<ShopItem>> syncRecords = loadSyncRecords(ShopItem.class);

        assertEquals(0, dao.getShopItems().size());
        assertEquals(0, syncRecords.size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddClientCategoryAfterDisconnect() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        addCategory("category2", 100);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
        assertCategoryEquals(category1, categoriesList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddClientProductAfterDisconnect() {
        final Product product1 = addProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        addProduct("product2", null, null, null);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
        assertProductEquals(product1, productsList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddClientShopItemAfterDisconnect() {
        final Product product1 = addProduct("product1", null, null, null);
        final ShopItem shopItem1 = addShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final Product product2 = addProduct("product2", null, null, null);
        addShopItem(product2, null, null, null);

        waitBackgroundTasks();

        final DataSnapshot shopItemList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemList.getChildrenCount());
        assertShopItemEquals(shopItem1, shopItemList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncChangeClientCategoryAfterDisconnect() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        category1.setName("newName");
        dao.saveCategory(category1);

        waitBackgroundTasks();

        final DataSnapshot serverCategory = findServerEntity(category1);
        assertNotEquals(category1.getName(), getString(serverCategory, "name"));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncChangeClientProductAfterDisconnect() {
        final Product product = addProduct("product", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        product.setName("newName");
        dao.saveProduct(product);

        waitBackgroundTasks();

        final DataSnapshot serverProduct = findServerEntity(product);
        assertNotEquals(product.getName(), getString(serverProduct, "name"));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncChangeClientShopItemAfterDisconnect() {
        final Product product = addProduct("product", null, null, null);
        final ShopItem shopItem = addShopItem(product, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        shopItem.setChecked(true);
        dao.saveShopItem(shopItem);

        waitBackgroundTasks();

        final DataSnapshot serverShopItem = findServerEntity(shopItem);
        assertNotEquals(shopItem.isChecked(), getBoolean(serverShopItem, "checked"));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteClientCategoryAfterDisconnect() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        dao.removeCategory(category1);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteClientProductAfterDisconnect() {
        final Product product = addProduct("product", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        dao.removeProduct(product);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteClientShopItemAfterDisconnect() {
        final Product product = addProduct("product", null, null, null);
        final ShopItem shopItem = addShopItem(product, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        dao.removeShopItem(shopItem);

        waitBackgroundTasks();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddServerCategoryAfterDisconnect() {
        final DataSnapshot category1 = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        addServerCategory("category2", 100);

        waitBackgroundTasks();

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());
        assertCategoryEquals(categories.get(0), category1);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddServerProductAfterDisconnect() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        addServerProduct("product2", null, null, null);

        waitBackgroundTasks();

        final List<Product> products = dao.getProducts();
        assertEquals(1, products.size());
        assertProductEquals(products.get(0), product1);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddServerShopItemAfterDisconnect() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot shopItem1 = addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final DataSnapshot product2 = addServerProduct("product2", null, null, null);
        addServerShopItem(product2, null, null, null);

        waitBackgroundTasks();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(1, shopItems.size());
        assertShopItemEquals(shopItems.get(0), shopItem1);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncChangeServerCategoryAfterDisconnect() {
        final DataSnapshot serverCategory = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverCategory));
        newValues.put("name", "changedName");
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverCategory.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerCategory = getUpdatedSnapshot(serverCategory);

        waitBackgroundTasks();

        final Category clientCategory = findClientEntity(serverCategory);
        assertNotNull(clientCategory);
        assertNotEquals(getString(updatedServerCategory, "name"), clientCategory.getName());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncChangeServerProductAfterDisconnect() {
        final DataSnapshot serverProduct = addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverProduct));
        newValues.put("name", "changedName");
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverProduct.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerProduct = getUpdatedSnapshot(serverProduct);

        waitBackgroundTasks();

        final Product clientProduct = findClientEntity(serverProduct);
        assertNotNull(clientProduct);
        assertNotEquals(getString(updatedServerProduct, "name"), clientProduct.getName());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncChangeServerShopItemAfterDisconnect() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot serverShopItem = addServerShopItem(product1, null, "comment", null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverShopItem));
        newValues.put("comment", "changed comment");
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverShopItem.getRef().updateChildren(newValues);
        final DataSnapshot updatedShopItem = getUpdatedSnapshot(serverShopItem);

        waitBackgroundTasks();

        final ShopItem clientShopItem = findClientEntity(serverShopItem);
        assertNotNull(clientShopItem);
        assertNotEquals(getString(updatedShopItem, "comment"), clientShopItem.getComment());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteServerCategoryAfterDisconnect() {
        final DataSnapshot category1 = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        category1.getRef().removeValue();

        waitBackgroundTasks();

        final Category clientCategory = findClientEntity(category1);
        assertNotNull(clientCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteServerProductAfterDisconnect() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        product1.getRef().removeValue();

        waitBackgroundTasks();

        final Product clientProduct = findClientEntity(product1);
        assertNotNull(clientProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteServerShopItemAfterDisconnect() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot shopItem1 = addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        shopItem1.getRef().removeValue();

        waitBackgroundTasks();

        final ShopItem clientShopItem = findClientEntity(shopItem1);
        assertNotNull(clientShopItem);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncAddClientCategory() {
        addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        final Category category2 = addCategory("category2", 100);

        authenticateAndWaitSynchronizer();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(2, categoriesList.getChildrenCount());
        assertCategoryEquals(category2, findServerEntity(category2));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncAddClientProduct() {
        addProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final Product product2 = addProduct("product2", null, null, null);

        authenticateAndWaitSynchronizer();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(2, productsList.getChildrenCount());
        assertProductEquals(product2, findServerEntity(product2));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncAddClientShopItem() {
        final Product product1 = addProduct("product1", null, null, null);
        addShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final Product product2 = addProduct("product2", null, null, null);
        final ShopItem shopItem2 = addShopItem(product2, null, null, null);

        authenticateAndWaitSynchronizer();

        final DataSnapshot shopItemList = getEntityListSnapshot("shopItems");
        assertEquals(2, shopItemList.getChildrenCount());
        assertShopItemEquals(shopItem2, findServerEntity(shopItem2));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncChangeClientCategory() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        category1.setName("newName");
        category1.setColor(null);
        dao.saveCategory(category1);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverCategory = findServerEntity(category1);
        assertCategoryEquals(category1, serverCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncChangeClientProduct() {
        final Category category1 = addCategory("category1", 12345);

        final Product product = addProduct("product", UnitOfMeasure.BOTTLE, 5, PeriodType.DAYS);
        product.setCategories(Collections.singletonList(category1));

        authenticateAndWaitSynchronizer();

        disconnect();

        final Category category2 = addCategory("category2", null);
        final Category category3 = addCategory("category3", 100);

        product.setName("newName");
        product.setPeriodCount(2);
        product.setDefaultUnits(null);
        product.setLastBuyDate(new Date());
        product.setPeriodType(WEEKS);
        product.setCategories(Arrays.asList(category2, category3));
        dao.saveProduct(product);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverProduct = findServerEntity(product);
        assertProductEquals(product, serverProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncChangeClientShopItem() {
        final Product product1 = addProduct("product1", null, null, null);
        final ShopItem shopItem = addShopItem(product1, BigDecimal.ONE, "test", UnitOfMeasure.PACK);

        authenticateAndWaitSynchronizer();

        disconnect();

        final Product product2 = addProduct("newProduct", null, null, null);

        shopItem.setProduct(product2);
        shopItem.setUnitOfMeasure(LITER);
        shopItem.setQuantity(null);
        shopItem.setComment("new comment");
        shopItem.setChecked(true);
        dao.saveShopItem(shopItem);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverShopItem = findServerEntity(shopItem);
        assertShopItemEquals(shopItem, serverShopItem);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteClientCategory() {
        final Category category1 = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        dao.removeCategory(category1);

        authenticateAndWaitSynchronizer();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(0, categoriesList.getChildrenCount());
        assertEquals(0, loadSyncRecords(Category.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteClientProduct() {
        final Product product = addProduct("product", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        dao.removeProduct(product);

        authenticateAndWaitSynchronizer();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(0, productsList.getChildrenCount());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteClientShopItem() {
        final Product product = addProduct("product", null, null, null);
        final ShopItem shopItem = addShopItem(product, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        dao.removeShopItem(shopItem);

        authenticateAndWaitSynchronizer();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(0, shopItemsList.getChildrenCount());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncAddServerCategory() {
        addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        final DataSnapshot category2 = addServerCategory("category2", 100);

        authenticateAndWaitSynchronizer();


        final List<Category> categories = dao.getCategories();
        assertEquals(2, categories.size());
        assertCategoryEquals((Category) findClientEntity(category2), category2);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncAddServerProduct() {
        addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final DataSnapshot product2 = addServerProduct("product2", null, null, null);

        authenticateAndWaitSynchronizer();

        final List<Product> products = dao.getProducts();
        assertEquals(2, products.size());
        assertProductEquals((Product) findClientEntity(product2), product2);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncAddServerShopItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final DataSnapshot product2 = addServerProduct("product2", null, null, null);
        final DataSnapshot shopItem2 = addServerShopItem(product2, null, null, null);

        authenticateAndWaitSynchronizer();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(2, shopItems.size());
        assertShopItemEquals((ShopItem) findClientEntity(shopItem2), shopItem2);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncChangeServerCategory() {
        final DataSnapshot serverCategory = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("name", "newName");
        newValues.put("naturalId", "newName".toLowerCase());
        newValues.put("color", null);
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverCategory.getRef().updateChildren(newValues);

        authenticateAndWaitSynchronizer();

        final Category clientCategory = findClientEntity(serverCategory);
        assertCategoryEquals(clientCategory, getUpdatedSnapshot(serverCategory));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncChangeServerProduct() {
        final DataSnapshot category1 = addServerCategory("category1", null);
        final DataSnapshot category2 = addServerCategory("category2", null);
        final DataSnapshot category3 = addServerCategory("category3", null);
        final DataSnapshot serverProduct = addServerProduct("product1", null, null, null, category1.getKey());

        authenticateAndWaitSynchronizer();

        disconnect();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverProduct));
        newValues.put("name", "newName");
        newValues.put("naturalId", "newName".toLowerCase());
        newValues.put("periodCount", 2);
        newValues.put("defaultUnits", null);
        newValues.put("lastBuyDate", toFirebaseDate(new Date()));
        newValues.put("periodType", toFirebaseEnum(WEEKS));
        newValues.put("categories", Arrays.asList(category2.getKey(), category3.getKey()));
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverProduct.getRef().updateChildren(newValues);

        authenticateAndWaitSynchronizer();

        final Product clientProduct = findClientEntity(serverProduct);
        assertProductEquals(clientProduct, getUpdatedSnapshot(serverProduct));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncChangeServerShopItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot serverShopItem = addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverShopItem));
        final DataSnapshot product2 = addServerProduct("product2", null, null, null);

        newValues.put("product", product2.getKey());
        newValues.put("naturalId", getString(product2, "naturalId"));
        newValues.put("unitOfMeasure", toFirebaseEnum(LITER));
        newValues.put("quantity", toFirebaseBigDecimal((BigDecimal) null));
        newValues.put("comment", "new comment");
        newValues.put("checked", true);
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverShopItem.getRef().updateChildren(newValues);

        authenticateAndWaitSynchronizer();

        final ShopItem clientShopItem = findClientEntity(serverShopItem);
        assertShopItemEquals(clientShopItem, getUpdatedSnapshot(serverShopItem));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteServerCategory() {
        final DataSnapshot category1 = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        disconnect();

        category1.getRef().removeValue();

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(0, categories.size());
        assertEquals(0, loadSyncRecords(Category.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteServerProduct() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        product1.getRef().removeValue();

        authenticateAndWaitSynchronizer();

        final List<Product> products = dao.getProducts();
        assertEquals(0, products.size());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteServerProductWithCategory() {
        final DataSnapshot category1 = addServerCategory("category1", 255);
        final DataSnapshot product1 = addServerProduct("product1", null, null, null, category1.getKey());

        authenticateAndWaitSynchronizer();

        disconnect();

        product1.getRef().removeValue();
        category1.getRef().removeValue();

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(0, categories.size());
        assertEquals(0, loadSyncRecords(Category.class).size());

        final List<Product> products = dao.getProducts();
        assertEquals(0, products.size());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }


    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteServerShopItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot shopItem1 = addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        shopItem1.getRef().removeValue();

        authenticateAndWaitSynchronizer();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(0, shopItems.size());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testReconnectBulkSyncDeleteServerProductWithLinkedItem() {
        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        addServerShopItem(product1, null, null, null);

        authenticateAndWaitSynchronizer();

        disconnect();

        product1.getRef().removeValue();

        authenticateAndWaitSynchronizer();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(0, shopItems.size());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(0, shopItemsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserAddClientCategory() {
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        addCategory("category1", 255);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(0, categoriesList.getChildrenCount());
        assertEquals(0, loadSyncRecords(Category.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserAddClientProduct() {
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        addProduct("product1", null, null, null);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(0, productsList.getChildrenCount());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAddAnotherUserClientShopItem() {
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        final Product product1 = addProduct("product1", null, null, null);
        addShopItem(product1, null, null, null);

        waitBackgroundTasks();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(0, shopItemsList.getChildrenCount());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserChangeClientCategory() {
        final Category category1 = addCategory("category1", 255);

        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        category1.setName("newName");
        dao.saveCategory(category1);

        waitBackgroundTasks();

        final DataSnapshot serverCategory = findServerEntity(category1);
        assertNotEquals(category1.getName(), getString(serverCategory, "name"));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserChangeClientProduct() {
        final Product product = addProduct("product", null, null, null);

        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        product.setName("newName");
        dao.saveProduct(product);

        waitBackgroundTasks();

        final DataSnapshot serverProduct = findServerEntity(product);
        assertNotEquals(product.getName(), getString(serverProduct, "name"));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserChangeClientShopItem() {
        final Product product = addProduct("product", null, null, null);
        final ShopItem shopItem = addShopItem(product, null, null, null);

        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        shopItem.setChecked(true);
        dao.saveShopItem(shopItem);

        waitBackgroundTasks();

        final DataSnapshot serverShopItem = findServerEntity(shopItem);
        assertNotEquals(shopItem.isChecked(), getBoolean(serverShopItem, "checked"));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoAnotherUserSyncDeleteClientCategory() {
        final Category category1 = addCategory("category1", 255);

        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        dao.removeCategory(category1);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoAnotherUserSyncDeleteClientProduct() {
        final Product product = addProduct("product", null, null, null);

        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        dao.removeProduct(product);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserDeleteClientShopItem() {
        final Product product = addProduct("product", null, null, null);
        final ShopItem shopItem = addShopItem(product, null, null, null);

        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        dao.removeShopItem(shopItem);

        waitBackgroundTasks();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserAddServerCategory() {
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        addServerCategory("category1", 100);

        waitBackgroundTasks();

        final List<Category> categories = dao.getCategories();
        assertEquals(0, categories.size());
        assertEquals(0, loadSyncRecords(Category.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserAddServerProduct() {
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        addServerProduct("product1", null, null, null);

        waitBackgroundTasks();

        final List<Product> products = dao.getProducts();
        assertEquals(0, products.size());
        assertEquals(0, loadSyncRecords(Product.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserAddServerShopItem() {
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        addServerShopItem(product1, null, null, null);

        waitBackgroundTasks();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(0, shopItems.size());
        assertEquals(0, loadSyncRecords(ShopItem.class).size());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserChangeServerCategory() {
        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();

        final DataSnapshot serverCategory = addServerCategory("category1", 255);

        waitBackgroundTasks();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(getChildrenValues(serverCategory));
        newValues.put("name", "changedName");
        serverCategory.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerCategory = getUpdatedSnapshot(serverCategory);

        waitBackgroundTasks();

        final Category clientCategory = findClientEntity(serverCategory);
        assertNotNull(clientCategory);
        assertNotEquals(getString(updatedServerCategory, "name"), clientCategory.getName());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserChangeServerProduct() {
        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();

        final DataSnapshot serverProduct = addServerProduct("product1", null, null, null);

        waitBackgroundTasks();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(getChildrenValues(serverProduct));
        newValues.put("name", "changedName");
        serverProduct.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerProduct = getUpdatedSnapshot(serverProduct);

        waitBackgroundTasks();

        final Product clientProduct = findClientEntity(serverProduct);
        assertNotNull(clientProduct);
        assertNotEquals(getString(updatedServerProduct, "name"), clientProduct.getName());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserChangeServerShopItem() {
        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();

        final DataSnapshot serverProduct = addServerProduct("product1", null, null, null);
        final DataSnapshot serverShopItem = addServerShopItem(serverProduct, null, "comment", null);

        waitBackgroundTasks();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(getChildrenValues(serverShopItem));
        newValues.put("comment", "changed comment");
        serverShopItem.getRef().updateChildren(newValues);
        final DataSnapshot updatedShopItem = getUpdatedSnapshot(serverShopItem);

        waitBackgroundTasks();

        final ShopItem clientShopItem = findClientEntity(serverShopItem);
        assertNotNull(clientShopItem);
        assertNotEquals(getString(updatedShopItem, "comment"), clientShopItem.getComment());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserDeleteServerCategory() {
        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();

        final DataSnapshot category1 = addServerCategory("category1", 255);

        waitBackgroundTasks();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        category1.getRef().removeValue();

        waitBackgroundTasks();

        final Category clientCategory = findClientEntity(category1);
        assertNotNull(clientCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncAnotherUserDeleteServerProduct() {
        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();

        final DataSnapshot product1 = addServerProduct("product1", null, null, null);

        waitBackgroundTasks();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        product1.getRef().removeValue();

        waitBackgroundTasks();

        final Product clientProduct = findClientEntity(product1);
        assertNotNull(clientProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testNoSyncDeleteAnotherUserServerShopItem() {
        setUnitTestFirebaseUser("AnotherUser");
        authenticateAndWaitSynchronizer();

        final DataSnapshot product1 = addServerProduct("product1", null, null, null);
        final DataSnapshot shopItem1 = addServerShopItem(product1, null, null, null);

        waitBackgroundTasks();
        disconnect();

        setUnitTestFirebaseUser(MAIN_USER);
        authenticateAndWaitSynchronizer();

        setUnitTestFirebaseUser("AnotherUser");

        shopItem1.getRef().removeValue();

        waitBackgroundTasks();

        final ShopItem clientShopItem = findClientEntity(shopItem1);
        assertNotNull(clientShopItem);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncMatchCategoryByNaturalId() {
        DataSnapshot serverCategory = addServerCategory("category1", 255);
        Category clientCategory = addCategory("category1", 100);

        authenticateAndWaitSynchronizer();

        List<EntitySynchronizationRecord<Category>> records = loadSyncRecords(Category.class);
        assertEquals(1, records.size());
        EntitySynchronizationRecord<Category> syncRecord = records.get(0);
        assertEquals(serverCategory.getKey(), syncRecord.getExternalId());
        assertEquals(clientCategory.getId(), syncRecord.getInternalId());

        List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncMatchProductByNaturalId() {
        Product clientProduct = addProduct("product1", null, null, null);
        DataSnapshot serverProduct = addServerProduct("product1", null, null, null);

        authenticateAndWaitSynchronizer();

        List<EntitySynchronizationRecord<Product>> records = loadSyncRecords(Product.class);
        assertEquals(1, records.size());
        EntitySynchronizationRecord<Product> syncRecord = records.get(0);
        assertEquals(serverProduct.getKey(), syncRecord.getExternalId());
        assertEquals(clientProduct.getId(), syncRecord.getInternalId());

        List<Product> products = dao.getProducts();
        assertEquals(1, products.size());

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testBulkSyncMatchShopItemByNaturalId() {
        final Product product1 = addProduct("product1", null, null, null);
        ShopItem clientShopItem = addShopItem(product1, null, null, null);

        DataSnapshot serverProduct = addServerProduct("product1", null, null, null);
        DataSnapshot serverShopItem = addServerShopItem(serverProduct, null, null, null);

        authenticateAndWaitSynchronizer();

        List<EntitySynchronizationRecord<ShopItem>> records = loadSyncRecords(ShopItem.class);
        assertEquals(1, records.size());
        EntitySynchronizationRecord<ShopItem> syncRecord = records.get(0);
        assertEquals(serverShopItem.getKey(), syncRecord.getExternalId());
        assertEquals(clientShopItem.getId(), syncRecord.getInternalId());

        List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(1, shopItems.size());

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemsList.getChildrenCount());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddClientCategoryMatchByNaturalId() {
        DataSnapshot serverCategory = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverCategory);
        dao.removeSynchronizationRecord(syncRecord);
        Category category = dao.findCategory(syncRecord.getInternalId());
        dao.removeCategory(category);

        Category newClientCategory = addCategory("category1", 100);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
        assertCategoryEquals(newClientCategory, categoriesList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddClientProductMatchByNaturalId() {
        DataSnapshot serverProduct = addServerProduct("product1", LITER, 2, WEEKS);

        authenticateAndWaitSynchronizer();

        EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverProduct);
        dao.removeSynchronizationRecord(syncRecord);
        Product product = dao.findProduct(syncRecord.getInternalId());
        dao.removeProduct(product);

        final Product newClientProduct = addProduct("product1", PACK, 3, DAYS);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
        final DataSnapshot currentServerProduct = productsList.getChildren().iterator().next();
        assertProductEquals(newClientProduct, currentServerProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddClientShopItemMatchByNaturalId() {
        DataSnapshot serverProduct = addServerProduct("product1", null, null, null);
        DataSnapshot serverShopItem = addServerShopItem(
                serverProduct, BigDecimal.ONE, "test", BOTTLE);

        authenticateAndWaitSynchronizer();

        EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverShopItem);
        dao.removeSynchronizationRecord(syncRecord);
        ShopItem shopItem = dao.findShopItem(syncRecord.getInternalId());
        dao.removeShopItem(shopItem);

        final Product clientProduct = findClientEntity(serverProduct);
        assertNotNull(clientProduct);
        final ShopItem newClientShopItem = addShopItem(
                clientProduct, BigDecimal.TEN, "new comment", LITER);

        waitBackgroundTasks();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemsList.getChildrenCount());
        assertShopItemEquals(newClientShopItem, shopItemsList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncUpdateClientCategoryMatchByNaturalId() {
        DataSnapshot serverCategory = addServerCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        final Category clientCategory = findClientEntity(serverCategory);
        assertNotNull(clientCategory);

        EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverCategory);
        dao.removeSynchronizationRecord(syncRecord);

        clientCategory.setColor(100);
        dao.saveCategory(clientCategory);

        waitBackgroundTasks();

        final DataSnapshot categoriesList = getEntityListSnapshot("categories");
        assertEquals(1, categoriesList.getChildrenCount());
        assertCategoryEquals(clientCategory, categoriesList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncUpdateClientProductMatchByNaturalId() {
        DataSnapshot serverProduct = addServerProduct("product1", LITER, 2, WEEKS);

        authenticateAndWaitSynchronizer();

        final Product clientProduct = findClientEntity(serverProduct);
        assertNotNull(clientProduct);

        EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverProduct);
        dao.removeSynchronizationRecord(syncRecord);

        clientProduct.setDefaultUnits(BOTTLE);
        clientProduct.setLastBuyDate(new Date());
        clientProduct.setPeriodType(DAYS);
        clientProduct.setPeriodCount(3);
        dao.saveProduct(clientProduct);

        waitBackgroundTasks();

        final DataSnapshot productsList = getEntityListSnapshot("products");
        assertEquals(1, productsList.getChildrenCount());
        final DataSnapshot currentServerProduct = productsList.getChildren().iterator().next();
        assertProductEquals(clientProduct, currentServerProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncUpdateClientShopItemMatchByNaturalId() {
        DataSnapshot serverProduct = addServerProduct("product1", null, null, null);
        DataSnapshot serverShopItem = addServerShopItem(
                serverProduct, BigDecimal.ONE, "test", BOTTLE);

        authenticateAndWaitSynchronizer();

        final ShopItem clientShopItem = findClientEntity(serverShopItem);
        assertNotNull(clientShopItem);

        EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverShopItem);
        dao.removeSynchronizationRecord(syncRecord);

        clientShopItem.setUnitOfMeasure(GRAM);
        clientShopItem.setChecked(true);
        clientShopItem.setComment("new comment");
        clientShopItem.setQuantity(new BigDecimal("1.5"));
        dao.saveShopItem(clientShopItem);

        waitBackgroundTasks();

        final DataSnapshot shopItemsList = getEntityListSnapshot("shopItems");
        assertEquals(1, shopItemsList.getChildrenCount());
        assertShopItemEquals(clientShopItem, shopItemsList.getChildren().iterator().next());
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddServerCategoryMatchByNaturalId() {
        Category clientCategory = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverEntity = findServerEntity(clientCategory);
        assertNotNull(serverEntity);
        EntitySynchronizationRecord<Category> syncRecord = findSyncRecord(clientCategory);
        dao.removeSynchronizationRecord(syncRecord);
        serverEntity.getRef().removeValue();

        DataSnapshot newServerCategory = addServerCategory("category1", 100);

        waitBackgroundTasks();
        assertAndRemoveWarning("client category is missing");

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());
        assertCategoryEquals(categories.get(0), newServerCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddServerProductMatchByNaturalId() {
        Product clientProduct = addProduct("product1", LITER, 2, WEEKS);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverEntity = findServerEntity(clientProduct);
        assertNotNull(serverEntity);
        EntitySynchronizationRecord<Product> syncRecord = findSyncRecord(clientProduct);
        dao.removeSynchronizationRecord(syncRecord);
        serverEntity.getRef().removeValue();

        final DataSnapshot newServerProduct = addServerProduct("product1", PACK, 3, DAYS);

        waitBackgroundTasks();
        assertAndRemoveWarning("client product is missing");

        final List<Product> products = dao.getProducts();
        assertEquals(1, products.size());
        assertProductEquals(products.get(0), newServerProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncAddServerShopItemMatchByNaturalId() {
        Product clientProduct = addProduct("product1", null, null, null);
        ShopItem clientShopItem = addShopItem(
                clientProduct, BigDecimal.ONE, "test", BOTTLE);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverEntity = findServerEntity(clientShopItem);
        assertNotNull(serverEntity);
        EntitySynchronizationRecord<ShopItem> syncRecord = findSyncRecord(clientShopItem);
        dao.removeSynchronizationRecord(syncRecord);
        serverEntity.getRef().removeValue();

        final DataSnapshot serverProduct = findServerEntity(clientProduct);
        assertNotNull(serverProduct);
        final DataSnapshot newServerShopItem = addServerShopItem(
                serverProduct, BigDecimal.TEN, "new comment", LITER);

        waitBackgroundTasks();
        assertAndRemoveWarning("client shopItem is missing");

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(1, shopItems.size());
        assertShopItemEquals(shopItems.get(0), newServerShopItem);
    }


    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncUpdateServerCategoryMatchByNaturalId() {
        Category clientCategory = addCategory("category1", 255);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverEntity = findServerEntity(clientCategory);
        assertNotNull(serverEntity);

        EntitySynchronizationRecord<Category> syncRecord = findSyncRecord(clientCategory);
        dao.removeSynchronizationRecord(syncRecord);

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverEntity));
        newValues.put("color", 1);
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverEntity.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerEntity = getUpdatedSnapshot(serverEntity);

        waitBackgroundTasks();

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());
        assertCategoryEquals(categories.get(0), updatedServerEntity);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncUpdateServerProductMatchByNaturalId() {
        Product clientProduct = addProduct("product1", LITER, 2, WEEKS);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverEntity = findServerEntity(clientProduct);
        assertNotNull(serverEntity);
        EntitySynchronizationRecord<Product> syncRecord = findSyncRecord(clientProduct);
        dao.removeSynchronizationRecord(syncRecord);

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverEntity));
        newValues.put("defaultUnits", toFirebaseEnum(UnitOfMeasure.BOTTLE));
        newValues.put("periodCount", 3);
        newValues.put("periodType", toFirebaseEnum(PeriodType.MONTHS));
        newValues.put("lastBuyDate", toFirebaseDate(date(2017, 5, 5)));
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverEntity.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerEntity = getUpdatedSnapshot(serverEntity);

        waitBackgroundTasks();

        final List<Product> products = dao.getProducts();
        assertEquals(1, products.size());
        assertProductEquals(products.get(0), updatedServerEntity);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testSyncUpdateServerShopItemMatchByNaturalId() {
        Product clientProduct = addProduct("product1", null, null, null);
        ShopItem clientShopItem = addShopItem(
                clientProduct, BigDecimal.ONE, "test", BOTTLE);

        authenticateAndWaitSynchronizer();

        final DataSnapshot serverEntity = findServerEntity(clientShopItem);
        assertNotNull(serverEntity);
        EntitySynchronizationRecord<ShopItem> syncRecord = findSyncRecord(clientShopItem);
        dao.removeSynchronizationRecord(syncRecord);

        final LinkedHashMap<String, Object> newValues = new LinkedHashMap<>(
                getChildrenValues(serverEntity));
        newValues.put("quantity", toFirebaseBigDecimal(5));
        newValues.put("checked", true);
        newValues.put("comment", "new c");
        newValues.put("unitOfMeasure", toFirebaseEnum(LITER));
        newValues.put("lastChangeDate", toFirebaseDate(new Date()));
        serverEntity.getRef().updateChildren(newValues);
        final DataSnapshot updatedServerEntity = getUpdatedSnapshot(serverEntity);

        waitBackgroundTasks();

        final List<ShopItem> shopItems = dao.getShopItems();
        assertEquals(1, shopItems.size());
        assertShopItemEquals(shopItems.get(0), updatedServerEntity);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListBulkSyncAddClientCategory() {
        final Category category1 = addCategory("category1", 12345);

        authenticateAndWaitSynchronizer();

        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final EntitySynchronizationRecord<Category> syncRecord = findSyncRecord(category1);
        assertEquals("newList", syncRecord.getListId());
        assertCategoryEquals(category1, findServerEntity(category1));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListBulkSyncAddClientProduct() {
        final Product product = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);

        authenticateAndWaitSynchronizer();

        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final EntitySynchronizationRecord<Product> syncRecord = findSyncRecord(product);
        assertEquals("newList", syncRecord.getListId());
        assertProductEquals(product, findServerEntity(product));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListBulkSyncAddClientShopItem() {
        final Product product = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final ShopItem shopItem = addShopItem(product, BigDecimal.ONE, "test comment", LITER);

        authenticateAndWaitSynchronizer();

        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final EntitySynchronizationRecord<ShopItem> syncRecord = findSyncRecord(shopItem);
        assertEquals("newList", syncRecord.getListId());
        assertShopItemEquals(shopItem, findServerEntity(shopItem));
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListBulkSyncAddServerCategory() {
        final DataSnapshot serverCategory = addServerCategory("category1", 255);
        final String mainUserListId = getListIdFromEntity(serverCategory.getRef());

        setUnitTestFirebaseUser("tempUser");
        authenticateAndWaitSynchronizer();

        assertEquals(0, loadSyncRecords(Category.class).size());

        getUserReference().child("listId").setValue(mainUserListId);

        waitBackgroundTasks();

        assertEquals(1, loadSyncRecords(Category.class).size());
        assertCategoryEquals((Category) findClientEntity(serverCategory), serverCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListBulkSyncAddServerProduct() {
        final DataSnapshot serverProduct =
                addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final String mainUserListId = getListIdFromEntity(serverProduct.getRef());

        setUnitTestFirebaseUser("tempUser");
        authenticateAndWaitSynchronizer();

        assertEquals(0, loadSyncRecords(Product.class).size());

        getUserReference().child("listId").setValue(mainUserListId);

        waitBackgroundTasks();

        assertEquals(1, loadSyncRecords(Product.class).size());
        assertProductEquals((Product) findClientEntity(serverProduct), serverProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListBulkSyncAddServerShopItem() {
        final DataSnapshot serverProduct =
                addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final DataSnapshot serverShopItem =
                addServerShopItem(serverProduct, BigDecimal.ONE, "test comment", LITER);
        final String mainUserListId = getListIdFromEntity(serverShopItem.getRef());

        setUnitTestFirebaseUser("tempUser");
        authenticateAndWaitSynchronizer();

        assertEquals(0, loadSyncRecords(ShopItem.class).size());

        getUserReference().child("listId").setValue(mainUserListId);

        waitBackgroundTasks();

        assertEquals(1, loadSyncRecords(ShopItem.class).size());
        assertShopItemEquals((ShopItem) findClientEntity(serverShopItem), serverShopItem);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListSyncChangeClientCategory() {
        final Category originalCategory = addCategory("category1", 12345);

        authenticateAndWaitSynchronizer();

        final String oldListId = getOrCreateListId();
        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final Category changedCategory = dao.findCategory(originalCategory.getId());
        changedCategory.setName("newName");
        changedCategory.setColor(111);
        dao.saveCategory(changedCategory);

        waitBackgroundTasks();

        assertCategoryEquals(changedCategory, findServerEntity(changedCategory));

        final DataSnapshot oldListCategory = firebaseMocker
                .getDataSnapshot("lists")
                .child(oldListId)
                .child("categories")
                .getChildren().iterator().next();
        assertCategoryEquals(originalCategory, oldListCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListSyncChangeClientProduct() {
        final Product originalProduct =
                addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);

        authenticateAndWaitSynchronizer();

        final String oldListId = getOrCreateListId();
        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final Product changedProduct = dao.findProduct(originalProduct.getId());
        changedProduct.setName("newName");
        changedProduct.setLastBuyDate(new Date());
        changedProduct.setPeriodCount(5);
        changedProduct.setPeriodType(WEEKS);
        changedProduct.setDefaultUnits(GRAM);
        dao.saveProduct(changedProduct);

        waitBackgroundTasks();

        assertProductEquals(changedProduct, findServerEntity(changedProduct));

        final DataSnapshot oldListProduct = firebaseMocker
                .getDataSnapshot("lists")
                .child(oldListId)
                .child("products")
                .getChildren().iterator().next();
        assertProductEquals(originalProduct, oldListProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListSyncChangeClientShopItem() {
        final Product product = addProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final ShopItem originalShopItem =
                addShopItem(product, BigDecimal.ONE, "test comment", LITER);

        authenticateAndWaitSynchronizer();

        final String oldListId = getOrCreateListId();
        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final ShopItem changedShopItem = dao.findShopItem(originalShopItem.getId());
        changedShopItem.setChecked(true);
        changedShopItem.setUnitOfMeasure(PACK);
        changedShopItem.setComment("new");
        changedShopItem.setQuantity(new BigDecimal(5));
        dao.saveShopItem(changedShopItem);

        waitBackgroundTasks();

        assertShopItemEquals(changedShopItem, findServerEntity(changedShopItem));

        final DataSnapshot oldListShopItem = firebaseMocker
                .getDataSnapshot("lists")
                .child(oldListId)
                .child("shopItems")
                .getChildren().iterator().next();
        assertShopItemEquals(originalShopItem, oldListShopItem);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListSyncChangeServerCategory() {
        final DataSnapshot originalServerCategory = addServerCategory("category1", 12345);

        authenticateAndWaitSynchronizer();

        final String oldListId = getOrCreateListId();
        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final DataSnapshot newListCategory = firebaseMocker
                .getDataSnapshot("lists")
                .child(oldListId)
                .child("categories")
                .getChildren().iterator().next();
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>(
                getChildrenValues(newListCategory));
        values.put("name", "newName");
        values.put("color", 111);
        newListCategory.getRef().updateChildren(values);

        waitBackgroundTasks();

        assertCategoryEquals((Category) findClientEntity(newListCategory),
                newListCategory);
        assertCategoryEquals((Category) findClientEntity(originalServerCategory),
                originalServerCategory);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListSyncChangeServerProduct() {
        final DataSnapshot originalServerProduct =
                addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);

        authenticateAndWaitSynchronizer();

        final String oldListId = getOrCreateListId();
        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final DataSnapshot newListProduct = firebaseMocker
                .getDataSnapshot("lists")
                .child(oldListId)
                .child("products")
                .getChildren().iterator().next();
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>(
                getChildrenValues(newListProduct));
        values.put("name", "newName");
        values.put("lastBuyDate", toFirebaseDate(new Date()));
        values.put("periodCount", toFirebaseBigDecimal(5));
        values.put("periodType", toFirebaseEnum(WEEKS));
        values.put("defaultUnits", toFirebaseEnum(GRAM));
        newListProduct.getRef().updateChildren(values);

        waitBackgroundTasks();

        assertProductEquals((Product) findClientEntity(newListProduct),
                newListProduct);
        assertProductEquals((Product) findClientEntity(originalServerProduct),
                originalServerProduct);
    }

    @Test(timeout = DEFAULT_TIMEOUT)
    public void testChangeListSyncChangeServerShopItem() {
        final DataSnapshot serverProduct = addServerProduct("product1", UnitOfMeasure.BOTTLE, 3, PeriodType.DAYS);
        final DataSnapshot originalServerShopItem =
                addServerShopItem(serverProduct, BigDecimal.ONE, "test comment", LITER);

        authenticateAndWaitSynchronizer();

        final String oldListId = getOrCreateListId();
        getUserReference().child("listId").setValue("newList");

        waitBackgroundTasks();

        final DataSnapshot newListShopItem = firebaseMocker
                .getDataSnapshot("lists")
                .child(oldListId)
                .child("shopItems")
                .getChildren().iterator().next();
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>(
                getChildrenValues(newListShopItem));
        values.put("checked", true);
        values.put("unitOfMeasure", toFirebaseEnum(PACK));
        values.put("comment", "new");
        values.put("quantity", toFirebaseBigDecimal(5));
        newListShopItem.getRef().updateChildren(values);

        waitBackgroundTasks();

        assertShopItemEquals((ShopItem) findClientEntity(newListShopItem),
                newListShopItem);
        assertShopItemEquals((ShopItem) findClientEntity(originalServerShopItem),
                originalServerShopItem);
    }

    @Test
    public void testBulkSync2ServerEntitiesWithSameNaturalId() {
        addServerCategory("CategoryX", 100);
        addServerCategory("CategoryX", 3);

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());
        final Category clientCategory = categories.get(0);
        assertEquals(clientCategory.getName(), "CategoryX");
        assertTrue("Incorrect category color: " + clientCategory.getColor(),
                   (clientCategory.getColor() == 3) || (clientCategory.getColor() == 100));

        final List<EntitySynchronizationRecord<Category>> records = loadSyncRecords(Category.class);
        assertEquals(1, records.size());

        assertAndRemoveWarning("found Category with the same natural id", 2);
    }

    @Test
    public void testBulkSync3ServerEntitiesWithSameNaturalId() {
        addServerCategory("CategoryX", 100);
        addServerCategory("CategoryX", 3);
        addServerCategory("CategoryX", 999);

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());
        final Category clientCategory = categories.get(0);
        assertEquals(clientCategory.getName(), "CategoryX");

        final List<EntitySynchronizationRecord<Category>> records = loadSyncRecords(Category.class);
        assertEquals(1, records.size());

        assertAndRemoveWarning("found Category with the same natural id", 4);
    }

    @Test
    public void testBulkSync2ServerEntitiesAnd1ClientEntityWithSameNaturalId() {
        addServerCategory("CategoryX", 100);
        addServerCategory("CategoryX", 3);

        addCategory("CategoryX", 5);

        authenticateAndWaitSynchronizer();

        final List<Category> categories = dao.getCategories();
        assertEquals(1, categories.size());

        final List<EntitySynchronizationRecord<Category>> records = loadSyncRecords(Category.class);
        assertEquals(1, records.size());

        assertAndRemoveWarning("found Category with the same natural id", 2);
    }

    private DataSnapshot toDataSnapshot(DatabaseReference serverEntity) {
        return getEntityListSnapshot(serverEntity.getParent().getKey()).child(serverEntity.getKey());
    }

    private void assertAndRemoveWarning(String expectedWarningText) {
        assertAndRemoveWarning(expectedWarningText, 1);
    }

    private void assertAndRemoveWarning(String expectedWarningText, int expectedCound) {
        assertFalse(logWarnings.isEmpty());

        int foundOccurrences = 0;

        for (final String warning : ImmutableList.copyOf(logWarnings)) {
            if (warning.contains(expectedWarningText)) {
                logWarnings.remove(warning);

                foundOccurrences++;
                if (foundOccurrences >= expectedCound) {
                    return;
                }
            }
        }

        if (foundOccurrences == 0) {
            fail("Warning not found: " + expectedWarningText);
        } else {
            fail(String.format("Less warnings found (%d) than expected (%d): %s",
                               foundOccurrences, expectedCound, expectedWarningText));
        }
    }

    private <T extends Entity> DataSnapshot findServerEntity(T entity) {
        final String listName;
        if (entity instanceof Category) {
            listName = "categories";
        } else if (entity instanceof Product) {
            listName = "products";
        } else if (entity instanceof ShopItem) {
            listName = "shopItems";
        } else {
            throw new IllegalStateException("Unsupported class " + entity.getClass());
        }
        final DataSnapshot entitiesList = getEntityListSnapshot(listName);

        final EntitySynchronizationRecord<T> record = findSyncRecord(entity);

        if (record == null) {
            return null;
        }

        return entitiesList.child(record.getExternalId());
    }

    private DataSnapshot getUpdatedSnapshot(DataSnapshot snapshot) {
        final DataSnapshot entitiesList = getEntityListSnapshot(
                snapshot.getRef().getParent().getKey());

        return entitiesList.child(snapshot.getKey());
    }


    @SuppressWarnings("unchecked")
    private <T extends Entity> T findClientEntity(DataSnapshot serverEntity) {
        final EntitySynchronizationRecord<Entity> syncRecord = findSyncRecord(serverEntity);

        if (syncRecord == null) {
            return null;
        }

        if (syncRecord.getEntityClass().isAssignableFrom(Category.class)) {
            return (T) dao.findCategory(syncRecord.getInternalId());
        } else if (syncRecord.getEntityClass().isAssignableFrom(Product.class)) {
            return (T) dao.findProduct(syncRecord.getInternalId());
        } else if (syncRecord.getEntityClass().isAssignableFrom(ShopItem.class)) {
            return (T) dao.findShopItem(syncRecord.getInternalId());
        }

        throw new IllegalStateException("Unsupported class " + syncRecord.getEntityClass());
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> EntitySynchronizationRecord<T> findSyncRecord(T entity) {
        final Class<T> entityClazz = (Class<T>) entity.getClass();
        final String listId = getAllListsReference().getKey();

        return dao.findSynchronizationRecord(
                entity.getId(), listId, entityClazz);
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> EntitySynchronizationRecord<T> findSyncRecord(DataSnapshot serverEntity) {
        final DatabaseReference parent = serverEntity.getRef().getParent();
        Class<T> clazz;
        if (parent.getKey().equals("categories")) {
            clazz = (Class<T>) Category.class;
        } else if (parent.getKey().equals("products")) {
            clazz = (Class<T>) Product.class;
        } else if (parent.getKey().equals("shopItems")) {
            clazz = (Class<T>) ShopItem.class;
        } else {
            throw new IllegalStateException("Unknown list " + parent.getKey());
        }

        final String listId = getListIdFromEntity(serverEntity.getRef());

        return dao.findSynchronizationByExternalId(serverEntity.getKey(), listId, clazz);
    }


    private DataSnapshot getEntityListSnapshot(String listKey) {
        DataSnapshot lists = getAllListsSnapshot();

        final DataSnapshot listNode = lists.child(listKey);
        assertNotNull(listKey + " should not be null", listNode);
        return listNode;
    }

    private DatabaseReference getEntityListReference(String listKey) {
        DatabaseReference lists = getAllListsReference();

        final DatabaseReference listNode = lists.child(listKey);
        assertNotNull(listKey + " should not be null", listNode);
        return listNode;
    }

    private void assertCategoryEquals(Category category, DataSnapshot snapshot) {
        assertNotNull(category);
        assertNotNull(snapshot);

        assertEquals(category.getName(), getString(snapshot, "name"));
        assertEquals(category.getColor(), snapshot.child("color").getValue(Integer.class));
        assertEquals(category.getName().toLowerCase(), getString(snapshot, "naturalId"));

        Set<String> expectedChildren = ImmutableSet.of(
                "name", "naturalId", "color", "lastChangeDate");
        for (DataSnapshot child : snapshot.getChildren()) {
            final String key = child.getKey();
            assertTrue(key + " key not found", expectedChildren.contains(key));
        }
    }

    private void assertProductEquals(Product product, DataSnapshot snapshot) {
        assertNotNull(product);
        assertNotNull(snapshot);

        assertEquals(product.getName(), getString(snapshot, "name"));
        assertEquals(product.getName().toLowerCase(), getString(snapshot, "naturalId"));
        assertEquals(product.getPeriodCount(), snapshot.child("periodCount").getValue(Integer.class));
        assertEquals(product.getPeriodType(),
                getEnumValue(snapshot.child("periodType"), PeriodType.class));
        assertEquals(product.getDefaultUnits(),
                getEnumValue(snapshot.child("defaultUnits"), UnitOfMeasure.class));
        assertEquals(product.getLastBuyDate(), getDateValue(snapshot.child("lastBuyDate")));

        final DataSnapshot serverCategories = snapshot.child("categories");
        if (!isEmpty(product.getCategories()) || (serverCategories.exists())) {
            assertFalse(isEmpty(product.getCategories()));
            assertTrue(serverCategories.exists());

            GenericTypeIndicator<List<String>> typeIndicator = new GenericTypeIndicator<List<String>>() {
            };
            final List<String> serverCategoryIds = serverCategories.getValue(typeIndicator);
            assertNotNull(serverCategoryIds);
            assertEquals(product.getCategories().size(), serverCategoryIds.size());

            final String listId = getListIdFromEntity(snapshot.getRef());

            for (Category category : product.getCategories()) {
                final EntitySynchronizationRecord<? extends Category> categoryRecord =
                        dao.findSynchronizationRecord(category.getId(), listId, category.getClass());
                assertNotNull("Record for category '" + category + "' not found", categoryRecord);

                assertTrue("Category '" + category + "' not found in server list",
                        serverCategoryIds.contains(categoryRecord.getExternalId()));
            }
        }

        Set<String> expectedChildren = ImmutableSet.of("name", "naturalId",
                "periodCount", "periodType",
                "defaultUnits", "lastBuyDate", "categories", "lastChangeDate");
        for (DataSnapshot child : snapshot.getChildren()) {
            final String key = child.getKey();
            assertTrue(key + " key not found", expectedChildren.contains(key));
        }
    }

    private void assertShopItemEquals(ShopItem shopItem, DataSnapshot snapshot) {
        assertNotNull(shopItem);
        assertNotNull(snapshot);

        assertEquals(shopItem.getComment(), getString(snapshot, "comment"));
        assertEquals(shopItem.getQuantity(), getBigDecimal(snapshot.child("quantity")));

        if (!snapshot.child("checked").exists()) {
            assertFalse(shopItem.isChecked());
        } else {
            assertEquals(shopItem.isChecked(), snapshot.child("checked").getValue(Boolean.class));
        }

        assertEquals(shopItem.getUnitOfMeasure(),
                getEnum(snapshot.child("unitOfMeasure"), UnitOfMeasure.class));

        final String serverProductId = getString(snapshot, "product");
        if ((serverProductId != null) || (shopItem.getProduct() != null)) {
            assertNotNull(serverProductId);
            assertNotNull(shopItem.getProduct());

            final String listId = getListIdFromEntity(snapshot.getRef());
            final EntitySynchronizationRecord<Product> productRecord =
                    dao.findSynchronizationRecord(shopItem.getProduct().getId(), listId, Product.class);
            assertNotNull("Record for product '" + shopItem.getProduct() + "' not found", productRecord);

            assertEquals(serverProductId, productRecord.getExternalId());
            assertEquals(
                    shopItem.getProduct().getName().toLowerCase(),
                    getString(snapshot, "naturalId"));
        }

        Set<String> expectedChildren = ImmutableSet.of("comment", "quantity", "checked",
                "unitOfMeasure", "product", "naturalId", "lastChangeDate");
        for (DataSnapshot child : snapshot.getChildren()) {
            final String key = child.getKey();
            assertTrue(key + " key not found", expectedChildren.contains(key));
        }
    }

    private DataSnapshot getAllListsSnapshot() {
        final String listId = getOrCreateListId();

        return firebaseMocker.getDataSnapshot("lists").child(listId);
    }

    private DatabaseReference getAllListsReference() {
        final String listId = getOrCreateListId();

        return firebaseDatabase.getReference("lists").child(listId);
    }

    private Category addCategory(String categoryName, Integer categoryColor) {
        final Category category = new Category();
        category.setName(categoryName);
        category.setColor(categoryColor);
        dao.addCategory(category);

        return category;
    }

    private DataSnapshot addServerCategory(String categoryName, Integer categoryColor) {
        final DatabaseReference categories = getEntityListReference("categories");

        final DatabaseReference categorySnapshot = categories.push();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", categoryName);
        values.put("naturalId", categoryName.toLowerCase());
        values.put("color", categoryColor);
        values.put("lastChangeDate", toFirebaseDate(new Date()));
        categorySnapshot.updateChildren(values);

        return toDataSnapshot(categorySnapshot);
    }

    private Product addProduct(String name, UnitOfMeasure unitOfMeasure, Integer periodCount, PeriodType periodType) {
        final Product product = new Product();
        product.setName(name);
        product.setDefaultUnits(unitOfMeasure);
        product.setPeriodCount(periodCount);
        product.setPeriodType(periodType);

        dao.addProduct(product);

        return product;
    }

    private DataSnapshot addServerProduct(
            String name, UnitOfMeasure unitOfMeasure, Integer periodCount, PeriodType periodType, String... categoryIds) {

        final DatabaseReference products = getEntityListReference("products");

        final DatabaseReference productReference = products.push();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("naturalId", name.toLowerCase());
        values.put("defaultUnits", toFirebaseEnum(unitOfMeasure));
        values.put("periodCount", periodCount);
        values.put("periodType", toFirebaseEnum(periodType));
        values.put("lastBuyDate", null);
        values.put("lastChangeDate", toFirebaseDate(new Date()));

        if (categoryIds.length > 0) {
            values.put("categories", Arrays.asList(categoryIds));
        }

        productReference.updateChildren(values);

        return toDataSnapshot(productReference);
    }

    private DataSnapshot addServerShopItem(
            DataSnapshot serverProduct,
            BigDecimal quantity,
            String comment,
            UnitOfMeasure unitOfMeasure) {

        final DatabaseReference shopItems = getEntityListReference("shopItems");

        final DatabaseReference shopItemReference = shopItems.push();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("product", serverProduct.getKey());
        values.put("naturalId", getString(serverProduct, "naturalId"));
        values.put("quantity", toFirebaseBigDecimal(quantity));
        values.put("comment", comment);
        values.put("unitOfMeasure", toFirebaseEnum(unitOfMeasure));
        values.put("lastChangeDate", toFirebaseDate(new Date()));

        shopItemReference.updateChildren(values);

        return toDataSnapshot(shopItemReference);
    }

    private ShopItem addShopItem(
            @NonNull Product product, BigDecimal quantity, String comment, UnitOfMeasure unitOfMeasure) {

        final ShopItem shopItem = new ShopItem();
        shopItem.setProduct(product);
        shopItem.setQuantity(quantity);
        shopItem.setComment(comment);
        shopItem.setUnitOfMeasure(unitOfMeasure);

        dao.addShopItem(shopItem);

        return shopItem;
    }


    private void mockLogs() {
        PowerMockito.mockStatic(Log.class);
        PowerMockito.when(Log.i(anyString(), anyString())).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                final Object tag = invocationOnMock.getArgument(0);
                final Object message = invocationOnMock.getArgument(1);

                log("INFO", tag, message);

                return null;
            }
        });

        if (DEBUG_LOGGING_ENABLED) {
            PowerMockito.when(Log.d(anyString(), anyString())).then(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final Object tag = invocationOnMock.getArgument(0);
                    final Object message = invocationOnMock.getArgument(1);

                    log("DEBUG", tag, message);

                    return null;
                }
            });
        }

        mockFailOnWarning();
        mockFailOnError();
    }

    private void log(final String level, Object tag, Object message) {
        System.out.println(level + " " + tag + ": " + message);
    }

    private void logError(final String level, Object tag, Object message) {
        System.err.println(level + " " + tag + ": " + message);
    }

    private void logError(final String level, Object tag, Object message, Throwable throwable) {
        String logStatement = level + " " + tag + ": " + message + "" +
                ". " + throwable.getClass();
        if (!Strings.isNullOrEmpty(throwable.getMessage())) {
            logStatement += ":" + throwable.getMessage();
        }

        System.err.println(logStatement);
        throwable.printStackTrace();
    }

    private void mockFirebaseDatabase() {
        firebaseMocker = new FirebaseDatabaseMocker();
        firebaseDatabase = firebaseMocker.getFirebaseDatabase();
    }

    private void mockFailOnWarning() {
        final Answer<Integer> warningHandlerAnswer = new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                final String tag = invocationOnMock.getArgument(0);

                final String message;
                final Throwable throwable;
                if (invocationOnMock.getArguments().length == 2) {
                    if (invocationOnMock.getArgument(1) instanceof Throwable) {
                        message = "";
                        throwable = invocationOnMock.getArgument(1);
                    } else {
                        message = invocationOnMock.getArgument(1);
                        throwable = null;
                    }
                } else {
                    message = invocationOnMock.getArgument(1);
                    throwable = invocationOnMock.getArgument(2);
                }

                if (throwable != null) {
                    logError("WARN", tag, message, throwable);
                } else {
                    logError("WARN", tag, message);
                }

                logWarnings.add(Strings.isNullOrEmpty(message) ? tag : message);

                return -1;
            }
        };

        PowerMockito.when(Log.w(anyString(), anyString())).then(warningHandlerAnswer);
        PowerMockito.when(Log.w(anyString(), anyString(), Mockito.any(Throwable.class))).then(warningHandlerAnswer);
        PowerMockito.when(Log.w(anyString(), Mockito.any(Throwable.class))).then(warningHandlerAnswer);
    }

    private void mockFailOnError() {
        PowerMockito.when(Log.e(anyString(), anyString())).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                final String message = invocationOnMock.getArgument(1);
                logError("ERROR", invocationOnMock.getArgument(0), message);
                logErrors.add(message);
                return null;
            }
        });

        PowerMockito.when(Log.e(anyString(), anyString(), Mockito.any(Throwable.class))).then(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                final String message = invocationOnMock.getArgument(1);
                logError("ERROR",
                        invocationOnMock.getArgument(0),
                        message,
                        (Throwable) invocationOnMock.getArgument(2));
                logErrors.add(message);
                return null;
            }
        });
    }

    private FirebaseAuth mockFirebaseAuth() {
        firebaseAuth = Mockito.mock(FirebaseAuth.class);

        Mockito.when(firebaseAuth.getCurrentUser()).then(new Answer<FirebaseUser>() {
            @Override
            public FirebaseUser answer(InvocationOnMock invocationOnMock) throws Throwable {
                return synchronizerUserReference.get();
            }
        });

        Mockito.doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                FirebaseAuth.AuthStateListener listener = invocationOnMock.getArgument(0);
                authListeners.add(listener);

                if (firebaseAuth.getCurrentUser() != null) {
                    listener.onAuthStateChanged(firebaseAuth);
                }

                return null;
            }
        }).when(firebaseAuth).addAuthStateListener(Mockito.any(FirebaseAuth.AuthStateListener.class));

        Mockito.doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                FirebaseAuth.AuthStateListener listener = invocationOnMock.getArgument(0);
                authListeners.remove(listener);
                return null;
            }
        }).when(firebaseAuth).removeAuthStateListener(Mockito.any(FirebaseAuth.AuthStateListener.class));

        return firebaseAuth;
    }

    private void setUnitTestFirebaseUser(String userName) {
        if (userName == null) {
            unitTestUserReference.set(null);
        } else {
            final FirebaseUser user = Mockito.mock(FirebaseUser.class);
            Mockito.when(user.getUid()).thenReturn(userName);
            Mockito.when(user.getDisplayName()).thenReturn(userName);
            Mockito.when(user.isEmailVerified()).thenReturn(true);

            unitTestUserReference.set(user);
        }
    }

    private void authenticate() {
        authenticate((unitTestUserReference.get() != null)
                ? unitTestUserReference.get().getDisplayName()
                : null);
    }

    private void disconnect() {
        authenticate(null);
    }

    private void authenticate(String userName) {
        final String oldUsername = synchronizerUserReference.get() != null
                ? synchronizerUserReference.get().getDisplayName()
                : null;

        if (Objects.equal(oldUsername, userName)) {
            return;
        }

        if (userName == null) {
            synchronizerUserReference.set(null);
        } else {
            final FirebaseUser user = Mockito.mock(FirebaseUser.class);
            Mockito.when(user.getUid()).thenReturn(userName);
            Mockito.when(user.getDisplayName()).thenReturn(userName);
            Mockito.when(user.isEmailVerified()).thenReturn(true);

            synchronizerUserReference.set(user);
        }

        for (FirebaseAuth.AuthStateListener authListener : authListeners) {
            authListener.onAuthStateChanged(firebaseAuth);
        }
    }

    private String getOrCreateListId() {
        final FirebaseUser user = unitTestUserReference.get();
        if (user == null) {
            throw new IllegalStateException("unitTestUserReference id is not set");
        }

        final DataSnapshot users = firebaseMocker.getDataSnapshot("users");
        String listId;
        if ((users != null) && (users.child(user.getUid()) != null)) {
            final DataSnapshot listIdNode = users.child(user.getUid()).child("listId");
            listId = listIdNode.getValue(String.class);
        } else {
            listId = null;
        }

        if (listId == null) {
            final DatabaseReference newNode = firebaseDatabase.getReference("lists").push();
            listId = newNode.getKey();
            firebaseDatabase.getReference("users").child(user.getUid()).child("listId").setValue(listId);
        }
        return listId;
    }

    private DatabaseReference getUserReference() {
        final DataSnapshot users = firebaseMocker.getDataSnapshot("users");
        return users.getRef().child(unitTestUserReference.get().getUid());
    }

    private void waitBackgroundTasks() {
        firebaseMocker.waitBackgroundTasks();
        firebaseSynchronizer.waitBackgroundTasks();
    }

    private void authenticateAndWaitSynchronizer() {
        authenticate();
        firebaseSynchronizer.waitState(SUBSCRIBED, 2000);

        waitBackgroundTasks();
    }

    private <T extends Entity> List<EntitySynchronizationRecord<T>> loadSyncRecords(Class<T> clazz) {
        final String listId = getAllListsReference().getKey();
        return dao.loadSynchronizationRecords(clazz, listId);
    }

    private String getString(DataSnapshot entity, String value) {
        return entity.child(value).getValue(String.class);
    }

    private Boolean getBoolean(DataSnapshot entity, String value) {
        return entity.child(value).getValue(Boolean.class);
    }

    private Integer getInteger(DataSnapshot entity, String value) {
        return entity.child(value).getValue(Integer.class);
    }

    private Date getDate(DataSnapshot entity, String value) {
        final DataSnapshot valueNode = entity.child(value);
        if (!valueNode.exists()) {
            return null;
        }
        final Long millis = valueNode.getValue(Long.class);
        //noinspection ConstantConditions
        return new Date(millis);
    }

    @SuppressWarnings("unchecked")
    private <T> T getValue(DataSnapshot serverCategory, String value) {
        return (T) serverCategory.child(value).getValue(Object.class);
    }

    private static <T extends Enum<T>> T getEnum(DataSnapshot snapshot, Class<T> clazz) {
        if ((snapshot == null) || (!snapshot.exists())) {
            return null;
        }

        final String name = snapshot.getValue(String.class);
        for (final T constant : clazz.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }

        throw new IllegalStateException("Incorrect enum " + name);
    }

    private static BigDecimal getBigDecimal(DataSnapshot snapshot) {
        if ((snapshot == null) || (!snapshot.exists())) {
            return null;
        }

        final String value = snapshot.getValue(String.class);
        return new BigDecimal(value);
    }

    private static Object toFirebaseDate(Date date) {
        if (date == null) {
            return null;
        }

        return date.getTime();
    }

    private static <T extends Enum<T>> Object toFirebaseEnum(T value) {
        if (value == null) {
            return null;
        }

        return value.name();
    }

    private static Object toFirebaseBigDecimal(Integer value) {
        if (value == null) {
            return null;
        }

        return toFirebaseBigDecimal(new BigDecimal(value));
    }

    private static Object toFirebaseBigDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    private class TestFailureCallback implements FirebaseSynchronizer.FailureCallback {
        @Override
        public void synchronizationStartFailed(Exception exception) {
            uncaughtExceptions.add(exception);
        }

        @Override
        public void updateServerEntityFailed(Entity entity, Exception exception) {
            uncaughtExceptions.add(exception);
        }

        @Override
        public void serverNotificationFailed(Exception exception) {
            uncaughtExceptions.add(exception);
        }
    }
}