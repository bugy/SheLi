package net.buggy.shoplist;


import android.content.Context;
import android.util.Log;

import com.activeandroid.ActiveAndroid;

import net.buggy.shoplist.crashes.ShopListCrashDialog;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;


public class Application extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/Neucha.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        final ACRAConfiguration configuration;
        try {
            configuration = new ConfigurationBuilder(this)
                    .setMailTo(ShopListActivity.DEVELOPER_EMAIL)
                    .setReportDialogClass(ShopListCrashDialog.class)
                    .setLogcatArguments("-t", "200", "-v", "long", "*")
                    .setCustomReportContent(
                            ReportField.ANDROID_VERSION,
                            ReportField.APP_VERSION_NAME,
                            ReportField.DISPLAY,
                            ReportField.LOGCAT,
                            ReportField.PHONE_MODEL,
                            ReportField.STACK_TRACE)
                    .setReportingInteractionMode(ReportingInteractionMode.DIALOG)
                    .build();

            ACRA.init(this, configuration);

        } catch (ACRAConfigurationException e) {
            Log.e("Application", "attachBaseContext: couldn't init ACRA", e);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }

}
