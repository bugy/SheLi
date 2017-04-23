package net.buggy.shoplist.navigation;


import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.AboutAppUnit;

public class AboutAppUnitNavigator extends UnitNavigator<ShopListActivity> {
    public AboutAppUnitNavigator(ShopListActivity unitHost) {
        super(R.string.unit_about_title, R.drawable.ic_help_outline_black_24dp, unitHost);
    }

    @Override
    public void navigate() {
        final AboutAppUnit unit = new AboutAppUnit();
        getUnitHost().startUnit(unit);
    }
}
