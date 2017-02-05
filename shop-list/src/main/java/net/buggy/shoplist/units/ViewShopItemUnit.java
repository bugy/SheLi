package net.buggy.shoplist.units;


import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.components.QuantityClickListener;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class ViewShopItemUnit extends Unit<ShopListActivity> {

    private final ShopItem shopItem;

    public ViewShopItemUnit(ShopItem shopItem) {
        this.shopItem = shopItem;
    }

    @Override
    public void start() {
        addRenderer(TOOLBAR_VIEW_ID, new ToolbarRenderer(shopItem.getProduct().getName()));

        MainViewRenderer mainViewRenderer = new MainViewRenderer();
        addRenderer(MAIN_VIEW_ID, mainViewRenderer);
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = activity.getLayoutInflater();

            inflater.inflate(R.layout.unit_view_shop_item, parentView, true);

            final Product product = shopItem.getProduct();

            final TextView nameField = (TextView) parentView.findViewById(R.id.unit_view_shop_item_name_field);
            nameField.setText(product.getName());

            final TextView quantityField = (TextView) parentView.findViewById(R.id.unit_view_shop_item_quantity_field);
            if (shopItem.getQuantity() != null) {
                quantityField.setText(StringUtils.toString(shopItem.getQuantity()));
            } else {
                quantityField.setText(activity.getString(R.string.unit_shopitem_not_specified_quantity));
            }
            quantityField.setOnClickListener(new QuantityClickListener(shopItem, null, quantityField));

            final TextView noteField = (TextView) parentView.findViewById(R.id.unit_view_shop_item_note_field);
            if (product.getNote() != null) {
                noteField.setText(product.getNote());
            }

            final EditText commentField = (EditText) parentView.findViewById(
                    R.id.unit_view_shop_item_comment_field);
            if (shopItem.getComment() != null) {
                commentField.setText(shopItem.getComment());
            }

            final FloatingActionButton saveButton = (FloatingActionButton) parentView.findViewById(
                    R.id.unit_view_shop_item_save_button);

            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String comment = commentField.getText().toString().trim();

                    shopItem.setComment(comment);

                    activity.stopUnit(ViewShopItemUnit.this);

                    fireEvent(new ShopItemEditedEvent(shopItem));
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
            final String titleText = activity.getString(R.string.unit_view_shop_item_title, productName);
            titleField.setText(titleText);
        }
    }

    public static final class ShopItemEditedEvent {
        private final ShopItem shopItem;

        public ShopItemEditedEvent(ShopItem shopItem) {
            this.shopItem = shopItem;
        }

        public ShopItem getShopItem() {
            return shopItem;
        }
    }
}
