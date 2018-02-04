package net.buggy.shoplist.data;

import com.activeandroid.Configuration;
import com.activeandroid.Model;
import com.activeandroid.serializer.BigDecimalSerializer;

public class ContentProvider extends com.activeandroid.content.ContentProvider {

    @Override
    protected Configuration getConfiguration() {
        final Configuration.Builder builder = new Configuration.Builder(getContext());

        for (Class<? extends Model> clazz : SqlliteDao.getModelClasses()) {
            builder.addModelClass(clazz);
        }
        builder.addTypeSerializer(BigDecimalSerializer.class);

        return builder.create();
    }
}
