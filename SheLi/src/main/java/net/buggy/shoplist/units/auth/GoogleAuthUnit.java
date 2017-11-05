package net.buggy.shoplist.units.auth;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.Unit;
import net.buggy.shoplist.units.views.InflatingViewRenderer;


public class GoogleAuthUnit extends Unit<ShopListActivity> {

    private static final int GOOGLE_SIGN_IN = 12300;

    private transient FirebaseAuth auth;
    private transient GoogleApiClient apiClient;

    @Override
    protected void initialize() {
        this.auth = FirebaseAuth.getInstance();

        apiClient = prepareApiClient();
        apiClient.connect();

        addRenderer(ShopListActivity.MAIN_VIEW_ID, new BodyRenderer());

        if (this.auth.getCurrentUser() == null) {
            signInGoogle();
        } else {
            getHostingActivity().stopUnit(GoogleAuthUnit.this);
        }

    }

    @Override
    protected void deinitialize() {
        if (apiClient.isConnected()) {
            Auth.GoogleSignInApi.signOut(apiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            Log.i("GoogleAuthUnit", "signOut.onResult: apiClient sign out finished");
                        }
                    });

            apiClient.disconnect();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GOOGLE_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                Log.i("SharingBodyRenderer", "onActivityResult: API connection succeeded");
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account, getHostingActivity());

            } else {
                Log.w("SharingBodyRenderer", "onActivityResult: API connection failed. "
                        + result.getStatus().getStatusMessage() + "."
                        + result.getStatus().toString());

                getHostingActivity().stopUnit(GoogleAuthUnit.this);
                fireEvent(new AuthFinishedEvent(false));
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct, final Activity activity) {
        Log.i("GoogleAuthUnit", "firebaseAuthWithGoogle: " + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        auth.signInWithCredential(credential).addOnCompleteListener(activity, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.i("SharingBodyRenderer", "firebaseAuthWithGoogle.Complete: authenticated successfully");

                } else {
                    Log.w("SharingBodyRenderer",
                            "firebaseAuthWithGoogle.Complete: authentication failed",
                            task.getException());
                }

                getHostingActivity().stopUnit(GoogleAuthUnit.this);
                fireEvent(new AuthFinishedEvent(task.isSuccessful()));
            }
        });
    }

    private GoogleApiClient prepareApiClient() {
        final ShopListActivity activity = getHostingActivity();
        GoogleSignInOptions signInOptions = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(activity.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        return new GoogleApiClient.Builder(activity)
                .addApi(Auth.GOOGLE_SIGN_IN_API, signInOptions)
                .build();
    }

    private void signInGoogle() {
        Log.i("GoogleAuthUnit", "signInGoogle: method called");

        final ShopListActivity activity = getHostingActivity();

        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(apiClient);
        activity.startAnotherActivity(signInIntent, GOOGLE_SIGN_IN, GoogleAuthUnit.this);
    }


    private class BodyRenderer extends InflatingViewRenderer<ShopListActivity, ViewGroup> {

        public BodyRenderer() {
            super(R.layout.progress_panel);
        }

        @Override
        public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
            super.renderTo(parentView, activity);

            final TextView progressPanelText = (TextView) parentView.findViewById(
                    R.id.progress_panel_text);
            progressPanelText.setText(R.string.unit_auth_google_login_progress_text);
        }
    }
}
