package net.buggy.shoplist.components;


import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.design.widget.CheckableImageButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

import net.buggy.components.TagFlag;
import net.buggy.components.TagFlagContainer;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;

import java.util.Set;

import static net.buggy.shoplist.model.ModelHelper.buildStringQuantity;

public class ToBuyShopItemCellFactory extends CellFactory<ShopItem, ViewGroup> {

    public static final int DISABLED_FLAG_BORDER_COLOR = Color.parseColor("#b4b4b4");

    @Override
    public ViewGroup createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final ViewGroup view = (ViewGroup) inflater.inflate(
                R.layout.cell_to_buy_shop_item, parent, false);

        final TagFlagContainer categoriesContainer = view.findViewById(
                R.id.cell_to_buy_shop_item_categories);
        categoriesContainer.setTagWidth(ViewUtils.dpToPx(5, context));

        return view;
    }

    @Override
    public void fillCell(Cell<ShopItem> cell, final ViewGroup view,
                         final CellContext<ShopItem> cellContext, final ChangeListener<ShopItem> listener) {

        final ShopItem shopItem = cell.getData();
        final Product product = cell.getData().getProduct();

        final TextView itemNameField = view.findViewById(R.id.cell_to_buy_shop_item_name_field);
        itemNameField.setText(product.getName());

        final TextView quantityView = view.findViewById(R.id.cell_to_buy_shop_item_quantity_field);
        if (shopItem.getQuantity() != null) {
            quantityView.setText(buildStringQuantity(shopItem, view.getContext()));
        } else {
            quantityView.setText("");
        }

        quantityView.setOnClickListener(new QuantityClickListener(shopItem, listener, quantityView, new Supplier<UnitOfMeasure>() {
            @Override
            public UnitOfMeasure get() {
                return ModelHelper.getUnitOfMeasure(shopItem);
            }
        }));

        final TextView commentField = view.findViewById(
                R.id.cell_to_buy_shop_item_comment_field);
        updateCommentField(shopItem, commentField);

        final CheckableImageButton checkButton = view.findViewById(R.id.cell_to_buy_shop_item_check_field);
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.setSelected(!checkButton.isChecked());

                if (cellContext.getPrevCell() != null) {
                    listener.redraw(cellContext.getPrevCell());
                }
            }
        });

        final TagFlagContainer categoriesContainer = view.findViewById(
                R.id.cell_to_buy_shop_item_categories);
        final Set<Category> categories = product.getCategories();
        final int categoriesMarginDp = Math.max(8 - categories.size(), 1);
        categoriesContainer.setTagMargin(ViewUtils.dpToPx(categoriesMarginDp, view.getContext()));

        final View separator = view.findViewById(R.id.cell_to_buy_shop_item_separator);

        view.setSelected(cell.isSelected());
        itemNameField.setEnabled(!cell.isSelected());
        commentField.setEnabled(!cell.isSelected());
        quantityView.setEnabled(!cell.isSelected());
        checkButton.setChecked(cell.isSelected());

        if (cell.isSelected()) {
            final int disabledBackground = ViewUtils.resolveColor(
                    R.color.color_disabled_background, view.getContext());
            final Multiset<Integer> colors = LinkedHashMultiset.create();
            for (int i = 0; i < categories.size(); i++) {
                colors.add(disabledBackground);
            }
            categoriesContainer.setColors(colors);
            categoriesContainer.setFlagBorderColor(DISABLED_FLAG_BORDER_COLOR);

            itemNameField.setPaintFlags(itemNameField.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            commentField.setPaintFlags(commentField.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            separator.setVisibility(View.INVISIBLE);

        } else {
            final Multiset<Integer> colors = ModelHelper.getColors(categories);
            categoriesContainer.setColors(colors);
            categoriesContainer.setFlagBorderColor(TagFlag.DEF_BORDER_COLOR);

            itemNameField.setPaintFlags(itemNameField.getPaintFlags() & ~(Paint.STRIKE_THRU_TEXT_FLAG));
            commentField.setPaintFlags(commentField.getPaintFlags() & ~(Paint.STRIKE_THRU_TEXT_FLAG));

            if ((cellContext.getNextCell() == null) || (cellContext.getNextCell().isSelected())) {
                separator.setVisibility(View.INVISIBLE);
            } else {
                separator.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateCommentField(ShopItem shopItem, TextView commentField) {
        String comment = shopItem.getComment();

        if (!Strings.isNullOrEmpty(comment)) {
            if (comment.length() > 30) {
                comment = comment.substring(0, 30);
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
