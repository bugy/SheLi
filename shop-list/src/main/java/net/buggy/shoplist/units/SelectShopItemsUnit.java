package net.buggy.shoplist.units;


import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import com.android.internal.util.Predicate;
import com.google.common.collect.ImmutableSet;

import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ProductComparator;
import net.buggy.shoplist.components.CategoriesFilter;
import net.buggy.shoplist.components.SelectableShopItemCellFactory;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.filters.ProductsFilter;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.EditProductUnit.ProductEditedEvent;
import net.buggy.shoplist.units.views.ViewRenderer;

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

    private final Set<ShopItem> existingItems;
    private List<Category> selectedCategories;

    private final Map<Product, ShopItem> cachedItems = new ConcurrentHashMap<>();
    private final Set<ShopItem> selectedItems = Collections.newSetFromMap(new ConcurrentHashMap<ShopItem, Boolean>());

    public SelectShopItemsUnit(Collection<ShopItem> existingItems) {
        if (existingItems != null) {
            this.existingItems = ImmutableSet.copyOf(existingItems);
            for (ShopItem existingItem : existingItems) {
                cachedItems.put(existingItem.getProduct(), existingItem);
            }
        } else {
            this.existingItems = ImmutableSet.of();
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

        } else if (event instanceof ViewShopItemUnit.ShopItemEditedEvent) {
            ViewShopItemUnit.ShopItemEditedEvent shopItemEvent =
                    (ViewShopItemUnit.ShopItemEditedEvent) event;

            final ShopItem shopItem = shopItemEvent.getShopItem();
            cachedItems.put(shopItem.getProduct(), shopItem);
            adapter.update(shopItem);
            adapter.selectItem(shopItem);

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
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_select_shopitems, parentView, true);

            initAdapter();

            final RecyclerView productsList = (RecyclerView) parentView.findViewById(R.id.unit_select_shopitems_list);
            ListDecorator.decorateList(productsList);

            productsList.setAdapter(adapter);

            final View acceptButton = parentView.findViewById(R.id.unit_select_shopitems_accept_button);
            acceptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<ShopItem> selectedItems = adapter.getSelectedItems();

                    sendSelectionEventAndExit(selectedItems);
                }
            });
        }

        private void initAdapter() {
            final DataStorage dataStorage = getHostingActivity().getDataStorage();

            final SelectableShopItemCellFactory factory = new SelectableShopItemCellFactory(
                    new SelectableShopItemCellFactory.Listener() {
                        @Override
                        public void onEditClick(ShopItem shopItem) {
                            final ViewShopItemUnit unit = new ViewShopItemUnit(shopItem);
                            unit.setListeningUnit(SelectShopItemsUnit.this);
                            getHostingActivity().startUnit(unit);
                        }
                    });
            adapter = new FactoryBasedAdapter<>(factory);
            adapter.setSelectionMode(MULTI);
            adapter.setSorter(createComparator());

            final List<Product> products = dataStorage.getProducts();
            for (Product product : products) {
                ShopItem shopItem = getOrCreateShopItem(product);

                if (!existingItems.contains(shopItem)) {
                    adapter.add(shopItem);
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

            final CategoriesFilter categoriesFilter = (CategoriesFilter) parentView.findViewById(
                    R.id.unit_create_shopitems_toolbar_categories_filter);
            categoriesFilter.setPopupAnchor(parentView);
            categoriesFilter.setCategories(dataStorage.getCategories());

            categoriesFilter.addListener(new CategoriesFilter.Listener() {
                @Override
                public void categoriesSelected(List<Category> categories) {
                    selectedCategories = categories;

                    filterProducts(selectedCategories);
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

                return productComparator.compare(p1, p2);
            }
        };
    }

    private void filterProducts(final List<Category> categories) {
        final ProductsFilter productsFilter = new ProductsFilter("", categories);

        adapter.setFilter(new Predicate<ShopItem>() {
            @Override
            public boolean apply(ShopItem shopItem) {
                return productsFilter.apply(shopItem.getProduct());
            }
        });
    }

}
