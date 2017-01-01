package net.buggy.shoplist.components;

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;

public class QuantityClickListener implements View.OnClickListener {
    private final ShopItem shopItem;
    private final CellFactory.ChangeListener<ShopItem> listener;
    private final TextView quantityView;

    public QuantityClickListener(
            ShopItem shopItem,
            @Nullable CellFactory.ChangeListener<ShopItem> listener,
            TextView quantityView) {

        this.shopItem = shopItem;
        this.listener = listener;
        this.quantityView = quantityView;
    }

    @Override
    public void onClick(View view) {
        final QuantityEditor quantityEditor = new QuantityEditor();
        quantityEditor.editQuantity(shopItem.getProduct(),
                shopItem.getQuantity(),
                view.getContext(),
                new QuantityEditor.Listener() {
                    @Override
                    public void quantitySelected(BigDecimal newValue) {
                        quantityView.setText(StringUtils.toString(newValue));

                        shopItem.setQuantity(newValue);

                        if (listener != null) {
                            listener.onChange(shopItem);
                        }
                    }
                });
    }
}
