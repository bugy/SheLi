package net.buggy.shoplist.units;


import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Objects;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.CategoryComparator;
import net.buggy.shoplist.components.CategoryCellFactory;
import net.buggy.shoplist.components.ListDecorator;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class EditProductUnit extends Unit<ShopListActivity> {

    private final Product product;
    private final boolean newProduct;

    private transient ToolbarRenderer toolbarRenderer;

    private final Set<Category> selectedCategories = Collections.newSetFromMap(
            new ConcurrentHashMap<Category, Boolean>());

    private transient FactoryBasedAdapter<Category> categoriesAdapter;

    public EditProductUnit(Product product, boolean newProduct) {
        this.newProduct = newProduct;
        this.product = product;
    }

    @Override
    public void start() {
        String productName;
        if (newProduct) {
            productName = getHostingActivity().getString(R.string.unit_edit_product_new_product);

        } else {
            productName = product.getName();
            selectedCategories.addAll(product.getCategories());
        }

        toolbarRenderer = new ToolbarRenderer(productName);
        addRenderer(TOOLBAR_VIEW_ID, toolbarRenderer);

        MainViewRenderer mainViewRenderer = new MainViewRenderer();
        addRenderer(MAIN_VIEW_ID, mainViewRenderer);
    }

    public static final class ProductEditedEvent {
        private final Product product;

        public ProductEditedEvent(Product product) {
            this.product = product;
        }

        public Product getProduct() {
            return product;
        }
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private boolean firstRender = true;

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = activity.getLayoutInflater();

            inflater.inflate(R.layout.unit_edit_product, parentView, true);

            final EditText nameField = (EditText) parentView.findViewById(R.id.edit_product_name_field);
            nameField.setText(product.getName());
            nameField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!newProduct) {
                        final String name = toName(s);
                        toolbarRenderer.setProductName(name);
                    }
                }
            });

            final EditText noteField = (EditText) parentView.findViewById(R.id.unit_edit_product_note_field);
            if (product.getNote() != null) {
                noteField.setText(product.getNote());
            }

            final FloatingActionButton saveButton = (FloatingActionButton) parentView.findViewById(R.id.unit_edit_product_save_button);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String name = toName(nameField.getText());
                    if (name.isEmpty()) {
                        final Toast toast = Toast.makeText(parentView.getContext(),
                                "Empty name is not allowed", Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }

                    final List<Product> products = activity.getDataStorage().getProducts();
                    for (Product anotherProduct : products) {
                        if (Objects.equal(anotherProduct, product)) {
                            continue;
                        }

                        if (StringUtils.equalIgnoreCase(anotherProduct.getName(), name)) {
                            final Toast toast = Toast.makeText(parentView.getContext(),
                                    activity.getString(
                                            R.string.products_unit_already_exists,
                                            anotherProduct.getName()),
                                    Toast.LENGTH_LONG);
                            toast.show();
                            return;
                        }
                    }

                    product.setCategories(categoriesAdapter.getSelectedItems());
                    product.setName(name);

                    final String note = noteField.getText().toString().trim();
                    product.setNote(note);

                    activity.stopUnit(EditProductUnit.this);

                    fireEvent(new ProductEditedEvent(product));
                }
            });

            initCategoriesSection(parentView, activity);

            if (newProduct && firstRender) {
                ViewUtils.focusTextField(nameField);
            }

            firstRender = false;
        }

        @NonNull
        private String toName(Editable editable) {
            return editable.toString().trim();
        }

        private void initCategoriesSection(ViewGroup parentView, final ShopListActivity activity) {
            final DataStorage dataStorage = activity.getDataStorage();

            categoriesAdapter = new FactoryBasedAdapter<>(new CategoryCellFactory());
            categoriesAdapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.MULTI);
            categoriesAdapter.setSorter(new CategoryComparator());
            categoriesAdapter.addAll(dataStorage.getCategories());
            for (Category selectedCategory : selectedCategories) {
                categoriesAdapter.selectItem(selectedCategory);
            }
            categoriesAdapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<Category>() {
                @Override
                public void selectionChanged(Category item, boolean selected) {
                    if (selected) {
                        selectedCategories.add(item);
                    } else {
                        selectedCategories.remove(item);
                    }
                }
            });

            final RecyclerView categoriesList = (RecyclerView) parentView.findViewById(
                    R.id.unit_edit_product_categories_list);
            ListDecorator.decorateList(categoriesList);
            categoriesList.setAdapter(categoriesAdapter);
        }
    }

    private static class ToolbarRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private transient String productName;
        private transient TextView titleField;
        private transient ShopListActivity activity;

        private ToolbarRenderer(String productName) {
            this.productName = productName;
        }

        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            this.activity = activity;
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_edit_product_toolbar, parentView, true);

            titleField = (TextView) parentView.findViewById(R.id.unit_edit_product_title);
            updateTitle();
        }

        private void updateTitle() {
            final String title = activity.getString(R.string.unit_edit_product_title, productName);
            titleField.setText(title);
        }

        public void setProductName(String productName) {
            this.productName = productName;

            if (titleField != null) {
                updateTitle();
            }
        }
    }

}
