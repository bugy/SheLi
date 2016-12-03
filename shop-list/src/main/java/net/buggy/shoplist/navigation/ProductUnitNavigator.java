package net.buggy.shoplist.navigation;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.ProductsUnit;

public class ProductUnitNavigator extends UnitNavigator<ShopListActivity> {

    public ProductUnitNavigator(ShopListActivity activity) {
        super(R.string.unit_products_title, R.drawable.ic_bubble_chart_black_24dp, activity);
    }

    @Override
    public void navigate() {
        final ProductsUnit unit = new ProductsUnit();
        getUnitHost().startUnit(unit);
    }
}
