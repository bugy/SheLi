package net.buggy.shoplist.units.views;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public class InflatingViewRenderer<A extends Activity, V extends ViewGroup> extends ViewRenderer<A, V> {

    private final int layoutId;

    public InflatingViewRenderer(int layoutId) {
        this.layoutId = layoutId;
    }

    @Override
    public void renderTo(V parentView, A activity) {
        final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
        inflater.inflate(layoutId, parentView, true);
    }
}
