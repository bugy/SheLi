package net.buggy.shoplist.components;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import net.buggy.components.TagFlag;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

public class CategoriesFilterCellFactory extends CellFactory<Category, ViewGroup> {
    @Override
    public ViewGroup createEmptyCell(Context context, ViewGroup parent) {
        final LayoutInflater toolbarInflater = LayoutInflater.from(parent.getContext());

        return (ViewGroup) toolbarInflater.inflate(
                R.layout.cell_categories_filter, parent, false);
    }

    @Override
    public void fillCell(Cell<Category> cell, ViewGroup view, CellContext<Category> cellContext, final ChangeListener<Category> listener) {
        final Category category = cell.getData();

        final TextView nameField = (TextView) view.findViewById(
                R.id.cell_categories_filter_name);
        nameField.setText(category.getName());

        final CheckBox checkBox = (CheckBox) view.findViewById(
                R.id.cell_categories_filter_checkbox);
        checkBox.setChecked(cell.isSelected());
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                listener.setSelected(isChecked);
            }
        });

        final TagFlag colorFlag = (TagFlag) view.findViewById(
                R.id.cell_categories_filter_color_flag);
        colorFlag.setColor(ModelHelper.getColor(category));
    }
}
