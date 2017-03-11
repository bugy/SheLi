package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;

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

        final TextView commentField = (TextView) view.findViewById(
                R.id.cell_selectable_shop_item_comment_field);

        String comment = buildComment(shopItem, view.getContext());
        if (Strings.isNullOrEmpty(comment)) {
            commentField.setVisibility(View.GONE);
        } else {
            commentField.setText(comment);
        }

        final ImageButton editButton = (ImageButton) view.findViewById(R.id.cell_selectable_shop_item_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectableShopItemCellFactory.this.listener.onEditClick(shopItem);
            }
        });

        final View separator = view.findViewById(
                R.id.cell_selectable_shop_item_separator);
        if ((cellContext.getNextCell() == null)
                || (cellContext.getNextCell().isSelected() != cell.isSelected())) {
            separator.setVisibility(View.INVISIBLE);
        } else {
            separator.setVisibility(View.VISIBLE);
        }
    }

    private String buildComment(ShopItem shopItem, Context context) {
        String result = "";

        if (shopItem.getQuantity() != null) {
            result += ModelHelper.buildStringQuantity(shopItem, context);
        }

        if (!Strings.isNullOrEmpty(shopItem.getComment())) {
            if (!result.isEmpty()) {
                result += ", ";
            }

            result += shopItem.getComment();
        }

        return result;
    }

    public interface Listener {
        void onEditClick(ShopItem shopItem);
    }
}
