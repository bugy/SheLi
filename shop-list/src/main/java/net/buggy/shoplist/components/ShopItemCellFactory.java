package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;

public class ShopItemCellFactory implements CellFactory<ShopItem, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(R.layout.cell_shop_item, parent, false);
    }

    @Override
    public void fillCell(final ShopItem shopItem, final ViewGroup view, final ChangeListener<ShopItem> listener, boolean selected, boolean enabled) {
        String itemInfo = shopItem.getProduct().getName();

        final TextView quantityField = (TextView) view.findViewById(R.id.cell_shop_item_quantity_field);
        quantityField.setText(String.valueOf(shopItem.getQuantity()));
        quantityField.setOnClickListener(new QuantityClickListener(shopItem, listener, quantityField));

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_shop_item_name_field);
        itemNameField.setText(itemInfo);

        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_shop_item_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(shopItem.getProduct().getCategories());
        categoriesContainer.setColors(colors);
    }

    private static class QuantityClickListener implements View.OnClickListener {
        private final ShopItem shopItem;
        private final ChangeListener<ShopItem> listener;
        private final TextView quantityField;

        public QuantityClickListener(
                ShopItem shopItem,
                ChangeListener<ShopItem> listener,
                TextView quantityField) {
            this.shopItem = shopItem;
            this.listener = listener;
            this.quantityField = quantityField;
        }

        @Override
        public void onClick(View view) {
            final QuantityEditor quantityEditor = new QuantityEditor();
            quantityEditor.editQuantity(shopItem.getProduct(),
                    shopItem.getQuantity(),
                    view.getContext(),
                    new QuantityEditor.Listener() {
                        @Override
                        public void quantitySelected(BigDecimal newValue) {
                            quantityField.setText(StringUtils.toString(newValue));

                            shopItem.setQuantity(newValue);
                            listener.onChange(shopItem);
                        }
                    });
        }
    }
}
