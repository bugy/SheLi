package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Strings;
import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.StringUtils;

public class ToBuyShopItemCellFactory extends CellFactory<ShopItem, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(
                R.layout.cell_to_buy_shop_item, parent, false);
    }

    @Override
    public void fillCell(Cell<ShopItem> cell, final ViewGroup view, boolean newCell, ChangeListener<ShopItem> listener) {
        final ShopItem shopItem = cell.getData();
        final Product product = cell.getData().getProduct();

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_to_buy_shop_item_name_field);
        itemNameField.setText(product.getName());

        final TextView quantityView = (TextView) view.findViewById(R.id.cell_to_buy_shop_item_quantity_field);
        quantityView.setText(StringUtils.toString(shopItem.getQuantity()));
        quantityView.setOnClickListener(new QuantityClickListener(shopItem, listener, quantityView));

        setCommentField(view, shopItem, product);

        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_to_buy_shop_item_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(product.getCategories());
        categoriesContainer.setColors(colors);
    }

    private void setCommentField(ViewGroup view, ShopItem shopItem, Product product) {
        final TextView commentField = (TextView) view.findViewById(
                R.id.cell_to_buy_shop_item_comment_field);
        String comment = shopItem.getComment();
        boolean ellipsize = false;
        if (Strings.isNullOrEmpty(shopItem.getComment())) {
            if (!Strings.isNullOrEmpty(product.getNote())) {
                comment = product.getNote();
            }
        } else if (!Strings.isNullOrEmpty(product.getNote())) {
            ellipsize = true;
            comment += " ";
        }

        if (!Strings.isNullOrEmpty(comment)) {
            if (comment.length() > 30) {
                comment = comment.substring(0, 30);
                ellipsize = true;
            }

            if (ellipsize) {
                comment += "...";
            }
        }

        if (Strings.isNullOrEmpty(comment)) {
            commentField.setVisibility(View.GONE);
        } else {
            commentField.setVisibility(View.VISIBLE);
            commentField.setText(comment);
        }
    }
}
