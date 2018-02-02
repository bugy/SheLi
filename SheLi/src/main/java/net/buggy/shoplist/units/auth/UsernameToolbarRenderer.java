package net.buggy.shoplist.units.auth;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.common.base.Strings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import net.buggy.components.ViewUtils;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.views.ViewRenderer;

public class UsernameToolbarRenderer extends ViewRenderer<ShopListActivity, ViewGroup> {

    private final Listener listener;

    public UsernameToolbarRenderer(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
        final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
        inflater.inflate(R.layout.unit_auth_toolbar, parentView, true);

        final ImageButton logOutButton = parentView.findViewById(
                R.id.unit_sharing_logout_button);
        final TextView usernameLabel = parentView.findViewById(
                R.id.unit_sharing_username_label);

        ViewUtils.setColorListTint(logOutButton, R.color.disableable_color_for_toolbar);

        logOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();

                updateLoginState(usernameLabel, logOutButton);

                listener.onLogout();
            }
        });

        updateLoginState(usernameLabel, logOutButton);
    }

    private void signOut() {
        Log.i("UsernameToolbarRenderer", "signOut: method called");

        FirebaseAuth.getInstance().signOut();
    }

    private void updateLoginState(TextView usernameLabel, ImageButton logOutButton) {
        final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        final boolean authorized = currentUser != null;

        if (authorized) {
            String displayName = currentUser.getDisplayName();
            if (Strings.isNullOrEmpty(displayName)) {
                displayName = currentUser.getEmail();
            }
            usernameLabel.setText(displayName);
            logOutButton.setVisibility(View.VISIBLE);

        } else {
            usernameLabel.setText(R.string.unit_auth_authorized_label);
            logOutButton.setVisibility(View.GONE);
        }

        logOutButton.setEnabled(authorized);
    }

    public interface Listener {
        void onLogout();
    }
}
