package net.buggy.shoplist.components;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Strings;

import net.buggy.components.ViewUtils;
import net.buggy.shoplist.R;

public class FastCreationPanel extends LinearLayout {

    private Listener listener;
    private EditText nameField;

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

    private void initView() {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.fast_creation_panel, this, true);

        nameField = (EditText) findViewById(R.id.fast_creation_name_field);
        final Typeface typeface =  nameField.getTypeface();
        updateTextFieldStyle(nameField.getText(), typeface);

        final ImageButton addButton = (ImageButton) findViewById(R.id.fast_creation_add_button);

        setEnabled(addButton, false);
        nameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateTextFieldStyle(s, typeface);

                String text = s.toString().trim();
                setEnabled(addButton, !Strings.isNullOrEmpty(text));

                fireOnNameChanged(s.toString().trim());
            }
        });
        nameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    final String name = nameField.getText().toString().trim();
                    nameField.setText("");
                    if (!name.isEmpty()) {
                        fireOnCreate(name);
                    }

                    nameField.clearFocus();
                    ViewUtils.hideSoftKeyboard((Activity) getContext(), FastCreationPanel.this);

                    return true;
                }

                return false;
            }
        });

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String name = nameField.getText().toString().trim();
                nameField.setText("");

                fireOnCreate(name);
            }
        });
    }

    private void updateTextFieldStyle(Editable text, Typeface originalTypeface) {
        if (text.length() == 0) {
            nameField.setTypeface(originalTypeface, Typeface.ITALIC);
        } else {
            nameField.setTypeface(originalTypeface, Typeface.NORMAL);
        }
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

    private void setEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);

        if (!enabled) {
            button.setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);
        } else {
            button.setColorFilter(null);
        }
    }

    public void setText(String text) {
        nameField.setText(text);
    }

    public interface Listener {
        void onCreate(String name);

        void onNameChanged(String name);
    }
}
