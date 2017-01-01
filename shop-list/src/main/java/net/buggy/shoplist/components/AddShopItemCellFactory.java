package net.buggy.shoplist.components;


import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.Cell;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ShopItem;

public class AddShopItemCellFactory extends ShopItemCellFactory {

    public AddShopItemCellFactory() {
        super(R.layout.cell_add_shop_item);
    }

    @Override
    public void fillCell(Cell<ShopItem> cell, ViewGroup view, boolean newCell, ChangeListener<ShopItem> listener) {
        super.fillCell(cell, view, newCell, listener);

        view.setSelected(cell.isSelected());

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_shop_item_name_field);
        final TextView quantityView = (TextView) view.findViewById(R.id.cell_shop_item_quantity_field);
        final EditText commentEditField = (EditText) view.findViewById(
                R.id.cell_shop_item_comment_field);
        final ImageButton commentButton = (ImageButton) view.findViewById(R.id.cell_shop_item_comment_button);

        if (!cell.isEnabled()) {
            final int disabledColor = ViewUtils.resolveColor(R.color.color_disabled_background, view.getContext());
            view.setBackgroundColor(disabledColor);

            itemNameField.setAlpha(0.6f);
            commentEditField.setAlpha(0.6f);

            quantityView.setVisibility(View.INVISIBLE);

            commentButton.setVisibility(View.GONE);
            commentEditField.setFocusable(false);

        } else {
            itemNameField.setAlpha(1);
            commentEditField.setAlpha(1);

            view.setBackgroundResource(R.drawable.selectable_background);

            quantityView.setVisibility(View.VISIBLE);

            commentEditField.setFocusable(true);
            commentButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void commentButtonClicked(EditText commentEditField, ChangeListener<ShopItem> listener) {
        super.commentButtonClicked(commentEditField, listener);

        listener.setSelected(true);
    }

    @Override
    protected void commentFocusChanged(boolean newFocused, ChangeListener<ShopItem> listener, EditText commentEditField) {
        super.commentFocusChanged(newFocused, listener, commentEditField);

        if (newFocused) {
            listener.setSelected(true);
        }
    }
}
