package net.buggy.shoplist.components;


import android.content.Context;
import android.view.ViewGroup;

import net.buggy.shoplist.R;

public class SelectableProductCellFactory extends ProductCellFactory {

    @Override
    public ViewGroup createEmptyCell(Context context, ViewGroup parent) {
        final ViewGroup emptyCell = super.createEmptyCell(context, parent);

        emptyCell.setBackgroundResource(R.drawable.selectable_background);

        return emptyCell;
    }
}
