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
import net.buggy.shoplist.components.ProductSelectCellFactory;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static net.buggy.components.list.FactoryBasedAdapter.SelectionMode.MULTI;

public class SelectProductsUnit extends Unit<ShopListActivity> {

    private static final int ACTIVITY_VIEW_ID = R.id.main_activity_view;
    private static final int TOOLBAR_VIEW_ID = R.id.toolbar_container;

    private transient FactoryBasedAdapter<RawShopItem> adapter;

    private final Set<Product> disabledProducts;
    private Category selectedCategory;
    private String filterText;

    public SelectProductsUnit(Collection<Product> disabledProducts) {
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

    private void sendSelectionEventAndExit(Collection<RawShopItem> rawShopItems) {
        ShopListActivity hostingActivity = getHostingActivity();

        hostingActivity.stopUnit(SelectProductsUnit.this);

        fireEvent(new ProductsSelectedEvent(rawShopItems));
    }

    public static final class ProductsSelectedEvent {
        private final Collection<RawShopItem> rawShopItems;

        public ProductsSelectedEvent(Collection<RawShopItem> rawShopItems) {
            this.rawShopItems = rawShopItems;
        }

        public Collection<RawShopItem> getRawItems() {
            return rawShopItems;
        }
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(final RelativeLayout parentView, final ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_select_products, parentView, true);

            final RecyclerView productsList = (RecyclerView) parentView.findViewById(R.id.unit_select_products_list);
            ListDecorator.decorateList(productsList);

            adapter = new FactoryBasedAdapter<>(
                    new ProductSelectCellFactory());
            adapter.setSelectionMode(MULTI);
            adapter.setSorter(createComparator());

            final List<Product> products = dataStorage.getProducts();
            for (Product product : products) {
                final RawShopItem shopItem = new RawShopItem(product);
                adapter.add(shopItem);

                if (disabledProducts.contains(product)) {
                    adapter.disableItem(shopItem);
                }
            }

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<RawShopItem>() {
                @Override
                public void added(RawShopItem item) {
                }

                @Override
                public void removed(RawShopItem item) {
                }

                @Override
                public void changed(RawShopItem changedItem) {
                    adapter.selectItem(changedItem);
                }
            });

            productsList.setAdapter(adapter);

            final View acceptButton = parentView.findViewById(R.id.unit_select_products_button_accept);
            acceptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final List<RawShopItem> selectedItems = adapter.getSelectedItems();

                    sendSelectionEventAndExit(selectedItems);
                }
            });

            final FastCreationPanel creationPanel = (FastCreationPanel)
                    parentView.findViewById(R.id.unit_select_products_creation_panel);
            final String searchHint = parentView.getResources().getString(R.string.unit_select_products_search_hint);
            creationPanel.setHint(searchHint);
            creationPanel.setListener(new FastCreationPanel.Listener() {
                @Override
                public void onCreate(String name) {
                    final List<RawShopItem> rawShopItems = adapter.getAllItems();
                    for (RawShopItem item : rawShopItems) {
                        final Product product = item.getProduct();

                        if (StringUtils.equalIgnoreCase(name, product.getName())) {
                            final Toast toast = Toast.makeText(
                                    parentView.getContext(),
                                    activity.getString(
                                            R.string.products_unit_already_exists,
                                            product.getName()),
                                    Toast.LENGTH_LONG);
                            toast.show();
                            adapter.selectItem(item);
                            return;
                        }
                    }

                    final Product product = new Product();
                    product.setName(name);

                    dataStorage.addProduct(product);

                    final RawShopItem newItem = new RawShopItem(product);
                    adapter.add(newItem);
                    adapter.selectItem(newItem);
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
    }

    private class ToolbarRenderer extends ViewRenderer<ShopListActivity, RelativeLayout> {
        @Override
        public void renderTo(RelativeLayout parentView, ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_select_products_toolbar, parentView, true);

            final CategoriesSpinner categorySpinner = (CategoriesSpinner) parentView.findViewById(
                    R.id.unit_select_products_category_spinner);
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

    private Comparator<RawShopItem> createComparator() {
        final ProductComparator productComparator = new ProductComparator();

        return new Comparator<RawShopItem>() {
            @Override
            public int compare(RawShopItem i1, RawShopItem i2) {
                final Product p1 = i1.getProduct();
                final Product p2 = i2.getProduct();

                if (SelectProductsUnit.this.disabledProducts.contains(p1)) {
                    if (!SelectProductsUnit.this.disabledProducts.contains(p2)) {
                        return 1;
                    }
                } else if (SelectProductsUnit.this.disabledProducts.contains(p2)) {
                    return -1;
                }

                return productComparator.compare(p1, p2);
            }
        };
    }

    private void filterProducts(final Category category, final String newText) {
        adapter.setFilter(new Predicate<RawShopItem>() {
            @Override
            public boolean apply(RawShopItem shopItem) {
                final Product product = shopItem.getProduct();

                if (!Strings.isNullOrEmpty(newText)) {
                    final String name = product.getName().toLowerCase();
                    final String text = newText.toLowerCase();

                    if (!name.contains(text)) {
                        return false;
                    }
                }

                if (category == null) {
                    return true;
                }

                return product.getCategories().contains(category);
            }
        });
    }

    public static final class RawShopItem {
        private final Product product;

        private BigDecimal quantity = BigDecimal.ONE;

        public RawShopItem(Product product) {
            this.product = product;
        }

        public Product getProduct() {
            return product;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }
    }
}
