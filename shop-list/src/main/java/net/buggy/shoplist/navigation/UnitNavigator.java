package net.buggy.shoplist.navigation;


import android.app.Activity;

import net.buggy.shoplist.units.UnitHost;

public abstract class UnitNavigator<A extends Activity & UnitHost> {

    private final String text;
    private final Integer textId;
    private final Integer iconDrawableId;

    private final A unitHost;

    protected UnitNavigator(String text, Integer iconDrawableId, A unitHost) {
        this.text = text;
        this.textId = null;
        this.iconDrawableId = iconDrawableId;
        this.unitHost = unitHost;
    }

    protected UnitNavigator(int textId, Integer iconDrawableId, A unitHost) {
        this.text = null;
        this.textId = textId;
        this.iconDrawableId = iconDrawableId;
        this.unitHost = unitHost;
    }


    public String getText() {
        if (textId != null) {
            return unitHost.getString(textId);
        }

        return text;
    }

    public Integer getIconDrawableId() {
        return iconDrawableId;
    }

    protected A getUnitHost() {
        return unitHost;
    }

    public abstract void navigate();
}
