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
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.rtoshiro.R;

public class FullscreenVideoLayout extends FullscreenVideoView implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, MediaPlayer.OnPreparedListener, View.OnTouchListener {

    /**
     * Log cat TAG name
     */
    private final static String TAG = "FullscreenVideoLayout";

    // Control views
    protected View videoControlsView;
    protected SeekBar seekBar;
    protected ImageButton imgplay;
    protected ImageButton imgfullscreen;
    protected TextView textTotal, textElapsed;

    protected OnTouchListener touchListener;

    // Counter
    protected static final Handler TIME_THREAD = new Handler();
    protected Runnable updateTimeRunnable = new Runnable() {
        public void run() {
            int elapsed = getCurrentPosition();
            Log.d(TAG, "elapsed = " + elapsed);

            // getCurrentPosition is a little bit buggy :(
            if (elapsed > 0 && elapsed < getDuration()) {
                elapsed = elapsed / 1000;
                seekBar.setProgress(elapsed);

                long s = elapsed % 60;
                long m = (elapsed / 60) % 60;
                long h = (elapsed / (60 * 60)) % 24;

                if (h > 0)
                    textElapsed.setText(String.format("%d:%02d:%02d", h, m, s));
                else
                    textElapsed.setText(String.format("%02d:%02d", m, s));
            }

            TIME_THREAD.postDelayed(this, 500);
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
    protected void init() {
        Log.d(TAG, "init");

        super.init();

        LayoutInflater inflater = (LayoutInflater) context.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        this.videoControlsView = inflater.inflate(R.layout.view_videocontrols, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(ALIGN_PARENT_BOTTOM);
        videoControlsView.setLayoutParams(params);
        addView(videoControlsView);

        this.seekBar = (SeekBar) this.videoControlsView.findViewById(R.id.vcv_seekbar);
        this.imgfullscreen = (ImageButton) this.videoControlsView.findViewById(R.id.vcv_img_fullscreen);
        this.imgplay = (ImageButton) this.videoControlsView.findViewById(R.id.vcv_img_play);
        this.textTotal = (TextView) this.videoControlsView.findViewById(R.id.vcv_txt_total);
        this.textElapsed = (TextView) this.videoControlsView.findViewById(R.id.vcv_txt_elapsed);

        // We need to add it to show/hide the controls
        super.setOnTouchListener(this);

        this.imgplay.setOnClickListener(this);
        this.imgfullscreen.setOnClickListener(this);
        this.seekBar.setOnSeekBarChangeListener(this);

        // Start controls invisible. Make it visible when it is prepared
        this.videoControlsView.setVisibility(View.INVISIBLE);
    }

    protected void startCounter() {
//        TIME_THREAD.postDelayed(updateTimeRunnable, 1000);
    }

    protected void stopCounter() {
//        TIME_THREAD.removeCallbacks(updateTimeRunnable);
    }

    @Override
    public void setOnTouchListener(View.OnTouchListener l) {
        touchListener = l;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        super.onCompletion(mp);
        stopCounter();
        updateControls();
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

        if (getCurrentState() == State.PREPARED) {
            int total = getDuration();
            if (total > 0) {
                total = total / 1000;
                seekBar.setMax(total);
                seekBar.setProgress(0);

                long s = total % 60;
                long m = (total / 60) % 60;
                long h = (total / (60 * 60)) % 24;
                if (h > 0) {
                    textElapsed.setText("00:00:00");
                    textTotal.setText(String.format("%d:%02d:%02d", h, m, s));
                } else {
                    textElapsed.setText("00:00");
                    textTotal.setText(String.format("%02d:%02d", m, s));
                }
            }

            videoControlsView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void start() throws IllegalStateException {
        if (!isPlaying()) {
            super.start();
            startCounter();
            updateControls();
        }
    }

    @Override
    public void pause() throws IllegalStateException {
        if (isPlaying()) {
            stopCounter();
            super.pause();
            updateControls();
        }
    }

    @Override
    public void reset() {
        super.reset();
        stopCounter();
        updateControls();
    }

    @Override
    public void stop() throws IllegalStateException {
        super.stop();
        stopCounter();
        updateControls();
    }

    protected void updateControls() {
        Drawable icon;
        if (getCurrentState() == State.STARTED) {
            icon = context.getResources().getDrawable(R.drawable.fvl_selector_pause);
        } else {
            icon = context.getResources().getDrawable(R.drawable.fvl_selector_play);
        }
        imgplay.setBackgroundDrawable(icon);
    }

    public void hideControls() {
        if (videoControlsView != null)
            videoControlsView.setVisibility(View.INVISIBLE);
    }

    public void showControls() {
        if (videoControlsView != null)
            videoControlsView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (videoControlsView != null) {
            if (videoControlsView.getVisibility() == View.VISIBLE)
                hideControls();
            else
                showControls();
        }

        if (touchListener != null) {
            return touchListener.onTouch(FullscreenVideoLayout.this, event);
        }

        return false;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.vcv_img_play) {
            if (isPlaying()) {
                pause();
            } else {
                start();
            }
        } else {
            fullscreen();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        Log.d(TAG, "onProgressChanged");
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        stopCounter();
        Log.d(TAG, "onStartTrackingTouch");

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int progress = seekBar.getProgress() * 1000;
        seekTo(progress);
        Log.d(TAG, "onStopTrackingTouch");

    }
}
