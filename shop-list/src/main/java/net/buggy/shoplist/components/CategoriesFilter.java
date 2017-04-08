package net.buggy.shoplist.components;


import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import net.buggy.components.TagFlag;
import net.buggy.components.ViewUtils;
import net.buggy.components.list.FactoryBasedAdapter;
import net.buggy.components.list.ListDecorator;
import net.buggy.shoplist.R;
import net.buggy.shoplist.compare.CategoryComparator;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.utils.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static android.widget.ListPopupWindow.MATCH_PARENT;
import static net.buggy.components.list.FactoryBasedAdapter.SelectionMode.MULTI;

public class CategoriesFilter extends FrameLayout {

    private FactoryBasedAdapter<Category> categoriesAdapter;
    private View popupAnchor;
    private Context popupContext;
    private final List<CategoriesFilter.Listener> listeners = new CopyOnWriteArrayList<>();
    private PopupWindow popupWindow;

    public CategoriesFilter(Context context) {
        super(context);

        init(context);
    }

    public CategoriesFilter(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    public CategoriesFilter(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CategoriesFilter(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(context);
    }

    private void init(final Context context) {
        popupAnchor = this;
        popupContext = context;

        inflate(context, R.layout.categories_filter, this);

        final ImageButton categoriesFilterButton = (ImageButton) this.findViewById(
                R.id.categories_filter_filter_button);

        final RelativeLayout activeCategoriesContainer = (RelativeLayout) this.findViewById(
                R.id.categories_filter_categories_container);
        final int filterButtonHeight = categoriesFilterButton.getDrawable().getIntrinsicHeight();

        categoriesAdapter = new FactoryBasedAdapter<>(
                new CategoriesFilterCellFactory());
        categoriesAdapter.setSorter(CategoryComparator.INSTANCE);
        categoriesAdapter.setSelectionMode(MULTI);

        final int flagCutHeight = ViewUtils.dpToPx(4, context);
        categoriesAdapter.addSelectionListener(new FactoryBasedAdapter.SelectionListener<Category>() {
            @Override
            public void selectionChanged(Category item, boolean selected) {
                final List<Category> selectedItems = categoriesAdapter.getSelectedItems();
                fireCategoriesSelected(selectedItems);

                activeCategoriesContainer.removeAllViews();

                if (!CollectionUtils.isEmpty(selectedItems)) {
                    categoriesFilterButton.setActivated(true);

                    final int itemHeight = filterButtonHeight / selectedItems.size();
                    int i = 0;

                    for (Category category : selectedItems) {
                        int flagHeight = itemHeight * (i + 1) + flagCutHeight;

                        final TagFlag tagFlag = new TagFlag(context);
                        tagFlag.setColor(ModelHelper.getColor(category));
                        tagFlag.setBorderColor(Color.TRANSPARENT);
                        final RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, flagHeight);
                        params.topMargin = flagCutHeight;
                        tagFlag.setLayoutParams(params);
                        tagFlag.setCutHeight(flagCutHeight);

                        i++;

                        activeCategoriesContainer.addView(tagFlag, 0);
                    }

                } else {
                    categoriesFilterButton.setActivated(false);
                }
            }
        });

        final Context popupContext;
        if (context instanceof ContextThemeWrapper) {
            popupContext = ((ContextThemeWrapper) context).getBaseContext();
        } else {
            popupContext = context.getApplicationContext();
        }

        final LayoutInflater popupInflater = LayoutInflater.from(popupContext);
        final ViewGroup popupContent = (ViewGroup) popupInflater.inflate(
                R.layout.popup_categories_filter,
                null);

        final RecyclerView categoriesList = (RecyclerView) popupContent.findViewById(
                R.id.popup_categories_filter_categories_list);
        ListDecorator.decorateList(categoriesList);
        categoriesList.setAdapter(categoriesAdapter);

        popupWindow = new PopupWindow(popupContent,
                MATCH_PARENT,
                MATCH_PARENT,
                true);
        popupWindow.setFocusable(false);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        popupContent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (popupWindow.isShowing()) {
                    popupWindow.dismiss();
                }
            }
        });

        categoriesFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!popupWindow.isShowing()) {
                    popupWindow.showAsDropDown(popupAnchor);
                }
            }
        });
    }

    private void fireCategoriesSelected(List<Category> categories) {
        for (Listener listener : listeners) {
            listener.categoriesSelected(categories);
        }
    }

    public void setCategories(Collection<Category> categories) {
        categoriesAdapter.clear();
        categoriesAdapter.addAll(categories);
    }

    public void setPopupAnchor(View view) {
        this.popupAnchor = view;
    }

    public void setPopupContext(Context popupContext) {
        this.popupContext = popupContext;
    }

    public void addListener(CategoriesFilter.Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void categoriesSelected(List<Category> categories);
    }
}
