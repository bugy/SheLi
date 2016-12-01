package net.buggy.shoplist.components;


import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

public class ListDecorator {

    public static void decorateList(RecyclerView recyclerView) {
        final Context context = recyclerView.getContext();

        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
    }
}
