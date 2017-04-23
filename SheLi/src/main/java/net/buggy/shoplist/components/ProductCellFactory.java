package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;

public class ProductCellFactory extends CellFactory<Product, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final ViewGroup view = (ViewGroup) inflater.inflate(R.layout.cell_product, parent, false);

        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_product_categories);
        categoriesContainer.setMaxCount(6);

        return view;
    }

    @Override
    public void fillCell(Cell<Product> cell, ViewGroup view, CellContext<Product> cellContext, ChangeListener<Product> listener) {
        final Product product = cell.getData();

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_product_name_field);
        itemNameField.setText(product.getName());


        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_product_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(product.getCategories());
        categoriesContainer.setColors(colors);

        view.setSelected(cell.isSelected());
    }
}
