package net.buggy.shoplist.data;


import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import net.buggy.shoplist.util.MockUtils;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;

public class FirebaseDatabaseMocker {

    private final FirebaseDatabase firebaseDatabase;

    private final Node rootNode = new Node(null, null);

    private final ExecutorService NOTIFICATION_SERVICE = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("Firebase-notification-service").build());
    private final AtomicLong runningTasksCount = new AtomicLong(0);

    private final List<Exception> backgroundExceptions = new CopyOnWriteArrayList<>();

    public FirebaseDatabaseMocker() {
        firebaseDatabase = MockUtils.mockStrict(FirebaseDatabase.class);

        Mockito.doAnswer(new Answer<DatabaseReference>() {
            @Override
            public DatabaseReference answer(InvocationOnMock invocationOnMock) throws Throwable {
                final String referencePath = invocationOnMock.getArgument(0);
                return rootNode.asReference().child(referencePath);
            }
        }).when(firebaseDatabase).getReference(anyString());
    }

    public FirebaseDatabase getFirebaseDatabase() {
        return firebaseDatabase;
    }

    public DataSnapshot getDataSnapshot(String referencePath) {
        return rootNode.createSnapshot().child(referencePath);
    }

    private Future<?> executeInBackground(final Runnable task) {
        runningTasksCount.incrementAndGet();

        return NOTIFICATION_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception exception) {
                    backgroundExceptions.add(exception);
                    throw exception;
                } finally {
                    runningTasksCount.decrementAndGet();
                }
            }
        });
    }

    public List<Exception> getBackgroundExceptions() {
        return backgroundExceptions;
    }

    public void waitBackgroundTasks() {
        while (!Thread.currentThread().isInterrupted() && (runningTasksCount.get() > 0)) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private class Node {

        private final Map<String, Node> children = new ConcurrentHashMap<>();
        private final Node parent;
        private final DatabaseReference databaseReference;
        private final String key;
        private volatile Object value;

        private final AtomicInteger pushCounter = new AtomicInteger();

        private final List<ChildEventListener> childListeners = new CopyOnWriteArrayList<>();
        private final List<ValueEventListener> valueListeners = new CopyOnWriteArrayList<>();

        private Node(final Node parent, final String key) {
            this.parent = parent;
            this.key = key;

            databaseReference = MockUtils.mockStrict(DatabaseReference.class);

            Mockito.doAnswer(new Answer<DatabaseReference>() {
                @Override
                public DatabaseReference answer(InvocationOnMock invocationOnMock) throws Throwable {
                    if (parent == null) {
                        return null;
                    }

                    return parent.asReference();
                }
            }).when(databaseReference).getParent();


            Mockito.doAnswer(new Answer<DatabaseReference>() {
                @Override
                public DatabaseReference answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final String referencePath = invocationOnMock.getArgument(0);
                    return getOrCreateChildReference(referencePath);
                }
            }).when(databaseReference).child(anyString());

            Mockito.doAnswer(new Answer() {
                @Override
                public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final ValueEventListener listener = invocationOnMock.getArgument(0);

                    executeInBackground(new Runnable() {
                        @Override
                        public void run() {
                            listener.onDataChange(createSnapshot());
                        }
                    });

                    return null;
                }
            }).when(databaseReference).addListenerForSingleValueEvent(any(ValueEventListener.class));

            Mockito.doAnswer(new Answer<ChildEventListener>() {
                @Override
                public ChildEventListener answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final ChildEventListener listener = invocationOnMock.getArgument(0);
                    childListeners.add(listener);

                    final Collection<Node> currentChildren = children.values();

                    executeInBackground(new Runnable() {
                        @Override
                        public void run() {
                            for (Node childNode : currentChildren) {
                                listener.onChildAdded(childNode.createSnapshot(), null);
                            }
                        }
                    });

                    return listener;
                }
            }).when(databaseReference).addChildEventListener(any(ChildEventListener.class));

            Mockito.doAnswer(new Answer<ValueEventListener>() {
                @Override
                public ValueEventListener answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final ValueEventListener listener = invocationOnMock.getArgument(0);
                    valueListeners.add(listener);

                    executeInBackground(new Runnable() {
                        @Override
                        public void run() {
                            listener.onDataChange(createSnapshot());
                        }
                    });

                    return listener;
                }
            }).when(databaseReference).addValueEventListener(any(ValueEventListener.class));

            Mockito.doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final ValueEventListener listener = invocationOnMock.getArgument(0);
                    valueListeners.remove(listener);

                    return null;
                }
            }).when(databaseReference).removeEventListener(any(ValueEventListener.class));

            Mockito.doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final ChildEventListener listener = invocationOnMock.getArgument(0);
                    childListeners.remove(listener);

                    return null;
                }
            }).when(databaseReference).removeEventListener(any(ChildEventListener.class));

            Mockito.doAnswer(new Answer<DatabaseReference>() {
                @Override
                public DatabaseReference answer(InvocationOnMock invocationOnMock) throws Throwable {
                    String childId = key + "-child-" + pushCounter.getAndIncrement();

                    return getOrCreateChildReference(childId);
                }
            }).when(databaseReference).push();

            Mockito.doAnswer(new Answer<Task<Void>>() {
                @Override
                public Task<Void> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    value = invocationOnMock.getArgument(0);

                    notifyValueChanged();

                    return new FinishedTask<>(null);
                }
            }).when(databaseReference).setValue(ArgumentMatchers.any());

            Mockito.doAnswer(new Answer<Task<Void>>() {
                @Override
                public Task<Void> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    Map<String, Object> newChildren = invocationOnMock.getArgument(0);

                    boolean exists = valueExists();

                    Map<String, Node> removedNodes = new LinkedHashMap<>(children);
                    for (String newKey : newChildren.keySet()) {
                        removedNodes.remove(newKey);
                    }
                    for (String removedKey : removedNodes.keySet()) {
                        children.remove(removedKey);
                    }

                    Set<String> newKeys = new LinkedHashSet<>(newChildren.keySet());
                    newKeys.removeAll(children.keySet());
                    for (String newKey : newKeys) {
                        addChild(newKey);
                    }

                    for (Map.Entry<String, Object> entry : newChildren.entrySet()) {
                        final Node childNode = children.get(entry.getKey());
                        childNode.value = entry.getValue();
                        childNode.notifyValueChanged();
                    }

                    if (exists) {
                        notifyNodeChanged();
                    } else {
                        notifyNodeAdded();
                    }

                    return new FinishedTask<>(null);
                }
            }).when(databaseReference).updateChildren(any(Map.class));

            Mockito.doAnswer(new Answer<Task<Void>>() {
                @Override
                public Task<Void> answer(InvocationOnMock invocationOnMock) throws Throwable {
                    parent.children.remove(key);

                    notifyNodeRemoved();

                    return new FinishedTask<>(null);
                }
            }).when(databaseReference).removeValue();

            Mockito.doAnswer(new Answer<Query>() {
                @Override
                public Query answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final String fieldName = invocationOnMock.getArgument(0);

                    QueryMock queryMock = new QueryMock(Node.this);
                    queryMock.orderByChild = fieldName;

                    return queryMock.asQuery();
                }
            }).when(databaseReference).orderByChild(anyString());

            Mockito.doReturn(key).when(databaseReference).getKey();

            Mockito.doReturn(databaseReference).when(databaseReference).getRef();
        }

        private void notifyNodeAdded() {
            final List<ChildEventListener> childListeners = parent.childListeners;
            if (!childListeners.isEmpty()) {
                executeInBackground(new Runnable() {
                    @Override
                    public void run() {
                        for (ChildEventListener childListener : childListeners) {
                            childListener.onChildAdded(createSnapshot(), null);
                        }
                    }
                });
            }
        }

        private void notifyNodeChanged() {
            final List<ChildEventListener> childListeners = parent.childListeners;
            if (!childListeners.isEmpty()) {
                executeInBackground(new Runnable() {
                    @Override
                    public void run() {
                        for (ChildEventListener childListener : childListeners) {
                            childListener.onChildChanged(createSnapshot(), null);
                        }
                    }
                });
            }
        }

        private void notifyValueChanged() {
            final ImmutableList<ValueEventListener> listeners = ImmutableList.copyOf(valueListeners);
            if (!listeners.isEmpty()) {
                executeInBackground(new Runnable() {
                    @Override
                    public void run() {
                        for (ValueEventListener listener : listeners) {
                            listener.onDataChange(createSnapshot());
                        }
                    }
                });
            }
        }

        private void notifyNodeRemoved() {
            final List<ChildEventListener> childListeners = parent.childListeners;
            if (childListeners.size() > 0) {
                executeInBackground(new Runnable() {
                    @Override
                    public void run() {
                        for (ChildEventListener childListener : childListeners) {
                            childListener.onChildRemoved(createSnapshot());
                        }
                    }
                });
            }
        }

        private boolean valueExists() {
            if (value != null) {
                return true;
            }

            for (Node child : children.values()) {
                if (child.valueExists()) {
                    return true;
                }
            }

            return false;
        }

        public DatabaseReference getOrCreateChildReference(String referencePath) {
            final Node child = getOrCreateChild(referencePath);

            return child.asReference();
        }

        private Node getOrCreateChild(String childKey) {
            if (children.get(childKey) == null) {
                addChild(childKey);
            }

            return children.get(childKey);
        }

        private void addChild(String childKey) {
            children.put(childKey, new Node(this, childKey));
        }

        public DatabaseReference asReference() {
            return databaseReference;
        }

        public DataSnapshot createSnapshot() {
            final DataSnapshot dataSnapshot = MockUtils.mockStrict(DataSnapshot.class);

            Object currentValue = value;
            final Map<String, DataSnapshot> currentChildren = new LinkedHashMap<>();
            for (Map.Entry<String, Node> entry : children.entrySet()) {
                currentChildren.put(entry.getKey(), entry.getValue().createSnapshot());
            }


            Mockito.doReturn(key).when(dataSnapshot).getKey();

            Mockito.doReturn(databaseReference).when(dataSnapshot).getRef();

            Mockito.doReturn(currentValue).when(dataSnapshot).getValue();

            Mockito.doReturn(currentValue).when(dataSnapshot).getValue(any(Class.class));

            Mockito.doReturn(currentValue).when(dataSnapshot).getValue(any(GenericTypeIndicator.class));

            Mockito.doAnswer(new Answer<DataSnapshot>() {
                @Override
                public DataSnapshot answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final String referencePath = invocationOnMock.getArgument(0);

                    final DataSnapshot child = currentChildren.get(referencePath);

                    if (child != null) {
                        return child;
                    }

                    return new Node(parent, referencePath).createSnapshot();
                }
            }).when(dataSnapshot).child(anyString());

            Mockito.doReturn(new ArrayList<>(currentChildren.values())).when(dataSnapshot).getChildren();

            Mockito.doReturn((long) currentChildren.size()).when(dataSnapshot).getChildrenCount();

            final boolean valueExist = (currentValue != null) || !currentChildren.isEmpty();
            Mockito.doReturn(valueExist).when(dataSnapshot).exists();

            return dataSnapshot;
        }

        public String getPath() {
            if (parent == null) {
                return "";
            }

            final String parentPath = parent.getPath();
            if (Strings.isNullOrEmpty(parentPath)) {
                return key;
            }

            return parentPath + '.' + key;
        }

        private class FinishedTask<T> extends Task<T> {

            private final T result;

            private FinishedTask(T result) {
                this.result = result;
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public boolean isSuccessful() {
                return true;
            }

            @Override
            public T getResult() {
                return result;
            }

            @Override
            public <X extends Throwable> T getResult(@NonNull Class<X> aClass) throws X {
                return (T) aClass.cast(result);
            }

            @Nullable
            @Override
            public Exception getException() {
                return null;
            }

            @NonNull
            @Override
            public Task<T> addOnSuccessListener(@NonNull OnSuccessListener<? super T> onSuccessListener) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public Task<T> addOnSuccessListener(@NonNull Executor executor, @NonNull OnSuccessListener<? super T> onSuccessListener) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public Task<T> addOnSuccessListener(@NonNull Activity activity, @NonNull OnSuccessListener<? super T> onSuccessListener) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public Task<T> addOnFailureListener(@NonNull OnFailureListener onFailureListener) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public Task<T> addOnFailureListener(@NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public Task<T> addOnFailureListener(@NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public Task<T> addOnCompleteListener(@NonNull final OnCompleteListener<T> onCompleteListener) {
                executeInBackground(new Runnable() {
                    @Override
                    public void run() {
                        onCompleteListener.onComplete(FinishedTask.this);
                    }
                });

                return FinishedTask.this;
            }

            @NonNull
            @Override
            public Task<T> addOnCompleteListener(@NonNull Executor executor, @NonNull final OnCompleteListener<T> onCompleteListener) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        onCompleteListener.onComplete(FinishedTask.this);
                    }
                });

                return FinishedTask.this;
            }

            @NonNull
            @Override
            public Task<T> addOnCompleteListener(@NonNull Activity activity, @NonNull final OnCompleteListener<T> onCompleteListener) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onCompleteListener.onComplete(FinishedTask.this);
                    }
                });

                return FinishedTask.this;
            }
        }
    }

    private final class QueryMock {
        private final Query query;
        private final Node node;

        private String orderByChild;
        private Object startAt;
        private Object equalTo;

        public QueryMock(final Node node) {
            this.node = node;

            query = MockUtils.mockStrict(Query.class);

            Answer<Query> startAtAnswer = new Answer<Query>() {
                @Override
                public Query answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final Object startAt = invocationOnMock.getArgument(0);

                    QueryMock queryMock = new QueryMock(node);
                    queryMock.orderByChild = orderByChild;
                    queryMock.equalTo = equalTo;
                    queryMock.startAt = startAt;

                    return queryMock.asQuery();
                }
            };

            Mockito.doAnswer(startAtAnswer).when(query).startAt(anyString());
            Mockito.doAnswer(startAtAnswer).when(query).startAt(anyBoolean());
            Mockito.doAnswer(startAtAnswer).when(query).startAt(anyDouble());

            final Answer<Query> equalToAnswer = new Answer<Query>() {
                @Override
                public Query answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final Object equalTo = invocationOnMock.getArgument(0);

                    QueryMock queryMock = new QueryMock(node);
                    queryMock.orderByChild = orderByChild;
                    queryMock.startAt = startAt;
                    queryMock.equalTo = equalTo;

                    return queryMock.asQuery();
                }
            };

            Mockito.doAnswer(equalToAnswer).when(query).equalTo(anyString());
            Mockito.doAnswer(equalToAnswer).when(query).equalTo(anyBoolean());
            Mockito.doAnswer(equalToAnswer).when(query).equalTo(anyDouble());

            Mockito.doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                    final ValueEventListener listener = invocationOnMock.getArgument(0);

                    final Map<String, Node> children = getChildren();

                    executeInBackground(new Runnable() {
                        @Override
                        public void run() {
                            Node notificationNode = new Node(node.parent, node.key + "-query");
                            notificationNode.children.putAll(children);

                            listener.onDataChange(notificationNode.createSnapshot());
                        }
                    });

                    return null;
                }
            }).when(query).addListenerForSingleValueEvent(any(ValueEventListener.class));
        }

        public Query asQuery() {
            return query;
        }

        @NonNull
        private Map<String, Node> getChildren() {
            final Comparator<Node> childComparator = getChildComparator();

            final Map<String, Node> nodeChildren = node.children;
            final TreeMap<String, Node> nodes = new TreeMap<>(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    Node child1 = nodeChildren.get(o1);
                    Node child2 = nodeChildren.get(o2);

                    return childComparator.compare(child1, child2);
                }
            });
            nodes.putAll(nodeChildren);

            if (equalTo != null) {
                Iterator<Map.Entry<String, Node>> iterator = nodes.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Node> entry = iterator.next();

                    Object nodeValue = getQueryValue(entry.getValue());

                    if (!Objects.equal(nodeValue, equalTo)) {
                        iterator.remove();
                    }
                }

            } else {
                if (startAt != null) {
                    Iterator<Map.Entry<String, Node>> iterator = nodes.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Node> entry = iterator.next();

                        Object nodeValue = getQueryValue(entry.getValue());

                        if (!Objects.equal(nodeValue, startAt)) {
                            iterator.remove();
                        } else {
                            break;
                        }
                    }
                }
            }

            return nodes;
        }

        private Object getQueryValue(Node childNode) {
            if (orderByChild != null) {
                Node valueNode = childNode.children.get(orderByChild);
                if (valueNode == null) {
                    return null;
                }

                return valueNode.value;
            }

            throw new UnsupportedOperationException();
        }

        private Comparator<Node> getChildComparator() {
            return new Comparator<Node>() {
                @Override
                public int compare(Node o1, Node o2) {
                    Object value1 = getQueryValue(o1);
                    Object value2 = getQueryValue(o2);

                    if (value1 == null) {
                        if (value2 == null) {
                            return o1.key.compareTo(o2.key);
                        }
                        return -1;
                    } else if (value2 == null) {
                        return 1;
                    }

                    if (Boolean.FALSE.equals(value1)) {
                        if (Boolean.FALSE.equals(value2)) {
                            return o1.key.compareTo(o2.key);
                        }
                        return -1;
                    } else if (Boolean.FALSE.equals(value2)) {
                        return 1;
                    }

                    if (Boolean.TRUE.equals(value1)) {
                        if (Boolean.TRUE.equals(value2)) {
                            return o1.key.compareTo(o2.key);
                        }
                        return -1;
                    } else if (Boolean.TRUE.equals(value2)) {
                        return 1;
                    }

                    if (value1 instanceof Number) {
                        if (value2 instanceof Number) {
                            int compare = Double.compare(
                                    ((Number) value1).doubleValue(),
                                    ((Number) value2).doubleValue());
                            if (compare != 0) {
                                return compare;
                            }

                            return o1.key.compareTo(o2.key);
                        }

                        return -1;
                    } else if (value2 instanceof Number) {
                        return 1;
                    }

                    if (value1 instanceof String) {
                        if (value2 instanceof String) {
                            int compareResult = ((String) value1).compareTo((String) value2);
                            if (compareResult != 0) {
                                return compareResult;
                            }

                            return o1.key.compareTo(o2.key);
                        }

                        return -1;
                    } else if (value2 instanceof String) {
                        return 1;
                    }

                    return o1.key.compareTo(o2.key);
                }
            };
        }
    }
}
