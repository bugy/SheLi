package net.buggy.shoplist.data;


import android.app.Application;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.Configuration;
import com.activeandroid.Model;
import com.activeandroid.serializer.BigDecimalSerializer;

public class ApplicationWithDatabase extends Application{

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
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }

}
