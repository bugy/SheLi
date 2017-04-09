package net.buggy.shoplist.data;


import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.Configuration;
import com.activeandroid.Model;
import com.activeandroid.serializer.BigDecimalSerializer;

import net.buggy.shoplist.R;
import net.buggy.shoplist.crashes.ShopListCrashDialog;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;


public class ApplicationWithDatabase extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        final Configuration.Builder builder = new Configuration.Builder(this);

        for (Class<? extends Model> clazz : DataStorage.getModelClasses()) {
            builder.addModelClass(clazz);
        }
        builder.addTypeSerializer(BigDecimalSerializer.class);

        Configuration dbConfiguration = builder.create();
        ActiveAndroid.initialize(dbConfiguration);

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
                    .setMailTo("buggygm@gmail.com")
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
            Log.e("ApplicationWithDatabase", "attachBaseContext: couldn't init ACRA", e);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }

}
