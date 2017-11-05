package net.buggy.shoplist.units;


import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.ViewGroup;

import net.buggy.shoplist.units.views.ViewRenderer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class Unit<A extends Activity & UnitHost> implements Serializable {

    private transient A hostingActivity;
    private Unit<A> listeningUnit;
    private String tag;

    private transient Map<Integer, ViewRenderer<A, ViewGroup>> renderers;
    private transient List<Integer> claimedViews;
    private transient boolean initialized = false;

    private transient Map<ViewRenderer, SparseArray<Parcelable>> savedStates;

    private List<StateListener> stateListeners; 

    public Unit() {
        initFields();
    }

    private void initFields() {
        renderers = new ConcurrentHashMap<>();
        claimedViews = new CopyOnWriteArrayList<>();
        savedStates = new ConcurrentHashMap<>();
        stateListeners = new CopyOnWriteArrayList<>();
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public void setHostingActivity(A hostingActivity) {
        this.hostingActivity = hostingActivity;
    }

    protected A getHostingActivity() {
        return hostingActivity;
    }

    protected abstract void initialize();

    protected void deinitialize() {

    }

    public final void stop() {
        deinitialize();

        fireUnitStopped();
    }

    public void start() {
        if (!initialized) {
            initialize();
            initialized = true;
        }

        for (Integer viewId : renderers.keySet()) {
            hostingActivity.claimView(viewId, this);
            if (!claimedViews.contains(viewId)) {
                claimedViews.add(viewId);
            }
        }

        displayUnit();
    }

    protected <T extends ViewGroup> void addRenderer(int viewId, ViewRenderer<A, T> viewRenderer) {
        renderers.put(viewId, (ViewRenderer<A, ViewGroup>) viewRenderer);
    }

    public void viewReclaimed(int viewId) {
        boolean shown = (claimedViews.size() == renderers.size());

        claimedViews.remove((Integer) viewId);

        if (shown) {
            clearParentViews();
            fireUnitHidden();
        }
    }

    private void clearParentViews() {
        for (Integer parentViewId : renderers.keySet()) {
            final ViewGroup parentView = (ViewGroup) hostingActivity.findViewById(parentViewId);

            SparseArray<Parcelable> state = new SparseArray<>();
            parentView.saveHierarchyState(state);
            savedStates.put(renderers.get(parentViewId), state);

            parentView.removeAllViews();
        }
    }

    private void displayUnit() {
        for (Map.Entry<Integer, ViewRenderer<A, ViewGroup>> entry : renderers.entrySet()) {
            final Integer viewId = entry.getKey();
            final ViewRenderer<A, ViewGroup> renderer = entry.getValue();

            renderView(viewId, renderer);
        }

        fireUnitShown();
    }

    private void fireUnitShown() {
        for (StateListener listener : stateListeners) {
            listener.shown();
        }
    }

    private void fireUnitHidden() {
        for (StateListener listener : stateListeners) {
            listener.hidden();
        }
    }

    private void fireUnitStopped() {
        for (StateListener listener : stateListeners) {
            listener.stopped();
        }
    }

    private <T extends ViewGroup> void renderView(Integer viewId, ViewRenderer<A, T> renderer) {
        final T parentView = (T) hostingActivity.findViewById(viewId);

        parentView.removeAllViews();

        renderer.renderTo(parentView, hostingActivity);

        final SparseArray<Parcelable> savedState = savedStates.get(renderer);
        if (savedState != null) {
            parentView.restoreHierarchyState(savedState);
        }
    }

    public void viewClaimed(int viewId) {
        if (!claimedViews.contains(viewId)) {
            claimedViews.add(viewId);

            if (claimedViews.size() == renderers.size()) {
                displayUnit();
            }
        }
    }

    public void setListeningUnit(Unit<A> listeningUnit) {
        this.listeningUnit = listeningUnit;
    }

    public void onBackPressed() {

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        throw new IllegalStateException("Shouldn't be called without explicit subscription");
    }

    public void fireEvent(Object event) {
        if (listeningUnit != null) {
            listeningUnit.onEvent(event);
        }
    }

    public void addStateListener(StateListener stateListener) {
        stateListeners.add(stateListener);
    }

    public void removeStateListener(StateListener stateListener) {
        stateListeners.remove(stateListener);
    }

    protected void onEvent(Object event) {
        String eventType = "null";
        if (event != null) {
            eventType = event.getClass().getSimpleName();
        }

        throw new UnsupportedOperationException("Handling of event " + eventType + " is not supported");
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        initFields();

        stream.defaultReadObject();
    }

    public static abstract class StateListener {
        public void hidden() {

        }

        public void shown() {

        }

        public void stopped() {

        }
    }
}
