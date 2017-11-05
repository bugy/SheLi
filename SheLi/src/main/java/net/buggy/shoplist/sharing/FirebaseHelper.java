package net.buggy.shoplist.sharing;

import android.util.Log;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;

public class FirebaseHelper {

    private static final DecimalFormat BIG_DECIMAL_FORMAT;

    static {
        BIG_DECIMAL_FORMAT = new DecimalFormat();
        BIG_DECIMAL_FORMAT.setGroupingUsed(false);
        BIG_DECIMAL_FORMAT.setParseBigDecimal(true);
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        BIG_DECIMAL_FORMAT.setDecimalFormatSymbols(symbols);
    }

    public static ImmutableMap<String, Object> getChildrenValues(DataSnapshot snapshot) {
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            if (child.getValue() != null) {
                values.put(child.getKey(), child.getValue());
            }
        }
        return ImmutableMap.copyOf(values);
    }

    public static DatabaseReference getUserReference() {
        final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            throw new IllegalStateException("User is not authorized");
        }

        return FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
    }

    public static Long serializeDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.getTime();
    }

    public static Date deserializeDate(Long value) {
        if (value == null) {
            return null;
        }
        return new Date(value);
    }

    public static Date getDateValue(DataSnapshot dataSnapshot) {
        if ((dataSnapshot == null) || (!dataSnapshot.exists())) {
            return null;
        }

        final Long millis = dataSnapshot.getValue(Long.class);
        return deserializeDate(millis);
    }

    public static String serializeEnum(Enum value) {
        if (value == null) {
            return null;
        }

        return value.name();
    }

    public static <T extends Enum<T>> T deserializeEnum(String value, Class<T> clazz) {
        if ((value == null) || (value.trim().isEmpty())) {
            return null;
        }

        for (final T e : clazz.getEnumConstants()) {
            if (e.name().equals(value.trim())) {
                return e;
            }
        }

        Log.e("FirebaseHelper", "deserializeEnum: no enum found for " + value + ", clazz=" + clazz.getSimpleName());
        return null;
    }

    public static <T extends Enum<T>> T getEnumValue(DataSnapshot dataSnapshot, Class<T> clazz) {
        if ((dataSnapshot == null) || (!dataSnapshot.exists())) {
            return null;
        }

        final String name = dataSnapshot.getValue(String.class);
        return deserializeEnum(name, clazz);
    }

    public static BigDecimal getBigDecimalValue(DataSnapshot dataSnapshot) {
        if ((dataSnapshot == null) || (!dataSnapshot.exists())) {
            return null;
        }

        final String value = dataSnapshot.getValue(String.class);
        return deserializeBigDecimal(value);
    }

    public static String serializeBigDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }

        return BIG_DECIMAL_FORMAT.format(value);
    }

    public static BigDecimal deserializeBigDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            final Number number = BIG_DECIMAL_FORMAT.parse(value);
            if (number instanceof BigDecimal) {
                return (BigDecimal) number;
            }

            return new BigDecimal(String.valueOf(number));
        } catch (ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
