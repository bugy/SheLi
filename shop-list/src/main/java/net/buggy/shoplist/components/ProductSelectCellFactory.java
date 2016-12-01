package net.buggy.shoplist.components;


import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;

public class ProductSelectCellFactory implements CellFactory<Product, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(R.layout.cell_product, parent, false);
    }

    @Override
    public void fillCell(Product product, ViewGroup view, ChangeListener<Product> listener, boolean selected, boolean enabled) {
        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_product_name_field);
        itemNameField.setText(product.getName());

        view.setSelected(selected);

        if (!enabled) {
            final int disabledColor = ViewUtils.resolveColor(R.color.color_disabled_background, view.getContext());
            view.setBackgroundColor(disabledColor);

            itemNameField.setAlpha(0.6f);
        } else {
            itemNameField.setAlpha(1);

            if (view.isSelected()) {
                final int color = ViewUtils.resolveColor(R.color.color_secondary, view.getContext());
                view.setBackgroundColor(color);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        }


        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_product_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(product.getCategories());
        categoriesContainer.setColors(colors);
    }
}
