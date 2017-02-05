package net.buggy.shoplist.units;


import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.util.Predicate;

import net.buggy.components.TagFlag;
import net.buggy.components.ViewUtils;
import net.buggy.components.animation.AnimationAdapter;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.SwipeToRemoveHandler;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.CategoryComparator;
import net.buggy.shoplist.compare.ShopItemComparator;
import net.buggy.shoplist.components.CategoriesFilterCellFactory;
import net.buggy.shoplist.components.ListDecorator;
import net.buggy.shoplist.components.SearchProductCellFactory;
import net.buggy.shoplist.components.SearchProductCellFactory.SearchedProduct;
import net.buggy.shoplist.components.ToBuyShopItemCellFactory;
import net.buggy.shoplist.data.DataStorage;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static android.widget.ListPopupWindow.MATCH_PARENT;
import static net.buggy.components.list.FactoryBasedAdapter.SelectionMode.MULTI;

public class ShopItemListUnit extends Unit<ShopListActivity> {

    private transient FactoryBasedAdapter<ShopItem> adapter;
    private transient EditText searchField;

    @Override
    public void start() {
        addRenderer(ShopListActivity.TOOLBAR_VIEW_ID, new ToolbarRenderer());
        addRenderer(ShopListActivity.OVERLAY_VIEW_ID, new SearchOverlayRenderer());
        addRenderer(ShopListActivity.MAIN_VIEW_ID, new MainViewRenderer());
    }

