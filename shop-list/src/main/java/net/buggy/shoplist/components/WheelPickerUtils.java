package net.buggy.shoplist.components;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.aigestudio.wheelpicker.WheelPicker;
import com.google.common.base.Function;

import net.buggy.components.ViewUtils;

import java.util.ArrayList;
import java.util.List;

public class WheelPickerUtils {

    public static <T> void selectValue(
            final List<T> possibleValues,
            T currentValue,
            T defaultValue,
            Function<T, String> stringifier,
            String title,
            Context context,
            final Listener<T> listener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle(title);

        T pickerValue = currentValue != null ? currentValue : defaultValue;
        int selectedIndex = getIndex(pickerValue, possibleValues);

        List<String> stringValues = new ArrayList<>(possibleValues.size());
        for (T possibleValue : possibleValues) {
            stringValues.add(stringifier.apply(possibleValue));
        }

        final WheelPicker quantityEditor = new WheelPicker(context);
        quantityEditor.setAtmospheric(true);
        quantityEditor.setCyclic(false);
        quantityEditor.setCurved(true);
        quantityEditor.setVisibleItemCount(5);
        quantityEditor.setData(stringValues);
        quantityEditor.setSelectedItemPosition(selectedIndex);
        builder.setView(quantityEditor);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final int position = quantityEditor.getCurrentItemPosition();
                final T selectedValue = possibleValues.get(position);

                listener.valueSelected(selectedValue);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        final AlertDialog dialog = builder.create();
        ViewUtils.showStyled(dialog);
    }

    private static <T> int getIndex(T value, List<T> possibleValues) {
        return possibleValues.indexOf(value);
    }

    public interface Listener<T> {
        void valueSelected(T newValue);
    }
}
