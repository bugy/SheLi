package net.buggy.shoplist.sharing;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import net.buggy.shoplist.data.Dao;
import net.buggy.shoplist.data.EntityListener;
import net.buggy.shoplist.model.Category;
import net.buggy.shoplist.model.Entity;
import net.buggy.shoplist.model.EntitySynchronizationRecord;
import net.buggy.shoplist.model.ModelHelper;
import net.buggy.shoplist.model.Product;
import net.buggy.shoplist.model.ShopItem;
import net.buggy.shoplist.utils.ExecutorServiceMonitoringDecorator;
import net.buggy.shoplist.utils.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static net.buggy.shoplist.sharing.EntitySynchronizer.getLastChangeDate;

public class FirebaseSynchronizer {

    public static final int RELOAD_EMAIL_DELAY = 30000;
    private final ExecutorServiceMonitoringDecorator BACKGROUND_SERVICE =
            createExecutor("Background-service");

    private final ExecutorServiceMonitoringDecorator FIREBASE_NOTIFICATIONS_SERVICE =
            createExecutor("Server-notification-service");

    private final Timer timer = new Timer("Synchronizer-timer", true);

    private FirebaseDatabase firebaseDatabase;

    private enum UpdateTarget {NONE, SERVER, CLIENT}

    enum State {UNSUBSCRIBED, INITIALIZING, SUBSCRIBED}

    private volatile FirebaseAuth.AuthStateListener authStateListener;
    private final Map<DatabaseReference, ChildEventListener> childListeners =
            new ConcurrentHashMap<>();
    private final AtomicReference<ValueListenerPair> userListListener = new AtomicReference<>();
    private final Map<Class, EntityListener> daoListeners = new ConcurrentHashMap<>();

    private final Dao dao;
    private final FirebaseAuth auth;
    private final AtomicReference<State> stateReference = new AtomicReference<>(State.UNSUBSCRIBED);
    private final AtomicBoolean started = new AtomicBoolean(false);

    // we cannot have a map here, because entity has no id, so no proper hash
    private final Set<EntityPair<Entity>> addedClientEntities = Sets.newConcurrentHashSet();
    private final Map<Entity, DataSnapshot> changingClientEntities = new ConcurrentHashMap<>();
    private final Set<String> removingClientEntities = Sets.newConcurrentHashSet();

    private volatile DatabaseReference activeUserList;

    private final EntitySynchronizer<Category> categoriesSynchronizer;
    private final EntitySynchronizer<Product> productSynchronizer;
    private final EntitySynchronizer<ShopItem> shopItemSynchronizer;
    private final FailureCallback failureCallback;

    private final Map<Class<? extends Entity>, BiMap<Long, EntitySynchronizationRecord>> synchronizationRecords =
            new ConcurrentHashMap<>();

    public FirebaseSynchronizer(Dao dao, FailureCallback failureCallback) {
        this(dao, FirebaseAuth.getInstance(), FirebaseDatabase.getInstance(), failureCallback);
    }

    // unit tests usage
    FirebaseSynchronizer(
            Dao dao,
            FirebaseAuth auth,
            FirebaseDatabase firebaseDatabase,
            FailureCallback failureCallback) {

        this.dao = dao;
        this.auth = auth;
        this.firebaseDatabase = firebaseDatabase;

        categoriesSynchronizer = new CategorySynchronizer(dao);
        productSynchronizer = new ProductSynchronizer(dao);
        shopItemSynchronizer = new ShopItemSynchronizer(dao);
        this.failureCallback = failureCallback;
    }

