package net.buggy.shoplist.units.views;

import android.app.Activity;
import android.view.ViewGroup;

public abstract class ViewRenderer<A extends Activity, V extends ViewGroup> {

    public abstract void renderTo(V parentView, A activity);
}
