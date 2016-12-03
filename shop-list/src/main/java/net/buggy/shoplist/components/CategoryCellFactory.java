package net.buggy.shoplist.components;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.buggy.components.TagFlag;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

public class CategoryCellFactory implements CellFactory<Category, LinearLayout> {

    @Override
    public LinearLayout createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (LinearLayout) inflater.inflate(R.layout.cell_category, parent, false);
    }

    @Override
    public void fillCell(final Category category, final LinearLayout view, final ChangeListener<Category> listener, boolean selected, boolean enabled) {
        final TextView nameField = (TextView) view.findViewById(R.id.cell_category_name_field);
        nameField.setText(category.getName());

        final TagFlag colorFlag = (TagFlag) view.findViewById(
                R.id.cell_category_color_flag);
        final int color = ModelHelper.getColor(category);
        colorFlag.setColor(color);

        view.setSelected(selected);
    }

}
