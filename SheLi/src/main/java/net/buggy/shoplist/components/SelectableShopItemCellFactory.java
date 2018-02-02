package net.buggy.shoplist.components;


import android.content.Context;
import android.graphics.Color;
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
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;

import static net.buggy.shoplist.model.ModelHelper.MIN_OVERDUE_AGE_PERCENT;

public class SelectableShopItemCellFactory extends CellFactory<ShopItem, ViewGroup> {

    public static final int MIN_OVERDUE_COLOR = Color.argb(255, 255, 230, 95);
    public static final int MAX_OVERDUE_COLOR = Color.argb(255, 255, 95, 95);
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

        final TextView itemNameField = view.findViewById(R.id.cell_selectable_shop_item_name_field);
        itemNameField.setText(product.getName());

        final TextView commentField = view.findViewById(
                R.id.cell_selectable_shop_item_comment_field);

        String comment = buildComment(shopItem, view.getContext());
        if (Strings.isNullOrEmpty(comment)) {
            commentField.setVisibility(View.GONE);
        } else {
            commentField.setText(comment);
        }

        final ImageButton editButton = view.findViewById(R.id.cell_selectable_shop_item_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectableShopItemCellFactory.this.listener.onEditClick(shopItem);
            }
        });

        final TextView ageField = view.findViewById(
                R.id.cell_selectable_shop_item_age_field);
        String ageText = ModelHelper.getAgeText(product.getLastBuyDate(), view.getContext());
        ageField.setText(ageText);

        final View overdueIndicator = view.findViewById(
                R.id.cell_selectable_shop_item_overdue_indicator);
        int agePercent = ModelHelper.ageToPercent(product);
        if (agePercent < MIN_OVERDUE_AGE_PERCENT) {
            overdueIndicator.setVisibility(View.INVISIBLE);
        } else {
            overdueIndicator.setVisibility(View.VISIBLE);

            float overdueScale = (Math.min(agePercent, 100) - MIN_OVERDUE_AGE_PERCENT) / 25f;
            final int color = ViewUtils.pickFromColorScale(MIN_OVERDUE_COLOR, MAX_OVERDUE_COLOR, overdueScale);
            overdueIndicator.setBackgroundColor(color);
        }

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
