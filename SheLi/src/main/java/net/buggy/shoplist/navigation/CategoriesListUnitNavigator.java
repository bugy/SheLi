package net.buggy.shoplist.navigation;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.CategoriesUnit;


public class CategoriesListUnitNavigator extends UnitNavigator<ShopListActivity> {
    public CategoriesListUnitNavigator(ShopListActivity activity) {
        super(R.string.unit_categories_title, R.drawable.ic_bookmark_border_black_24dp, activity);
    }

    @Override
    public void navigate() {
        final CategoriesUnit unit = new CategoriesUnit();
        getUnitHost().startUnit(unit);
    }
}
