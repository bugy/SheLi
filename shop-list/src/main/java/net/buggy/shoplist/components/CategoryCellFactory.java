package net.buggy.shoplist.components;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.common.base.Objects;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.CellFactory;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

import java.util.Random;

public class CategoryCellFactory implements CellFactory<Category, LinearLayout> {

    public static final int[] COLORS = new int[]{
            Color.RED,
            Color.argb(255, 255, 255, 0),
            Color.GREEN,
            Color.argb(255, 0, 255, 255),
            Color.BLUE,
            Color.argb(255, 255, 0, 255),
            Color.RED};

    @Override
    public LinearLayout createEmptyCell(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        return (LinearLayout) inflater.inflate(R.layout.cell_category_item, parent, false);
    }

    @Override
    public void fillCell(final Category category, final LinearLayout view, final ChangeListener<Category> listener, boolean selected, boolean enabled) {
        final ShopListActivity activity = (ShopListActivity) view.getContext();

        final EditText nameField = (EditText) view.findViewById(R.id.cell_category_name_field);
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

        final SeekBar colorSlider = (SeekBar) view.findViewById(R.id.cell_category_color_slider);

        final View colorView = view.findViewById(R.id.cell_category_color_field);

        initColorSlider(colorSlider, colorView);

        setColorViewColor(colorView, ModelHelper.getColor(category));

        final ColorChooserHandler colorChooserHandler =
                new ColorChooserHandler(view, colorSlider, category, listener, activity);
        colorChooserHandler.activateOn(colorView);
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

    private static int getProgressFromColor(int color) {
        if (color == Color.WHITE) {
            final Random random = new Random();
            return random.nextInt(101);
        }

        final int red = Color.red(color);
        final int green = Color.green(color);
        final int blue = Color.blue(color);

        int bestProgress = 0;
        int bestDiff = Integer.MAX_VALUE;

        for (int i = 0; i <= 100; i++) {
            final int colorFromProgress = getColorFromProgress(i);
            int progressRed = Color.red(colorFromProgress);
            int progressGreen = Color.green(colorFromProgress);
            int progressBlue = Color.blue(colorFromProgress);

            int sumDiff = Math.abs(red - progressRed)
                    + Math.abs(green - progressGreen)
                    + Math.abs(blue - progressBlue);

            if (sumDiff < bestDiff) {
                bestDiff = sumDiff;
                bestProgress = i;
            }
        }

        return bestProgress;
    }

    private void initColorSlider(SeekBar colorSlider, final View colorView) {
        final Drawable progressDrawable = ViewUtils.createGradientDrawable(COLORS);
        colorSlider.setProgressDrawable(progressDrawable);

        colorSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int selectedColor = getColorFromProgress(progress);

                setColorViewColor(colorView, selectedColor);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private static int getColorFromProgress(int progress) {
        float progressPerPosition = 100f / (COLORS.length - 1);
        final int position = (int) Math.floor(progress / (progressPerPosition));
        int positionProgress = progress - (int) (position * progressPerPosition);
        float weight2 = positionProgress / progressPerPosition;
        float weight1 = 1 - weight2;


        int selectedColor;
        if (position >= (COLORS.length - 1)) {
            selectedColor = COLORS[COLORS.length - 1];

        } else {
            final int color1 = COLORS[position];
            final int color2 = COLORS[position + 1];

            final int red = (int) (Color.red(color1) * weight1 + Color.red(color2) * weight2);
            final int green = (int) (Color.green(color1) * weight1 + Color.green(color2) * weight2);
            final int blue = (int) (Color.blue(color1) * weight1 + Color.blue(color2) * weight2);

            selectedColor = Color.argb(255, Math.min(red, 255), Math.min(green, 255), Math.min(blue, 255));
        }
        return selectedColor;
    }

    private void setColorViewColor(View colorView, int color) {
        colorView.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }

    private static class ColorChooserHandler implements ShopListActivity.OutsideClickListener{
        private final LinearLayout view;
        private final int paddingBottom;
        private final SeekBar colorSlider;
        private final Category category;
        private final ChangeListener<Category> listener;
        private final ShopListActivity activity;
        private Runnable sliderCloseRunnable;

        public ColorChooserHandler(
                LinearLayout parentView,
                SeekBar colorSlider,
                Category category,
                ChangeListener<Category> listener,
                ShopListActivity activity) {

            this.view = parentView;
            this.colorSlider = colorSlider;
            this.category = category;
            this.listener = listener;
            this.activity = activity;

            this.paddingBottom = parentView.getPaddingBottom();
        }

        @Override
        public void clickedOutside() {
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    paddingBottom);

            if (colorSlider.isShown()) {
                final int selectedColor = getColorFromProgress(colorSlider.getProgress());
                category.setColor(selectedColor);

                listener.onChange(category);
            }

            activity.removeOutsideClickListener(colorSlider, this);

            if (sliderCloseRunnable == null) {
                sliderCloseRunnable = new Runnable() {
                    @Override
                    public void run() {
                        colorSlider.setVisibility(View.GONE);
                    }
                };
            }
            view.post(sliderCloseRunnable);
        }

        public void activateOn(View controllingView) {
            controllingView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (colorSlider.isShown()) {
                        return;
                    }

                    final int progress = getProgressFromColor(ModelHelper.getColor(category));
                    colorSlider.setProgress(progress);

                    colorSlider.setVisibility(View.VISIBLE);
                    colorSlider.requestFocusFromTouch();

                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), 0);

                    activity.addOutsideClickListener(colorSlider, ColorChooserHandler.this);
                }
            });
        }
    }
}
