package net.buggy.shoplist.units;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.components.list.SwipeToRemoveHandler;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.compare.CategoryComparator;
import net.buggy.shoplist.components.EditableCategoryCellFactory;
import net.buggy.shoplist.components.FastCreationPanel;
import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.UiThreadEntityListener;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.units.views.InflatingViewRenderer;
import net.buggy.shoplist.units.views.ViewRenderer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;
import static net.buggy.shoplist.units.UnitsHelper.addTemporalDaoListener;

public class CategoriesUnit extends Unit<ShopListActivity> {

    private transient FactoryBasedAdapter<Category> adapter;

    @Override
    public void initialize() {
        addRenderer(TOOLBAR_VIEW_ID, new InflatingViewRenderer<ShopListActivity, ViewGroup>(
                R.layout.unit_categories_toolbar));

        addRenderer(MAIN_VIEW_ID, new MainRenderer());
    }

    @Override
    protected void onEvent(final Object event) {
        if (event instanceof EditCategoryUnit.CategoryEditedEvent) {
            final EditCategoryUnit.CategoryEditedEvent categoryEvent =
                    (EditCategoryUnit.CategoryEditedEvent) event;

            final Dao dao = getHostingActivity().getDao();
            final Category category = categoryEvent.getCategory();
            dao.saveCategory(category);

            final Runnable relinkRunnable = new Runnable() {
                @Override
                public void run() {
                    ModelHelper.saveCategoryLinkedProducts(
                            categoryEvent.getCategory(),
                            categoryEvent.getCategoryProducts(),
                            dao);
                }
            };

            new Thread(relinkRunnable).start();

            return;
        }

        super.onEvent(event);
    }

    private class MainRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {
        @Override
        public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_categories, parentView, true);

            initList(parentView, activity);

            initAddCategory(parentView, activity);
        }

        private void initList(final ViewGroup parentView, final ShopListActivity activity) {
            final EditableCategoryCellFactory cellFactory = new EditableCategoryCellFactory(new EditableCategoryCellFactory.Listener() {
                @Override
                public void onEdit(Category category) {
                    final EditCategoryUnit unit = new EditCategoryUnit(category, false);
                    unit.setListeningUnit(CategoriesUnit.this);
                    activity.startUnit(unit);
                }
            });

            adapter = new FactoryBasedAdapter<>(cellFactory);
            adapter.setSorter(new CategoryComparator());

            final Dao dao = activity.getDao();
            adapter.addAll(dao.getCategories());

            final RecyclerView categoriesView = parentView.findViewById(R.id.unit_categories_list);
            ListDecorator.decorateList(categoriesView);
            categoriesView.setAdapter(adapter);

            final int backgroundColor = ViewUtils.resolveColor(R.color.color_underground, parentView.getContext());
            final SwipeToRemoveHandler handler = new SwipeToRemoveHandler(backgroundColor);
            handler.attach(categoriesView);
            handler.setDeletionHandler(new SwipeToRemoveHandler.DeletionHandler() {
                @Override
                public boolean canDelete(int position) {
                    return true;
                }

                @Override
                public void onDelete(int position, SwipeToRemoveHandler.DeletionCallback callback) {
                    final Category category = adapter.getItem(position);

                    final List<Product> products = dao.getProducts();
                    final Set<Product> matchingProducts = new LinkedHashSet<>();
                    for (Product product : products) {
                        if (product.getCategories().contains(category)) {
                            matchingProducts.add(product);
                        }
                    }

                    if (matchingProducts.isEmpty()) {
                        callback.delete();

                    } else {
                        final int count = matchingProducts.size();
                        final String confirmation = activity.getResources().getQuantityString(
                                R.plurals.unit_categories_unlink_confirmation, count, count);
                        callback.askConfirmation(confirmation);
                    }
                }
            });

            adapter.addDataListener(new FactoryBasedAdapter.DataListener<Category>() {
                @Override
                public void added(Category item) {
                }

                @Override
                public void removed(Category item) {
                    dao.removeCategory(item);
                }

                @Override
                public void changed(Category changedItem) {
                    final String name = changedItem.getName();

                    if (!ModelHelper.isUnique(changedItem, name, activity)) {
                        final Toast toast = Toast.makeText(
                                parentView.getContext(),
                                getHostingActivity().getString(
                                        R.string.categories_unit_already_exists_on_edit,
                                        name),
                                Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }

                    dao.saveCategory(changedItem);
                }
            });

            final SwipeRefreshLayout swipeRefreshLayout = parentView.findViewById(R.id.unit_categories_swipe_refresh);
            swipeRefreshLayout.setColorSchemeResources(R.color.color_primary);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    adapter.clear();
                    adapter.addAll(dao.getCategories());

                    swipeRefreshLayout.setRefreshing(false);
                }
            });

            final UiThreadEntityListener<Category> listener = new TableAdapterEntityListener<>(
                    adapter, getHostingActivity());
            addTemporalDaoListener(dao, Category.class, listener, CategoriesUnit.this);
        }

        private void initAddCategory(final ViewGroup parentView, final ShopListActivity activity) {
            final FastCreationPanel creationPanel = parentView.findViewById(R.id.unit_categories_creation_panel);
            creationPanel.setListener(new FastCreationPanel.Listener() {
                @Override
                public void onCreate(String name) {
                    addCategory(name, parentView.getContext(), activity.getDao());
                }
            });
        }

        private void addCategory(String name, Context context, Dao dao) {
            if (!ModelHelper.isUnique(null, name, (ShopListActivity) context)) {
                final Toast toast = Toast.makeText(context,
                        context.getResources().getString(R.string.category_already_exists, name),
                        Toast.LENGTH_LONG);
                toast.show();
                return;
            }

            final Category category = ModelHelper.createCategory(name);

            dao.addCategory(category);
        }
    }
}
