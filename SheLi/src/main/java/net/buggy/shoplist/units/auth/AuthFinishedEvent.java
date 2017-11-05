package net.buggy.shoplist.units.auth;


public class AuthFinishedEvent {

    private final boolean success;

    public AuthFinishedEvent(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
