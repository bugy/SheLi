package net.buggy.shoplist.components;


import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.common.base.Strings;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.Cell;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;

public class ToBuyShopItemCellFactory extends ShopItemCellFactory {

    public ToBuyShopItemCellFactory() {
        super(R.layout.cell_to_buy_shop_item);
    }

    @Override
    public ViewGroup createEmptyCell(Context context, ViewGroup parent) {
        final ViewGroup view = super.createEmptyCell(context, parent);

        final TextView nameField = (TextView) view.findViewById(R.id.cell_shop_item_name_field);
        nameField.setTypeface(nameField.getTypeface(), Typeface.BOLD);

        return view;
    }

    @Override
    public void fillCell(Cell<ShopItem> cell, final ViewGroup view, boolean newCell, ChangeListener<ShopItem> listener) {
        super.fillCell(cell, view, newCell, listener);

        final ImageButton infoButton = (ImageButton) view.findViewById(R.id.cell_to_buy_shop_item_info_button);

        final Product product = cell.getData().getProduct();

        if (Strings.isNullOrEmpty(product.getNote())) {
            infoButton.setVisibility(View.GONE);
        } else {
            infoButton.setVisibility(View.VISIBLE);
        }

        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clickedView) {
                final Context context = clickedView.getContext();
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                final ViewGroup dialogContent = (ViewGroup) inflater.inflate(
                        R.layout.dialog_shop_item_info, null);

                final TextView noteField = (TextView) dialogContent.findViewById(R.id.dialog_shopitem_info_note_field);
                noteField.setText(product.getNote());

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);

                final String title = context.getString(
                        R.string.cell_shop_item_info_dialog_title, product.getName());
                builder.setTitle(title);
                builder.setView(dialogContent);


                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

                final android.app.AlertDialog dialog = builder.create();
                ViewUtils.showStyled(dialog);
            }
        });
    }
}
