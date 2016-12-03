package net.buggy.shoplist.units;


import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import com.android.internal.util.Predicate;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.SwipeToRemoveHandler;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ShopItemComparator;
import net.buggy.shoplist.components.CategoriesSpinner;
import net.buggy.shoplist.components.ListDecorator;
import net.buggy.shoplist.components.ShopItemCellFactory;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.views.ViewRenderer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShopItemListUnit extends Unit<ShopListActivity> {

    private transient FactoryBasedAdapter<ShopItem> adapter;

    @Override
    public void start() {
        addRenderer(ShopListActivity.MAIN_VIEW_ID, new MainViewRenderer());
        addRenderer(ShopListActivity.TOOLBAR_VIEW_ID, new ToolbarRenderer());
    }

    @Override
    protected void onEvent(Object event) {
        if (event instanceof SelectProductsUnit.ProductsSelectedEvent) {
            final Collection<SelectProductsUnit.RawShopItem> rawShopItems =
                    ((SelectProductsUnit.ProductsSelectedEvent) event).getRawItems();

            final DataStorage dataStorage = getHostingActivity().getDataStorage();
            for (SelectProductsUnit.RawShopItem rawShopItem : rawShopItems) {
                final ShopItem shopItem = new ShopItem();
                shopItem.setProduct(rawShopItem.getProduct());
                shopItem.setQuantity(rawShopItem.getQuantity());

                dataStorage.addShopItem(shopItem);

                adapter.add(shopItem);
            }

            return;
        }

        super.onEvent(event);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout>{

        @Override
        public void renderTo(RelativeLayout parentView, final ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(
                    R.layout.unit_shop_item_list,
                    parentView,
                    true);

            final RecyclerView itemsList = (RecyclerView) parentView.findViewById(R.id.shopping_list);
            ListDecorator.decorateList(itemsList);

            final int backgroundColor = ViewUtils.resolveColor(R.color.color_underground, parentView.getContext());
            final SwipeToRemoveHandler handler = new SwipeToRemoveHandler(backgroundColor);
            handler.attach(itemsList);

            adapter = new FactoryBasedAdapter<>(
                    new ShopItemCellFactory());
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

            final FloatingActionButton addShopItemButton = (FloatingActionButton) parentView.findViewById(R.id.unit_shop_item_list_button_add);
            addShopItemButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<ShopItem> availableShopItems = adapter.getAllItems();
                    Set<Product> existingProducts = new LinkedHashSet<>();
                    for (ShopItem availableShopItem : availableShopItems) {
                        existingProducts.add(availableShopItem.getProduct());
                    }

                    final SelectProductsUnit selectProductsUnit = new SelectProductsUnit(existingProducts);
                    selectProductsUnit.setListeningUnit(ShopItemListUnit.this);
                    activity.startUnit(selectProductsUnit);
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
        }
    }

    private class ToolbarRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(RelativeLayout parentView, ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(
                    R.layout.unit_shop_item_list_toolbar,
                    parentView,
                    true);

            final DataStorage dataStorage = activity.getDataStorage();
            CategoriesSpinner categoriesSpinner = (CategoriesSpinner) parentView.findViewById(
                    R.id.unit_shop_items_categories_spinner);
            categoriesSpinner.setCategories(dataStorage.getCategories());
            categoriesSpinner.setListener(new CategoriesSpinner.Listener() {
                @Override
                public void categorySelected(Category category) {
                    filterShopItems(category);
                }
            });
        }
    }

    private void filterShopItems(final Category category) {
        adapter.setFilter(new Predicate<ShopItem>() {
            @Override
            public boolean apply(ShopItem shopItem) {
                if (category == null) {
                    return true;
                }

                final Set<Category> categories = shopItem.getProduct().getCategories();
                return categories.contains(category);
            }
        });
    }
}
