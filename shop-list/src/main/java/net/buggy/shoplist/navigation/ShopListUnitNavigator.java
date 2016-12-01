package net.buggy.shoplist.navigation;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.ShopItemListUnit;

public class ShopListUnitNavigator extends UnitNavigator<ShopListActivity> {

    public ShopListUnitNavigator(ShopListActivity unitHost) {
        super(R.string.unit_shot_items_title, R.drawable.ic_shopping_cart_black_24dp, unitHost);
    }

    @Override
    public void navigate() {
        final ShopListActivity unitHost = getUnitHost();

        final ShopItemListUnit unit = new ShopItemListUnit();
        unitHost.startUnit(unit);
    }
}
