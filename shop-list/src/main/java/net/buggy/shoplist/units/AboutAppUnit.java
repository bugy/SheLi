package net.buggy.shoplist.units;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.buggy.components.ViewUtils;
import net.buggy.shoplist.BuildConfig;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.views.InflatingViewRenderer;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class AboutAppUnit extends Unit<ShopListActivity> {
    @Override
    public void initialize() {
        addRenderer(MAIN_VIEW_ID, new MainViewRenderer());

        final InflatingViewRenderer<ShopListActivity, ViewGroup> toolbarRenderer =
                new InflatingViewRenderer<>(R.layout.unit_about_toolbar);
        addRenderer(TOOLBAR_VIEW_ID, toolbarRenderer);
    }

    private class MainViewRenderer extends net.buggy.shoplist.units.views.ViewRenderer<ShopListActivity, ViewGroup> {
        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_about, parentView, true);

            final TextView versionView = (TextView) parentView.findViewById(R.id.unit_about_version);

            String version = ViewUtils.getVersion(activity);

            final String versionString = activity.getString(R.string.version_pattern, version);
            versionView.setText(versionString);

            if (BuildConfig.DEBUG) {
                versionView.setOnClickListener(new View.OnClickListener() {
                    int count = 0;

                    @Override
                    public void onClick(View v) {
                        count++;

                        if (count > 3) {
                            throw new RuntimeException("This is debug exception. To test reporting");
                        }
                    }
                });
            }
        }
    }
}
