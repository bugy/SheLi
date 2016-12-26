package net.buggy.shoplist.units;


import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.internal.util.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ProductComparator;
import net.buggy.shoplist.components.CategoriesSpinner;
import net.buggy.shoplist.components.FastCreationPanel;
import net.buggy.shoplist.components.ListDecorator;
import net.buggy.shoplist.components.SelectShopItemCellFactory;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.filters.ProductsFilter;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.EditProductUnit.ProductEditedEvent;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.buggy.components.list.FactoryBasedAdapter.SelectionMode.MULTI;

public class SelectShopItemsUnit extends Unit<ShopListActivity> {

    private static final int ACTIVITY_VIEW_ID = R.id.main_activity_view;
    private static final int TOOLBAR_VIEW_ID = R.id.toolbar_container;

    private transient FactoryBasedAdapter<ShopItem> adapter;

    private final Set<Product> disabledProducts;
    private Category selectedCategory;
    private String filterText;

    private final Map<Product, ShopItem> cachedItems = new ConcurrentHashMap<>();
    private final Set<ShopItem> selectedItems = Collections.newSetFromMap(new ConcurrentHashMap<ShopItem, Boolean>());

    public SelectShopItemsUnit(Collection<Product> disabledProducts) {
        if (disabledProducts != null) {
            this.disabledProducts = ImmutableSet.copyOf(disabledProducts);
        } else {
            this.disabledProducts = ImmutableSet.of();
        }
    }

    @Override
    public void start() {
        addRenderer(ACTIVITY_VIEW_ID, new MainViewRenderer());
        addRenderer(TOOLBAR_VIEW_ID, new ToolbarRenderer());
    }

    private ShopItem getOrCreateShopItem(Product product) {
        if (cachedItems.containsKey(product)) {
            return cachedItems.get(product);
        }

        final ShopItem shopItem = new ShopItem();
        shopItem.setQuantity(BigDecimal.ONE);
        shopItem.setProduct(product);

        cachedItems.put(product, shopItem);

        return shopItem;
    }

    @Override
    protected void onEvent(Object event) {
        if (event instanceof ProductEditedEvent) {
            ProductEditedEvent productEvent = (ProductEditedEvent) event;
            final Product product = productEvent.getProduct();

            getHostingActivity().getDataStorage().addProduct(product);

            final ShopItem newItem = getOrCreateShopItem(product);
            adapter.add(newItem);
            adapter.selectItem(newItem);

            return;
        }

        super.onEvent(event);
    }

    @Override
    public void onBackPressed() {
        final List<ShopItem> selectedItems = adapter.getSelectedItems();

        fireEvent(new ShopItemsCreatedEvent(selectedItems));
    }

    private void sendSelectionEventAndExit(Collection<ShopItem> shopItems) {
        ShopListActivity hostingActivity = getHostingActivity();

        hostingActivity.stopUnit(SelectShopItemsUnit.this);

        fireEvent(new ShopItemsCreatedEvent(shopItems));
    }

    public static final class ShopItemsCreatedEvent {
        private final Collection<ShopItem> shopItems;

        public ShopItemsCreatedEvent(Collection<ShopItem> shopItems) {
            this.shopItems = shopItems;
        }

        public Collection<ShopItem> getShopItems() {
            return shopItems;
        }
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(final RelativeLayout parentView, final ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_select_shopitems, parentView, true);

            initAdapter();

            final RecyclerView productsList = (RecyclerView) parentView.findViewById(R.id.unit_select_shopitems_list);
            ListDecorator.decorateList(productsList);

            productsList.setAdapter(adapter);

            final View acceptButton = parentView.findViewById(R.id.unit_select_shopitems_button_accept);
            acceptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<ShopItem> selectedItems = adapter.getSelectedItems();

