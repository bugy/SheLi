package net.buggy.shoplist.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.common.base.Strings;

import net.buggy.components.ViewUtils;
import net.buggy.shoplist.R;

public class FastCreationPanel extends LinearLayout {

    private Listener listener;
    private SearchEditText nameField;
    private boolean editAddEnabled = false;
    private ImageButton editAddButton;

    public FastCreationPanel(Context context) {
        super(context);

        initView();
    }

    public FastCreationPanel(Context context, AttributeSet attrs) {
        super(context, attrs);

        initView();
    }

    public FastCreationPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initView();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FastCreationPanel(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        initView();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setEditAddEnabled(boolean editAddEnabled) {
        this.editAddEnabled = editAddEnabled;

        editAddButton.setVisibility(editAddEnabled ? VISIBLE : INVISIBLE);
    }

    private void initView() {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.fast_creation_panel, this, true);

        nameField = (SearchEditText) findViewById(R.id.fast_creation_name_field);

        final ImageButton addButton = (ImageButton) findViewById(R.id.fast_creation_add_button);
        ViewUtils.setColorListTint(addButton, R.color.disableable_accent);

        editAddButton = (ImageButton) findViewById(R.id.fast_creation_add_and_edit_button);
        ViewUtils.setColorListTint(editAddButton, R.color.disableable_accent);

        addButton.setEnabled(false);
        editAddButton.setEnabled(false);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String name = nameField.getText().toString().trim();
                nameField.setText("");

                fireOnCreate(name);
            }
        });

        editAddButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final String name = nameField.getText().toString().trim();
                nameField.setText("");

                fireOnEditCreate(name);
            }
        });

        nameField.setListener(new SearchEditText.Listener() {
            @Override
            public void onTextChanged(String text) {
                addButton.setEnabled(!Strings.isNullOrEmpty(text));
                editAddButton.setEnabled(!Strings.isNullOrEmpty(text));

                fireOnNameChanged(text);
            }

            @Override
            public void onFinish(String text) {
                nameField.setText("");

                if (!text.isEmpty()) {
                    fireOnCreate(text);
                }
            }
        });
    }

    public void setHint(String hint) {
        nameField.setHint(hint);
    }

    private void fireOnNameChanged(String name) {
        if (listener == null) {
            throw new IllegalStateException("FastCreationPanel requires listener");
        }

        listener.onNameChanged(name);
    }

    private void fireOnCreate(String name) {
        if (listener == null) {
            throw new IllegalStateException("FastCreationPanel requires listener");
        }

        listener.onCreate(name);
    }

    private void fireOnEditCreate(String name) {
        if (listener == null) {
            throw new IllegalStateException("FastCreationPanel requires listener");
        }

        listener.onEditCreate(name);
    }

    public void setText(String text) {
        nameField.setText(text);
    }

    public static abstract class Listener {
        public void onCreate(String name) {

        }

        public void onEditCreate(String name) {

        }

        public void onNameChanged(String name) {

        }
    }
}
