package net.buggy.shoplist.components;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import net.buggy.components.TagFlag;
import net.buggy.shoplist.R;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CategoriesSpinner extends AppCompatSpinner {

    private String ALL_CATEGORIES;

    private Listener listener;

    private TreeMap<String, Category> categoriesMap;

    public CategoriesSpinner(Context context) {
        super(context);

        init(context);
    }

    public CategoriesSpinner(Context context, int mode) {
        super(context, mode);

        init(context);
    }

    public CategoriesSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    public CategoriesSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context);
    }

    public CategoriesSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);

        init(context);
    }

    private void init(Context context) {
        ALL_CATEGORIES = context.getString(R.string.categories_spinner_all);
    }

    public void setCategories(List<Category> categories) {
        categoriesMap = new TreeMap<>();
        for (Category category : categories) {
            categoriesMap.put(category.getName(), category);
        }

        final ArrayAdapter<String> categoryAdapter = createCategoryAdapter(categoriesMap);

        this.setAdapter(categoryAdapter);
        this.setOnItemSelectedListener(new OnItemSelectedListener() {
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

                final LayoutParams layoutParams = view.getLayoutParams();
                if (position == getSelectedItemPosition()) {
                    //we cannot set height to 0, because it will be ignored
                    layoutParams.height = 1;
                } else {
                    layoutParams.height = LayoutParams.WRAP_CONTENT;
                }

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

        categoryAdapter.setDropDownViewResource(R.layout.category_spinner_dropdown_item);

        return categoryAdapter;
    }

    public interface Listener {
        void categorySelected(Category category);
    }
}
