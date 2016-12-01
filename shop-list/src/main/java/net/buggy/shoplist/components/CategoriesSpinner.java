package net.buggy.shoplist.components;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import net.buggy.components.TagFlag;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CategoriesSpinner extends Spinner {

    private static final String ALL_CATEGORIES = "All";

    private Listener listener;

    private TreeMap<String, Category> categoriesMap;

    public CategoriesSpinner(Context context) {
        super(context);
    }

    public CategoriesSpinner(Context context, int mode) {
        super(context, mode);
    }

    public CategoriesSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CategoriesSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CategoriesSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CategoriesSpinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, int mode) {
        super(context, attrs, defStyleAttr, defStyleRes, mode);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public CategoriesSpinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, int mode, Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, defStyleRes, mode, popupTheme);
    }

    public void setCategories(List<Category> categories) {
        categoriesMap = new TreeMap<>();
        for (Category category : categories) {
            categoriesMap.put(category.getName(), category);
        }

        final ArrayAdapter<String> categoryAdapter = createCategoryAdapter(categoriesMap);

        this.setAdapter(categoryAdapter);
        this.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final String selectedName = (String) parent.getItemAtPosition(position);

                final Category category = categoriesMap.get(selectedName);
                listener.categorySelected(category);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                listener.categorySelected(null);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private ArrayAdapter<String> createCategoryAdapter(Map<String, Category> categoriesMap) {
        final ArrayAdapter<String> categoryAdapter = new ArrayAdapter<String>(
                getContext(),
                R.layout.category_spinner_item,
                R.id.category_spinner_item_name_field) {
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                final View view = super.getDropDownView(position, convertView, parent);

                updateColor(view, position);

                return view;
            }

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                final View view = super.getView(position, convertView, parent);

                updateColor(view, position);

                return view;
            }

            private void updateColor(View view, int position) {
                final TagFlag categoryFlag = (TagFlag) view.findViewById(R.id.category_spinner_item_color);

                if (position == 0) {
                    categoryFlag.setVisibility(View.INVISIBLE);
                } else {
                    final String name = (String) getItemAtPosition(position);

                    final Category category = CategoriesSpinner.this.categoriesMap.get(name);
                    categoryFlag.setColor(ModelHelper.getColor(category));
                    categoryFlag.setVisibility(VISIBLE);
                }
            }
        };
        categoryAdapter.add(ALL_CATEGORIES);
        categoryAdapter.addAll(categoriesMap.keySet());

        return categoryAdapter;
    }

    public interface Listener {
        void categorySelected(Category category);
    }
}
