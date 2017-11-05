package net.buggy.shoplist.units;


import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.components.spinners.MaterialSpinner;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.CategoryComparator;
import net.buggy.shoplist.components.CategoryCellFactory;
import net.buggy.shoplist.components.WheelPickerUtils;
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.PeriodType;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.UnitOfMeasure;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.CollectionUtils;
import net.buggy.shoplist.utils.PeriodTypeStringifier;
import net.buggy.shoplist.utils.SimpleStringifier;
import net.buggy.shoplist.utils.StringUtils;
import net.buggy.shoplist.utils.UnitOfMeasureStringifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.buggy.components.ViewUtils.setTextWithoutAnimation;
import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class EditProductUnit extends Unit<ShopListActivity> {

    private final Product product;
    private final boolean newProduct;

    private transient ToolbarRenderer toolbarRenderer;

    private final Set<Category> selectedCategories = Collections.newSetFromMap(
            new ConcurrentHashMap<Category, Boolean>());

    private transient FactoryBasedAdapter<Category> categoriesAdapter;

    public EditProductUnit(Product product, boolean newProduct) {
        this.newProduct = newProduct;
        this.product = product;
    }

    @Override
    public void initialize() {
        String productName;
        if (newProduct) {
            productName = getHostingActivity().getString(R.string.unit_edit_product_new_product);

        } else {
            productName = product.getName();
            selectedCategories.addAll(product.getCategories());
        }

        toolbarRenderer = new ToolbarRenderer(productName);
        addRenderer(TOOLBAR_VIEW_ID, toolbarRenderer);

        MainViewRenderer mainViewRenderer = new MainViewRenderer();
        addRenderer(MAIN_VIEW_ID, mainViewRenderer);
    }

    public static final class ProductEditedEvent {
        private final Product product;

        public ProductEditedEvent(Product product) {
            this.product = product;
        }

        public Product getProduct() {
            return product;
        }
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private boolean firstRender = true;

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = activity.getLayoutInflater();

            inflater.inflate(R.layout.unit_edit_product, parentView, true);

            final EditText nameField = (EditText) parentView.findViewById(R.id.edit_product_name_field);
            setTextWithoutAnimation(nameField, product.getName());
            nameField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!newProduct) {
                        final String name = toName(s);
                        toolbarRenderer.setProductName(name);
                    }
                }
            });

            @SuppressWarnings("unchecked")
            final MaterialSpinner<UnitOfMeasure> unitsField = (MaterialSpinner<UnitOfMeasure>)
                    parentView.findViewById(R.id.merge_edit_product_units_field);
            @SuppressWarnings("unchecked")
            final MaterialSpinner<PeriodType> periodTypeField = (MaterialSpinner<PeriodType>)
                    parentView.findViewById(R.id.merge_edit_product_period_type);
            final EditText periodCountField = (EditText) parentView.findViewById(
                    R.id.merge_edit_product_period_count);

            initEditProductFields(parentView, activity, product);
            categoriesAdapter = initCategoriesSection(parentView, activity, selectedCategories);

            final FloatingActionButton saveButton = (FloatingActionButton) parentView.findViewById(R.id.unit_edit_product_save_button);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String name = toName(nameField.getText());
                    final boolean valid = validateProductName(name, product, parentView, activity);
                    if (!valid) {
                        return;
                    }

                    product.setCategories(categoriesAdapter.getSelectedItems());
                    product.setName(name);

                    final UnitOfMeasure unitOfMeasure = unitsField.getSelectedItem();
                    product.setDefaultUnits(unitOfMeasure);

                    final Integer periodCount = parsePeriodCount(periodCountField.getText().toString());
                    product.setPeriodCount(periodCount);

                    final PeriodType periodType = periodTypeField.getSelectedItem();
                    product.setPeriodType(periodType);

                    activity.stopUnit(EditProductUnit.this);

                    fireEvent(new ProductEditedEvent(product));
                }
            });

            if (newProduct && firstRender && Strings.isNullOrEmpty(product.getName())) {
                ViewUtils.focusTextField(nameField);
            }

            firstRender = false;
        }

        @NonNull
        private String toName(Editable editable) {
            return editable.toString().trim();
        }

    }

    public static void initEditProductFields(
            ViewGroup parentView, final ShopListActivity activity,
            final Product product) {

        @SuppressWarnings("unchecked")
        final MaterialSpinner<UnitOfMeasure> unitsField = (MaterialSpinner<UnitOfMeasure>)
                parentView.findViewById(R.id.merge_edit_product_units_field);
        @SuppressWarnings("unchecked")
        final MaterialSpinner<PeriodType> periodTypeField = (MaterialSpinner<PeriodType>)
                parentView.findViewById(R.id.merge_edit_product_period_type);
        final EditText periodCountField = (EditText) parentView.findViewById(
                R.id.merge_edit_product_period_count);

        unitsField.setHint(activity.getString(R.string.edit_product_units_field_label));
        unitsField.setValues(Arrays.asList(UnitOfMeasure.values()));
        unitsField.setSelectedItem(product.getDefaultUnits(), false);
        unitsField.setStringConverter(new UnitOfMeasureStringifier(activity));
        unitsField.setNullString(activity.getString(R.string.material_spinner_default_null_string));


        periodTypeField.setValues(Arrays.asList(PeriodType.values()));
        periodTypeField.setSelectedItem(product.getPeriodType(), false);
        final PeriodTypeStringifier periodTypeStringifier = new PeriodTypeStringifier(activity);
        periodTypeStringifier.setCount(product.getPeriodCount());
        periodTypeField.setStringConverter(periodTypeStringifier);
        periodTypeField.setNullString(activity.getString(R.string.material_spinner_default_null_string));

        setTextWithoutAnimation(periodCountField, getPeriodCountString(product.getPeriodCount()));
        periodCountField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WheelPickerUtils.selectValue(
                        CollectionUtils.range(1, 15),
                        product.getPeriodCount(),
                        1,
                        new SimpleStringifier<Integer>(),
                        activity.getString(R.string.edit_product_period_count_label),
                        activity,
                        new WheelPickerUtils.Listener<Integer>() {
                            @Override
                            public void valueSelected(Integer newValue) {
                                periodCountField.setText(getPeriodCountString(newValue));

                                final PeriodTypeStringifier periodTypeStringifier =
                                        new PeriodTypeStringifier(activity);
                                periodTypeStringifier.setCount(newValue);
                                periodTypeField.setStringConverter(periodTypeStringifier);
                            }
                        }
                );
            }
        });
    }

    public static FactoryBasedAdapter<Category> initCategoriesSection(
            ViewGroup parentView,
            final ShopListActivity activity,
            final Set<Category> selectedCategories) {

        final Dao dao = activity.getDao();

        FactoryBasedAdapter<Category> categoriesAdapter = new FactoryBasedAdapter<>(new CategoryCellFactory());
        categoriesAdapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.MULTI);
        categoriesAdapter.setSorter(new CategoryComparator());
        categoriesAdapter.addAll(dao.getCategories());
        for (Category selectedCategory : selectedCategories) {
            categoriesAdapter.selectItem(selectedCategory);
        }
        categoriesAdapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<Category>() {
            @Override
            public void selectionChanged(Category item, boolean selected) {
                if (selected) {
                    selectedCategories.add(item);
                } else {
                    selectedCategories.remove(item);
                }
            }
        });

        final RecyclerView categoriesList = (RecyclerView) parentView.findViewById(
                R.id.merge_edit_product_categories_list);
        ListDecorator.decorateList(categoriesList);
        categoriesList.setAdapter(categoriesAdapter);

        return categoriesAdapter;
    }

    public static boolean validateProductName(
            String name, Product product, ViewGroup parentView, ShopListActivity activity) {

        if (name.isEmpty()) {
            final Toast toast = Toast.makeText(parentView.getContext(),
                    activity.getString(R.string.unit_edit_product_empty_name_not_allowed),
                    Toast.LENGTH_LONG);
            toast.show();
            return false;
        }

        final List<Product> products = activity.getDao().getProducts();
        for (Product anotherProduct : products) {
            if (Objects.equal(anotherProduct, product)) {
                continue;
            }

            if (StringUtils.equalIgnoreCase(anotherProduct.getName(), name)) {
                final Toast toast = Toast.makeText(parentView.getContext(),
                        activity.getString(
                                R.string.products_unit_already_exists,
                                anotherProduct.getName()),
                        Toast.LENGTH_LONG);
                toast.show();
                return false;
            }
        }
        return true;
    }

    private static String getPeriodCountString(Integer count) {
        if (count == null) {
            return "";
        }

        return String.valueOf(count);
    }

    public static Integer parsePeriodCount(String countString) {
        if ((countString == null) || (countString.trim().isEmpty())) {
            return null;
        }

        try {
            return Integer.parseInt(countString);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class ToolbarRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private transient String productName;
        private transient TextView titleField;

        private ToolbarRenderer(String productName) {
            this.productName = productName;
        }

        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_edit_product_toolbar, parentView, true);

            titleField = (TextView) parentView.findViewById(R.id.unit_edit_product_title);
            updateTitle();
        }

        private void updateTitle() {
            final String title = titleField.getContext().getString(
                    R.string.unit_edit_product_title, productName);

            titleField.setText(title);
        }

        public void setProductName(String productName) {
            this.productName = productName;

            if (titleField != null) {
                updateTitle();
            }
        }
    }

}
