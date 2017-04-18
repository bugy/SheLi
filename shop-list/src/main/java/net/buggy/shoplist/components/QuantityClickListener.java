package net.buggy.shoplist.components;

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

import com.google.common.base.Supplier;

import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.model.UnitOfMeasure;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;

public class QuantityClickListener implements View.OnClickListener {
    private final ShopItem shopItem;
    private final CellFactory.ChangeListener<ShopItem> listener;
    private final TextView quantityView;
    private final Supplier<UnitOfMeasure> unitOfMeasureSupplier;

    public QuantityClickListener(
            ShopItem shopItem,
            @Nullable CellFactory.ChangeListener<ShopItem> listener,
            TextView quantityView,
            Supplier<UnitOfMeasure> unitOfMeasureSupplier) {

        this.shopItem = shopItem;
        this.listener = listener;
        this.quantityView = quantityView;
        this.unitOfMeasureSupplier = unitOfMeasureSupplier;
    }

    @Override
    public void onClick(View view) {
        final QuantityEditor quantityEditor = new QuantityEditor();

        final UnitOfMeasure unitOfMeasure = unitOfMeasureSupplier.get();

        quantityEditor.editQuantity(shopItem.getProduct(),
                shopItem.getQuantity(),
                view.getContext(),
                new WheelPickerUtils.Listener<BigDecimal>() {

                    @Override
                    public void valueSelected(BigDecimal newValue) {
                        quantityView.setText(StringUtils.toString(newValue));

                        shopItem.setQuantity(newValue);

                        if (listener != null) {
                            listener.onChange(shopItem);
                        }
                    }
                },
                unitOfMeasure);
    }
}
