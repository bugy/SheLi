package net.buggy.shoplist.components;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.units.SelectProductsUnit.RawShopItem;
import net.buggy.shoplist.utils.StringUtils;

import java.math.BigDecimal;

public class ProductSelectCellFactory implements CellFactory<RawShopItem, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(R.layout.cell_product_for_shop_item, parent, false);
    }

    @Override
    public void fillCell(RawShopItem rawShopItem,
                         ViewGroup view,
                         ChangeListener<RawShopItem> listener,
                         boolean selected,
                         boolean enabled) {

        final Product product = rawShopItem.getProduct();

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_product_name_field);
        itemNameField.setText(product.getName());

        final TextView quantityView = (TextView) view.findViewById(R.id.cell_product_quantity);
        quantityView.setText(String.valueOf(rawShopItem.getQuantity()));
        quantityView.setOnClickListener(new QuantityClickListener(rawShopItem, listener, quantityView));

        view.setSelected(selected);

        if (!enabled) {
            final int disabledColor = ViewUtils.resolveColor(R.color.color_disabled_background, view.getContext());
            view.setBackgroundColor(disabledColor);

            itemNameField.setAlpha(0.6f);
            quantityView.setVisibility(View.INVISIBLE);
        } else {
            itemNameField.setAlpha(1);

            view.setBackgroundResource(R.drawable.selectable_background);

            quantityView.setVisibility(View.VISIBLE);
        }

        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_product_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(product.getCategories());
        categoriesContainer.setColors(colors);
    }

    private static class QuantityClickListener implements View.OnClickListener {
        private final RawShopItem shopItem;
        private final ChangeListener<RawShopItem> listener;
        private final TextView quantityView;

        public QuantityClickListener(
                RawShopItem shopItem,
                ChangeListener<RawShopItem> listener,
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
                            listener.onChange(shopItem);
                        }
                    });
        }
    }
}
