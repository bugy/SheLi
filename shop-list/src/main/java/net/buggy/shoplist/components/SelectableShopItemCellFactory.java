package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.StringUtils;

public class SelectableShopItemCellFactory extends CellFactory<ShopItem, ViewGroup> {

    private final Listener listener;

    public SelectableShopItemCellFactory(Listener listener) {
        Preconditions.checkNotNull(listener);

        this.listener = listener;
    }

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(
                R.layout.cell_selectable_shop_item, parent, false);
    }

    @Override
    public void fillCell(final Cell<ShopItem> cell, final ViewGroup view, CellContext<ShopItem> cellContext, final ChangeListener<ShopItem> listener) {
        final ShopItem shopItem = cell.getData();
        final Product product = shopItem.getProduct();

        view.setSelected(cell.isSelected());

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_selectable_shop_item_name_field);
        itemNameField.setText(product.getName());

        final TextView quantityView = (TextView) view.findViewById(R.id.cell_selectable_shop_item_quantity_field);
        final TextView commentEditField = (TextView) view.findViewById(
                R.id.cell_selectable_shop_item_comment_field);
        final ImageButton editButton = (ImageButton) view.findViewById(R.id.cell_selectable_shop_item_edit_button);

        if (!cell.isEnabled()) {
            final int disabledColor = ViewUtils.resolveColor(R.color.color_disabled_background, view.getContext());
            view.setBackgroundColor(disabledColor);

            itemNameField.setAlpha(0.6f);
            commentEditField.setAlpha(0.6f);

            quantityView.setVisibility(View.INVISIBLE);

            editButton.setVisibility(View.GONE);

        } else {
            itemNameField.setAlpha(1);
            commentEditField.setAlpha(1);

            view.setBackgroundResource(R.drawable.selectable_background);

            quantityView.setVisibility(View.VISIBLE);
            editButton.setVisibility(View.VISIBLE);

            if (shopItem.getQuantity() != null) {
                quantityView.setText(StringUtils.toString(shopItem.getQuantity()));
            } else {
                quantityView.setText("");
            }
            quantityView.setOnClickListener(new QuantityClickListener(shopItem, listener, quantityView));
        }

        commentEditField.setText(shopItem.getComment());
        if (Strings.isNullOrEmpty(shopItem.getComment())) {
            commentEditField.setVisibility(View.GONE);
        }

        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectableShopItemCellFactory.this.listener.onEditClick(shopItem);
            }
        });
    }

    public interface Listener {
        void onEditClick(ShopItem shopItem);
    }
}
