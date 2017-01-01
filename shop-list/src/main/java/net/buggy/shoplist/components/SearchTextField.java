package net.buggy.shoplist.components;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import net.buggy.components.ViewUtils;

public class SearchTextField extends EditText {

    private Typeface originalTypeface;
    private Listener listener;

    public SearchTextField(Context context) {
        super(context);

        init();
    }

    public SearchTextField(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public SearchTextField(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public SearchTextField(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    private void init() {
        setGravity(Gravity.CENTER_VERTICAL);
        setImeOptions(EditorInfo.IME_ACTION_DONE);
        setInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        setMaxLines(1);

        ViewUtils.makeHintItalic(this);

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable newEditable) {
                String text = newEditable.toString().trim();

                fireTextChanged(text);
            }
        });

        setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    final String text = getText().toString().trim();

                    clearFocus();
                    ViewUtils.hideSoftKeyboard(SearchTextField.this);

                    fireOnFinish(text);

                    return true;
                }

                return false;
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void fireTextChanged(String text) {
        if (listener == null) {
            throw new IllegalStateException("SearchTextField requires listener");
        }

        listener.onTextChanged(text);
    }

    private void fireOnFinish(String text) {
        if (listener == null) {
            throw new IllegalStateException("SearchTextField requires listener");
        }

        listener.onFinish(text);
    }

    public interface Listener {
        void onTextChanged(String text);

        void onFinish(String text);
    }
}
