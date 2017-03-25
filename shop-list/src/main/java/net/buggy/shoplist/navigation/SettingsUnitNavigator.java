package net.buggy.shoplist.navigation;


import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.SettingsUnit;

public class SettingsUnitNavigator extends UnitNavigator<ShopListActivity> {
    public SettingsUnitNavigator(ShopListActivity unitHost) {
        super(R.string.unit_settings_title, R.drawable.ic_settings_black_24dp, unitHost);
    }

    @Override
    public void navigate() {
        final SettingsUnit unit = new SettingsUnit();
        getUnitHost().startUnit(unit);
    }
}
