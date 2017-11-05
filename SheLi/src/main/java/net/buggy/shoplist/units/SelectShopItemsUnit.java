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
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.UiThreadEntityListener;
import net.buggy.shoplist.filters.ProductsFilter;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
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
import static net.buggy.shoplist.units.UnitsHelper.addTemporalDaoListener;

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
    public void initialize() {
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

            getHostingActivity().getDao().addProduct(product);

            final ShopItem newItem = getOrCreateShopItem(product);
            adapter.add(newItem);
            adapter.selectItem(newItem);

            return;

        } else if (event instanceof EditShopItemUnit.ShopItemEditedEvent) {
            EditShopItemUnit.ShopItemEditedEvent shopItemEvent =
                    (EditShopItemUnit.ShopItemEditedEvent) event;

            final ShopItem shopItem = shopItemEvent.getShopItem();
            cachedItems.put(shopItem.getProduct(), shopItem);
            adapter.update(shopItem);
            adapter.selectItem(shopItem);

            final Product changedProduct = shopItemEvent.getProduct();
            getHostingActivity().getDao().saveProduct(changedProduct);

            return;
        }

        super.onEvent(event);
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
            final Dao dao = getHostingActivity().getDao();

            final SelectableShopItemCellFactory factory = new SelectableShopItemCellFactory(
                    new SelectableShopItemCellFactory.Listener() {
                        @Override
                        public void onEditClick(ShopItem shopItem) {
                            final EditShopItemUnit unit = new EditShopItemUnit(shopItem);
                            unit.setListeningUnit(SelectShopItemsUnit.this);
                            getHostingActivity().startUnit(unit);
                        }
                    });
            adapter = new FactoryBasedAdapter<>(factory);
            adapter.setSelectionMode(MULTI);
            adapter.setSorter(createComparator());

            final List<Product> products = dao.getProducts();
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

            final UiThreadEntityListener<Product> listener = new UiThreadEntityListener<Product>(
                    getHostingActivity()) {
                @Override
                public void entityAddedUi(Product newEntity) {
                    final ShopItem shopItem = getOrCreateShopItem(newEntity);

                    if (!existingItems.contains(shopItem)) {
                        if (!adapter.getAllItems().contains(shopItem)) {
                            adapter.add(shopItem);
                        }
                    }
                }

                @Override
                public void entityChangedUi(Product changedEntity) {
                    final ShopItem shopItem = getOrCreateShopItem(changedEntity);

                    if (!existingItems.contains(shopItem)) {
                        shopItem.setProduct(changedEntity);

                        if (!adapter.getAllItems().contains(shopItem)) {
                            adapter.add(shopItem);
                        } else {
                            adapter.update(shopItem);
                        }
                    }
                }

                @Override
                public void entityRemovedUi(Product removedEntity) {
                    final ShopItem shopItem = cachedItems.get(removedEntity);
                    if (shopItem != null) {
                        adapter.remove(shopItem);
                    }
                }
            };
            addTemporalDaoListener(dao, Product.class, listener, SelectShopItemsUnit.this);
        }
    }

    private class ToolbarRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(RelativeLayout parentView, ShopListActivity activity) {
            final Dao dao = activity.getDao();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_create_shopitems_toolbar, parentView, true);

            final CategoriesFilter categoriesFilter = (CategoriesFilter) parentView.findViewById(
                    R.id.unit_create_shopitems_toolbar_categories_filter);
            categoriesFilter.setPopupAnchor(parentView);
            categoriesFilter.setCategories(dao.getCategories());

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

                final int age1 = ModelHelper.ageToPercent(p1);
                final int age2 = ModelHelper.ageToPercent(p2);
                if (age1 >= ModelHelper.MIN_OVERDUE_AGE_PERCENT) {
                    if (age2 >= ModelHelper.MIN_OVERDUE_AGE_PERCENT) {
                        return age2 - age1;
                    }

                    return -1;
                } else if (age2 >= ModelHelper.MIN_OVERDUE_AGE_PERCENT) {
                    return 1;
                }

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
