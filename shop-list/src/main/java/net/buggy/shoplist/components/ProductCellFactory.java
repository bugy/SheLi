package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;

public class ProductCellFactory implements CellFactory<Product, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(R.layout.cell_product, parent, false);
    }

    @Override
    public void fillCell(Product product, ViewGroup view, ChangeListener<Product> listener, boolean selected, boolean enabled) {
        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_product_name_field);
        itemNameField.setText(product.getName());


        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_product_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(product.getCategories());
        categoriesContainer.setColors(colors);

        view.setSelected(selected);
    }
}
