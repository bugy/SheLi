package net.buggy.shoplist.units;

import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.sharing.FirebaseHelper;
import net.buggy.shoplist.units.auth.AuthFinishedEvent;
import net.buggy.shoplist.units.auth.EmailAuthUnit;
import net.buggy.shoplist.units.auth.GoogleAuthUnit;
import net.buggy.shoplist.units.auth.UsernameToolbarRenderer;
import net.buggy.shoplist.units.views.InflatingViewRenderer;
import net.buggy.shoplist.units.views.ViewRenderer;
import net.buggy.shoplist.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SharingUnit extends Unit<ShopListActivity> {

    private transient FirebaseAuth auth;
    private transient DispatchingBodyRenderer dispatchingBodyRenderer;

    @Override
    protected void initialize() {
        this.auth = FirebaseAuth.getInstance();

        dispatchingBodyRenderer = new DispatchingBodyRenderer();
        addRenderer(ShopListActivity.MAIN_VIEW_ID, dispatchingBodyRenderer);
        addRenderer(ShopListActivity.TOOLBAR_VIEW_ID,
                new UsernameToolbarRenderer(new LogoutListener()));
    }

    private String serializeEmail(String email) {
        if (email == null) {
            return null;
        }

        return email.trim().toLowerCase().replace('.', ',');
    }

    private String deserializeEmail(String emailText) {
        if (emailText == null) {
            return null;
        }

        return emailText.replace(',', '.').trim().toLowerCase();
    }

    @Override
    protected void onEvent(Object event) {
        if (event instanceof AuthFinishedEvent) {
            AuthFinishedEvent authEvent = (AuthFinishedEvent) event;
            if (!authEvent.isSuccess()) {
                Toast.makeText(
                        getHostingActivity(), R.string.unit_login_auth_failed, Toast.LENGTH_LONG)
                        .show();
            }

        } else {
            super.onEvent(event);
        }
    }

    public static boolean sendEmailConfirmation(final ShopListActivity activity) {
        final FirebaseAuth auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            Log.w("EmailAuthUnit", "sendEmailConfirmation:" +
                    " tried to send confirmation for null user");
            return false;
        }

        if (auth.getCurrentUser().isEmailVerified()) {
            Log.w("EmailAuthUnit", "sendEmailConfirmation:" +
                    " tried to send confirmation for verified user");
            return false;
        }

        auth.getCurrentUser().sendEmailVerification()
                .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(
                                    activity,
                                    R.string.unit_auth_email_verification_sent,
                                    Toast.LENGTH_LONG)
                                    .show();
                        } else {
                            Toast.makeText(
                                    activity,
                                    R.string.unit_auth_email_verification_failed,
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                });

        return true;
    }

    private void refreshUnit() {
        dispatchingBodyRenderer.refresh();
    }

    private class DispatchingBodyRenderer extends InflatingViewRenderer<ShopListActivity, ViewGroup> {

        private final LoginBodyRenderer loginBodyRenderer;
        private final SharingBodyRenderer sharingBodyRenderer;
        private final EmailVerificationBodyRenderer emailVerificationBodyRenderer;
        private SwipeRefreshLayout swipeRefreshLayout;
        private ViewGroup loginPanel;
        private ViewGroup verificationPanel;
        private ViewGroup sharePanel;

        public DispatchingBodyRenderer() {
            super(R.layout.unit_sharing_container);

            loginBodyRenderer = new LoginBodyRenderer();
            sharingBodyRenderer = new SharingBodyRenderer();
            emailVerificationBodyRenderer = new EmailVerificationBodyRenderer();
        }

        @Override
        public void renderTo(final ViewGroup parentView, final ShopListActivity activity) {
            super.renderTo(parentView, activity);
            swipeRefreshLayout = parentView.findViewById(
                    R.id.unit_sharing_container_refresh_layout);

            swipeRefreshLayout.setColorSchemeResources(R.color.color_primary);
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refresh();
                }
            });

            loginPanel = parentView.findViewById(
                    R.id.unit_sharing_container_login_panel);
            verificationPanel = parentView.findViewById(
                    R.id.unit_sharing_container_verification_panel);
            sharePanel = parentView.findViewById(
                    R.id.unit_sharing_container_share_panel);

            refresh();
        }

        public void refresh() {
            final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                refreshView(loginBodyRenderer, loginPanel);

            } else if (user.isEmailVerified()) {
                refreshView(sharingBodyRenderer, sharePanel);

            } else {
                swipeRefreshLayout.setRefreshing(true);

                final Task<Void> reloadTask = user.reload();
                reloadTask.addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        final FirebaseUser updatedUser
                                = FirebaseAuth.getInstance().getCurrentUser();
                        if ((updatedUser != null) && (!updatedUser.isEmailVerified())) {
                            refreshView(emailVerificationBodyRenderer, verificationPanel);
                        } else if (updatedUser != null) {
                            refreshView(sharingBodyRenderer, sharePanel);
                        } else {
                            refreshView(loginBodyRenderer, loginPanel);
                        }
                    }
                });
            }
        }

        private <T extends ViewRenderer<ShopListActivity, ViewGroup> & Refreshable> void refreshView(
                T renderer, ViewGroup panel) {
            cleanViews();

            panel.setVisibility(View.VISIBLE);
            if (panel.getChildCount() == 0) {
                renderer.renderTo(panel, getHostingActivity());
            }
            renderer.refresh();
        }

        private void cleanViews() {
            loginPanel.setVisibility(View.GONE);
            verificationPanel.setVisibility(View.GONE);
            sharePanel.setVisibility(View.GONE);

            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private class LoginBodyRenderer
                extends InflatingViewRenderer<ShopListActivity, ViewGroup>
                implements Refreshable {

            private ImageButton logInGoogleButton;
            private ImageButton logInWithEmailButton;

            public LoginBodyRenderer() {
                super(R.layout.unit_login);
            }

            @Override
            public void renderTo(ViewGroup parentView, ShopListActivity activity) {
                super.renderTo(parentView, activity);

                logInGoogleButton = parentView.findViewById(
                        R.id.unit_sharing_auth_google_button);
                logInWithEmailButton = parentView.findViewById(
                        R.id.unit_sharing_auth_email_button);

                logInGoogleButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final GoogleAuthUnit authUnit = new GoogleAuthUnit();
                        authUnit.setListeningUnit(SharingUnit.this);

                        getHostingActivity().startUnit(authUnit);
                    }
                });

                logInWithEmailButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final EmailAuthUnit emailUnit = new EmailAuthUnit();
                        emailUnit.setListeningUnit(SharingUnit.this);

                        getHostingActivity().startUnit(emailUnit);
                    }
                });

                updateLoginState(logInGoogleButton, logInWithEmailButton);
            }

            private void updateLoginState(
                    ImageButton logInGoogleButton,
                    ImageButton logInWithEmailButton) {
                final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                final boolean authorized = currentUser != null;

                logInGoogleButton.setEnabled(!authorized);
                logInWithEmailButton.setEnabled(!authorized);
            }

            @Override
            public void refresh() {
                updateLoginState(logInGoogleButton, logInWithEmailButton);
            }
        }

        private class SharingBodyRenderer
                extends InflatingViewRenderer<ShopListActivity, ViewGroup>
                implements Refreshable {

            private transient EditText friendsEmailField;
            private transient TextView unitSharingTipLabel;
            private transient Button shareButton;
            private transient Button cancelShareButton;

            private volatile DataSnapshot requestSnapshot;

            public SharingBodyRenderer() {
                super(R.layout.unit_sharing_share);
            }

            @Override
            public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
                super.renderTo(parentView, activity);

                friendsEmailField = activity.findViewById(
                        R.id.unit_sharing_friends_email);
                unitSharingTipLabel = activity.findViewById(
                        R.id.unit_sharing_tip_label);
                shareButton = activity.findViewById(
                        R.id.unit_sharing_share_button);
                cancelShareButton = activity.findViewById(
                        R.id.unit_sharing_cancel_share_button);

                friendsEmailField.setEnabled(false);
                shareButton.setEnabled(false);
                cancelShareButton.setEnabled(false);

                unitSharingTipLabel.setText(R.string.unit_sharing_loading_information);

                final FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    initShareButton(activity, user);

                    initCancelShareButton(activity);

                    refreshSharingStatus();
                }
            }

            @Override
            public void refresh() {
                refreshSharingStatus();
            }

            private void initCancelShareButton(final ShopListActivity activity) {
                cancelShareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        cancelRequest(activity);
                    }
                });
            }

            private void cancelRequest(final ShopListActivity activity) {
                cancelShareButton.setEnabled(false);
                unitSharingTipLabel.setText(R.string.unit_sharing_cancelling_share);
                friendsEmailField.setEnabled(false);

                DataSnapshot currentRequest = requestSnapshot;

                if (currentRequest == null) {
                    Log.w("SharingBodyRenderer", "onClick: sharing request is null, skipping");
                    refreshSharingStatus();
                    return;
                }

                Log.i("SharingBodyRenderer", "cancelRequest: cancelling request");

                final String userEmail;
                final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userEmail = currentUser.getEmail();
                } else {
                    userEmail = "";
                }

                deleteRequest(currentRequest, userEmail, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(
                            DatabaseError databaseError, DatabaseReference databaseReference) {
                        if (databaseError != null) {
                            Log.e("SharingBodyRenderer", "cancelRequest.removeValue.onComplete: " +
                                            "failed to remove shareRequest",
                                    databaseError.toException());
                            Toast.makeText(activity, R.string.unit_sharing_empty_email,
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }

                        refreshSharingStatus();
                    }
                });

                requestSnapshot = null;

                final DatabaseReference userNode = FirebaseHelper.getUserReference();

                final String initiator =
                        currentRequest.child("initiator").getValue(String.class);

                if (StringUtils.equalIgnoreCase(initiator, userEmail)) {
                    final Pair<String, Boolean> pair = getAnotherEmail(currentRequest, userEmail);
                    if (Boolean.TRUE.equals(pair.second)) {
                        userNode.child("listId").removeValue();
                        Log.i("SharingBodyRenderer", "cancelRequest: removed linked list (active)");

                    } else {
                        currentRequest.getRef().addListenerForSingleValueEvent(
                                new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        final Pair<String, Boolean> pair =
                                                getAnotherEmail(dataSnapshot, userEmail);
                                        if (Boolean.TRUE.equals(pair.second)) {
                                            userNode.child("listId").removeValue();
                                            Log.i("SharingBodyRenderer",
                                                    "cancelRequest.onDataChange:" +
                                                            " removed linked list (active)");

                                        }
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {
                                        Log.w("SharingBodyRenderer", "cancelRequest.onCancelled:" +
                                                " couldn't load request snapshot");
                                        userNode.child("listId").removeValue();
                                    }
                                });
                    }
                } else {
                    userNode.child("listId").removeValue();
                    Log.i("SharingBodyRenderer", "cancelRequest: removed linked list (passive)");
                }
            }

            private void initShareButton(final ShopListActivity activity, final FirebaseUser user) {
                shareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String friendsEmail = friendsEmailField.getText().toString();
                        if (friendsEmail.isEmpty() && (requestSnapshot == null)) {
                            Toast.makeText(activity, R.string.unit_sharing_empty_email,
                                    Toast.LENGTH_SHORT)
                                    .show();
                            return;
                        }

                        shareButton.setEnabled(false);
                        unitSharingTipLabel.setText(R.string.unit_sharing_sending_request);
                        friendsEmailField.setEnabled(false);

                        final DatabaseReference userNode = FirebaseHelper.getUserReference();

                        if ((user == null) || (user.getEmail() == null)) {
                            Log.e("SharingBodyRenderer",
                                    "shareButton.onClick: user or email is null");
                            return;
                        }

                        final String userEmail = user.getEmail().toLowerCase();
                        final String serializedUserEmail = serializeEmail(userEmail);

                        if (requestSnapshot != null) {
                            final String initiator =
                                    requestSnapshot.child("initiator").getValue(String.class);

                            if (userEmail.equalsIgnoreCase(initiator)) {
                                final Pair<String, Boolean> pair =
                                        getAnotherEmail(requestSnapshot, userEmail);
                                if (!pair.first.equalsIgnoreCase(friendsEmail)) {
                                    Log.i("SharingBodyRenderer", "shareButton.onClick: friends email changed" +
                                            ". oldValue=" + pair.first +
                                            ", newValue=" + friendsEmail);

                                    if (pair.second) {
                                        Log.i("SharingBodyRenderer", "shareButton.onClick:" +
                                                " another user already accepted" +
                                                ". acceptedUser=" + pair.first);
                                        userNode.child("listId").removeValue();
                                    }

                                    deleteRequest(requestSnapshot, userEmail, null);
                                    requestSnapshot = null;

                                } else {
                                    Log.i("SharingBodyRenderer", "shareButton.onClick: friends email is unchanged" +
                                            ". email=" + friendsEmail);
                                    refreshSharingStatus();
                                    return;
                                }
                            } else {
                                if (friendsEmail.equalsIgnoreCase(initiator)) {
                                    Log.i("SharingBodyRenderer", "shareButton.onClick: accepting incoming request" +
                                            ". email=" + friendsEmail);

                                    acceptShareRequest(requestSnapshot);

                                } else {
                                    Log.i("SharingBodyRenderer", "shareButton.onClick:" +
                                            " rejecting request, will create own request" +
                                            ". email=" + friendsEmail +
                                            ", initiator=" + initiator);
                                    requestSnapshot = null;
                                }
                            }
                        }

                        if (friendsEmail.isEmpty()) {
                            Log.i("SharingBodyRenderer", "shareButton.onClick: email is empty, nothing to do");

                            refreshSharingStatus();
                            return;
                        }

                        if (requestSnapshot == null) {
                            Log.i("SharingBodyRenderer", "shareButton.onClick: creating a new request" +
                                    ". email=" + friendsEmail);

                            userNode.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot userSnapshot) {
                                    final String listId;
                                    if (!userSnapshot.child("listId").exists()) {
                                        final DatabaseReference newList =
                                                FirebaseDatabase.getInstance().getReference("lists").push();
                                        listId = newList.getKey();
                                        userSnapshot.getRef().child("listId").setValue(listId);
                                    } else {
                                        listId = userSnapshot.child("listId").getValue(String.class);
                                    }

                                    final DatabaseReference shareReference =
                                            FirebaseDatabase.getInstance().getReference("share");
                                    final DatabaseReference newRequest =
                                            shareReference.child("requests").push();
                                    Map<String, Object> updateMap = new LinkedHashMap<>();
                                    updateMap.put(
                                            "requests/" + newRequest.getKey() + "/initiator",
                                            userEmail);
                                    final String serializedFriendEmail = serializeEmail(friendsEmail);
                                    updateMap.put(
                                            "requests/" + newRequest.getKey() + "/emails/" + serializedFriendEmail,
                                            false);
                                    updateMap.put(
                                            "requests/" + newRequest.getKey() + "/listId",
                                            listId);
                                    updateMap.put(
                                            "incoming/" + serializedFriendEmail + "/" + newRequest.getKey(),
                                            userEmail);
                                    updateMap.put(
                                            "incoming/" + serializedUserEmail + "/" + newRequest.getKey(),
                                            userEmail);

                                    shareReference.updateChildren(updateMap, new DatabaseReference.CompletionListener() {
                                        @Override
                                        public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                            if (databaseError != null) {
                                                Log.e("SharingBodyRenderer",
                                                        "share.updateChildren.onComplete: couldn't execute request",
                                                        databaseError.toException());
                                                Toast.makeText(activity, R.string.unit_sharing_failed_to_save,
                                                        Toast.LENGTH_LONG).show();
                                            }

                                            refreshSharingStatus();
                                        }
                                    });
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    Log.e("SharingBodyRenderer", "share.userNode.onCancelled:" +
                                            " couldn't execute request", databaseError.toException());
                                    Toast.makeText(activity, R.string.unit_sharing_failed_to_save,
                                            Toast.LENGTH_LONG).show();
                                    refreshSharingStatus();
                                }
                            });
                        }
                    }

                });
            }

            private void deleteRequest(
                    final DataSnapshot request,
                    final String userEmail,
                    final DatabaseReference.CompletionListener listener) {

                final String serializedUserEmail = serializeEmail(userEmail);

                request.getRef().removeValue(new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                        if (databaseError == null) {
                            final String requestId = request.getKey();

                            FirebaseDatabase.getInstance().getReference(
                                    "share/incoming/" + serializedUserEmail + "/" + requestId)
                                    .removeValue();

                            final String initiator = request.child("initiator").getValue(String.class);
                            if ((initiator != null) && (!initiator.equalsIgnoreCase(userEmail))) {
                                FirebaseDatabase.getInstance().getReference(
                                        "share/incoming/" + serializeEmail(initiator) + "/" + requestId)
                                        .removeValue();
                            }

                            for (final DataSnapshot emailSnapshot : request.child("emails").getChildren()) {
                                final String anotherEmail = emailSnapshot.getKey();
                                FirebaseDatabase.getInstance().getReference(
                                        "share/incoming/" + anotherEmail + "/" + requestId)
                                        .removeValue();
                            }
                        }

                        if (listener != null) {
                            listener.onComplete(databaseError, databaseReference);
                        }
                    }
                });
            }

            private void acceptShareRequest(final DataSnapshot requestSnapshot) {
                final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if ((currentUser == null) || (currentUser.getEmail() == null)) {
                    Log.e("SharingBodyRenderer",
                            "acceptShareRequest: user or email is null");
                    return;
                }

                final String userEmail = currentUser.getEmail().toLowerCase();

                final String sharedListId = requestSnapshot.child("listId").getValue(String.class);

                Log.i("SharingBodyRenderer", "acceptShareRequest: accepting incoming request" +
                        ". userEmail=" + userEmail +
                        ", sharedListId=" + sharedListId);

                requestSnapshot.getRef()
                        .child("emails")
                        .child(serializeEmail(userEmail))
                        .setValue(true, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                if (databaseError != null) {
                                    Log.e("SharingBodyRenderer", "share.requestSnapshot.setValue.onComplete:" +
                                            " couldn't execute request", databaseError.toException());
                                    Toast.makeText(getHostingActivity(),
                                            R.string.unit_sharing_failed_to_save,
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    replaceUserListWithShared(sharedListId, requestSnapshot, userEmail);
                                }

                                refreshSharingStatus();
                            }
                        });
            }

            private void replaceUserListWithShared(
                    final String sharedListId,
                    final DataSnapshot requestSnapshot,
                    final String userEmail) {

                final DatabaseReference userNode = FirebaseHelper.getUserReference();

                userNode.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        final String oldListId = dataSnapshot.child("listId").getValue(String.class);

                        Log.i("SharingBodyRenderer", "share.requestSnapshot.setValue.onComplete:" +
                                " replacing user list" +
                                ". newListId=" + sharedListId +
                                ", oldListId=" + oldListId);
                        if (oldListId != null) {
                            dataSnapshot.getRef().child("listId").setValue(sharedListId,
                                    new DatabaseReference.CompletionListener() {
                                        @Override
                                        public void onComplete(
                                                DatabaseError databaseError, DatabaseReference databaseReference) {
                                            if (databaseError == null) {
                                                FirebaseDatabase.getInstance().getReference("lists").child(oldListId)
                                                        .removeValue();
                                            } else {
                                                Log.w("SharingBodyRenderer", "replaceUserListWithShared.onComplete: " +
                                                        "couldn't change user listId ", databaseError.toException());
                                            }
                                        }
                                    });
                        } else {
                            dataSnapshot.getRef().child("listId").setValue(sharedListId);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e("SharingBodyRenderer", "replaceUserListWithShared.onCancelled:" +
                                        " couldn't replace user listId",
                                databaseError.toException());
                        Toast.makeText(
                                getHostingActivity(), R.string.unit_sharing_failed_to_share_list, Toast.LENGTH_LONG)
                                .show();
                        requestSnapshot.getRef()
                                .child("emails")
                                .child(serializeEmail(userEmail))
                                .setValue(false);
                    }
                });
            }

            private Pair<String, Boolean> getAnotherEmail(DataSnapshot requestSnapshot, String email) {
                final DataSnapshot emails = requestSnapshot.child("emails");

                for (DataSnapshot emailNode : emails.getChildren()) {
                    final String anotherEmail = deserializeEmail(emailNode.getKey());
                    if (anotherEmail.equals(email)) {
                        continue;
                    }

                    return new Pair<>(anotherEmail, emailNode.getValue(Boolean.class));
                }

                return new Pair<>(null, false);
            }

            private void refreshSharingStatus() {
                final FirebaseUser user = auth.getCurrentUser();
                if ((user == null) || (user.getEmail() == null)) {
                    Log.e("SharingBodyRenderer",
                            "refreshSharingStatus: user or email is null");
                    return;
                }

                final String userEmail = user.getEmail().toLowerCase();
                final String serializedUserEmail = serializeEmail(userEmail);

                final DatabaseReference incomingRequestsReference =
                        FirebaseDatabase.getInstance().getReference("share/incoming/" + serializedUserEmail);

                incomingRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        final Iterable<DataSnapshot> incomingRequests = dataSnapshot.getChildren();

                        final Map<String, String> requestsWithEmails = new LinkedHashMap<>();
                        final List<String> requestIds = new ArrayList<>();

                        for (DataSnapshot requestNode : incomingRequests) {
                            final String requester = requestNode.getValue(String.class);
                            final String requestId = requestNode.getKey();

                            if (requester.equalsIgnoreCase(userEmail)) {
                                requestIds.add(0, requestId);
                            } else {
                                requestIds.add(requestId);
                            }

                            requestsWithEmails.put(requestId, requester);
                        }

                        loadAndShowRequest(requestIds, requestsWithEmails, userEmail, null);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e("SharingBodyRenderer", "onCancelled: Couldn't load users information",
                                databaseError.toException());
                        Toast.makeText(
                                getHostingActivity(),
                                R.string.unit_sharing_failed_to_load_user_information, Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            private void loadAndShowRequest(
                    final List<String> incomingRequestIds,
                    final Map<String, String> requestsWithEmails,
                    final String userEmail,
                    final DataSnapshot activeRequest) {

                if (incomingRequestIds.isEmpty()) {
                    if (activeRequest != null) {
                        requestSnapshot = activeRequest;
                        showRequest(requestSnapshot, userEmail);

                    } else {
                        requestSnapshot = null;

                        shareButton.setEnabled(true);
                        cancelShareButton.setEnabled(false);
                        unitSharingTipLabel.setText("");
                        friendsEmailField.setText("");
                        friendsEmailField.setEnabled(true);
                    }

                    return;
                }

                final String requestId = incomingRequestIds.remove(0);
                final DatabaseReference incomingRequestsReference =
                        FirebaseDatabase.getInstance().getReference("share/requests/" + requestId);
                incomingRequestsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if ((dataSnapshot == null) || (!dataSnapshot.exists())) {
                            Log.i("SharingBodyRenderer", "onDataChange: request doesn't exist anymore, deleting" +
                                    ". requestId=" + requestId);
                            FirebaseDatabase.getInstance().getReference(
                                    "share/incoming/" + serializeEmail(userEmail) + "/" + requestId)
                                    .removeValue();

                            loadAndShowRequest(incomingRequestIds, requestsWithEmails, userEmail, activeRequest);
                            return;
                        }

                        if (activeRequest != null) {
                            Log.i("SharingBodyRenderer", "loadAndShowRequest.onDataChange:" +
                                    " found 2 matching requests, merging" +
                                    ". requestId=" + dataSnapshot.getKey());
                            deleteRequest(activeRequest, userEmail, null);
                            requestSnapshot = null;
                            acceptShareRequest(dataSnapshot);

                            return;
                        }

                        final String initiator = dataSnapshot.child("initiator").getValue(String.class);
                        if (initiator.equalsIgnoreCase(userEmail)) {
                            final Pair<String, Boolean> pair = getAnotherEmail(dataSnapshot, userEmail);
                            final String anotherEmail = pair.first;

                            if (requestsWithEmails.containsValue(anotherEmail)) {
                                final ArrayList<String> matchingRequestIds = new ArrayList<>();
                                for (Map.Entry<String, String> entry : requestsWithEmails.entrySet()) {
                                    if (entry.getValue().equalsIgnoreCase(anotherEmail)) {
                                        matchingRequestIds.add(entry.getKey());
                                    }
                                }

                                loadAndShowRequest(matchingRequestIds, requestsWithEmails, userEmail, dataSnapshot);

                            } else {
                                requestSnapshot = dataSnapshot;
                                showRequest(requestSnapshot, userEmail);
                            }

                            return;
                        }

                        requestSnapshot = dataSnapshot;
                        showRequest(requestSnapshot, userEmail);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e("SharingBodyRenderer", "loadAndShowRequest.onCancelled:" +
                                        " Couldn't load request information" +
                                        ". requestId=requestId",
                                databaseError.toException());
                        Toast.makeText(
                                getHostingActivity(), R.string.unit_sharing_failed_to_load_request, Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            private void showRequest(DataSnapshot requestSnapshot, String userEmail) {
                final String initiator = requestSnapshot.child("initiator").getValue(String.class);
                final Pair<String, Boolean> receiverEmail = getAnotherEmail(requestSnapshot, initiator);

                final boolean activeRequest = initiator.equalsIgnoreCase(userEmail);
                final String anotherUser = activeRequest ? receiverEmail.first : initiator;

                if (Boolean.TRUE.equals(receiverEmail.second)) {
                    shareButton.setEnabled(false);
                    cancelShareButton.setEnabled(true);
                    unitSharingTipLabel.setText(R.string.unit_sharing_sharing_active);
                    friendsEmailField.setText(anotherUser);
                    friendsEmailField.setEnabled(false);

                } else if (activeRequest) {
                    shareButton.setEnabled(true);
                    cancelShareButton.setEnabled(true);
                    unitSharingTipLabel.setText(R.string.unit_sharing_request_is_sent);
                    friendsEmailField.setText(anotherUser);
                    friendsEmailField.setEnabled(true);

                } else {
                    shareButton.setEnabled(true);
                    cancelShareButton.setEnabled(false);
                    unitSharingTipLabel.setText(R.string.unit_sharing_incoming_share_request);
                    friendsEmailField.setText(anotherUser);
                    friendsEmailField.setEnabled(true);
                }
            }
        }

        private class EmailVerificationBodyRenderer
                extends InflatingViewRenderer<ShopListActivity, ViewGroup>
                implements Refreshable {

            private transient Button verifyEmailButton;
            private transient CountDownTimer verificationTimer;
            private transient CharSequence verifyEmailButtonText;
            private transient Button refreshScreenButton;

            public EmailVerificationBodyRenderer() {
                super(R.layout.unit_sharing_verification);
            }

            @Override
            public void renderTo(ViewGroup parentView, final ShopListActivity activity) {
                super.renderTo(parentView, activity);

                verifyEmailButton = parentView.findViewById(
                        R.id.unit_sharing_verification_send_verification_button);
                refreshScreenButton = parentView.findViewById(
                        R.id.unit_sharing_verification_refresh_button);

                verifyEmailButtonText = verifyEmailButton.getText();

                verifyEmailButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendEmailConfirmation();
                    }
                });

                refreshScreenButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharingUnit.this.refreshUnit();
                    }
                });
            }

            @Override
            public void refresh() {
                stopVerificationTimer();
            }

            private void sendEmailConfirmation() {
                final boolean sent = SharingUnit.sendEmailConfirmation(getHostingActivity());

                if (sent) {
                    verifyEmailButton.setEnabled(false);

                    verificationTimer = new CountDownTimer(60000, 1000) {
                        int remaining = 60;

                        @Override
                        public void onTick(long millisUntilFinished) {
                            if (!verifyEmailButton.isEnabled()) {
                                remaining--;
                                verifyEmailButton.setText(verifyEmailButtonText + " (" + remaining + ")");
                            } else {

                                verifyEmailButton.setText(verifyEmailButtonText);
                                verificationTimer = null;
                                cancel();
                            }
                        }

                        @Override
                        public void onFinish() {
                            verifyEmailButton.setText(verifyEmailButtonText);
                            verificationTimer = null;
                            SharingUnit.this.refreshUnit();
                        }
                    };
                    verificationTimer.start();
                }
            }

            private void stopVerificationTimer() {
                if (verificationTimer != null) {
                    verificationTimer.cancel();
                    verificationTimer = null;
                }

                verifyEmailButton.setText(verifyEmailButtonText);
                verifyEmailButton.setEnabled(true);
            }
        }
    }

    private class LogoutListener implements UsernameToolbarRenderer.Listener {
        @Override
        public void onLogout() {
            refreshUnit();
        }
    }

    private interface Refreshable {
        void refresh();
    }
}