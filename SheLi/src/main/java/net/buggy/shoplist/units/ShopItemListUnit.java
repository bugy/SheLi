package net.buggy.shoplist.units;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.util.Predicate;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import net.buggy.components.ViewUtils;
import net.buggy.components.animation.AnimationAdapter;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.components.list.SwipeToRemoveHandler;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ShopItemComparator;
import net.buggy.shoplist.components.CategoriesFilter;
import net.buggy.shoplist.components.SearchProductCellFactory;
import net.buggy.shoplist.components.SearchProductCellFactory.SearchedProduct;
import net.buggy.shoplist.components.ToBuyShopItemCellFactory;
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.UiThreadEntityListener;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.CollectionUtils;
import net.buggy.shoplist.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.buggy.components.list.FactoryBasedAdapter.SelectionMode.MULTI;
import static net.buggy.shoplist.units.UnitsHelper.addTemporalDaoListener;

public class ShopItemListUnit extends Unit<ShopListActivity> {

    private transient FactoryBasedAdapter<ShopItem> adapter;
    private transient EditText searchField;
    private transient ImageButton cleanCheckedButton;
    private Boolean showTipsOnEmpty;

    private final List<Category> filterCategories = new ArrayList<>();

    @Override
    public void initialize() {
        addRenderer(ShopListActivity.TOOLBAR_VIEW_ID, new ToolbarRenderer());
        addRenderer(ShopListActivity.OVERLAY_VIEW_ID, new SearchOverlayRenderer());
        addRenderer(ShopListActivity.MAIN_VIEW_ID, new MainViewRenderer());
    }