    public void start() {
        Log.i("FirebaseSynchronizer", "start: method called");

        started.set(true);

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (!started.get()) {
                    Log.i("FirebaseSynchronizer", "onAuthStateChanged: synchronizer is stopped");
                    return;
                }

                final FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                if (currentUser == null) {
                    Log.i("FirebaseSynchronizer", "onAuthStateChanged: unsubscribing");
                    fullUnsubscribe();
                } else if (!currentUser.isEmailVerified()) {
                    Log.i("FirebaseSynchronizer", "onAuthStateChanged:" +
                            " waiting for email verification");
                    waitEmailVerification(currentUser);
                } else {
                    Log.i("FirebaseSynchronizer", "onAuthStateChanged: subscribing");
                    subscribe();
                }
            }
        };
        auth.addAuthStateListener(authStateListener);

        subscribeOnClientChanges(categoriesSynchronizer);
        subscribeOnClientChanges(productSynchronizer);
        subscribeOnClientChanges(shopItemSynchronizer);
        subscribeOnSynchronizationRecords();
    }

    private void waitEmailVerification(final FirebaseUser user) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                user.reload().addOnCompleteListener(BACKGROUND_SERVICE, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!task.isSuccessful()) {
                            Log.e("FirebaseSynchronizer", "onComplete: failed to reload user", task.getException());
                            return;
                        }

                        final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser == null) {
                            Log.i("FirebaseSynchronizer", "waitEmailVerification.run:" +
                                    "current user is null, stopping await");
                            return;
                        }

                        if (!currentUser.getUid().equals(user.getUid())) {
                            Log.w("FirebaseSynchronizer", "waitEmailVerification.run:" +
                                    "current user was changed, stopping await");
                            return;
                        }

                        if (!currentUser.isEmailVerified()) {
                            Log.d("FirebaseSynchronizer", "waitEmailVerification.run:" +
                                    "email still not verified, waiting");
                            waitEmailVerification(user);
                            return;
                        }

                        if (stateReference.get() != State.UNSUBSCRIBED) {
                            Log.w("FirebaseSynchronizer", "waitEmailVerification.run:" +
                                    "subscription is already in progress");
                            return;
                        }

                        Log.i("FirebaseSynchronizer", "waitEmailVerification.run:" +
                                " email verified, subscribing");
                        subscribe();
                    }
                });
            }
        }, RELOAD_EMAIL_DELAY);
    }

    public void stop() {
        Log.i("FirebaseSynchronizer", "stop: method called");

        started.set(false);

        auth.removeAuthStateListener(authStateListener);

        if (stateReference.get() != State.UNSUBSCRIBED) {
            unsubscribe();
        }

        for (Map.Entry<Class, EntityListener> entry : daoListeners.entrySet()) {
            dao.removeEntityListener(entry.getKey(), entry.getValue());
        }
        daoListeners.clear();

        timer.cancel();
    }

    //tests only
    State getState() {
        return stateReference.get();
    }

    //tests only
    State waitState(State expectedState, long waitTime) {
        if (stateReference.get() == expectedState) {
            return expectedState;
        }

        final long maxTime = System.currentTimeMillis() + waitTime;

        while (System.currentTimeMillis() < maxTime) {
            if (stateReference.get() == expectedState) {
                return expectedState;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return stateReference.get();
    }


    //tests only
    void waitBackgroundTasks() {
        while (BACKGROUND_SERVICE.isExecuting() || FIREBASE_NOTIFICATIONS_SERVICE.isExecuting()) {
            try {
                Thread.sleep(0);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private synchronized void subscribe() {
        if (stateReference.get() == State.SUBSCRIBED) {
            Log.w("FirebaseSynchronizer", "subscribe: already subscribed");
            return;
        }

        stateReference.set(State.INITIALIZING);

        final FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Subscription when user is null shouldn't be activated");
        }

        final DatabaseReference users = firebaseDatabase.getReference("users");

        final DatabaseReference userNode = users.child(user.getUid());

        final DatabaseReference userListNode = userNode.child("listId");
        final ValueEventListener valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String listId = dataSnapshot.getValue(String.class);

                Log.i("FirebaseSynchronizer", "onDataChange: user listId changed" +
                        ". listId=" + listId);

                DatabaseReference currentUserList = activeUserList;
                if (currentUserList != null) {
                    Log.i("FirebaseSynchronizer", "onDataChange: unsubscribing from the old list" +
                            ". oldListId=" + currentUserList.getKey()
                            + ", newListId=" + listId);
                    unsubscribe();
                    stateReference.set(State.INITIALIZING);
                }

                final DatabaseReference lists = firebaseDatabase.getReference("lists");

                final DatabaseReference userList;

                if (Strings.isNullOrEmpty(listId)) {
                    userList = lists.push();

                    listId = userList.getKey();
                    userNode.child("listId").setValue(listId);

                } else {
                    userList = lists.child(listId);

                    activeUserList = userList;

                    startSynchronization(userList);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                if (databaseError.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Log.i("FirebaseSynchronizer", "userListNode.onCancelled: " +
                            "user changed. New user cannot the node");
                    return;
                }

                Log.e("FirebaseSynchronizer",
                        "onCancelled: database error." + databaseError.getMessage(),
                        databaseError.toException());

                failureCallback.synchronizationStartFailed(databaseError.toException());
            }
        };
        userListNode.addValueEventListener(valueEventListener);

        final ValueListenerPair oldPair = userListListener.get();
        if (oldPair != null) {
            Log.w("FirebaseSynchronizer", "subscribe: userListListener wasn't null" +
                    ". nodeId=" + oldPair.node.getKey());
            oldPair.node.removeEventListener(oldPair.valueListener);
        }
        userListListener.set(new ValueListenerPair(userListNode, valueEventListener));
    }

    private synchronized void unsubscribe() {
        if (stateReference.get() == State.UNSUBSCRIBED) {
            Log.w("FirebaseSynchronizer", "unsubscribe: already unsubscribed");
            return;
        }

        activeUserList = null;

        for (Map.Entry<DatabaseReference, ChildEventListener> entry : childListeners.entrySet()) {
            final DatabaseReference reference = entry.getKey();
            reference.removeEventListener(entry.getValue());
        }
    }

    private void fullUnsubscribe() {
        unsubscribe();

        final ValueListenerPair pair = userListListener.get();
        if (pair != null) {
            userListListener.set(null);
            pair.node.removeEventListener(pair.valueListener);
        }

        stateReference.set(State.UNSUBSCRIBED);
    }

    private void startSynchronization(final DatabaseReference userList) {
        BACKGROUND_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    FirebaseSynchronizer.this.synchronizationRecords.clear();
                    final List<EntitySynchronizationRecord<Entity>> synchronizationRecords =
                            dao.loadSynchronizationRecords(null, userList.getKey());
                    for (EntitySynchronizationRecord<Entity> record : synchronizationRecords) {
                        cacheSyncRecord(record);
                    }


                    final Future<Boolean> categoriesBulkFuture = bulkSynchronizeEntities(userList, categoriesSynchronizer);
                    waitFirebaseFuture(categoriesBulkFuture);

                    final Future<Boolean> productsBulkFuture = bulkSynchronizeEntities(userList, productSynchronizer);
                    waitFirebaseFuture(productsBulkFuture);

                    final Future<Boolean> shopItemsBulkFuture = bulkSynchronizeEntities(userList, shopItemSynchronizer);
                    waitFirebaseFuture(shopItemsBulkFuture);

                    subscribeOnServerChanges(userList, categoriesSynchronizer);
                    subscribeOnServerChanges(userList, productSynchronizer);
                    subscribeOnServerChanges(userList, shopItemSynchronizer);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception exception) {
                    Log.e("FirebaseSynchronizer", "startSynchronization: bulk synchronization failed", exception);
                    stateReference.set(State.UNSUBSCRIBED);

                    failureCallback.synchronizationStartFailed(exception);
                    throw new RuntimeException(exception.getMessage(), exception);
                }

                stateReference.set(State.SUBSCRIBED);
            }
        });
    }

    private void subscribeOnSynchronizationRecords() {
        addDaoListener(EntitySynchronizationRecord.class, new EntityListener<EntitySynchronizationRecord>() {
            @Override
            public void entityAdded(EntitySynchronizationRecord synchronizationRecord) {
                cacheSyncRecord(synchronizationRecord);
            }

            @Override
            public void entityChanged(EntitySynchronizationRecord changedRecord) {
                cacheSyncRecord(changedRecord);
                if (!changedRecord.isDeleted()) {
                    return;
                }

                final DatabaseReference userList = FirebaseSynchronizer.this.activeUserList;
                if ((userList == null)
                        || (!changedRecord.getListId().equals(userList.getKey()))) {
                    return;
                }

                final String externalId = changedRecord.getExternalId();
                final Class entityClass = changedRecord.getEntityClass();

                final String type = StringUtils.lowerFirstLetter(entityClass.getSimpleName());

                if (removingClientEntities.contains(externalId)) {
                    Log.i("FirebaseSynchronizer", "changedSynchronizationRecord: server " + type + " deleted" +
                            ". Client " + type + " deletion is in progress" +
                            ". externalId=" + externalId);
                    removingClientEntities.remove(externalId);
                    return;
                }

                if (stateReference.get() != State.SUBSCRIBED) {
                    Log.i("FirebaseSynchronizer", "changedSynchronizationRecord: not yet subscribed" +
                            ", skipping " + type + " removal record" +
                            ". externalId=" + externalId);
                    return;
                }

                Log.i("FirebaseSynchronizer", "changedSynchronizationRecord: removing server " +
                        type +
                        ". externalId=" + externalId);

                final String listName;
                if (Category.class.isAssignableFrom(entityClass)) {
                    listName = categoriesSynchronizer.getFirebaseListName();
                } else if (Product.class.isAssignableFrom(entityClass)) {
                    listName = productSynchronizer.getFirebaseListName();
                } else if (ShopItem.class.isAssignableFrom(entityClass)) {
                    listName = shopItemSynchronizer.getFirebaseListName();
                } else {
                    throw new IllegalStateException("Unknown entity class " + entityClass);
                }

                removeServerSnapshot(userList.child(listName), externalId);
            }

            @Override
            public void entityRemoved(EntitySynchronizationRecord removedEntity) {
                final String type = removedEntity.getEntityClass().getSimpleName();
                Log.d("FirebaseSynchronizer", "removedSynchronizationRecord: removed record for " + type +
                        ". externalId=" + removedEntity.getExternalId());

                final BiMap<Long, EntitySynchronizationRecord<? extends Entity>> map =
                        getRecordsByClass(removedEntity.getEntityClass());

                map.remove(removedEntity.getInternalId());
            }
        });
    }

    private <T extends Entity> void cacheSyncRecord(
            EntitySynchronizationRecord<T> synchronizationRecord) {
        if (synchronizationRecord.getId() == null) {
            throw new IllegalStateException("Record has no id");
        }

        final Class<T> entityClass = synchronizationRecord.getEntityClass();

        BiMap<Long, EntitySynchronizationRecord> map = synchronizationRecords.get(entityClass);
        if (map == null) {
            map = HashBiMap.create();
            synchronizationRecords.put(entityClass, map);
        }

        map.remove(synchronizationRecord.getInternalId());
        map.put(synchronizationRecord.getInternalId(), synchronizationRecord);
    }

    private <T extends Entity> void subscribeOnClientChanges(
            final EntitySynchronizer<T> entitySynchronizer) {
        final String type = StringUtils.lowerFirstLetter(entitySynchronizer.getEntityClass().getSimpleName());

        final EntityListener<T> listener = new EntityListener<T>() {
            @Override
            public void entityAdded(final T newEntity) {
                EntityPair<Entity> foundPair = getAddInProgressPair(newEntity);
                if (foundPair != null) {
                    Log.i("FirebaseSynchronizer", "entityAdded: " + type + " was just created from server" +
                            ", storing sync record" +
                            ". externalId=" + foundPair.serverEntity.getKey());
                    finishAddInProgress(newEntity, foundPair, entitySynchronizer);
                    return;
                }

                final DatabaseReference userList = FirebaseSynchronizer.this.activeUserList;
                if (userList == null) {
                    return;
                }

                final String naturalId = entitySynchronizer.getNaturalId(newEntity);

                final EntitySynchronizationRecord record = getSyncRecord(
                        entitySynchronizer.getEntityClass(), newEntity.getId());
                if (record != null) {
                    Log.i("FirebaseSynchronizer", "entityAdded: " + type + " has synchronization record" +
                            ", won't save to server" +
                            ". externalId=" +
                            ", naturalId=" + naturalId);
                    return;
                }

                Log.i("FirebaseSynchronizer", "entityAdded: creating server " + type +
                        ". naturalId=" + naturalId +
                        ", internalId=" + newEntity.getId());

                createOrLinkServerEntity(newEntity, userList);
            }

            @Override
            public void entityChanged(final T changedEntity) {
                final DataSnapshot serverEntity = changingClientEntities.get(changedEntity);
                if (serverEntity != null) {
                    Log.i("FirebaseSynchronizer", "entityChanged: " + type + " was changed by server" +
                            ", updating change date" +
                            ". externalId=" + serverEntity.getKey());
                    finishChangeInProgress(changedEntity, serverEntity);
                    return;
                }

                final Class<T> entityClass = entitySynchronizer.getEntityClass();

                dao.updateSynchronizationRecords(
                        changedEntity.getId(), entityClass, new Date(), false);

                final DatabaseReference userList = FirebaseSynchronizer.this.activeUserList;
                if (userList == null) {
                    return;
                }

                final EntitySynchronizationRecord record = getSyncRecord(
                        entityClass, changedEntity.getId());

                final String naturalId = entitySynchronizer.getNaturalId(changedEntity);

                if (record == null) {
                    Log.i("FirebaseSynchronizer", "entityChanged:" +
                            " " + type + " has not sync record" +
                            ", creating server entity" +
                            ". naturalId=" + naturalId);
                    createOrLinkServerEntity(changedEntity, userList);
                    return;
                }

                Log.i("FirebaseSynchronizer", "entityChanged: updating server " + type +
                        ". externalId=" + record.getExternalId() +
                        ", naturalId=" + naturalId);

                BACKGROUND_SERVICE.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final DatabaseReference entitiesListNode = userList.child(
                                    entitySynchronizer.getFirebaseListName());

                            entitySynchronizer.updateServerEntity(
                                    changedEntity, entitiesListNode.child(record.getExternalId()));
                        } catch (Exception e) {
                            Log.e("FirebaseSynchronizer", "entityChanged:" +
                                    " failed to update server " + type +
                                    ". externalId=" + record.getExternalId() +
                                    ", naturalId=" + naturalId, e);
                            failureCallback.updateServerEntityFailed(changedEntity, e);
                            throw e;
                        }
                    }
                });
            }

            @Override
            public void entityRemoved(T removedEntity) {
                dao.updateSynchronizationRecords(
                        removedEntity.getId(),
                        entitySynchronizer.getEntityClass(),
                        new Date(),
                        true);
            }


            private void finishAddInProgress(
                    T persistedEntity,
                    EntityPair<Entity> entityPair,
                    EntitySynchronizer<T> synchronizer) {

                final DataSnapshot serverEntity = entityPair.serverEntity;
                final Date changeDate = EntitySynchronizer.getLastChangeDate(serverEntity);
                addSyncRecord(persistedEntity.getId(), serverEntity, synchronizer, changeDate);

                addedClientEntities.remove(entityPair);
            }

            @Nullable
            private EntityPair<Entity> getAddInProgressPair(T newEntity) {
                EntityPair<Entity> foundPair = null;
                for (EntityPair<Entity> addedEntity : addedClientEntities) {
                    if (addedEntity.clientEntity == newEntity) {
                        foundPair = addedEntity;
                        break;
                    }
                }
                return foundPair;
            }

            private void finishChangeInProgress(T changedEntity, DataSnapshot serverEntity) {
                dao.updateSynchronizationRecords(changedEntity.getId(),
                        entitySynchronizer.getEntityClass(),
                        EntitySynchronizer.getLastChangeDate(serverEntity),
                        false);

                changingClientEntities.remove(changedEntity);
            }

            private void createOrLinkServerEntity(final T newEntity, DatabaseReference userList) {
                final DatabaseReference entitiesListNode = userList.child(
                        entitySynchronizer.getFirebaseListName());
                final String naturalId = entitySynchronizer.getNaturalId(newEntity);

                final CountDownLatch creationFinishedLatch = new CountDownLatch(1);

                BACKGROUND_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            creationFinishedLatch.await();
                        } catch (InterruptedException e) {
                            Log.i("FirebaseSynchronizer", "createOrLinkServerEntity.run: interrupted");
                        }
                    }
                });

                final ValueEventListener eventListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        try {

                            if (dataSnapshot.getChildrenCount() == 0) {
                                Log.i("FirebaseSynchronizer", "createOrLinkServerEntity.onDataChange:" +
                                        " server " + type + " doesn't exist, creating." +
                                        ". naturalId=" + naturalId +
                                        ", internalId=" + newEntity.getId());
                                try {
                                    entitySynchronizer.createServerEntity(newEntity, entitiesListNode);
                                    return;
                                } catch (Exception e) {
                                    Log.e("FirebaseSynchronizer", "createOrLinkServerEntity.onDataChange:" +
                                            " Couldn't create server " + type +
                                            ". entity=" + newEntity, e);
                                    failureCallback.updateServerEntityFailed(newEntity, e);
                                    throw e;
                                }
                            }

                            if (dataSnapshot.getChildrenCount() > 1) {
                                Log.w("FirebaseSynchronizer", "createOrLinkServerEntity.onDataChange:" +
                                        " multiple server " + type + " matched by naturalId" +
                                        ". naturalId=" + naturalId +
                                        ", internalId=" + newEntity.getId() +
                                        ", childrenCount=" + dataSnapshot.getChildrenCount());
                            }

                            final DataSnapshot serverEntity =
                                    dataSnapshot.getChildren().iterator().next();
                            Log.i("FirebaseSynchronizer", "createOrLinkServerEntity.onDataChange:" +
                                    " server " + type + " matched by naturalId" +
                                    ". naturalId=" + naturalId +
                                    ", internalId=" + newEntity.getId() +
                                    ", externalId=" + serverEntity.getKey());

                            addSyncRecord(
                                    newEntity.getId(), serverEntity, entitySynchronizer, new Date());
                            entitySynchronizer.updateServerEntity(
                                    newEntity, serverEntity.getRef());
                        } finally {
                            creationFinishedLatch.countDown();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        try {
                            Log.e("FirebaseSynchronizer", "createOrLinkServerEntity.onCancelled:" +
                                            " couldn't get server " + type + " by naturalId" +
                                            ". naturalId=" + naturalId +
                                            ", internalId=" + newEntity.getId(),
                                    databaseError.toException());
                            failureCallback.updateServerEntityFailed(
                                    newEntity, databaseError.toException());
                        } finally {
                            creationFinishedLatch.countDown();
                        }
                    }
                };

                try {
                    entitiesListNode
                            .orderByChild("naturalId")
                            .equalTo(naturalId)
                            .addListenerForSingleValueEvent(wrapInBackgroundThread(eventListener));
                } catch (RuntimeException e) {
                    creationFinishedLatch.countDown();
                    throw e;
                }
            }
        };

        addDaoListener(entitySynchronizer.getEntityClass(), listener);
    }

    private <T extends Entity> EntitySynchronizationRecord getSyncRecord(Class<T> entityClass, Long internalId) {
        final BiMap<Long, EntitySynchronizationRecord> classRecords = synchronizationRecords.get(entityClass);
        if (classRecords == null) {
            return null;
        }

        return classRecords.get(internalId);
    }

    private <T extends Entity> EntitySynchronizationRecord getSyncRecord(Class<T> entityClass, String externalId) {
        final BiMap<Long, EntitySynchronizationRecord<T>> classRecords = getRecordsByClass(entityClass);

        for (EntitySynchronizationRecord record : classRecords.values()) {
            if (record.getExternalId().equals(externalId)) {
                return record;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> BiMap<Long, EntitySynchronizationRecord<T>> getRecordsByClass(Class<T> entityClass) {
        final BiMap<Long, EntitySynchronizationRecord<T>> records = (BiMap<Long, EntitySynchronizationRecord<T>>)
                (BiMap) synchronizationRecords.get(entityClass);

        return (records != null) ? records : HashBiMap.<Long, EntitySynchronizationRecord<T>>create();
    }

    private <T extends Entity> void addDaoListener(Class<T> entityClass, EntityListener<T> listener) {
        final EntityListener oldListener = daoListeners.put(entityClass, listener);
        if (oldListener != null) {
            Log.w("FirebaseSynchronizer", "addDaoListener: listener already exists. Unsubscribing old listener");
            dao.removeEntityListener(entityClass, listener);
        }

        dao.addEntityListener(entityClass, listener);
    }

    @SuppressWarnings("DuplicateThrows")
    private void waitFirebaseFuture(Future<?> future) throws Exception, InterruptedException {
        try {
            future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private <T extends Entity> void subscribeOnServerChanges(
            final DatabaseReference userList, final EntitySynchronizer<T> entitySynchronizer) {
        final String type = StringUtils.lowerFirstLetter(entitySynchronizer.getEntityClass().getSimpleName());

        final DatabaseReference serverListNode = userList.child(entitySynchronizer.getFirebaseListName());
        addFirebaseChildListener(serverListNode, wrapInBackgroundThread(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot serverEntity, String s) {
                final String naturalId = entitySynchronizer.getNaturalId(serverEntity);
                final String externalId = serverEntity.getKey();

                final String listId = userList.getKey();

                T clientEntity = entitySynchronizer.findOnClientByExternalId(externalId, listId);
                final EntitySynchronizationRecord record = getSyncRecord(
                        entitySynchronizer.getEntityClass(), externalId);

                if (record == null) {
                    Log.i("FirebaseSynchronizer", "onChildAdded: adding new client " + type +
                            ". id=" + externalId +
                            ", naturalId=" + naturalId);

                    createOrLinkClientEntity(serverEntity, entitySynchronizer);

                } else {
                    if (record.isDeleted()) {
                        Log.i("FirebaseSynchronizer", "onChildAdded: " +
                                "the " + type + " is removed on client. Removing from the server" +
                                ". id=" + externalId +
                                ", naturalId=" + naturalId);
                        removeServerSnapshot(serverEntity.getRef(), serverEntity.getKey());

                    } else {
                        Log.i("FirebaseSynchronizer", "onChildAdded: client " + type + " already exists" +
                                ". Synchronizing object state" +
                                ". id=" + externalId +
                                ", naturalId=" + naturalId);

                        mergeEntity(serverEntity, clientEntity, record, entitySynchronizer);
                    }
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                final String externalId = dataSnapshot.getKey();
                final String naturalId = entitySynchronizer.getNaturalId(dataSnapshot);

                final EntitySynchronizationRecord record = getSyncRecord(
                        entitySynchronizer.getEntityClass(), externalId);

                if (record != null) {
                    final T foundEntity = entitySynchronizer.findOnClient(record.getInternalId());

                    final UpdateTarget updateTarget = calcUpdateTarget(
                            EntitySynchronizer.getLastChangeDate(dataSnapshot), record.getLastChangeDate());

                    if (foundEntity == null) {
                        Log.w("FirebaseSynchronizer", "onChildChanged: client " + type + " not found" +
                                ". externalId=" + externalId +
                                ", naturalId=" + naturalId);

                    } else if (updateTarget == UpdateTarget.CLIENT) {
                        Log.i("FirebaseSynchronizer", "onChildChanged: " +
                                "updating client " + type +
                                ". externalId=" + externalId +
                                ", naturalId=" + naturalId);

                        updateClientEntity(dataSnapshot, foundEntity, entitySynchronizer);

                    } else {
                        Log.i("FirebaseSynchronizer", "onChildChanged: server " + type + " change date" +
                                " is same or older, skipping" +
                                ". externalId=" + externalId +
                                ", naturalId=" + naturalId +
                                ", updateTarget=" + updateTarget);
                    }

                } else {
                    Log.i("FirebaseSynchronizer", "onChildChanged: " +
                            "client " + type + " is missing, creating " +
                            ". id=" + externalId +
                            ", naturalId=" + naturalId);

                    createOrLinkClientEntity(dataSnapshot, entitySynchronizer);
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                final String externalId = dataSnapshot.getKey();
                final String naturalId = entitySynchronizer.getNaturalId(dataSnapshot);

                final EntitySynchronizationRecord record = getSyncRecord(
                        entitySynchronizer.getEntityClass(), externalId);
                if (record != null) {
                    final T foundEntity = entitySynchronizer.findOnClient(record.getInternalId());

                    if (foundEntity == null) {
                        if (record.isDeleted()) {
                            Log.i("FirebaseSynchronizer", "onChildRemoved: " +
                                    "received server confirmation of client removed " + type +
                                    ". id=" + externalId +
                                    ", naturalId=" + naturalId);
                        } else {
                            Log.w("FirebaseSynchronizer", "onChildRemoved: client " + type + " not found" +
                                    ". id=" + externalId +
                                    ", naturalId=" + naturalId);
                        }

                    } else {
                        Log.i("FirebaseSynchronizer", "onChildRemoved: " +
                                "removing client " + type +
                                ". id=" + externalId +
                                ", naturalId=" + naturalId);
                        removingClientEntities.add(externalId);
                        entitySynchronizer.removeFromClient(foundEntity);
                    }

                    dao.removeSynchronizationRecord(record);

                } else {
                    Log.w("FirebaseSynchronizer", "onChildRemoved: client " + type + " is missing" +
                            ". externalId=" + externalId +
                            ", naturalId=" + naturalId);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                Log.w("FirebaseSynchronizer",
                        "onChildMoved: ignoring. key=" + dataSnapshot.getKey());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                if (databaseError.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Log.i("FirebaseSynchronizer", "onCancelled: ");
                    unsubscribe();
                } else {
                    Log.e("FirebaseSynchronizer",
                            "onCancelled: request cancelled",
                            databaseError.toException());
                }
            }
        }));
    }

    private <T extends Entity> void mergeEntity(
            DataSnapshot serverEntity,
            T clientEntity,
            EntitySynchronizationRecord record,
            EntitySynchronizer<T> entitySynchronizer) {

        final String type = StringUtils.lowerFirstLetter(
                entitySynchronizer.getEntityClass().getSimpleName());
        final String naturalId = entitySynchronizer.getNaturalId(clientEntity);
        final String externalId = serverEntity.getKey();

        final Date serverChangeDate = getLastChangeDate(serverEntity);
        final Date clientChangeDate = record.getLastChangeDate();

        final UpdateTarget updateTarget = calcUpdateTarget(
                serverChangeDate, clientChangeDate);

        if (updateTarget == UpdateTarget.SERVER) {
            Log.i("FirebaseSynchronizer", "mergeEntity: updating " + type + " on server" +
                    ". naturalId=" + naturalId
                    + ", externalId=" + externalId
                    + ", clientChangeDate=" + clientChangeDate
                    + ", serverChangeDate=" + serverChangeDate);

            entitySynchronizer.updateServerEntity(clientEntity, serverEntity.getRef());
        } else if (updateTarget == UpdateTarget.CLIENT) {
            Log.i("FirebaseSynchronizer", "mergeEntity: updating " + type + " on client" +
                    ". naturalId=" + naturalId
                    + ",externalId=" + externalId
                    + ", clientChangeDate=" + clientChangeDate
                    + ", serverChangeDate=" + serverChangeDate);

            updateClientEntity(serverEntity, clientEntity, entitySynchronizer);

        } else {
            Log.i("FirebaseSynchronizer", "mergeEntity: " + type + " is in sync" +
                    ". naturalId=" + naturalId
                    + ",externalId=" + externalId
                    + ", clientChangeDate=" + clientChangeDate
                    + ", serverChangeDate=" + serverChangeDate);
        }
    }

    private void addFirebaseChildListener(DatabaseReference databaseReference, ChildEventListener childListener) {
        databaseReference.addChildEventListener(childListener);
        childListeners.put(databaseReference, childListener);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private <T extends Entity> Future<Boolean> bulkSynchronizeEntities(
            final DatabaseReference userList, final EntitySynchronizer<T> entitySynchronizer) {

        final SettableFuture<Boolean> resultFuture = SettableFuture.create();

        final String type = StringUtils.lowerFirstLetter(entitySynchronizer.getEntityClass().getSimpleName());

        final DatabaseReference entitiesNode = userList.child(entitySynchronizer.getFirebaseListName());
        final List<T> clientEntities = entitySynchronizer.loadEntities();
        final Map<Long, EntitySynchronizationRecord<T>> syncRecordsMap =
                getRecordsByClass(entitySynchronizer.getEntityClass());

        entitiesNode.addListenerForSingleValueEvent(wrapInBackgroundThread(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    Map<String, DataSnapshot> serverEntityIdsMap = mapToExternalIds(dataSnapshot.getChildren());
                    final Map<String, DataSnapshot> serverEntityNaturalIdsMap = mapToNaturalIds(
                            dataSnapshot.getChildren(), entitySynchronizer);

                    final Map<Long, T> entityIdsMap = ModelHelper.mapIds(clientEntities);

                    final BiMap<T, String> clientExternalIdsMap = HashBiMap.create();
                    final Set<EntitySynchronizationRecord<T>> removalRecords = new LinkedHashSet<>();
                    for (EntitySynchronizationRecord<T> record : syncRecordsMap.values()) {
                        final T entity = entityIdsMap.get(record.getInternalId());

                        if (record.isDeleted()) {
                            removalRecords.add(record);
                            continue;
                        } else if (entity == null) {
                            Log.w("FirebaseSynchronizer", "bulkSynchronizeEntities: " + type + " not found" +
                                    " for synchronization record, removing the record" +
                                    ". internalId=" + record.getInternalId() +
                                    ", externalId=" + record.getExternalId());
                            dao.removeSynchronizationRecord(record);
                            continue;
                        }

                        clientExternalIdsMap.put(entity, record.getExternalId());
                    }

                    final Set<T> newClientEntities = new LinkedHashSet<>(clientEntities);
                    newClientEntities.removeAll(clientExternalIdsMap.keySet());

                    Set<String> clientEntitiesToRemove = Sets.newLinkedHashSet(Sets.difference(
                            clientExternalIdsMap.values(), serverEntityIdsMap.keySet()));
                    for (String externalId : clientEntitiesToRemove) {
                        final T entity = clientExternalIdsMap.inverse().get(externalId);
                        Log.i("FirebaseSynchronizer", "bulkSynchronizeEntities: removing client " + type + ". " +
                                "externalId=" + externalId);

                        removingClientEntities.add(externalId);
                        entitySynchronizer.removeFromClient(entity);
                        clientExternalIdsMap.inverse().remove(externalId);

                        final EntitySynchronizationRecord<T> syncRecord = syncRecordsMap.get(entity.getId());
                        if (syncRecord != null) {
                            dao.removeSynchronizationRecord(syncRecord);
                        }
                    }

                    for (final EntitySynchronizationRecord<T> removalRecord : removalRecords) {
                        final String externalId = removalRecord.getExternalId();

                        if (serverEntityIdsMap.containsKey(externalId)) {
                            Log.i("FirebaseSynchronizer", "bulkSynchronizeEntities: removing server " + type +
                                    ". externalId=" + externalId);
                            final Task<Void> removalTask = removeServerSnapshot(dataSnapshot.getRef(), externalId);

                            removalTask.addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    if (task.isSuccessful()) {
                                        Log.i("FirebaseSynchronizer", "bulkSynchronizeEntities.onComplete:" +
                                                " server " + type + " removed, deleting sync record" +
                                                ". externalId=" + externalId);
                                        dao.removeSynchronizationRecord(removalRecord);
                                    } else {
                                        Log.e("FirebaseSynchronizer", "bulkSynchronizeEntities.onComplete:" +
                                                        " failed to remove server " + type +
                                                        ". externalId=" + externalId,
                                                task.getException());
                                        failureCallback.updateServerEntityFailed(null, task.getException());
                                    }
                                }
                            });

                            serverEntityIdsMap.remove(externalId);
                        } else {
                            Log.w("FirebaseSynchronizer", "bulkSynchronizeEntities: server " + type + " is missing" +
                                    " for a removal record, deleting the record" +
                                    ". externalId=" + externalId);
                            dao.removeSynchronizationRecord(removalRecord);
                        }
                    }

                    Set<String> missingClientEntities = Sets.difference(
                            serverEntityIdsMap.keySet(), clientExternalIdsMap.values());
                    for (String externalId : missingClientEntities) {
                        final DataSnapshot serverEntity = serverEntityIdsMap.get(externalId);
                        final String naturalId = entitySynchronizer.getNaturalId(serverEntity);

                        Log.i("FirebaseSynchronizer", "bulkSynchronizeEntities: adding new client " + type +
                                ". externalId=" + externalId + ", naturalId=" + naturalId);
                        createOrLinkClientEntity(serverEntity, entitySynchronizer);
                    }

                    for (T clientEntity : newClientEntities) {
                        final String naturalId = entitySynchronizer.getNaturalId(clientEntity);

                        final DataSnapshot serverEntity = serverEntityNaturalIdsMap.get(naturalId);
                        if (serverEntity == null) {
                            Log.i("FirebaseSynchronizer", "bulkSynchronizeEntities: adding new " + type + " to server" +
                                    ", naturalId=" + naturalId);
                            entitySynchronizer.createServerEntity(clientEntity, entitiesNode);

                        } else {
                            Log.i("FirebaseSynchronizer", "bulkSynchronizeEntities: " +
                                    "client " + type + " matched server " + type + " by naturalId" +
                                    ", should have been linked by previous step" +
                                    ". naturalId=" + naturalId +
                                    ". externalId=" + serverEntity.getKey());
                        }
                    }

                    resultFuture.set(true);

                } catch (Exception e) {
                    resultFuture.setException(e);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseSynchronizer",
                        "bulkSynchronizeEntities.onCancelled: request cancelled. type=" + type,
                        databaseError.toException());
                resultFuture.setException(databaseError.toException());
            }
        }));

        return resultFuture;
    }

    private ValueEventListener wrapInBackgroundThread(final ValueEventListener valueEventListener) {
        return new ValueEventListener() {

            @Override
            public void onDataChange(final DataSnapshot dataSnapshot) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            valueEventListener.onDataChange(dataSnapshot);
                        } catch (Exception e) {
                            failureCallback.serverNotificationFailed(e);
                        }
                    }
                });
            }

            @Override
            public void onCancelled(final DatabaseError databaseError) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        valueEventListener.onCancelled(databaseError);
                    }
                });
            }
        };
    }

    private ChildEventListener wrapInBackgroundThread(final ChildEventListener childEventListener) {
        return new ChildEventListener() {
            @Override
            public void onChildAdded(final DataSnapshot dataSnapshot, final String s) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            childEventListener.onChildAdded(dataSnapshot, s);
                        } catch (Exception e) {
                            failureCallback.serverNotificationFailed(e);
                        }
                    }
                });
            }

            @Override
            public void onChildChanged(final DataSnapshot dataSnapshot, final String s) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            childEventListener.onChildChanged(dataSnapshot, s);
                        } catch (Exception e) {
                            failureCallback.serverNotificationFailed(e);
                        }
                    }
                });
            }

            @Override
            public void onChildRemoved(final DataSnapshot dataSnapshot) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            childEventListener.onChildRemoved(dataSnapshot);
                        } catch (Exception e) {
                            failureCallback.serverNotificationFailed(e);
                        }
                    }
                });
            }

            @Override
            public void onChildMoved(final DataSnapshot dataSnapshot, final String s) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            childEventListener.onChildMoved(dataSnapshot, s);
                        } catch (Exception e) {
                            failureCallback.serverNotificationFailed(e);
                        }
                    }
                });
            }

            @Override
            public void onCancelled(final DatabaseError databaseError) {
                FIREBASE_NOTIFICATIONS_SERVICE.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            childEventListener.onCancelled(databaseError);
                        } catch (Exception e) {
                            failureCallback.serverNotificationFailed(e);
                        }
                    }
                });
            }
        };
    }


    private Task<Void> removeServerSnapshot(DatabaseReference parentReference, String externalId) {
        return parentReference.child(externalId).getRef().removeValue();
    }

    @NonNull
    private static UpdateTarget calcUpdateTarget(Date serverChangeDate, Date clientChangeDate) {
        if (clientChangeDate == null) {
            if (serverChangeDate == null) {
                return UpdateTarget.SERVER;
            } else {
                return UpdateTarget.CLIENT;
            }
        } else if ((serverChangeDate == null)
                || clientChangeDate.after(serverChangeDate)) {
            return UpdateTarget.SERVER;

        } else if (clientChangeDate.before(serverChangeDate)) {
            return UpdateTarget.CLIENT;
        } else {
            return UpdateTarget.NONE;
        }
    }

    @NonNull
    private static Map<String, DataSnapshot> mapToExternalIds(Iterable<DataSnapshot> snapshots) {
        Map<String, DataSnapshot> result = new LinkedHashMap<>();
        for (DataSnapshot snapshot : snapshots) {
            result.put(snapshot.getKey(), snapshot);
        }
        return result;
    }

    @NonNull
    private static Map<String, DataSnapshot> mapToNaturalIds(Iterable<DataSnapshot> snapshots,
                                                             EntitySynchronizer entitySynchronizer) {
        Map<String, DataSnapshot> result = new LinkedHashMap<>();
        for (DataSnapshot snapshot : snapshots) {
            final String id = entitySynchronizer.getNaturalId(snapshot);
            if (id != null) {
                result.put(id, snapshot);
            }
        }
        return result;
    }

    private <T extends Entity> void createOrLinkClientEntity(
            DataSnapshot serverEntity, EntitySynchronizer<T> entitySynchronizer) {
        final T clientEntity = entitySynchronizer.findOnClientByNaturalId(serverEntity);
        final String externalId = serverEntity.getKey();

        if (clientEntity == null) {
            final T newClientEntity = entitySynchronizer.createClientEntityInstance(serverEntity);

            addedClientEntities.add(new EntityPair<Entity>(newClientEntity, serverEntity));

            entitySynchronizer.saveNewClientEntity(newClientEntity);

        } else {
            final EntitySynchronizationRecord record =
                    getSyncRecord(entitySynchronizer.getEntityClass(), clientEntity.getId());

            if (record != null) {
                final String type = entitySynchronizer.getEntityClass().getSimpleName();
                Log.w("FirebaseSynchronizer",
                        "createOrLinkClientEntity: " +
                                "found " + type + " with the same natural id, but existing sync record. Skipping"
                                + ". record.externalId=" + record.getExternalId()
                                + ", server.externalId=" + externalId);
            } else {
                Log.i("FirebaseSynchronizer", "createOrLinkClientEntity: found existing entity by natural id" +
                        ". externalId=" + externalId);
                addSyncRecord(
                        clientEntity.getId(),
                        serverEntity,
                        entitySynchronizer,
                        EntitySynchronizer.getLastChangeDate(serverEntity));

                updateClientEntity(serverEntity, clientEntity, entitySynchronizer);
            }
        }
    }

    private <T extends Entity> EntitySynchronizationRecord<T> addSyncRecord(
            Long internalId,
            DataSnapshot serverEntity,
            EntitySynchronizer<T> entitySynchronizer,
            Date lastChangeDate) {

        final EntitySynchronizationRecord<T> syncRecord = ModelHelper.createSyncRecord(internalId,
                serverEntity.getKey(),
                EntitySynchronizer.getListIdFromEntity(serverEntity.getRef()),
                lastChangeDate,
                entitySynchronizer.getEntityClass());
        dao.addSynchronizationRecord(syncRecord);

        return syncRecord;
    }

    private <T extends Entity> void updateClientEntity(
            DataSnapshot serverEntity, T clientEntity, EntitySynchronizer<T> entitySynchronizer) {
        changingClientEntities.put(clientEntity, serverEntity);

        entitySynchronizer.updateClientEntity(clientEntity, serverEntity);
    }

    @NonNull
    private ExecutorServiceMonitoringDecorator createExecutor(String threadName) {
        final ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat(threadName).build();
        final ExecutorService executorService = Executors.newSingleThreadExecutor(factory);
        return new ExecutorServiceMonitoringDecorator(executorService);
    }

    public interface FailureCallback {
        void synchronizationStartFailed(Exception exception);

        void updateServerEntityFailed(Entity entity, Exception exception);

        void serverNotificationFailed(Exception exception);
    }

    private final static class EntityPair<T extends Entity> {

        private final T clientEntity;
        private final DataSnapshot serverEntity;

        private EntityPair(T clientEntity, DataSnapshot serverEntity) {
            this.clientEntity = clientEntity;
            this.serverEntity = serverEntity;
        }
    }

    private final static class ValueListenerPair {
        private final DatabaseReference node;
        private final ValueEventListener valueListener;

        private ValueListenerPair(DatabaseReference node, ValueEventListener valueListener) {
            this.node = node;
            this.valueListener = valueListener;
        }
    }
}
