package net.buggy.shoplist.components;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.buggy.components.TagFlag;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

public class CategoryCellFactory extends CellFactory<Category, LinearLayout> {

    @Override
    public LinearLayout createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (LinearLayout) inflater.inflate(R.layout.cell_category, parent, false);
    }

    @Override
    public void fillCell(Cell<Category> cell, LinearLayout view, CellContext<Category> cellContext, ChangeListener<Category> listener) {
        final Category category = cell.getData();

        final TextView nameField = view.findViewById(R.id.cell_category_name_field);
        nameField.setText(category.getName());

        final TagFlag colorFlag = view.findViewById(
                R.id.cell_category_color_flag);
        final int color = ModelHelper.getColor(category);
        colorFlag.setColor(color);

        view.setSelected(cell.isSelected());
    }

}
