package net.buggy.shoplist.units;

import android.content.Context;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.buggy.components.TagFlag;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.ProductComparator;
import net.buggy.shoplist.components.SelectableProductCellFactory;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.units.views.InflatingViewRenderer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import yuku.ambilwarna.AmbilWarnaDialog;

import static net.buggy.shoplist.utils.CollectionUtils.findSame;

public class EditCategoryUnit extends Unit<ShopListActivity> {

    private final boolean newCategory;
    private final Category category;
    private final Set<Product> selectedProducts = new LinkedHashSet<>();
    private ToolbarRenderer toolbarRenderer;

    public EditCategoryUnit(Category category, boolean newCategory) {
        this.category = category;
        this.newCategory = newCategory;
    }

    @Override
    public void start() {
        final ShopListActivity activity = getHostingActivity();
        final DataStorage dataStorage = activity.getDataStorage();
        final List<Product> products = dataStorage.getProducts();
        for (Product product : products) {
            if (product.getCategories().contains(category)) {
                selectedProducts.add(product);
            }
        }

        toolbarRenderer = new ToolbarRenderer(category.getName(), newCategory, activity);
        addRenderer(ShopListActivity.TOOLBAR_VIEW_ID, toolbarRenderer);
        addRenderer(ShopListActivity.MAIN_VIEW_ID, new MainViewRenderer());
    }

    private class MainViewRenderer extends InflatingViewRenderer<ShopListActivity, ViewGroup> {

        private transient FactoryBasedAdapter<Product> adapter;

        public MainViewRenderer() {
            super(R.layout.unit_edit_category);
        }

        @Override
        public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
            super.renderTo(parentView, activity);

            initProductsList(parentView, activity);

            final EditText nameField = initNameField(parentView);

            final TagFlag colorFlag = initColorFlag(parentView);

            final FloatingActionButton acceptButton = (FloatingActionButton) parentView.findViewById(
                    R.id.unit_edit_category_button_accept);
            acceptButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            final String name = toName(nameField.getText().toString());

                            if (name.isEmpty()) {
                                final Toast toast = Toast.makeText(view.getContext(),
                                        R.string.unit_edit_category_empty_name_not_allowed, Toast.LENGTH_LONG);
                                toast.show();
                                return;
                            }

                            if (!ModelHelper.isUnique(category, name, activity)) {
                                final Toast toast = Toast.makeText(
                                        getHostingActivity(),
                                        getHostingActivity().getResources().getString(R.string.category_already_exists),
                                        Toast.LENGTH_LONG);
                                toast.show();
                                return;
                            }

                            category.setName(toName(name));
                            category.setColor(colorFlag.getColor());

                            fireEvent(new CategoryEditedEvent(category, selectedProducts));
                            activity.stopUnit(EditCategoryUnit.this);
                        }
                    }
            );
        }

        private EditText initNameField(ViewGroup parentView) {
            final EditText nameField = (EditText) parentView.findViewById(R.id.unit_edit_category_name_field);
            nameField.setText(category.getName());

            if (!newCategory) {
                nameField.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        toolbarRenderer.setCategoryName(toName(s.toString()));
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });
            }


            return nameField;
        }

        private TagFlag initColorFlag(ViewGroup parentView) {
            final TagFlag colorFlag = (TagFlag) parentView.findViewById(R.id.unit_edit_category_color_field);
            colorFlag.setColor(ModelHelper.getColor(category));
            colorFlag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AmbilWarnaDialog dialog = new AmbilWarnaDialog(
                            colorFlag.getContext(),
                            colorFlag.getColor(),
                            new AmbilWarnaDialog.OnAmbilWarnaListener() {
                                @Override
                                public void onCancel(AmbilWarnaDialog dialog) {
                                }

                                @Override
                                public void onOk(AmbilWarnaDialog dialog, int color) {
                                    colorFlag.setColor(color);
                                    category.setColor(color);

                                    for (Product selectedProduct : selectedProducts) {
                                        final Category productCategory = findSame(selectedProduct.getCategories(), category);
                                        if (productCategory != null) {
                                            productCategory.setColor(color);
                                        }

                                        adapter.update(selectedProduct);
                                    }
                                }
                            });

                    ViewUtils.showStyled(dialog.getDialog());
                }
            });

            return colorFlag;
        }

        private String toName(String text) {
            return text.trim();
        }

        private void initProductsList(ViewGroup parentView, ShopListActivity activity) {
            adapter = new FactoryBasedAdapter<>(new SelectableProductCellFactory());
            adapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.MULTI);
            adapter.setSorter(new ProductComparator());
            final DataStorage dataStorage = activity.getDataStorage();
            adapter.addAll(dataStorage.getProducts());

            for (Product selectedProduct : selectedProducts) {
                adapter.selectItem(selectedProduct);
            }

            adapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<Product>() {
                @Override
                public void selectionChanged(Product item, boolean selected) {
                    if (selected) {
                        item.getCategories().add(category);
                        selectedProducts.add(item);
                    } else {
                        item.getCategories().remove(category);
                        selectedProducts.remove(item);
                    }
                }
            });

            final RecyclerView productListView = (RecyclerView) parentView.findViewById(
                    R.id.unit_edit_category_products_list);
            ListDecorator.decorateList(productListView);
            productListView.setAdapter(adapter);
        }
    }

    private static class ToolbarRenderer extends InflatingViewRenderer<ShopListActivity, ViewGroup> {
        private String categoryName;
        private final boolean newCategory;
        private final Context context;
        private TextView titleView;

        public ToolbarRenderer(String categoryName, boolean newCategory, Context context) {
            super(R.layout.unit_edit_category_toolbar);

            this.categoryName = categoryName;
            this.newCategory = newCategory;
            this.context = context;
        }

        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            super.renderTo(parentView, activity);

            titleView = (TextView) parentView.findViewById(R.id.unit_edit_category_title);
            titleView.setText(getTitle());
        }

        public String getTitle() {
            String name = newCategory
                    ? context.getResources().getString(R.string.unit_edit_category_new_category)
                    : categoryName;

            return context.getResources().getString(
                    R.string.unit_edit_category_title,
                    name);
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;

            if (titleView != null) {
                titleView.setText(getTitle());
            }
        }
    }

    public static class CategoryEditedEvent {
        private final Set<Product> categoryProducts;
        private final Category category;

        public CategoryEditedEvent(Category category, Set<Product> categoryProducts) {
            this.category = category;
            this.categoryProducts = categoryProducts;
        }

        public Set<Product> getCategoryProducts() {
            return categoryProducts;
        }

        public Category getCategory() {
            return category;
        }
    }
}
