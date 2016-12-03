package net.buggy.shoplist.components;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.aigestudio.wheelpicker.WheelPicker;

import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuantityEditor {

    public void editQuantity(Product product, BigDecimal currentQuantity, Context context, final Listener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        final String title = context.getString(
                R.string.quantity_editor_title, product.getName());
        builder.setTitle(title);

        final List<BigDecimal> possibleQuantities = getPossibleQuantities();
        BigDecimal quantity = currentQuantity != null
                ? currentQuantity
                : BigDecimal.ONE;
        int selectedIndex = getQuantityIndex(quantity, possibleQuantities);

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

                listener.quantitySelected((BigDecimal) value);
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

    private int getQuantityIndex(BigDecimal quantity, List<BigDecimal> possibleQuantities) {
        int selectedIndex = 0;
        for (int i = 0; i < possibleQuantities.size(); i++) {
            final BigDecimal possibleQuantity = possibleQuantities.get(i);

            if (possibleQuantity.compareTo(quantity) == 0) {
                selectedIndex = i;
                break;
            }
        }

        return selectedIndex;
    }

    private List<BigDecimal> getPossibleQuantities() {
        List<BigDecimal> quantityValues = new ArrayList<>(15);
        for (int i = 1; i < 10; i++) {
            quantityValues.add(new BigDecimal("0." + i));
        }
        for (int i = 0; i < 5; i++) {
            quantityValues.add(new BigDecimal(i + 1));
        }
        return quantityValues;
    }


    public interface Listener {
        void quantitySelected(BigDecimal newValue);
    }
}