                    sendSelectionEventAndExit(selectedItems);
                }
            });

            final FastCreationPanel creationPanel = (FastCreationPanel)
                    parentView.findViewById(R.id.unit_select_shopitems_creation_panel);
            creationPanel.setEditAddEnabled(true);
            final String searchHint = parentView.getResources().getString(R.string.unit_select_shopitems_search_hint);
            creationPanel.setHint(searchHint);
            creationPanel.setListener(new FastCreationPanel.Listener() {
                @Override
                public void onCreate(String name) {
                    if (checkIfExists(name)) {
                        return;
                    }

                    final Product product = new Product();
                    product.setName(name);

                    dataStorage.addProduct(product);

                    final ShopItem newItem = getOrCreateShopItem(product);
                    adapter.add(newItem);
                    adapter.selectItem(newItem);
                }

                @Override
                public void onEditCreate(String name) {
                    if (checkIfExists(name)) {
                        return;
                    }

                    final Product product = new Product();
                    product.setName(name);
                    final EditProductUnit editProductUnit = new EditProductUnit(product, true);
                    editProductUnit.setListeningUnit(SelectShopItemsUnit.this);
                    activity.startUnit(editProductUnit);
                }

                private boolean checkIfExists(String name) {
                    final List<ShopItem> shopItems = adapter.getAllItems();
                    for (ShopItem item : shopItems) {
                        final Product product = item.getProduct();

                        if (StringUtils.equalIgnoreCase(name, product.getName())) {
                            final Toast toast = Toast.makeText(
                                    parentView.getContext(),
                                    activity.getString(
                                            R.string.products_unit_already_exists,
                                            product.getName()),
                                    Toast.LENGTH_LONG);
                            toast.show();

                            if (!disabledProducts.contains(product)) {
                                adapter.selectItem(item);
                            }

                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public void onNameChanged(String name) {
                    filterText = name;

                    filterProducts(selectedCategory, filterText);
                }
            });

            if (!Strings.isNullOrEmpty(filterText)) {
                creationPanel.setText(filterText);
            }
        }

        private void initAdapter() {
            final DataStorage dataStorage = getHostingActivity().getDataStorage();

            adapter = new FactoryBasedAdapter<>(
                    new SelectShopItemCellFactory());
            adapter.setSelectionMode(MULTI);
            adapter.setSorter(createComparator());

            final List<Product> products = dataStorage.getProducts();
            for (Product product : products) {
                ShopItem shopItem = getOrCreateShopItem(product);

                adapter.add(shopItem);

                if (disabledProducts.contains(product)) {
                    adapter.disableItem(shopItem);
                }
            }

            for (ShopItem selectedItem : selectedItems) {
                adapter.selectItem(selectedItem);
            }

            adapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<ShopItem>() {
                @Override
                public void selectionChanged(ShopItem item, boolean selected) {
                    if (selected) {
                        selectedItems.add(item);
                    } else {
                        selectedItems.remove(item);
                    }
                }
            });

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<ShopItem>() {
                @Override
                public void added(ShopItem item) {
                }

                @Override
                public void removed(ShopItem item) {
                }

                @Override
                public void changed(ShopItem changedItem) {
                    adapter.selectItem(changedItem);
                }
            });
        }
    }

    private class ToolbarRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(RelativeLayout parentView, ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_create_shopitems_toolbar, parentView, true);

            final CategoriesSpinner categorySpinner = (CategoriesSpinner) parentView.findViewById(
                    R.id.unit_select_shopitems_category_spinner);
            categorySpinner.setCategories(dataStorage.getCategories());
            categorySpinner.setListener(new CategoriesSpinner.Listener() {
                @Override
                public void categorySelected(Category category) {
                    selectedCategory = category;

                    filterProducts(selectedCategory, filterText);
                }
            });
        }
    }

    private Comparator<ShopItem> createComparator() {
        final ProductComparator productComparator = new ProductComparator();

        return new Comparator<ShopItem>() {
            @Override
            public int compare(ShopItem i1, ShopItem i2) {
                final Product p1 = i1.getProduct();
                final Product p2 = i2.getProduct();

                if (SelectShopItemsUnit.this.disabledProducts.contains(p1)) {
                    if (!SelectShopItemsUnit.this.disabledProducts.contains(p2)) {
                        return 1;
                    }
                } else if (SelectShopItemsUnit.this.disabledProducts.contains(p2)) {
                    return -1;
                }

                return productComparator.compare(p1, p2);
            }
        };
    }

    private void filterProducts(final Category category, final String newText) {
        final ProductsFilter productsFilter = new ProductsFilter(newText, category);

        adapter.setFilter(new Predicate<ShopItem>() {
            @Override
            public boolean apply(ShopItem shopItem) {
                return productsFilter.apply(shopItem.getProduct());
            }
        });
    }

}
