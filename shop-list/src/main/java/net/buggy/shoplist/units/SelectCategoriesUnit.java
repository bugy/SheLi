package net.buggy.shoplist.units;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.CategoryComparator;
import net.buggy.shoplist.components.CategoryCellFactory;
import net.buggy.shoplist.components.FastCreationPanel;
import net.buggy.shoplist.components.ListDecorator;
import net.buggy.shoplist.data.DataStorage;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.units.views.ViewRenderer;

import java.util.List;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;
import static net.buggy.shoplist.utils.StringUtils.equalIgnoreCase;

public class SelectCategoriesUnit extends Unit<ShopListActivity> {

    private final String productName;
    private final List<Category> selectedCategories;

    private transient FactoryBasedAdapter<Category> adapter;

    public SelectCategoriesUnit(String productName, List<Category> selectedCategories) {
        this.productName = productName;
        this.selectedCategories = selectedCategories;
    }

    @Override
    public void start() {
        addRenderer(TOOLBAR_VIEW_ID, new ViewRenderer<ShopListActivity, ViewGroup>() {
            @Override
            public void renderTo(ViewGroup parentView, ShopListActivity activity) {
                final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
                inflater.inflate(R.layout.unit_select_categories_toolbar, parentView, true);

                final TextView titleField = (TextView) parentView.findViewById(R.id.unit_select_categories_title);
                final String titlePattern = activity.getString(R.string.unit_categories_list_title);
                titleField.setText(String.format(titlePattern, productName));
            }
        });
        addRenderer(MAIN_VIEW_ID, new MainRenderer());
    }

    private class MainRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {
        @Override
        public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_select_categories, parentView, true);

            initList(parentView, activity);

            initAddCategory(parentView, activity);

            final ImageButton acceptButton = (ImageButton) parentView.findViewById(R.id.unit_select_categories_accept);
            acceptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.stopUnit(SelectCategoriesUnit.this);

                    List<Category> selectedCategories = adapter.getSelectedItems();

                    fireEvent(new CategoriesSelectedEvent(selectedCategories));
                }
            });
        }

        private void initList(ViewGroup parentView, final ShopListActivity activity) {
            final CategoryCellFactory cellFactory = new CategoryCellFactory();

            adapter = new FactoryBasedAdapter<>(cellFactory);
            adapter.setSelectionMode(FactoryBasedAdapter.SelectionMode.MULTI);
            adapter.setSorter(new CategoryComparator());

            final DataStorage dataStorage = activity.getDataStorage();
            adapter.addAll(dataStorage.getCategories());

            for (Category selectedCategory : selectedCategories) {
                adapter.selectItem(selectedCategory);
            }

            final RecyclerView categoriesView = (RecyclerView) parentView.findViewById(R.id.unit_select_categories_list);
            ListDecorator.decorateList(categoriesView);
            categoriesView.setAdapter(adapter);
        }

        private void initAddCategory(final ViewGroup parentView, final ShopListActivity activity) {
            final FastCreationPanel creationPanel = (FastCreationPanel)
                    parentView.findViewById(R.id.unit_select_categories_creation_panel);
            creationPanel.setListener(new FastCreationPanel.Listener() {
                @Override
                public void onCreate(String name) {
                    addCategory(name, parentView.getContext(), activity.getDataStorage());
                }
            });

        }

        private void addCategory(String name, Context context, DataStorage dataStorage) {
            final List<Category> categories = adapter.getAllItems();
            for (Category category : categories) {
                if (equalIgnoreCase(name, category.getName())) {
                    final Toast toast = Toast.makeText(
                            context,
                            getHostingActivity().getString(
                                    R.string.category_already_exists,
                                    category.getName()),
                            Toast.LENGTH_LONG);
                    toast.show();
                    adapter.selectItem(category);
                    return;
                }
            }

            final Category category = ModelHelper.createCategory(name);

            dataStorage.addCategory(category);

            adapter.add(category);
            adapter.selectItem(category);
        }
    }

    public static final class CategoriesSelectedEvent {
        private final List<Category> selectedCategories;

        public CategoriesSelectedEvent(List<Category> selectedCategories) {
            this.selectedCategories = selectedCategories;
        }

        public List<Category> getSelectedCategories() {
            return selectedCategories;
        }
    }

}
