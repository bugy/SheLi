package net.buggy.shoplist;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.components.list.MenuCellFactory;
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.SqlliteDao;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Defaults;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.Language;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.Settings;
import net.buggy.shoplist.navigation.AboutAppUnitNavigator;
import net.buggy.shoplist.navigation.CategoriesListUnitNavigator;
import net.buggy.shoplist.navigation.ProductUnitNavigator;
import net.buggy.shoplist.navigation.SettingsUnitNavigator;
import net.buggy.shoplist.navigation.SharingUnitNavigator;
import net.buggy.shoplist.navigation.ShopListUnitNavigator;
import net.buggy.shoplist.navigation.UnitNavigator;
import net.buggy.shoplist.sharing.FirebaseSynchronizer;
import net.buggy.shoplist.units.Unit;
import net.buggy.shoplist.units.UnitHost;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

import static net.buggy.shoplist.model.ModelHelper.normalizeName;

public class ShopListActivity extends AppCompatActivity implements UnitHost {

    public static final String DEVELOPER_EMAIL = "buggygm@gmail.com";

    public static final int MAIN_VIEW_ID = R.id.main_activity_view;
    public static final int TOOLBAR_VIEW_ID = R.id.toolbar_container;
    public static final int OVERLAY_VIEW_ID = R.id.activity_overlay_view;

    private final List<UnitNavigator<ShopListActivity>> navigators;

    private SqlliteDao dao;

    private FirebaseSynchronizer synchronizer;

    private final Deque<UnitDescriptor> activeUnits = new LinkedList<>();

    private final Map<Integer, List<Unit>> capturedViewOwners = new ConcurrentHashMap<>();

    // clear listeners on unit stop
    private final Map<Integer, Unit> anotherActivityListeners = new ConcurrentHashMap<>();

    private DrawerLayout menuLayout;
    private RecyclerView menuList;
    private ViewGroup menuPanel;
    private ImageButton menuButton;

    private volatile String currentNavigatorName;

    private final Multimap<View, OutsideClickListener> outsideClickListeners =
            Multimaps.synchronizedSetMultimap(LinkedHashMultimap.<View, OutsideClickListener>create());

    private boolean firstLaunch = false;

    public ShopListActivity() {
        navigators = ImmutableList.of(
                new ShopListUnitNavigator(this),
                new ProductUnitNavigator(this),
                new CategoriesListUnitNavigator(this),
                new SettingsUnitNavigator(this),
                new SharingUnitNavigator(this),
                new AboutAppUnitNavigator(this)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ViewUtils.addFont(getApplicationContext(), "decorated", "fonts/MarckScript-Regular.ttf");
        ViewUtils.addFont(getApplicationContext(), "main", "fonts/Neucha.ttf");

        setContentView(R.layout.activity_shop_list);

        if (getDao().isFirstLaunch()) {
            askForInitialData();
        }

        synchronizer = new FirebaseSynchronizer(dao, new FirebaseSynchronizer.FailureCallback() {
            @Override
            public void synchronizationStartFailed(final Exception exception) {
                if (BuildConfig.DEBUG) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            throw new RuntimeException(exception.getMessage(), exception);
                        }
                    });
                } else {
                    Toast.makeText(
                            ShopListActivity.this,
                            R.string.shop_activity_synchronization_start_failed,
                            Toast.LENGTH_LONG)
                            .show();
                }
            }

