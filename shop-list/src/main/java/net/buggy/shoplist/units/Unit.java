package net.buggy.shoplist.units;


import android.app.Activity;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.ViewGroup;

import net.buggy.shoplist.units.views.ViewRenderer;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class Unit<A extends Activity & UnitHost> implements Serializable {

    private transient A hostingActivity;
    private Unit<A> listeningUnit;
    private String tag;

    private transient final Map<Integer, ViewRenderer<A, ViewGroup>> renderers = new ConcurrentHashMap<>();
    private transient final List<Integer> claimedViews = new CopyOnWriteArrayList<>();

    private transient final Map<ViewRenderer, SparseArray<Parcelable>> savedStates = new ConcurrentHashMap<>();

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

    public abstract void start();

    protected <T extends ViewGroup> void addRenderer(int viewId, ViewRenderer<A, T> viewRenderer) {
        renderers.put(viewId, (ViewRenderer<A, ViewGroup>) viewRenderer);

        hostingActivity.claimView(viewId, this);
        claimedViews.add(viewId);

        renderView(viewId, viewRenderer);
    }

    public void viewReclaimed(int viewId) {
        boolean shown = (claimedViews.size() == renderers.size());

        claimedViews.remove((Integer) viewId);

        if (shown) {
            clearParentViews();
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
        claimedViews.add(viewId);

        if (claimedViews.size() == renderers.size()) {
            displayUnit();
        }
    }

    public void fireEvent(Object event) {
        if (listeningUnit != null) {
            listeningUnit.onEvent(event);
        }
    }

    protected void onEvent(Object event) {
        String eventType = "null";
        if (event != null) {
            eventType = event.getClass().getSimpleName();
        }

        throw new UnsupportedOperationException("Handling of event " + eventType + " is not supported");
    }

    public void setListeningUnit(Unit<A> listeningUnit) {
        this.listeningUnit = listeningUnit;
    }

    public void onBackPressed() {

    }
}
