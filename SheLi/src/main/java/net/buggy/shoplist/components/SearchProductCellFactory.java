package net.buggy.shoplist.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.buggy.components.list.Cell;
import net.buggy.components.list.CellContext;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Product;

public class SearchProductCellFactory extends CellFactory<SearchProductCellFactory.SearchedProduct, ViewGroup> {

    @Override
    public ViewGroup createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (ViewGroup) inflater.inflate(R.layout.cell_search_product, parent, false);
    }

    @Override
    public void fillCell(Cell<SearchedProduct> cell, ViewGroup view, CellContext<SearchedProduct> cellContext, ChangeListener<SearchedProduct> listener) {
        final SearchedProduct searchedProduct = cell.getData();
        final Product product = searchedProduct.getProduct();

        final TextView nameView = (TextView) view.findViewById(
                R.id.cell_search_product_name);
        nameView.setText(product.getName());

        final TextView newTextView = (TextView) view.findViewById(
                R.id.cell_search_product_new_text);
        final View spacer = view.findViewById(
                R.id.cell_search_product_left_spacer);
        final ImageView alreadyAddedImage = (ImageView) view.findViewById(
                R.id.cell_search_product_added_image);

        if (product.getId() == null) {
            nameView.setTypeface(nameView.getTypeface(), Typeface.ITALIC);
            newTextView.setVisibility(View.VISIBLE);
            spacer.setVisibility(View.GONE);
            alreadyAddedImage.setVisibility(View.GONE);

        } else {
            nameView.setTypeface(nameView.getTypeface(), Typeface.NORMAL);
            newTextView.setVisibility(View.GONE);

            nameView.setEnabled(!searchedProduct.isExists());

            if (searchedProduct.isExists()) {
                alreadyAddedImage.setVisibility(View.VISIBLE);
                spacer.setVisibility(View.GONE);
            } else {
                alreadyAddedImage.setVisibility(View.GONE);
                spacer.setVisibility(View.VISIBLE);
            }
        }

    }

    public static class SearchedProduct {
        private final Product product;
        private final boolean exists;

        public SearchedProduct(Product product, boolean exists) {
            this.product = product;
            this.exists = exists;
        }

        public Product getProduct() {
            return product;
        }

        public boolean isExists() {
            return exists;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SearchedProduct)) return false;

            SearchedProduct that = (SearchedProduct) o;

            return product.equals(that.product);

        }

        @Override
        public int hashCode() {
            return product.hashCode();
        }
    }
}
