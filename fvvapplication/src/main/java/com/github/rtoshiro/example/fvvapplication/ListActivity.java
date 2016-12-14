package com.github.rtoshiro.example.fvvapplication;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.rtoshiro.view.video.FullscreenVideoLayout;

import java.io.IOException;
import java.util.ArrayList;

public class ListActivity extends Activity {
    RecyclerView recyclerView;
    ItemAdapter adapter;
    RecyclerView.LayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        this.recyclerView = (RecyclerView) findViewById(R.id.recyclerview);

        recyclerView.setHasFixedSize(true);

        ArrayList<String> items = new ArrayList<>();
        items.add("http://techslides.com/demos/sample-videos/small.mp4");
        items.add("http://techslides.com/demos/sample-videos/small.mp4");
        items.add("http://techslides.com/demos/sample-videos/small.mp4");
        items.add("http://techslides.com/demos/sample-videos/small.mp4");
        items.add("http://techslides.com/demos/sample-videos/small.mp4");
        items.add("http://techslides.com/demos/sample-videos/small.mp4");

        this.adapter = new ItemAdapter(this);
        this.adapter.setItems(items);
        this.recyclerView.setAdapter(adapter);

        // use a linear layout manager
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private ArrayList<String> items;
        private Context context;

        public ItemAdapter(Context context) {
            this.context = context;
        }

        public ArrayList<String> getItems() {
            return items;
        }

        public void setItems(ArrayList<String> items) {
            this.items = items;
        }

        @Override
        public ItemAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_item, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (items != null) {
                String item = items.get(position);
                if (item != null) {
                    holder.itemView.setTag(position);

                    Uri videoUri = Uri.parse(item);
                    try {
                        Log.i("ListActivity", "Reseting");
                        holder.videoLayout.reset();
                        holder.videoLayout.setVideoURI(videoUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return (items != null) ? items.size() : 0;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public View itemView;
            public FullscreenVideoLayout videoLayout;

            public ViewHolder(View v) {
                super(v);
                this.itemView = v;
                this.videoLayout = (FullscreenVideoLayout) v.findViewById(R.id.videoview);

            }
        }
    }

}
