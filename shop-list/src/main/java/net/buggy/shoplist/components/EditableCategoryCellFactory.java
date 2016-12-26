package net.buggy.shoplist.components;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Objects;

import net.buggy.components.TagFlag;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

import yuku.ambilwarna.AmbilWarnaDialog;

public class EditableCategoryCellFactory implements CellFactory<Category, LinearLayout> {

    private final Listener listener;

    public EditableCategoryCellFactory(Listener listener) {
        this.listener = listener;
    }

    @Override
    public LinearLayout createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (LinearLayout) inflater.inflate(R.layout.cell_category_editable, parent, false);
    }

    @Override
    public void fillCell(final Category category, final LinearLayout view, final ChangeListener<Category> listener, boolean selected, boolean enabled) {
        final ShopListActivity activity = (ShopListActivity) view.getContext();

        final EditText nameField = (EditText) view.findViewById(R.id.cell_category_editable_name_field);
        nameField.setText(category.getName());
        nameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    ViewUtils.hideSoftKeyboard((Activity) view.getContext(), view);
                    nameField.clearFocus();

                    saveNameChange(nameField, category, listener);

                    return true;
                }

                return false;
            }
        });
        nameField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    saveNameChange(nameField, category, listener);
                }
            }
        });

        final TagFlag colorFlag = (TagFlag) view.findViewById(R.id.cell_category_editable_color_field);
        colorFlag.setColor(ModelHelper.getColor(category));
        colorFlag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AmbilWarnaDialog dialog = new AmbilWarnaDialog(
                        colorFlag.getContext(),
                        colorFlag.getColor(),
                        new AmbilWarnaDialog.OnAmbilWarnaListener() {
                            @Override
                            public void onCancel(AmbilWarnaDialog dialog) {
                            }

                            @Override
                            public void onOk(AmbilWarnaDialog dialog, int color) {
                                colorFlag.setColor(color);
                                category.setColor(color);

                                listener.onChange(category);
                            }
                        });

                ViewUtils.showStyled(dialog.getDialog());
            }
        });


        final ImageButton editButton = (ImageButton) view.findViewById(
                R.id.cell_category_editable_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditableCategoryCellFactory.this.listener.onEdit(category);
            }
        });
    }

    private void saveNameChange(EditText nameField, Category category, ChangeListener<Category> listener) {
        final Editable nameEditable = nameField.getText();
        nameEditable.clearSpans();

        final String newName = nameEditable.toString();
        if (Objects.equal(newName, category.getName())) {
            return;
        }

        category.setName(newName);
        listener.onChange(category);
    }

    public interface Listener {
        void onEdit(Category category);
    }
}
