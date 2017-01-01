package net.buggy.shoplist.components;


import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.common.base.Strings;
import com.google.common.collect.Multiset;

import net.buggy.components.TagFlagContainer;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.Cell;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.StringUtils;

public class SelectableShopItemCellFactory extends CellFactory<ShopItem, ViewGroup> {

    public static final String COMMENT_LISTENER_KEY = "COMMENT_LISTENER";

    @Override
    public ViewGroup createEmptyCell(final Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final ViewGroup result = (ViewGroup) inflater.inflate(
                R.layout.cell_selectable_shop_item, parent, false);

        final EditText commentEditField = (EditText) result.findViewById(
                R.id.cell_selectable_shop_item_comment_field);
        ViewUtils.makeHintItalic(commentEditField);

        final ImageButton commentButton = (ImageButton) result.findViewById(
                R.id.cell_selectable_shop_item_comment_button);

        final ShopListActivity activity = (ShopListActivity) commentEditField.getContext();
        activity.addOutsideClickListener(commentEditField, new ShopListActivity.OutsideClickListener() {
            @Override
            public void clickedOutside(int x, int y) {
                if (commentEditField.isFocused() &&
                        !ViewUtils.getLocationOnScreen(commentButton).contains(x, y)) {

                    commentEditField.clearFocus();
                    ViewUtils.hideSoftKeyboard(commentEditField);
                }
            }
        });

        return result;
    }

    @Override
    public void fillCell(final Cell<ShopItem> cell, final ViewGroup view, boolean newCell, final ChangeListener<ShopItem> listener) {
        final ShopItem shopItem = cell.getData();
        final Product product = shopItem.getProduct();

        view.setSelected(cell.isSelected());

        final TextView itemNameField = (TextView) view.findViewById(R.id.cell_selectable_shop_item_name_field);
        if (newCell) {
            itemNameField.setText(product.getName());
        }

        final ImageButton commentButton = (ImageButton) view.findViewById(R.id.cell_selectable_shop_item_comment_button);

        final TextView quantityView = (TextView) view.findViewById(R.id.cell_selectable_shop_item_quantity_field);
        final EditText commentEditField = (EditText) view.findViewById(
                R.id.cell_selectable_shop_item_comment_field);

        if (!cell.isEnabled()) {
            final int disabledColor = ViewUtils.resolveColor(R.color.color_disabled_background, view.getContext());
            view.setBackgroundColor(disabledColor);

            itemNameField.setAlpha(0.6f);
            commentEditField.setAlpha(0.6f);

            quantityView.setVisibility(View.INVISIBLE);

            commentButton.setVisibility(View.GONE);
            commentEditField.setFocusable(false);

        } else {
            itemNameField.setAlpha(1);
            commentEditField.setAlpha(1);

            view.setBackgroundResource(R.drawable.selectable_background);

            quantityView.setVisibility(View.VISIBLE);

            commentEditField.setFocusable(true);
            commentButton.setVisibility(View.VISIBLE);

            quantityView.setText(StringUtils.toString(shopItem.getQuantity()));
            quantityView.setOnClickListener(new QuantityClickListener(shopItem, listener, quantityView));

            commentButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!commentEditField.isFocused()) {
                        commentEditField.setVisibility(View.VISIBLE);
                        ViewUtils.focusTextField(commentEditField);
                    } else {
                        commentEditField.clearFocus();
                        ViewUtils.hideSoftKeyboard(commentEditField);
                    }

                    listener.setSelected(true);
                }
            });
        }

        if (newCell) {
            setupCommentField(cell, listener, shopItem, commentEditField);
        }

        final TagFlagContainer categoriesContainer = (TagFlagContainer) view.findViewById(
                R.id.cell_selectable_shop_item_categories);
        final Multiset<Integer> colors = ModelHelper.getColors(product.getCategories());
        categoriesContainer.setColors(colors);
    }

    private void setupCommentField(final Cell<ShopItem> cell,
                                   final ChangeListener<ShopItem> listener,
                                   final ShopItem shopItem,
                                   final EditText commentEditField) {

        commentEditField.setText(shopItem.getComment());
        if (Strings.isNullOrEmpty(shopItem.getComment())) {
            commentEditField.setVisibility(View.GONE);
        }

        commentEditField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    ViewUtils.hideSoftKeyboard(v);
                    v.clearFocus();

                    return true;
                }

                return false;
            }
        });

        commentEditField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    final String comment = commentEditField.getText().toString().trim();

                    if (comment.isEmpty()) {
                        commentEditField.setVisibility(View.GONE);
                    }
                } else {
                    listener.setSelected(true);
                }
            }
        });

        final TextWatcher textChangeListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String oldComment = shopItem.getComment();

                final String comment = s.toString().trim();

                if (!StringUtils.equal(comment, oldComment)) {
                    shopItem.setComment(comment);

                    listener.onChange(shopItem);
                }
            }
        };
        cell.getViewState().put(COMMENT_LISTENER_KEY, textChangeListener);

        commentEditField.addTextChangedListener(textChangeListener);
    }

    @Override
    public void clearCell(Cell<ShopItem> cell, ViewGroup itemView) {
        final Object listener = cell.getViewState().get(COMMENT_LISTENER_KEY);
        if (listener != null) {
            final EditText commentField = (EditText) itemView.findViewById(
                    R.id.cell_selectable_shop_item_comment_field);
            commentField.removeTextChangedListener((TextWatcher) listener);
        }
    }

}
