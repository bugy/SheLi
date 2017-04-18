package net.buggy.shoplist.components;

import android.content.Context;

import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.UnitOfMeasure;
import net.buggy.shoplist.utils.BigDecimalStringifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuantityEditor {

    public void editQuantity(
            Product product,
            BigDecimal currentQuantity,
            Context context,
            final WheelPickerUtils.Listener<BigDecimal> listener,
            UnitOfMeasure unitsOfMeasure) {


        final String title;

        if (unitsOfMeasure != null) {
            final String unitsString = context.getString(unitsOfMeasure.getShortNameKey());
            title = context.getString(
                    R.string.quantity_editor_title_with_units, product.getName(), unitsString);
        } else {
            title = context.getString(
                    R.string.quantity_editor_title, product.getName());
        }

        WheelPickerUtils.selectValue(
                getPossibleQuantities(),
                currentQuantity,
                BigDecimal.ONE,
                BigDecimalStringifier.INSTANCE,
                title,
                context,
                listener
        );
    }

    private List<BigDecimal> getPossibleQuantities() {
        List<BigDecimal> quantityValues = new ArrayList<>(15);
        for (int i = 1; i < 10; i++) {
            final BigDecimal value = new BigDecimal(i).divide(BigDecimal.TEN, 1, BigDecimal.ROUND_HALF_DOWN);
            quantityValues.add(value);
        }
        for (int i = 0; i < 5; i++) {
            quantityValues.add(new BigDecimal(i + 1));
        }
        return quantityValues;
    }
}
