package net.buggy.shoplist.units;

import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.buggy.components.spinners.MaterialSpinner;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.model.Language;
import net.buggy.shoplist.model.Settings;
import net.buggy.shoplist.units.views.InflatingViewRenderer;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.LanguageEnumStringifier;

import java.util.Arrays;

import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class SettingsUnit extends Unit<ShopListActivity> {

    @Override
    public void initialize() {
        addRenderer(MAIN_VIEW_ID, new MainViewRenderer());
        addRenderer(TOOLBAR_VIEW_ID, new InflatingViewRenderer<ShopListActivity, ViewGroup>(
                R.layout.unit_settings_toolbar));
    }

    private class MainViewRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {
        @Override
        public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_settings, parentView, true);

            final Settings settings = activity.getDao().getSettings();

            @SuppressWarnings("unchecked") final MaterialSpinner<Language> languageField = parentView.findViewById(R.id.unit_settings_language_field);
            languageField.setValues(Arrays.asList(Language.values()));
            languageField.setHint(activity.getString(R.string.unit_settings_language_hint));
            languageField.setSelectedItem(settings.getLanguage());
            languageField.setStringConverter(new LanguageEnumStringifier(activity));
            languageField.setNullString(activity.getString(R.string.unit_settings_system_language));
            languageField.setShowNullValue(true);


            final FloatingActionButton applyButton = parentView.findViewById(
                    R.id.unit_settings_apply_button);

            applyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.setLanguage(languageField.getSelectedItem());

                    activity.getDao().saveSettings(settings);

                    activity.recreate();
                }
            });
        }
    }
}
