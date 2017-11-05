package net.buggy.shoplist.navigation;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.SharingUnit;

public class SharingUnitNavigator extends UnitNavigator<ShopListActivity> {

    public SharingUnitNavigator(ShopListActivity unitHost) {
        super(R.string.unit_sharing_title, R.drawable.ic_share_variant_black_24dp, unitHost);
    }

    @Override
    public void navigate() {
        final ShopListActivity unitHost = getUnitHost();

        final SharingUnit unit = new SharingUnit();
        unitHost.startUnit(unit);
    }
}
