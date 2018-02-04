package net.buggy.shoplist.units.auth;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Strings;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.SharingUnit;
import net.buggy.shoplist.units.Unit;
import net.buggy.shoplist.units.views.ViewRenderer;


public class EmailAuthUnit extends Unit<ShopListActivity> {

    @Override
    protected void initialize() {
        final BodyRenderer bodyRenderer = new BodyRenderer();

        addRenderer(ShopListActivity.MAIN_VIEW_ID, bodyRenderer);
        addRenderer(ShopListActivity.TOOLBAR_VIEW_ID,
                new UsernameToolbarRenderer(new UsernameToolbarRenderer.Listener() {
                    @Override
                    public void onLogout() {
                        bodyRenderer.refresh();
                    }
                }));
    }

    private class BodyRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

        private transient Button logInButton;
        private transient Button registerButton;
        private transient EditText addressField;
        private transient EditText passwordField;
        private transient TextView errorLabel;

        @Override
        public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_auth_email, parentView, true);

            logInButton = parentView.findViewById(
                    R.id.unit_auth_email_login_button);
            registerButton = parentView.findViewById(
                    R.id.unit_auth_email_register_button);
            addressField = parentView.findViewById(
                    R.id.unit_auth_email_address);
            passwordField = parentView.findViewById(
                    R.id.unit_auth_email_password);
            errorLabel = parentView.findViewById(
                    R.id.unit_auth_email_error_label);

            refresh();
        }

        public void refresh() {
            final FirebaseAuth auth = FirebaseAuth.getInstance();

            if (auth.getCurrentUser() == null) {
                renderAuthentication();
            } else {
                getHostingActivity().stopUnit(EmailAuthUnit.this);
                fireEvent(new AuthFinishedEvent(true));
            }
        }

        private void renderAuthentication() {
            final FirebaseAuth auth = FirebaseAuth.getInstance();

            logInButton.setEnabled(true);
            registerButton.setEnabled(true);

            logInButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String password = passwordField.getText().toString();
                    final String email = addressField.getText().toString().trim();

                    if (Strings.isNullOrEmpty(password) || Strings.isNullOrEmpty(email)) {
                        if (Strings.isNullOrEmpty(email)) {
                            errorLabel.setText(R.string.unit_auth_email_empty_email);
                        } else {
                            errorLabel.setText(R.string.unit_auth_email_empty_password);
                        }
                        errorLabel.setVisibility(View.VISIBLE);

                        return;
                    }

                    errorLabel.setVisibility(View.GONE);
                    logInButton.setEnabled(false);
                    registerButton.setEnabled(false);

                    auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(getHostingActivity(), new SignInListener());
                }
            });


            registerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String password = passwordField.getText().toString();
                    final String email = addressField.getText().toString().trim();

                    if (Strings.isNullOrEmpty(password) || Strings.isNullOrEmpty(email)) {
                        if (Strings.isNullOrEmpty(email)) {
                            errorLabel.setText(R.string.unit_auth_email_empty_email);
                        } else {
                            errorLabel.setText(R.string.unit_auth_email_empty_password);
                        }
                        errorLabel.setVisibility(View.VISIBLE);

                        return;
                    }

                    logInButton.setEnabled(false);
                    registerButton.setEnabled(false);
                    errorLabel.setVisibility(View.GONE);

                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(getHostingActivity(), new RegistrationListener());
                }
            });
        }

        private class SignInListener implements OnCompleteListener<AuthResult> {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.i("EmailAuthUnit", "logInButton.onComplete: authenticated successfully");
                    errorLabel.setVisibility(View.GONE);

                } else {
                    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                    final Exception exception = task.getException();

                    Log.w("EmailAuthUnit",
                          "logInButton.onComplete: couldn't authenticate the user",
                          exception);

                    final int errorTextKey = getErrorTextKey(exception);

                    errorLabel.setText(errorTextKey);
                    errorLabel.setVisibility(View.VISIBLE);
                }

                refresh();
            }

            private int getErrorTextKey(Exception exception) {
                if (exception instanceof FirebaseAuthException) {
                    FirebaseAuthException authException = (FirebaseAuthException) exception;
                    final String errorCode = authException.getErrorCode();

                    if ("ERROR_WRONG_PASSWORD".equals(errorCode)) {
                        return R.string.unit_auth_email_login_error_wrong_password;
                    } else if ("ERROR_INVALID_EMAIL".equals(errorCode)) {
                        return R.string.unit_auth_email_login_error_user_not_exists;
                    }
                }

                if (exception instanceof FirebaseAuthInvalidUserException) {
                    return R.string.unit_auth_email_login_error_user_not_exists;
                }

                return R.string.unit_auth_email_login_error_default;
            }
        }

        private class RegistrationListener implements OnCompleteListener<AuthResult> {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.i("EmailAuthUnit",
                          "registerButton.onComplete: email registered");

                    errorLabel.setVisibility(View.GONE);

                    SharingUnit.sendEmailConfirmation(getHostingActivity());

                } else {
                    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                    final Exception exception = task.getException();

                    Log.w("EmailAuthUnit",
                          "registerButton.onComplete: couldn't register the email",
                          exception);

                    final int errorTextKey;
                    if (exception instanceof FirebaseAuthUserCollisionException) {
                        errorTextKey = R.string.unit_auth_email_registration_error_already_registered;
                    } else if (exception instanceof FirebaseAuthWeakPasswordException) {
                        errorTextKey = R.string.unit_auth_email_registration_error_weak_password;
                    } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                        errorTextKey = R.string.unit_auth_email_registration_error_invalid_email;
                    } else {
                        errorTextKey = R.string.unit_auth_email_registration_error_default;
                    }

                    errorLabel.setText(errorTextKey);
                    errorLabel.setVisibility(View.VISIBLE);
                }

                refresh();
            }
        }
    }
}
