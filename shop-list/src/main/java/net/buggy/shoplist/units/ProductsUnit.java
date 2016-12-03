package net.buggy.shoplist.units;

import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.google.common.base.Objects;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.SwipeToRemoveHandler;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ProductComparator;
import net.buggy.shoplist.components.ListDecorator;
import net.buggy.shoplist.components.ProductCellFactory;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.views.InflatingViewRenderer;
import net.buggy.shoplist.units.views.ViewRenderer;

import java.util.ArrayList;
import java.util.List;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class ProductsUnit extends Unit<ShopListActivity> {

    private transient FactoryBasedAdapter<Product> adapter;

    @Override
    public void start() {
        addRenderer(MAIN_VIEW_ID, new MainViewRenderer());
        addRenderer(TOOLBAR_VIEW_ID,
                new InflatingViewRenderer<ShopListActivity, ViewGroup>(R.layout.unit_products_toolbar));
    }

    @Override
    protected void onEvent(Object event) {
        final DataStorage dataStorage = getHostingActivity().getDataStorage();

        if (event instanceof EditProductUnit.ProductEditedEvent) {
            final Product product = ((EditProductUnit.ProductEditedEvent) event).getProduct();

            if (product.getId() == null) {
                dataStorage.addProduct(product);
                adapter.add(product);

            } else {
                dataStorage.saveProduct(product);
                adapter.update(product);
            }

            return;
        }

        super.onEvent(event);
    }

    private List<ShopItem> findLinkedItems(DataStorage dataStorage, Product product) {
        final List<ShopItem> shopItems = dataStorage.getShopItems();
        final List<ShopItem> linkedItems = new ArrayList<>();
        for (ShopItem shopItem : shopItems) {
            if (Objects.equal(product, shopItem.getProduct())) {
                linkedItems.add(shopItem);
            }
        }
        return linkedItems;
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(RelativeLayout parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_products, parentView, true);

            setupList(parentView, activity);

            setupAddButton(parentView, activity);

            setupSwipeToRefresh(parentView, activity);
        }

        private void setupList(RelativeLayout parentView, final ShopListActivity activity) {
            adapter = new FactoryBasedAdapter<>(new ProductCellFactory());
            adapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.SINGLE);
            adapter.setSorter(new ProductComparator());

            final DataStorage dataStorage = activity.getDataStorage();
            adapter.addAll(dataStorage.getProducts());

            final RecyclerView productsList = (RecyclerView) parentView.findViewById(R.id.unit_products_list);
            ListDecorator.decorateList(productsList);
            productsList.setAdapter(adapter);

            adapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<Product>() {
                @Override
                public void selectionChanged(Product item, boolean selected) {
                    if (selected) {
                        adapter.deselectItem(item);
                        final EditProductUnit editProductUnit = new EditProductUnit(item, false);
                        editProductUnit.setListeningUnit(ProductsUnit.this);
                        activity.startUnit(editProductUnit);
                    }
                }
            });

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<Product>() {
                @Override
                public void added(Product item) {
                }

                @Override
                public void removed(Product item) {
                    final List<ShopItem> linkedItems = findLinkedItems(dataStorage, item);
                    for (ShopItem linkedItem : linkedItems) {
                        dataStorage.removeShopItem(linkedItem);
                    }

                    dataStorage.removeProduct(item);
                }

                @Override
                public void changed(Product changedItem) {
                }
            });

            final int backgroundColor = ViewUtils.resolveColor(R.color.color_underground, parentView.getContext());
            final SwipeToRemoveHandler handler = new SwipeToRemoveHandler(backgroundColor);
            handler.attach(productsList);
            handler.setDeletionHandler(new SwipeToRemoveHandler.DeletionHandler() {
                @Override
                public boolean canDelete(int position) {
                    return true;
                }

                @Override
                public void onDelete(int position, SwipeToRemoveHandler.DeletionCallback callback) {
                    final Product item = adapter.getItem(position);

                    final List<ShopItem> linkedItems = findLinkedItems(dataStorage, item);
                    if (linkedItems.isEmpty()) {
                        callback.delete();
                    } else {
                        callback.askConfirmation("Delete it from the cart?");
                    }
                }
            });
        }

        private void setupAddButton(RelativeLayout parentView, final ShopListActivity activity) {
            final FloatingActionButton buttonAdd = (FloatingActionButton) parentView.findViewById(
                    R.id.unit_products_list_button_add);
            buttonAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final EditProductUnit editProductUnit = new EditProductUnit(new Product(), true);
                    editProductUnit.setListeningUnit(ProductsUnit.this);
                    activity.startUnit(editProductUnit);
                }
            });
        }

        private void setupSwipeToRefresh(RelativeLayout parentView, final ShopListActivity activity) {
            final SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout)
                    parentView.findViewById(R.id.unit_products_swipe_refresh);
            swipeRefreshLayout.setColorSchemeResources(R.color.color_primary);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    final DataStorage dataStorage = activity.getDataStorage();

                    adapter.clear();
                    adapter.addAll(dataStorage.getProducts());

                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }
    }
}
