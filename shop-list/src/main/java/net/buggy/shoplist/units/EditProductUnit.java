package net.buggy.shoplist.units;


import android.graphics.PorterDuff;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import net.buggy.components.Chip;
import net.buggy.components.ViewUtils;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;

import org.apmem.tools.layouts.FlowLayout;

import java.util.List;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class EditProductUnit extends Unit<ShopListActivity> {

    private final Product product;
    private final boolean newProduct;

    private transient MainViewRenderer mainViewRenderer;

    public EditProductUnit(Product product, boolean newProduct) {
        this.newProduct = newProduct;
        this.product = product;
    }

    @Override
    public void start() {
        mainViewRenderer = new MainViewRenderer();
        addRenderer(MAIN_VIEW_ID, mainViewRenderer);

        String productName;
        if (newProduct) {
            productName = "new product";
        } else {
            productName = product.getName();
        }
        addRenderer(TOOLBAR_VIEW_ID, new ToolbarRenderer(productName));
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

    @Override
    protected void onEvent(Object event) {
        if (event instanceof SelectCategoriesUnit.CategoriesSelectedEvent) {
            final List<Category> categories =
                    ((SelectCategoriesUnit.CategoriesSelectedEvent) event).getSelectedCategories();

            product.setCategories(categories);

            mainViewRenderer.refillCategories();

            return;
        }

        super.onEvent(event);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private FlowLayout categoryChips;
        private boolean firstRender = true;

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = activity.getLayoutInflater();

            inflater.inflate(R.layout.unit_edit_product, parentView, true);

            final EditText nameField = (EditText) parentView.findViewById(R.id.edit_product_name_field);
            nameField.setText(product.getName());


            final FloatingActionButton saveButton = (FloatingActionButton) parentView.findViewById(R.id.unit_edit_product_save_button);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String name = nameField.getText().toString().trim();
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

                    product.setName(name);

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

        private void initCategoriesSection(ViewGroup parentView, final ShopListActivity activity) {
            final TextView categoriesLabel = (TextView) parentView.findViewById(R.id.edit_product_categories_label);
            final ImageButton categoriesButton = (ImageButton) parentView.findViewById(R.id.edit_product_select_categories);
            categoriesButton.setColorFilter(categoriesLabel.getCurrentTextColor(), PorterDuff.Mode.MULTIPLY);

            categoryChips = (FlowLayout) parentView.findViewById(R.id.edit_product_category_chips_container);
            refillCategories();

            categoriesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String productName = newProduct ? "New product" : product.getName();

                    final SelectCategoriesUnit unit = new SelectCategoriesUnit(
                            productName,
                            ImmutableList.copyOf(product.getCategories()));

                    activity.startUnit(unit);
                    unit.setListeningUnit(EditProductUnit.this);
                }
            });
        }

        public void refillCategories() {
            categoryChips.removeAllViews();
            for (final Category category : product.getCategories()) {
                final Chip chip = createChip(category.getName(), categoryChips);
                categoryChips.addView(chip);

                chip.addListener(new Chip.Listener() {
                    @Override
                    public void closeClicked() {
                        categoryChips.removeView(chip);
                        product.getCategories().remove(category);
                    }
                });
            }
        }

        private Chip createChip(String text, FlowLayout parentView) {
            final FlowLayout.LayoutParams layoutParams = new FlowLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(0, 0, ViewUtils.dpToPx(7, parentView.getContext()), 0);

            final Chip chip = new Chip(parentView.getContext());
            chip.setText(text);
            chip.setLayoutParams(layoutParams);

            return chip;
        }
    }

    private static class ToolbarRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private final String productName;

        private ToolbarRenderer(String productName) {
            this.productName = productName;
        }

        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_edit_product_toolbar, parentView, true);

            final TextView titleField = (TextView) parentView.findViewById(R.id.unit_edit_product_title);
            final String titlePattern = activity.getString(R.string.unit_edit_product_title);
            titleField.setText(String.format(titlePattern, productName));
        }
    }

}
