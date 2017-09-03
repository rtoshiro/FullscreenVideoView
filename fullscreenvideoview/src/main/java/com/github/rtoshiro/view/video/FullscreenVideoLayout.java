/**
 * Copyright (C) 2016 Toshiro Sugii
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rtoshiro.view.video;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class FullscreenVideoLayout extends FullscreenVideoView implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, MediaPlayer.OnPreparedListener, View.OnTouchListener {

    /**
     * Log cat TAG name
     */
    private final static String TAG = "FullscreenVideoLayout";

    /**
     * RelativeLayout that contains all control related views
     */
    protected View videoControlsView;

    /**
     * SeekBar reference (from videoControlsView)
     */
    protected SeekBar seekBar;

    /**
     * Reference to ImageButton play
     */
    protected ImageButton imgplay;

    /**
     * Reference to ImageButton fullscreen
     */
    protected ImageButton imgfullscreen;

    /**
     * Reference to TextView for elapsed time and total time
     */
    protected TextView textTotal, textElapsed;

    protected OnTouchListener touchListener;

    /**
     * Handler and Runnable to keep tracking on elapsed time
     */
    protected static final Handler TIME_THREAD = new Handler();
    protected Runnable updateTimeRunnable = new Runnable() {
        public void run() {
            updateCounter();

            TIME_THREAD.postDelayed(this, 200);
        }
    };

    public FullscreenVideoLayout(Context context) {
        super(context);
    }

    public FullscreenVideoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FullscreenVideoLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void release() {
        super.release();
        super.setOnTouchListener(null);
    }

    @Override
    protected void initObjects() {
        super.initObjects();

        // We need to add it to show/hide the controls
        super.setOnTouchListener(this);

        if (this.videoControlsView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.videoControlsView = inflater.inflate(R.layout.view_videocontrols, this, false);
        }

        if (videoControlsView != null) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            params.addRule(ALIGN_PARENT_BOTTOM);
            addView(videoControlsView, params);

            this.seekBar = (SeekBar) this.videoControlsView.findViewById(R.id.vcv_seekbar);
            this.imgfullscreen = (ImageButton) this.videoControlsView.findViewById(R.id.vcv_img_fullscreen);
            this.imgplay = (ImageButton) this.videoControlsView.findViewById(R.id.vcv_img_play);
            this.textTotal = (TextView) this.videoControlsView.findViewById(R.id.vcv_txt_total);
            this.textElapsed = (TextView) this.videoControlsView.findViewById(R.id.vcv_txt_elapsed);
        }

        if (this.imgplay != null)
            this.imgplay.setOnClickListener(this);
        if (this.imgfullscreen != null)
            this.imgfullscreen.setOnClickListener(this);
        if (this.seekBar != null)
            this.seekBar.setOnSeekBarChangeListener(this);

        // Start controls invisible. Make it visible when it is prepared
        if (this.videoControlsView != null)
            this.videoControlsView.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void releaseObjects() {
        super.releaseObjects();

        if (this.videoControlsView != null)
            removeView(this.videoControlsView);
    }

    protected void startCounter() {
        Log.d(TAG, "startCounter");

        TIME_THREAD.postDelayed(updateTimeRunnable, 200);
    }

    protected void stopCounter() {
        Log.d(TAG, "stopCounter");

        TIME_THREAD.removeCallbacks(updateTimeRunnable);
    }

    protected void updateCounter() {
        if (this.textElapsed == null)
            return;

        int elapsed = getCurrentPosition();
        // getCurrentPosition is a little bit buggy :(
        if (elapsed > 0 && elapsed < getDuration()) {
            seekBar.setProgress(elapsed);

            elapsed = Math.round(elapsed / 1000.f);
            long s = elapsed % 60;
            long m = (elapsed / 60) % 60;
            long h = (elapsed / (60 * 60)) % 24;

            if (h > 0)
                textElapsed.setText(String.format(Locale.US, "%d:%02d:%02d", h, m, s));
            else
                textElapsed.setText(String.format(Locale.US, "%02d:%02d", m, s));
        }
    }

    @Override
    public void setOnTouchListener(View.OnTouchListener l) {
        touchListener = l;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "onCompletion");

        super.onCompletion(mp);
        stopCounter();
        updateControls();
        if (currentState != State.ERROR)
            updateCounter();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        boolean result = super.onError(mp, what, extra);
        stopCounter();
        updateControls();
        return result;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (getCurrentState() == State.END) {
            Log.d(TAG, "onDetachedFromWindow END");
            stopCounter();
        }
    }

    @Override
    protected void tryToPrepare() {
        Log.d(TAG, "tryToPrepare");
        super.tryToPrepare();

        if (getCurrentState() == State.PREPARED || getCurrentState() == State.STARTED) {
            if (textElapsed != null && textTotal != null) {
                int total = getDuration();
                if (total > 0) {
                    seekBar.setMax(total);
                    seekBar.setProgress(0);

                    total = total / 1000;
                    long s = total % 60;
                    long m = (total / 60) % 60;
                    long h = (total / (60 * 60)) % 24;
                    if (h > 0) {
                        textElapsed.setText("00:00:00");
                        textTotal.setText(String.format(Locale.US, "%d:%02d:%02d", h, m, s));
                    } else {
                        textElapsed.setText("00:00");
                        textTotal.setText(String.format(Locale.US, "%02d:%02d", m, s));
                    }
                }
            }

            if (videoControlsView != null)
                videoControlsView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void start() throws IllegalStateException {
        Log.d(TAG, "start");

        if (!isPlaying()) {
            super.start();
            startCounter();
            updateControls();
        }
    }

    @Override
    public void pause() throws IllegalStateException {
        Log.d(TAG, "pause");

        if (isPlaying()) {
            stopCounter();
            super.pause();
            updateControls();
        }
    }

    @Override
    public void reset() {
        Log.d(TAG, "reset");

        super.reset();

        stopCounter();
        updateControls();
    }

    @Override
    public void stop() throws IllegalStateException {
        Log.d(TAG, "stop");

        super.stop();
        stopCounter();
        updateControls();
    }

    protected void updateControls() {
        if (imgplay == null) return;

        Drawable icon;
        if (getCurrentState() == State.STARTED) {
            icon = context.getResources().getDrawable(R.drawable.fvl_selector_pause);
        } else {
            icon = context.getResources().getDrawable(R.drawable.fvl_selector_play);
        }
        imgplay.setBackgroundDrawable(icon);
    }

    public void hideControls() {
        Log.d(TAG, "hideControls");
        if (videoControlsView != null) {
            videoControlsView.setVisibility(View.INVISIBLE);
        }
    }

    public void showControls() {
        Log.d(TAG, "showControls");
        if (videoControlsView != null) {
            videoControlsView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (videoControlsView != null) {
                if (videoControlsView.getVisibility() == View.VISIBLE)
                    hideControls();
                else
                    showControls();
            }
        }

        if (touchListener != null) {
            return touchListener.onTouch(FullscreenVideoLayout.this, event);
        }

        return false;
    }

    /**
     * Onclick action
     * Controls play button and fullscreen button.
     *
     * @param v View defined in XML
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.vcv_img_play) {
            if (isPlaying()) {
                pause();
            } else {
                start();
            }
        } else {
            setFullscreen(!isFullscreen());
        }
    }

    /**
     * SeekBar Listener
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        Log.d(TAG, "onProgressChanged " + progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        stopCounter();
        Log.d(TAG, "onStartTrackingTouch");

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int progress = seekBar.getProgress();
        seekTo(progress);
        Log.d(TAG, "onStopTrackingTouch");

    }
}
