package net.buggy.shoplist.components;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.aigestudio.wheelpicker.WheelPicker;

import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuantityEditor {

    public void editQuantity(Product product, BigDecimal currentQuantity, Context context, final Listener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        final String title = context.getString(
                R.string.quantity_editor_title, product.getName());
        builder.setTitle(title);

        final List<String> possibleQuantities = getPossibleQuantities();
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

                listener.quantitySelected(new BigDecimal((String) value));
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

    private int getQuantityIndex(BigDecimal quantity, List<String> possibleQuantities) {
        int selectedIndex = 0;
        for (int i = 0; i < possibleQuantities.size(); i++) {
            final BigDecimal possibleQuantity = new BigDecimal(possibleQuantities.get(i));

            if (possibleQuantity.compareTo(quantity) == 0) {
                selectedIndex = i;
                break;
            }
        }

        return selectedIndex;
    }

    private List<String> getPossibleQuantities() {
        List<String> quantityValues = new ArrayList<>(15);
        for (int i = 1; i < 10; i++) {
            final BigDecimal value = new BigDecimal(i).divide(BigDecimal.TEN, 1, BigDecimal.ROUND_HALF_DOWN);
            quantityValues.add(StringUtils.toString(value));
        }
        for (int i = 0; i < 5; i++) {
            quantityValues.add(StringUtils.toString(new BigDecimal(i + 1)));
        }
        return quantityValues;
    }


    public interface Listener {
        void quantitySelected(BigDecimal newValue);
    }
}