    @Override
    protected void onEvent(Object event) {
        if (event instanceof SelectShopItemsUnit.ShopItemsCreatedEvent) {
            final Collection<ShopItem> newItems =
                    ((SelectShopItemsUnit.ShopItemsCreatedEvent) event).getShopItems();

            final Dao dao = getHostingActivity().getDao();
            final List<ShopItem> existingItems = dao.getShopItems();
            final LinkedHashMap<Product, ShopItem> itemsPerProduct = new LinkedHashMap<>();
            for (ShopItem item : existingItems) {
                itemsPerProduct.put(item.getProduct(), item);
            }

            for (ShopItem newItem : newItems) {
                final ShopItem existingItem = itemsPerProduct.get(newItem.getProduct());
                if (existingItem == null) {
                    dao.addShopItem(newItem);
                } else {
                    Log.w("ShopItemListUnit", "onEvent: " + newItem + " shopItem already exists");
                    existingItem.setQuantity(newItem.getQuantity());
                    existingItem.setComment(newItem.getComment());
                    existingItem.setUnitOfMeasure(newItem.getUnitOfMeasure());
                    dao.saveShopItem(existingItem);
                }
            }

            return;

        } else if (event instanceof EditShopItemUnit.ShopItemEditedEvent) {
            final ShopItem shopItem = ((EditShopItemUnit.ShopItemEditedEvent) event).getShopItem();
            final Product changedProduct = ((EditShopItemUnit.ShopItemEditedEvent) event).getProduct();

            getHostingActivity().getDao().saveProduct(changedProduct);
            getHostingActivity().getDao().saveShopItem(shopItem);

            return;

        } else if (event instanceof EditProductUnit.ProductEditedEvent) {
            EditProductUnit.ProductEditedEvent productEditedEvent = (EditProductUnit.ProductEditedEvent) event;
            final Product product = productEditedEvent.getProduct();

            final Dao dao = getHostingActivity().getDao();
            dao.addProduct(product);
            Product cleanProduct = dao.findProduct(product.getId());

            addShopItem(cleanProduct, dao);
            return;
        }

        super.onEvent(event);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {

        @Override
        public void renderTo(final RelativeLayout parentView, final ShopListActivity activity) {
            final Dao dao = activity.getDao();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(
                    R.layout.unit_shop_item_list,
                    parentView,
                    true);

            adapter = initList(parentView, activity, dao);

            initCleanButton(parentView);

            initCheckListener();

            initAddItemButton(parentView);

            initCopyContentButton(parentView);

            initEmptyScreen(parentView);
        }

        private void initCopyContentButton(final RelativeLayout parentView) {
            final ImageButton copyButton = (ImageButton) parentView.findViewById(
                    R.id.unit_shopitem_list_copy_list_button);
            ViewUtils.setColorListTint(copyButton, R.color.imagebutton_white_tint);

            copyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Context context = parentView.getContext();

                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                            Context.CLIPBOARD_SERVICE);

                    List<String> rows = new ArrayList<>();
                    final List<ShopItem> items = adapter.getAllItems();
                    for (ShopItem item : items) {
                        if (!item.isChecked()) {
                            String row = item.getProduct().getName();
                            if (item.getQuantity() != null) {
                                row += " - " + ModelHelper.buildStringQuantity(item, context);
                            }

                            if (!Strings.isNullOrEmpty(item.getComment())) {
                                row += ", " + item.getComment();
                            }

                            rows.add(row);
                        }
                    }

                    final String listAsString = Joiner.on("\n").join(rows);

                    ClipData clip = ClipData.newPlainText(
                            context.getString(R.string.unit_shopitem_list_copy_clipboard_label),
                            listAsString);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(context,
                            R.string.unit_shopitem_list_copied_into_clipboard,
                            Toast.LENGTH_SHORT).show();
                }
            });

            copyButton.setEnabled(adapter.getAllItems().size() > adapter.getSelectedItems().size());

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<ShopItem>() {
                @Override
                public void added(ShopItem item) {
                    copyButton.setEnabled(true);
                }

                @Override
                public void removed(ShopItem item) {
                    copyButton.setEnabled(adapter.getAllItems().size() > adapter.getSelectedItems().size());
                }

                @Override
                public void changed(ShopItem changedItem) {
                }
            });

            adapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<ShopItem>() {
                @Override
                public void selectionChanged(ShopItem item, boolean selected) {
                    if (selected) {
                        copyButton.setEnabled(adapter.getAllItems().size() > adapter.getSelectedItems().size());
                    } else {
                        copyButton.setEnabled(true);
                    }
                }
            });
        }

        private FactoryBasedAdapter<ShopItem> initList(RelativeLayout parentView, final ShopListActivity activity, final Dao dao) {
            final RecyclerView itemsList = (RecyclerView) parentView.findViewById(R.id.shopping_list);
            ListDecorator.decorateList(itemsList);

            final int backgroundColor = ViewUtils.resolveColor(R.color.color_underground, parentView.getContext());
            final SwipeToRemoveHandler handler = new SwipeToRemoveHandler(backgroundColor);
            handler.attach(itemsList);

            final FactoryBasedAdapter<ShopItem> adapter = new FactoryBasedAdapter<>(new ToBuyShopItemCellFactory());
            adapter.setSelectionMode(MULTI);
            adapter.setSorter(new ShopItemComparator());

            refreshShopItems(dao.getShopItems(), adapter);

            itemsList.setAdapter(adapter);

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<ShopItem>() {
                @Override
                public void added(ShopItem item) {

                }

                @Override
                public void removed(ShopItem item) {
                    dao.removeShopItem(item);

                    if (item.isChecked()) {
                        final Product product = item.getProduct();
                        product.setLastBuyDate(new Date());
                        dao.saveProduct(product);
                    }
                }

                @Override
                public void changed(ShopItem changedItem) {
                    dao.saveShopItem(changedItem);
                }
            });

            adapter.addLongClickListener(new FactoryBasedAdapter.ClickListener<ShopItem>() {
                @Override
                public void itemClicked(ShopItem item) {
                    final EditShopItemUnit unit = new EditShopItemUnit(item);
                    unit.setListeningUnit(ShopItemListUnit.this);
                    activity.startUnit(unit);
                }
            });

            final SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout)
                    parentView.findViewById(R.id.unit_shop_item_list_swipe_refresh);
            swipeRefreshLayout.setColorSchemeResources(R.color.color_primary);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refreshShopItems(dao.getShopItems(), adapter);

                    swipeRefreshLayout.setRefreshing(false);
                }
            });

            final UiThreadEntityListener<ShopItem> listener =
                    new TableAdapterEntityListener<ShopItem>(
                            adapter, getHostingActivity()) {
                        @Override
                        public void entityChangedUi(ShopItem changedEntity) {
                            super.entityChangedUi(changedEntity);
                            if (changedEntity.isChecked()) {
                                adapter.selectItem(changedEntity);
                            } else {
                                adapter.deselectItem(changedEntity);
                            }
                        }
                    };
            addTemporalDaoListener(dao, ShopItem.class, listener, ShopItemListUnit.this);

            return adapter;
        }

        private void initAddItemButton(RelativeLayout parentView) {
            final ImageButton addItemButton = (ImageButton) parentView.findViewById(
                    R.id.unit_shopitem_list_add_item_button);
            addItemButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ViewUtils.focusTextField(searchField);
                }
            });
        }

        private void initCleanButton(RelativeLayout parentView) {
            cleanCheckedButton = (ImageButton) parentView.findViewById(
                    R.id.unit_shopitem_list_clean_checked_button);
            cleanCheckedButton.setEnabled(!adapter.getSelectedItems().isEmpty());
            cleanCheckedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<ShopItem> selectedItems = adapter.getSelectedItems();
                    for (ShopItem selectedItem : selectedItems) {
                        getHostingActivity().getDao().removeShopItem(selectedItem);
                    }
                    cleanCheckedButton.setEnabled(false);
                }
            });
        }

        private void initCheckListener() {
            final ExecutorService executorService = Executors.newSingleThreadExecutor();

            adapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<ShopItem>() {
                @Override
                public void selectionChanged(final ShopItem item, boolean selected) {
                    if (selected) {
                        cleanCheckedButton.setEnabled(true);
                    } else {
                        final boolean anythingSelected = !adapter.getSelectedItems().isEmpty();
                        cleanCheckedButton.setEnabled(anythingSelected);
                    }

                    if (item.isChecked() == selected) {
                        return;
                    }

                    item.setChecked(selected);

                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            final Dao dao = getHostingActivity().getDao();
                            dao.saveShopItem(item);
                        }
                    });
                }
            });
        }

    }

    private void initEmptyScreen(RelativeLayout parentView) {
        final ViewGroup defaultEmptyPanel = (ViewGroup) parentView.findViewById(
                R.id.unit_shop_item_list_empty_stub);

        if (showTipsOnEmpty == null) {
            showTipsOnEmpty = getHostingActivity().getDao().isShowTips();
        }

        adapter.addDataListener(new FactoryBasedAdapter.DataListener<ShopItem>() {
            @Override
            public void added(ShopItem item) {
                showTipsOnEmpty = false;
                getHostingActivity().getDao().setShowTips(showTipsOnEmpty);
            }

            @Override
            public void removed(ShopItem item) {
            }

            @Override
            public void changed(ShopItem changedItem) {

            }
        });


        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateEmptyScreen(defaultEmptyPanel);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateEmptyScreen(defaultEmptyPanel);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateEmptyScreen(defaultEmptyPanel);
            }
        });

        updateEmptyScreen(defaultEmptyPanel);
    }

    private void updateEmptyScreen(ViewGroup emptyPanel) {
        if (adapter.getItemCount() == 0) {
            emptyPanel.setVisibility(View.VISIBLE);

            final TextView emptyLabel = (TextView) emptyPanel.findViewById(
                    R.id.unit_shop_item_list_empty_stub_label);
            final ImageView emptyImage = (ImageView) emptyPanel.findViewById(
                    R.id.unit_shop_item_list_stub_image);

            final View firstLaunchPanel = emptyPanel.findViewById(
                    R.id.unit_shop_item_list_empty_first_launch);
            firstLaunchPanel.setVisibility(View.GONE);

            final Context context = emptyPanel.getContext();
            final String text;
            final int imageId;

            if (adapter.getAllItems().isEmpty()) {
                if (showTipsOnEmpty) {
                    text = context.getString(R.string.unit_shop_item_list_list_is_empty);
                    firstLaunchPanel.setVisibility(View.VISIBLE);
                } else {
                    text = context.getString(R.string.unit_shop_item_list_finished_shopping);
                }
                imageId = R.drawable.to_buy_empty_screen_image;
            } else {
                text = context.getString(R.string.unit_shop_item_list_no_filter_matches);
                imageId = R.drawable.to_buy_empty_no_matches;
            }

            emptyLabel.setText(text);
            emptyImage.setImageResource(imageId);

        } else {
            emptyPanel.setVisibility(View.GONE);
        }
    }

    private void refreshShopItems(List<ShopItem> setShopItems, FactoryBasedAdapter<ShopItem> adapter) {
        adapter.clear();
        adapter.addAll(setShopItems);

        for (ShopItem shopItem : setShopItems) {
            if (shopItem.isChecked()) {
                adapter.selectItem(shopItem);
            }
        }
    }

    private class ToolbarRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(final RelativeLayout parentView, final ShopListActivity activity) {
            final LayoutInflater toolbarInflater = LayoutInflater.from(parentView.getContext());
            toolbarInflater.inflate(
                    R.layout.unit_shop_item_list_toolbar,
                    parentView,
                    true);

            final CategoriesFilter categoriesFilter = (CategoriesFilter) parentView.findViewById(
                    R.id.unit_shop_item_list_toolbar_categories_filter);
            categoriesFilter.setPopupAnchor(parentView);
            categoriesFilter.setCategories(activity.getDao().getCategories());
            categoriesFilter.selectCategories(filterCategories);

            categoriesFilter.addListener(new CategoriesFilter.Listener() {
                @Override
                public void categoriesSelected(List<Category> categories) {
                    filterCategories.clear();
                    filterCategories.addAll(categories);
                    
                    filterShopItems(categories);
                }
            });

            filterShopItems(filterCategories);
        }
    }

    private void addShopItem(Product product, Dao dao) {
        final ShopItem shopItem = new ShopItem();
        shopItem.setProduct(product);
        dao.addShopItem(shopItem);
    }

    private void filterShopItems(final List<Category> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            adapter.setFilter(null);

        } else {
            adapter.setFilter(new Predicate<ShopItem>() {
                @Override
                public boolean apply(ShopItem shopItem) {
                    final Set<Category> productCategories = shopItem.getProduct().getCategories();
                    return productCategories.containsAll(categories);
                }
            });
        }
    }

    private class SearchOverlayRenderer extends ViewRenderer<ShopListActivity, android.view.ViewGroup> {
        private ViewGroup parentView;
        private FactoryBasedAdapter<SearchedProduct> searchListAdapter;
        private RecyclerView searchList;
        private ViewGroup searchPanel;
        private int originalLeftMargin;
        private ImageButton backButton;
        private ShopListActivity activity;

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            this.parentView = parentView;
            this.activity = activity;

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(
                    R.layout.unit_shop_item_list_overlay,
                    parentView,
                    true);

            final ImageButton addShopItemButton = (ImageButton) parentView.findViewById(R.id.unit_shopitem_list_add_products_button);
            addShopItemButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<ShopItem> availableShopItems = adapter.getAllItems();

                    final SelectShopItemsUnit selectShopItemsUnit = new SelectShopItemsUnit(availableShopItems);
                    selectShopItemsUnit.setListeningUnit(ShopItemListUnit.this);
                    activity.startUnit(selectShopItemsUnit);
                }
            });

            searchListAdapter = new FactoryBasedAdapter<>(new SearchProductCellFactory());
            searchListAdapter.addClickListener(new FactoryBasedAdapter.ClickListener<SearchedProduct>() {
                @Override
                public void itemClicked(SearchedProduct item) {
                    addItemClicked(item.getProduct());
                }
            });

            searchList = (RecyclerView) parentView.findViewById(
                    R.id.unit_shopitem_list_search_list);
            ListDecorator.decorateList(searchList);
            searchList.setAdapter(searchListAdapter);

            searchPanel = (ViewGroup) parentView.findViewById(
                    R.id.unit_shopitem_list_search_panel);

            originalLeftMargin = ((ViewGroup.MarginLayoutParams) searchPanel.getLayoutParams()).leftMargin;

            backButton = (ImageButton) parentView.findViewById(
                    R.id.unit_shopitem_list_search_back_button);

            searchField = (EditText) parentView.findViewById(
                    R.id.unit_shopitem_list_search_field);
            final int overlayColor = ViewUtils.resolveColor(R.color.color_overlay, activity);
            searchField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        startSearch(overlayColor, searchField.getText().toString());
                    } else {
                        finishSearch();
                    }
                }
            });
            searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        final List<Product> products = activity.getDao().getProducts();

                        Product foundProduct = null;
                        final String searchString = v.getText().toString().trim();
                        for (Product product : products) {
                            if (StringUtils.equalIgnoreCase(product.getName(), searchString)) {
                                foundProduct = product;
                                break;
                            }
                        }

                        if (foundProduct == null) {
                            foundProduct = new Product();
                            foundProduct.setName(searchString);
                        }

                        ViewUtils.hideSoftKeyboard(v);
                        v.clearFocus();
                        addItemClicked(foundProduct);

                        return true;
                    }

                    return false;
                }
            });

            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateSearchProducts(searchListAdapter, s.toString().trim());
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });
        }

        private void addItemClicked(Product product) {
            if (product.getId() != null) {
                final Set<Product> addedProducts = getAddedProducts();
                if (addedProducts.contains(product)) {
                    Toast.makeText(activity,
                            activity.getString(R.string.shop_item_already_exists, product.getName()),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                addShopItem(product, activity.getDao());
                return;
            }

            final EditProductUnit editProductUnit = new EditProductUnit(product, true);
            editProductUnit.setListeningUnit(ShopItemListUnit.this);
            activity.startUnit(editProductUnit);
        }

        private void finishSearch() {
            parentView.setBackgroundColor(Color.TRANSPARENT);
            parentView.setClickable(false);

            ViewUtils.setLeftMargin(searchPanel, originalLeftMargin);
            ViewUtils.setLeftMargin(searchList, originalLeftMargin);

            backButton.setVisibility(View.GONE);
            searchList.setVisibility(View.GONE);

            searchPanel.setActivated(false);

            searchField.setText("");
        }

        private void startSearch(int overlayColor, String searchText) {
            parentView.setBackgroundColor(overlayColor);
            parentView.setClickable(true);

            searchList.setVisibility(View.VISIBLE);
            searchListAdapter.setFilter(null);
            searchListAdapter.clear();

            searchPanel.setActivated(true);

            final Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    int animatedMargin = Math.round(originalLeftMargin * interpolatedTime);

                    ViewUtils.setLeftMargin(searchPanel, originalLeftMargin - animatedMargin);
                    ViewUtils.setLeftMargin(searchList, originalLeftMargin - animatedMargin);

                    if ((interpolatedTime < 1f) && (interpolatedTime > 0f)) {
                        ViewUtils.setLeftMargin(searchField, animatedMargin);
                    }
                }
            };
            animation.setDuration(300);
            animation.setAnimationListener(new AnimationAdapter() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    if (searchField.isFocused()) {
                        ViewUtils.setLeftMargin(searchField, 0);
                        backButton.setVisibility(View.VISIBLE);
                    }
                }
            });
            searchPanel.startAnimation(animation);

            updateSearchProducts(searchListAdapter, searchText);
        }
    }

    private void updateSearchProducts(
            final FactoryBasedAdapter<SearchedProduct> searchListAdapter,
            final String searchString) {
        final AsyncTask<String, SearchedProduct, List<SearchedProduct>> loadProductsTask =
                new UpdateSearchListTask(searchListAdapter);

        loadProductsTask.execute(searchString);
    }

    private Set<Product> getAddedProducts() {
        final List<ShopItem> shopItems = adapter.getAllItems();

        Set<Product> existingProducts = new LinkedHashSet<>();
        for (ShopItem shopItem : shopItems) {
            existingProducts.add(shopItem.getProduct());
        }

        return existingProducts;
    }


    private class UpdateSearchListTask extends AsyncTask<String, SearchedProduct, List<SearchedProduct>> {
        private final FactoryBasedAdapter<SearchedProduct> searchListAdapter;

        public UpdateSearchListTask(FactoryBasedAdapter<SearchedProduct> searchListAdapter) {
            this.searchListAdapter = searchListAdapter;
        }

        @Override
        protected List<SearchedProduct> doInBackground(String[] params) {
            String searchString = params[0];

            final String searchStringLower = searchString.toLowerCase();
            final Dao dao = getHostingActivity().getDao();

            Set<Product> existingProducts = getAddedProducts();

            List<Product> products = new ArrayList<>(
                    dao.getProducts());
            final Iterator<Product> iterator = products.iterator();
            boolean hasExactMatch = false;
            while (iterator.hasNext()) {
                final Product product = iterator.next();
                if (!product.getName().toLowerCase().contains(searchStringLower)) {
                    iterator.remove();
                    continue;
                }

                if (StringUtils.equalIgnoreCase(product.getName(), searchString)) {
                    hasExactMatch = true;
                }
            }

            if (!hasExactMatch && (!searchString.isEmpty())) {
                final Product stubProduct = new Product();
                stubProduct.setName(searchString);
                products.add(stubProduct);
            }

            List<SearchedProduct> result = new ArrayList<>();
            for (Product product : products) {
                boolean exists = existingProducts.contains(product);

                result.add(new SearchedProduct(product, exists));
            }

            Collections.sort(result, new SearchedProductComparator(searchStringLower));

            if (result.size() > 5) {
                result = result.subList(0, 5);
            }

            return result;
        }

        @Override
        protected void onPostExecute(List<SearchedProduct> products) {
            searchListAdapter.clear();
            searchListAdapter.addAll(products);
        }
    }

}
