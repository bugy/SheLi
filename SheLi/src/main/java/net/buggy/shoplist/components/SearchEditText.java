package net.buggy.shoplist.components;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import net.buggy.components.ViewUtils;

public class SearchEditText extends AppCompatEditText {

    private Listener listener;

    public SearchEditText(Context context) {
        super(context);

        init();
    }

    public SearchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public SearchEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        ViewUtils.makeHintItalic(this);
    }

    private void init() {
        setGravity(Gravity.CENTER_VERTICAL);
        setImeOptions(EditorInfo.IME_ACTION_DONE);
        setInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        setMaxLines(1);

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
                    ViewUtils.hideSoftKeyboard(SearchEditText.this);

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
