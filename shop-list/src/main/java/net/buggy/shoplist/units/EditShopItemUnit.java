package net.buggy.shoplist.units;


import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.spinners.MaterialSpinner;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.components.QuantityClickListener;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.PeriodType;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;
import net.buggy.shoplist.utils.UnitOfMeasureStringifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.buggy.components.ViewUtils.setTextWithoutAnimation;
import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class EditShopItemUnit extends Unit<ShopListActivity> {

    private final ShopItem shopItem;

    private final Set<Category> selectedCategories = Collections.newSetFromMap(
            new ConcurrentHashMap<Category, Boolean>());

    private transient FactoryBasedAdapter<Category> categoriesAdapter;

    public EditShopItemUnit(ShopItem shopItem) {
        this.shopItem = shopItem;

        selectedCategories.addAll(shopItem.getProduct().getCategories());
    }

    @Override
    public void initialize() {
        addRenderer(TOOLBAR_VIEW_ID, new ToolbarRenderer(shopItem.getProduct().getName()));

        MainViewRenderer mainViewRenderer = new MainViewRenderer();
        addRenderer(MAIN_VIEW_ID, mainViewRenderer);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = activity.getLayoutInflater();

            inflater.inflate(R.layout.unit_edit_shop_item, parentView, true);

            final Product product = shopItem.getProduct();

            final TextView nameField = (TextView) parentView.findViewById(R.id.unit_edit_shop_item_name_field);
            setTextWithoutAnimation(nameField, product.getName());

            final EditText quantityField = (EditText) parentView.findViewById(R.id.unit_edit_shop_item_quantity_field);
            if (shopItem.getQuantity() != null) {
                setTextWithoutAnimation(quantityField, StringUtils.toString(shopItem.getQuantity()));
            } else {
                setTextWithoutAnimation(quantityField, activity.getString(R.string.unit_shopitem_not_specified_quantity));
            }
            quantityField.setOnClickListener(new QuantityClickListener(shopItem, null, quantityField));

            final MaterialSpinner<UnitOfMeasure> unitsField = (MaterialSpinner<UnitOfMeasure>)
                    parentView.findViewById(R.id.unit_edit_shop_item_units_field);
            unitsField.setHint(activity.getString(R.string.unit_edit_shop_item_units_field_label));
            unitsField.setValues(Arrays.asList(UnitOfMeasure.values()));
            unitsField.setNullString(activity.getString(R.string.material_spinner_default_null_string));
            if (shopItem.getUnitOfMeasure() != null) {
                unitsField.setSelectedItem(shopItem.getUnitOfMeasure());
            } else if (product.getDefaultUnits() != null) {
                unitsField.setSelectedItem(product.getDefaultUnits());
            }
            unitsField.setStringConverter(new UnitOfMeasureStringifier(activity));

            final EditText commentField = (EditText) parentView.findViewById(
                    R.id.unit_edit_shop_item_comment_field);
            if (shopItem.getComment() != null) {
                setTextWithoutAnimation(commentField, shopItem.getComment());
            }

            @SuppressWarnings("unchecked")
            final MaterialSpinner<UnitOfMeasure> defaultUnitsField = (MaterialSpinner<UnitOfMeasure>)
                    parentView.findViewById(R.id.merge_edit_product_units_field);
            @SuppressWarnings("unchecked")
            final MaterialSpinner<PeriodType> periodTypeField = (MaterialSpinner<PeriodType>)
                    parentView.findViewById(R.id.merge_edit_product_period_type);
            final EditText periodCountField = (EditText) parentView.findViewById(
                    R.id.merge_edit_product_period_count);

            EditProductUnit.initEditProductFields(parentView, activity, product);
            categoriesAdapter = EditProductUnit.initCategoriesSection(
                    parentView, activity, selectedCategories);

            final FloatingActionButton saveButton = (FloatingActionButton) parentView.findViewById(
                    R.id.unit_edit_shop_item_save_button);

            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Product product = shopItem.getProduct();
                    final String productName = nameField.getText().toString().trim();
                    final boolean nameValid = EditProductUnit.validateProductName(
                            productName, product, parentView, activity);
                    if (!nameValid) {
                        return;
                    }

                    final String comment = commentField.getText().toString().trim();

                    shopItem.setComment(comment);

                    final UnitOfMeasure selectedItem = unitsField.getSelectedItem();
                    shopItem.setUnitOfMeasure(selectedItem);

                    product.setName(productName);
                    product.setDefaultUnits(defaultUnitsField.getSelectedItem());
                    final String periodCountText = periodCountField.getText().toString();
                    product.setPeriodCount(EditProductUnit.parsePeriodCount(periodCountText));
                    product.setPeriodType(periodTypeField.getSelectedItem());
                    product.setCategories(new LinkedHashSet<>(selectedCategories));

                    activity.stopUnit(EditShopItemUnit.this);

                    fireEvent(new ShopItemEditedEvent(shopItem, product));
                }
            });
        }
    }

    private static class ToolbarRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private transient String productName;

        private ToolbarRenderer(String productName) {
            this.productName = productName;
        }

        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_edit_product_toolbar, parentView, true);

            final TextView titleField = (TextView) parentView.findViewById(R.id.unit_edit_product_title);
            final String titleText = activity.getString(R.string.unit_edit_shop_item_title, productName);
            titleField.setText(titleText);
        }
    }

    public static final class ShopItemEditedEvent {
        private final ShopItem shopItem;
        private final Product product;

        public ShopItemEditedEvent(ShopItem shopItem, Product product) {
            this.shopItem = shopItem;
            this.product = product;
        }

        public ShopItem getShopItem() {
            return shopItem;
        }

        public Product getProduct() {
            return product;
        }
    }
}