            @Override
            public void updateServerEntityFailed(Entity entity, final Exception exception) {
                if (BuildConfig.DEBUG) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            throw new RuntimeException(exception.getMessage(), exception);
                        }
                    });
                }
            }

            @Override
            public void serverNotificationFailed(final Exception exception) {
                if (BuildConfig.DEBUG) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            throw new RuntimeException(exception.getMessage(), exception);
                        }
                    });
                }
            }
        });
        synchronizer.start();

        if (savedInstanceState == null) {

            final UnitNavigator<ShopListActivity> navigator = navigators.get(0);
            currentNavigatorName = navigator.getText();
            navigator.navigate();

        } else {
            restoreState(savedInstanceState);

            restartUnits();
        }

        initMenu();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        synchronizer.stop();
    }

    private void fillInitialData() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMax(100);
        progressDialog.setMessage(this.getString(R.string.shop_list_activity_init_data_message));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgressNumberFormat(null);

        final AsyncTask<String, Integer, Object> fillDataTask =
                new AsyncTask<String, Integer, Object>() {
                    @Override
                    protected Object doInBackground(String... params) {
                        final List<Category> defaultCategories = Defaults.createDefaultCategories(ShopListActivity.this);
                        Map<String, Category> existingCategoriesMap = new LinkedHashMap<>();
                        final List<Category> existingCategories = getDao().getCategories();
                        for (Category category : existingCategories) {
                            existingCategoriesMap.put(category.getName().toLowerCase(), category);
                        }

                        float progress = 2f;
                        publishProgress((int) progress);

                        float categoryStepProgress = 8f / defaultCategories.size();


                        for (Category category : defaultCategories) {
                            if (!existingCategoriesMap.containsKey(category.getName().toLowerCase())) {
                                getDao().addCategory(category);
                            }

                            progress += categoryStepProgress;
                            publishProgress((int) progress);
                        }


                        final List<Category> actualCategories = getDao().getCategories();
                        final List<Product> defaultProducts =
                                Defaults.createDefaultProducts(ShopListActivity.this, actualCategories);

                        Map<String, Product> existingProductsMap = new LinkedHashMap<>();
                        final List<Product> existingProducts = getDao().getProducts();
                        for (Product product : existingProducts) {
                            existingProductsMap.put(normalizeName(product.getName()), product);
                        }

                        progress += 2f;
                        publishProgress((int) progress);

                        float productStepProgress = (100f - progress) / defaultProducts.size();

                        for (Product defaultProduct : defaultProducts) {
                            if (!existingProductsMap.containsKey(defaultProduct.getName().toLowerCase())) {
                                getDao().addProduct(defaultProduct);
                            }

                            progress += productStepProgress;
                            publishProgress((int) progress);
                        }

                        getDao().clearFirstLaunch();

                        return null;
                    }

                    @Override
                    protected void onProgressUpdate(Integer... values) {
                        int progress = values[values.length - 1];
                        progressDialog.setProgress(progress);
                    }

                    @Override
                    protected void onPostExecute(Object o) {
                        progressDialog.hide();

                        recreate();
                    }
                };

        fillDataTask.execute();
        progressDialog.show();
    }

    private void askForInitialData() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.shop_list_activity_init_data_confirm_title)
                .setMessage(R.string.shop_list_activity_init_data_confirm_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        fillInitialData();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dao.clearFirstLaunch();
                    }
                })
                .show();
    }

    @Override
    protected void attachBaseContext(Context newContext) {
        final Settings settings = getDao().getSettings();
        final Language language = settings.getLanguage();

        final Locale locale;
        if (language != null) {
            locale = new Locale(language.getLocale());
        } else {
            locale = ViewUtils.getSystemLocale();
        }
        Locale.setDefault(locale);
        newContext = ViewUtils.wrap(newContext, locale);

        super.attachBaseContext(CalligraphyContextWrapper.wrap(newContext));
    }

    private void initMenu() {
        menuButton = (ImageButton) findViewById(R.id.toolbar_menu_button);
        menuLayout = (DrawerLayout) findViewById(R.id.main_activity_menu_layout);
        menuList = (RecyclerView) findViewById(R.id.main_activity_menu_list);
        menuPanel = (ViewGroup) findViewById(R.id.main_activity_menu_panel);

        final MenuCellFactory menuCellFactory = new MenuCellFactory();
        final FactoryBasedAdapter<MenuCellFactory.Item> menuAdapter = new FactoryBasedAdapter<>(
                menuCellFactory);
        menuAdapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.SINGLE);
        menuAdapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<MenuCellFactory.Item>() {
            @Override
            public void selectionChanged(MenuCellFactory.Item item, boolean selected) {
                if (selected) {
                    menuAdapter.deselectItem(item);
                    menuLayout.closeDrawers();

                    UnitNavigator foundNavigator = null;
                    for (final UnitNavigator<ShopListActivity> navigator : navigators) {
                        if (navigator.getText().equals(item.getText())) {
                            foundNavigator = navigator;

                            break;
                        }
                    }

                    if (foundNavigator == null) {
                        throw new IllegalStateException("Couldn't found navigator for " + item.getText());
                    }

                    clearOldUnits(foundNavigator.getText());

                    final UnitNavigator navigator = foundNavigator;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            currentNavigatorName = navigator.getText();
                            navigator.navigate();
                        }
                    });
                }
            }
        });

        for (UnitNavigator<ShopListActivity> navigator : navigators) {
            menuAdapter.add(createMenuItem(
                    navigator.getText(),
                    navigator.getIconDrawableId()));
        }

        menuList.setAdapter(menuAdapter);
        ListDecorator.decorateList(menuList);

        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isMenuOpened()) {
                    menuLayout.openDrawer(menuPanel);
                }
            }
        });
    }

    private void clearOldUnits(String newNavigatorName) {
        if (activeUnits.isEmpty()) {
            return;
        }

        final Iterator<UnitDescriptor> iterator = activeUnits.descendingIterator();
        final UnitDescriptor firstElement = iterator.next();
        String activeNavigatorName = firstElement.getNavigatorName();

        if (Objects.equal(activeNavigatorName, newNavigatorName)) {
            if (!iterator.hasNext()) {
                stopUnit(firstElement);
                return;
            }

            final UnitDescriptor secondElement = iterator.next();
            if (!Objects.equal(secondElement.getNavigatorName(), newNavigatorName)) {
                stopUnit(firstElement);
                return;
            }
        }

        boolean deleteOthers = false;
        List<UnitDescriptor> toDelete = new ArrayList<>();

        while (iterator.hasNext()) {
            final UnitDescriptor previousUnit = iterator.next();
            if (deleteOthers) {
                toDelete.add(previousUnit);
                continue;
            }

            if (!Objects.equal(previousUnit.getNavigatorName(), activeNavigatorName)) {
                deleteOthers = true;
                toDelete.add(previousUnit);
            }
        }

        for (UnitDescriptor unitDescriptor : toDelete) {
            stopUnit(unitDescriptor);
        }
    }

    private MenuCellFactory.Item createMenuItem(String text, int drawableId) {
        final Drawable drawable = ViewUtils.getDrawable(drawableId, this);
        drawable.setAlpha(160);

        return new MenuCellFactory.Item(text, drawable);
    }

    private void restoreState(Bundle bundle) {
        Preconditions.checkNotNull(bundle);
        List<UnitDescriptor> savedActiveUnits = (List<UnitDescriptor>) bundle.getSerializable("activeUnits");
        Preconditions.checkNotNull(savedActiveUnits);

        for (UnitDescriptor descriptor : savedActiveUnits) {
            descriptor.getUnit().setHostingActivity(this);
        }

        activeUnits.addAll(savedActiveUnits);
    }

    private void restartUnits() {
        final List<UnitDescriptor> units = ImmutableList.copyOf(activeUnits);
        for (UnitDescriptor descriptor : units) {
            descriptor.getUnit().start();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handleReturn = super.dispatchTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            View focusedView = getCurrentFocus();

            if (!outsideClickListeners.containsKey(focusedView)) {
                if (focusedView instanceof EditText) {
                    if (!ViewUtils.getLocationOnScreen(focusedView).contains(x, y)) {
                        focusedView.clearFocus();
                        ViewUtils.hideSoftKeyboard(focusedView);
                    }

                } else {
                    ViewUtils.hideSoftKeyboard(findViewById(R.id.main_activity_view));
                }
            }

            for (View view : outsideClickListeners.keySet()) {
                if (!ViewUtils.getLocationOnScreen(view).contains(x, y)) {
                    final Collection<OutsideClickListener> listeners =
                            ImmutableList.copyOf(this.outsideClickListeners.get(view));
                    for (OutsideClickListener listener : listeners) {
                        listener.clickedOutside(x, y);
                    }
                }
            }
        }

        return handleReturn;
    }

    @Override
    public void claimView(int viewId, Unit unit) {
        List<Unit> owners = capturedViewOwners.get(viewId);
        if (owners == null) {
            owners = new ArrayList<>();
            capturedViewOwners.put(viewId, owners);
        }

        Unit lastOwner = null;
        if (!owners.isEmpty()) {
            lastOwner = owners.get(owners.size() - 1);
        }

        if (!Objects.equal(lastOwner, unit)) {
            if (lastOwner != null) {
                lastOwner.viewReclaimed(viewId);
            }

            owners.add(unit);
        }
    }

    @Override
    public <T extends Unit> T findUnit(String unitTag) {
        final Iterator<UnitDescriptor> iterator = activeUnits.descendingIterator();

        while (iterator.hasNext()) {
            final UnitDescriptor next = iterator.next();
            final Unit unit = next.getUnit();

            if (unit.getTag().equals(unitTag)) {
                return (T) unit;
            }
        }

        return null;
    }

    @Override
    public void startAnotherActivity(Intent intent, int requestCode, Unit listener) {
        anotherActivityListeners.put(requestCode, listener);

        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final Unit listeningUnit = anotherActivityListeners.remove(requestCode);
        if (listeningUnit == null) {
            throw new IllegalStateException("Unknown reply from activity: " + requestCode);
        }

        listeningUnit.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable("activeUnits", ImmutableList.copyOf(activeUnits));
    }

    public Dao getDao() {
        if (dao == null) {
            initDao();
        }

        return dao;
    }

    private void initDao() {
        dao = new SqlliteDao();

        if (dao.isFirstLaunch()) {
            this.firstLaunch = true;

            final Settings settings = new Settings();
            dao.saveSettings(settings);
        }
    }

    public boolean isFirstLaunch() {
        return firstLaunch;
    }

    public void addOutsideClickListener(View view, OutsideClickListener listener) {
        outsideClickListeners.put(view, listener);
    }

    public void removeOutsideClickListener(View view, OutsideClickListener listener) {
        outsideClickListeners.remove(view, listener);
    }

    @Override
    public void onBackPressed() {
        if (isMenuOpened()) {
            menuLayout.closeDrawers();
            return;
        }

        if (activeUnits.size() > 1) {
            final UnitDescriptor active = activeUnits.getLast();

            active.getUnit().onBackPressed();
            stopUnit(active);

            return;
        }

        super.onBackPressed();
    }

    private boolean isMenuOpened() {
        return menuLayout.isDrawerOpen(GravityCompat.END);
    }

    public void startUnit(Unit<ShopListActivity> unit) {
        final UnitDescriptor descriptor = new UnitDescriptor(unit, currentNavigatorName);

        if ((activeUnits.size() > 0) && (activeUnits.getLast().equals(descriptor))) {
            return;
        }

        activeUnits.add(descriptor);

        unit.setHostingActivity(this);
        unit.start();
    }

    public void stopUnit(Unit unit) {
        final Iterator<UnitDescriptor> iterator = activeUnits.descendingIterator();
        UnitDescriptor foundDescriptor = null;
        while (iterator.hasNext()) {
            final UnitDescriptor descriptor = iterator.next();
            if (Objects.equal(descriptor.getUnit(), unit)) {
                foundDescriptor = descriptor;
                break;
            }
        }

        if (foundDescriptor != null) {
            stopUnit(foundDescriptor);

        } else {
            Log.w("ShopListActivity",
                    "stopUnit: unit descriptor not found " + unit.getClass().getSimpleName());
            unit.stop();
        }
    }

    @Override
    protected void onStop() {
        for (UnitDescriptor unit : activeUnits) {
            unit.getUnit().stop();
        }

        super.onStop();
    }

    private void stopUnit(UnitDescriptor descriptor) {
        activeUnits.remove(descriptor);

        final Unit stoppingUnit = descriptor.getUnit();

        for (Map.Entry<Integer, List<Unit>> entry : capturedViewOwners.entrySet()) {
            final List<Unit> units = entry.getValue();

            final int lastIndex = units.lastIndexOf(stoppingUnit);
            if (lastIndex >= 0) {
                units.remove(lastIndex);

                if ((units.size() > 0) && (lastIndex == units.size())) {
                    final Unit lastOwner = units.get(units.size() - 1);
                    lastOwner.viewClaimed(entry.getKey());
                } else {
                    final ViewGroup view = (ViewGroup) findViewById(entry.getKey());
                    view.removeAllViews();
                }
            }
        }

        if (!activeUnits.isEmpty()) {
            currentNavigatorName = activeUnits.getLast().getNavigatorName();
        }

        descriptor.getUnit().stop();
    }

    public interface OutsideClickListener {
        void clickedOutside(int x, int y);
    }

    private final static class UnitDescriptor implements Serializable {
        private final Unit unit;
        private final String navigatorName;

        private UnitDescriptor(Unit unit, String navigatorName) {
            this.unit = unit;
            this.navigatorName = navigatorName;
        }

        public Unit getUnit() {
            return unit;
        }

        public String getNavigatorName() {
            return navigatorName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UnitDescriptor)) return false;

            UnitDescriptor that = (UnitDescriptor) o;

            if (!unit.equals(that.unit)) return false;
            return navigatorName.equals(that.navigatorName);

        }

        @Override
        public int hashCode() {
            int result = unit.hashCode();
            result = 31 * result + navigatorName.hashCode();
            return result;
        }
    }
}
