package com.github.rtoshiro.example.fvvapplication;

import android.app.Activity;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.rtoshiro.view.video.FullscreenVideoLayout;

import java.io.IOException;

public class MainActivity extends Activity {
    private android.widget.TextView textview;
    private android.widget.Button button;
    private FullscreenVideoLayout videoLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.button = (Button) findViewById(R.id.button);
        this.textview = (TextView) findViewById(R.id.textview);
        this.videoLayout = (FullscreenVideoLayout) findViewById(R.id.videoview);

        videoLayout.setActivity(this);
        videoLayout.setShouldAutoplay(false);

        loadVideo();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        videoLayout.resize();
    }

    public void loadVideo() {
        Uri videoUri = Uri.parse("http://techslides.com/demos/sample-videos/small.mp4");
        try {
            videoLayout.setVideoURI(videoUri);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reset(View v) {
        if (this.videoLayout != null) {
            this.videoLayout.reset();
            loadVideo();
        }
    }
}