    @Override
    protected void onEvent(Object event) {
        if (event instanceof SelectShopItemsUnit.ShopItemsCreatedEvent) {
            final Collection<ShopItem> newItems =
                    ((SelectShopItemsUnit.ShopItemsCreatedEvent) event).getShopItems();

            final DataStorage dataStorage = getHostingActivity().getDataStorage();
            for (ShopItem item : newItems) {
                dataStorage.addShopItem(item);

                adapter.add(item);
            }

            return;

        } else if (event instanceof ViewShopItemUnit.ShopItemEditedEvent) {
            final ShopItem shopItem = ((ViewShopItemUnit.ShopItemEditedEvent) event).getShopItem();

            getHostingActivity().getDataStorage().saveShopItem(shopItem);
            adapter.update(shopItem);

            return;

        } else if (event instanceof EditProductUnit.ProductEditedEvent) {
            EditProductUnit.ProductEditedEvent productEditedEvent = (EditProductUnit.ProductEditedEvent) event;
            final Product product = productEditedEvent.getProduct();

            final DataStorage dataStorage = getHostingActivity().getDataStorage();
            dataStorage.addProduct(product);

            addShopItem(product, dataStorage);
            return;
        }

        super.onEvent(event);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {

        @Override
        public void renderTo(final RelativeLayout parentView, final ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(
                    R.layout.unit_shop_item_list,
                    parentView,
                    true);

            adapter = initList(parentView, activity, dataStorage);

            initCleanButton(parentView);

            initAddItemButton(parentView);
        }

        private FactoryBasedAdapter<ShopItem> initList(RelativeLayout parentView, final ShopListActivity activity, final DataStorage dataStorage) {
            final RecyclerView itemsList = (RecyclerView) parentView.findViewById(R.id.shopping_list);
            ListDecorator.decorateList(itemsList);

            final int backgroundColor = ViewUtils.resolveColor(R.color.color_underground, parentView.getContext());
            final SwipeToRemoveHandler handler = new SwipeToRemoveHandler(backgroundColor);
            handler.attach(itemsList);

            final FactoryBasedAdapter<ShopItem> adapter = new FactoryBasedAdapter<>(new ToBuyShopItemCellFactory());
            adapter.setSelectionMode(MULTI);
            adapter.setSorter(new ShopItemComparator());

            final List<ShopItem> shopItems = dataStorage.getShopItems();
            adapter.addAll(shopItems);

            itemsList.setAdapter(adapter);

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<ShopItem>() {
                @Override
                public void added(ShopItem item) {

                }

                @Override
                public void removed(ShopItem item) {
                    dataStorage.removeShopItem(item);
                }

                @Override
                public void changed(ShopItem changedItem) {
                    dataStorage.saveShopItem(changedItem);
                }
            });

            adapter.addClickListener(new FactoryBasedAdapter.ClickListener<ShopItem>() {
                @Override
                public void itemClicked(ShopItem item) {
                    final ViewShopItemUnit unit = new ViewShopItemUnit(item);
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
                    adapter.clear();
                    adapter.addAll(dataStorage.getShopItems());

                    swipeRefreshLayout.setRefreshing(false);
                }
            });

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
            final ImageButton cleanCheckedButton =
                    (ImageButton) parentView.findViewById(R.id.unit_shopitem_list_clean_checked_button);
            cleanCheckedButton.setEnabled(false);
            cleanCheckedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<ShopItem> selectedItems = adapter.getSelectedItems();
                    for (ShopItem selectedItem : selectedItems) {
                        adapter.remove(selectedItem);
                    }
                    cleanCheckedButton.setEnabled(false);
                }
            });

            adapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<ShopItem>() {
                @Override
                public void selectionChanged(ShopItem item, boolean selected) {
                    if (selected) {
                        cleanCheckedButton.setEnabled(true);
                    } else {
                        final boolean anythingSelected = !adapter.getSelectedItems().isEmpty();
                        cleanCheckedButton.setEnabled(anythingSelected);
                    }
                }
            });
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

            final ImageButton categoriesFilterButton = (ImageButton) parentView.findViewById(
                    R.id.unit_shopitem_list_filter_button);

            final RelativeLayout activeCategoriesContainer = (RelativeLayout) parentView.findViewById(
                    R.id.unit_shop_item_list_active_categories);
            final int filterButtonHeight = categoriesFilterButton.getDrawable().getIntrinsicHeight();

            final FactoryBasedAdapter<Category> categoriesAdapter = new FactoryBasedAdapter<>(
                    new CategoriesFilterCellFactory());
            categoriesAdapter.setSorter(CategoryComparator.INSTANCE);
            categoriesAdapter.setSelectionMode(MULTI);
            categoriesAdapter.addAll(activity.getDataStorage().getCategories());

            final int flagCutHeight = ViewUtils.dpToPx(4, activity);
            categoriesAdapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<Category>() {
                @Override
                public void selectionChanged(Category item, boolean selected) {
                    final List<Category> selectedItems = categoriesAdapter.getSelectedItems();
                    filterShopItems(selectedItems);

                    activeCategoriesContainer.removeAllViews();

                    if (!CollectionUtils.isEmpty(selectedItems)) {
                        categoriesFilterButton.setActivated(true);

                        final int itemHeight = filterButtonHeight / selectedItems.size();
                        int i = 0;

                        for (Category category : selectedItems) {
                            int flagHeight = itemHeight * (i + 1) + flagCutHeight;

                            final TagFlag tagFlag = new TagFlag(activity);
                            tagFlag.setColor(ModelHelper.getColor(category));
                            tagFlag.setBorderColor(Color.TRANSPARENT);
                            final RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                                    LayoutParams.MATCH_PARENT, flagHeight);
                            params.topMargin = flagCutHeight;
                            tagFlag.setLayoutParams(params);
                            tagFlag.setCutHeight(flagCutHeight);

                            i++;

                            activeCategoriesContainer.addView(tagFlag, 0);
                        }

                    } else {
                        categoriesFilterButton.setActivated(false);
                    }
                }
            });

            final LayoutInflater popupInflater = LayoutInflater.from(activity);
            final ViewGroup popupContent = (ViewGroup) popupInflater.inflate(
                    R.layout.popup_categories_filter,
                    parentView,
                    false);

            final RecyclerView categoriesList = (RecyclerView) popupContent.findViewById(
                    R.id.popup_categories_filter_categories_list);
            ListDecorator.decorateList(categoriesList);
            categoriesList.setAdapter(categoriesAdapter);

            final PopupWindow popupWindow = new PopupWindow(popupContent,
                    MATCH_PARENT,
                    MATCH_PARENT,
                    true);
            popupWindow.setFocusable(false);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            popupContent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (popupWindow.isShowing()) {
                        popupWindow.dismiss();
                    }
                }
            });

            categoriesFilterButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!popupWindow.isShowing()) {
                        popupWindow.showAsDropDown(parentView);
                    }
                }
            });
        }
    }

    private void addShopItem(Product product, DataStorage dataStorage) {
        final ShopItem shopItem = new ShopItem();
        shopItem.setProduct(product);
        dataStorage.addShopItem(shopItem);

        adapter.add(shopItem);
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
                        startSearch(overlayColor);
                    } else {
                        finishSearch();
                    }
                }
            });
            searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        final List<Product> products = activity.getDataStorage().getProducts();

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
            final Set<Product> addedProducts = getAddedProducts();
            if (addedProducts.contains(product)) {
                Toast.makeText(activity,
                        activity.getString(R.string.shop_item_already_exists, product.getName()),
                        Toast.LENGTH_SHORT).show();
                return;

            }

            if (product.getId() != null) {
                addShopItem(product, activity.getDataStorage());
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

        private void startSearch(int overlayColor) {
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

            updateSearchProducts(searchListAdapter, "");
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
            final DataStorage dataStorage = getHostingActivity().getDataStorage();

            Set<Product> existingProducts = getAddedProducts();

            List<Product> products = new ArrayList<>(
                    dataStorage.getProducts());
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
