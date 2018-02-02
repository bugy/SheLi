package net.buggy.shoplist.units;

import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.components.list.SwipeToRemoveHandler;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ProductComparator;
import net.buggy.shoplist.components.ProductCellFactory;
import net.buggy.shoplist.components.SearchEditText;
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.UiThreadEntityListener;
import net.buggy.shoplist.filters.ProductsFilter;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.views.InflatingViewRenderer;
import net.buggy.shoplist.units.views.ViewRenderer;

import java.util.Collections;
import java.util.List;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;
import static net.buggy.shoplist.units.UnitsHelper.addTemporalDaoListener;

public class ProductsUnit extends Unit<ShopListActivity> {

    @Override
    public void initialize() {
        addRenderer(MAIN_VIEW_ID, new MainViewRenderer());
        addRenderer(TOOLBAR_VIEW_ID, new ToolbarRenderer());
    }

    @Override
    protected void onEvent(Object event) {
        final Dao dao = getHostingActivity().getDao();

        if (event instanceof EditProductUnit.ProductEditedEvent) {
            final Product product = ((EditProductUnit.ProductEditedEvent) event).getProduct();

            if (product.getId() == null) {
                dao.addProduct(product);

            } else {
                dao.saveProduct(product);
            }

            return;
        }

        super.onEvent(event);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {

        private transient FactoryBasedAdapter<Product> adapter;
        private transient String searchedText;

        @Override
        public void renderTo(RelativeLayout parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_products, parentView, true);

            setupList(parentView, activity);

            setupAddButton(parentView, activity);

            setupSwipeToRefresh(parentView, activity);

            setupSearchField(parentView);
        }

        private void setupList(RelativeLayout parentView, final ShopListActivity activity) {
            adapter = new FactoryBasedAdapter<>(new ProductCellFactory());
            adapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.SINGLE);
            adapter.setSorter(new ProductComparator());

            final Dao dao = activity.getDao();
            adapter.addAll(dao.getProducts());

            final RecyclerView productsList = parentView.findViewById(R.id.unit_products_list);
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
                    final List<ShopItem> linkedItems = dao.findLinkedItems(item);
                    for (ShopItem linkedItem : linkedItems) {
                        dao.removeShopItem(linkedItem);
                    }

                    dao.removeProduct(item);
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

                    final List<ShopItem> linkedItems = dao.findLinkedItems(item);
                    if (linkedItems.isEmpty()) {
                        callback.delete();
                    } else {
                        callback.askConfirmation(activity.getString(
                                R.string.products_unit_delete_from_cart));
                    }
                }
            });

            final UiThreadEntityListener<Product> listener = new TableAdapterEntityListener<>(
                    adapter, getHostingActivity());
            addTemporalDaoListener(dao, Product.class, listener, ProductsUnit.this);
        }

        private void setupAddButton(RelativeLayout parentView, final ShopListActivity activity) {
            final FloatingActionButton buttonAdd = parentView.findViewById(
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
            final SwipeRefreshLayout swipeRefreshLayout = parentView.findViewById(R.id.unit_products_swipe_refresh);
            swipeRefreshLayout.setColorSchemeResources(R.color.color_primary);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    final Dao dao = activity.getDao();

                    adapter.clear();
                    adapter.addAll(dao.getProducts());

                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }

        private void setupSearchField(RelativeLayout parentView) {
            final SearchEditText searchTextField = parentView.findViewById(
                    R.id.unit_products_search_field);

            searchTextField.setListener(new SearchEditText.Listener() {
                @Override
                public void onTextChanged(String text) {
                    searchedText = text;

                    filterProducts(searchedText);
                }

                @Override
                public void onFinish(String text) {
                }
            });


            final AppCompatImageView searchIcon = parentView.findViewById(R.id.unit_products_search_icon);
            ViewUtils.setTint(searchIcon, R.color.color_primary_icons);
        }

        private void filterProducts(String searchedText) {
            adapter.setFilter(new ProductsFilter(searchedText, Collections.<Category>emptyList()));
        }
    }

    private class ToolbarRenderer extends InflatingViewRenderer<ShopListActivity, ViewGroup> {
        public ToolbarRenderer() {
            super(R.layout.unit_products_toolbar);
        }

        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            super.renderTo(parentView, activity);
        }
    }
}
