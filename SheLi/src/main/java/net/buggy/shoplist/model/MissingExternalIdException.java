package net.buggy.shoplist.model;


import android.os.Build;
import android.support.annotation.RequiresApi;

public class MissingExternalIdException extends Exception {

    public MissingExternalIdException() {
    }

    public MissingExternalIdException(String message) {
        super(message);
    }

    public MissingExternalIdException(String message, Throwable cause) {
        super(message, cause);
    }

    public MissingExternalIdException(Throwable cause) {
        super(cause);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public MissingExternalIdException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
