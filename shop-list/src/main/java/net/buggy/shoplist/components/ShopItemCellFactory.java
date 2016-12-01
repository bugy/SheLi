package net.buggy.shoplist.components;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.aigestudio.wheelpicker.WheelPicker;
import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.ShopItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ShopItemCellFactory implements CellFactory<ShopItem, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(R.layout.cell_shop_item, parent, false);
    }

    private List<BigDecimal> getPossibleQuantities() {
        List<BigDecimal> quantityValues = new ArrayList<>(15);
        for (int i = 0; i < 10; i++) {
            quantityValues.add(new BigDecimal("0." + i));
        }
        for (int i = 0; i < 5; i++) {
            quantityValues.add(new BigDecimal(i + 1));
        }
        return quantityValues;
    }

    @Override
    public void fillCell(final ShopItem shopItem, final ViewGroup view, ChangeListener<ShopItem> listener, boolean selected, boolean enabled) {
        String itemInfo = shopItem.getProduct().getName();

        final TextView quantityField = (TextView) view.findViewById(R.id.cell_shop_item_quantity_field);
        quantityField.setText(String.valueOf(shopItem.getQuantity()));
        quantityField.setOnClickListener(new QuantityClickListener(view, shopItem, listener));

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_shop_item_name_field);
        itemNameField.setText(itemInfo);

        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_shop_item_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(shopItem.getProduct().getCategories());
        categoriesContainer.setColors(colors);
    }

    private class QuantityClickListener implements View.OnClickListener {
        private final ViewGroup view;
        private final ShopItem shopItem;
        private final ChangeListener<ShopItem> dataListener;

        public QuantityClickListener(ViewGroup view, ShopItem shopItem, ChangeListener<ShopItem> dataListener) {
            this.view = view;
            this.shopItem = shopItem;
            this.dataListener = dataListener;
        }

        @Override
        public void onClick(View v) {
            final Context context = view.getContext();

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(shopItem.getProduct().getName() + " quantity");

            final List<BigDecimal> possibleQuantities = getPossibleQuantities();
            BigDecimal currentQuantity = shopItem.getQuantity() != null
                    ? shopItem.getQuantity()
                    : BigDecimal.ONE;
            int selectedIndex = 0;
            for (int i = 0; i < possibleQuantities.size(); i++) {
                final BigDecimal possibleQuantity = possibleQuantities.get(i);

                if (possibleQuantity.compareTo(currentQuantity) == 0) {
                    selectedIndex = i;
                    break;
                }
            }

            final WheelPicker quantityEditor = new WheelPicker(context);
            quantityEditor.setAtmospheric(true);
            quantityEditor.setCyclic(false);
            quantityEditor.setCurved(true);
            quantityEditor.setVisibleItemCount(5);
            quantityEditor.setData(possibleQuantities);
            quantityEditor.setSelectedItemPosition(selectedIndex);
            builder.setView(quantityEditor);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final int position = quantityEditor.getCurrentItemPosition();
                    final Object value = quantityEditor.getData().get(position);

                    shopItem.setQuantity((BigDecimal) value);
                    dataListener.onChange(shopItem);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        }
    }
}
