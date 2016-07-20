/**
 * Copyright (C) 2016 Toshiro Sugii
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rtoshiro.view.video;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import java.io.IOException;

/**
 * Acts like a android.widget.VideoView with fullscreen functionality
 *
 * @author rtoshiro
 * @version 2015.0527
 * @since 1.0
 */
public class FullscreenVideoView extends RelativeLayout implements SurfaceHolder.Callback, OnPreparedListener, OnErrorListener, OnSeekCompleteListener, OnCompletionListener, OnInfoListener, OnVideoSizeChangedListener {

    /**
     * Debug Tag for use logging debug output to LogCat
     */
    private final static String TAG = "FullscreenVideoView";

    protected Context context;
    protected Activity activity; // Used when orientation changes is not static

    protected MediaPlayer mediaPlayer;
    protected SurfaceHolder surfaceHolder;
    protected SurfaceView surfaceView;
    protected boolean videoIsReady, surfaceIsReady;
    protected boolean detachedByFullscreen;
    protected State currentState;
    protected State lastState; // Tells onSeekCompletion what to do

    protected View loadingView;

    protected ViewGroup parentView; // Controls fullscreen container
    protected ViewGroup.LayoutParams currentLayoutParams;

    protected boolean fullscreen;
    protected boolean shouldAutoplay;
    protected int initialConfigOrientation;
    protected int initialMovieWidth, initialMovieHeight;

    protected OnErrorListener errorListener;
    protected OnPreparedListener preparedListener;
    protected OnSeekCompleteListener seekCompleteListener;
    protected OnCompletionListener completionListener;
    protected OnInfoListener infoListener;

    /**
     * States of MediaPlayer
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#StateDiagram">MediaPlayer</a>
     */
    public enum State {
        IDLE,
        INITIALIZED,
        PREPARED,
        PREPARING,
        STARTED,
        STOPPED,
        PAUSED,
        PLAYBACKCOMPLETED,
        ERROR,
        END
    }

    public FullscreenVideoView(Context context) {
        super(context);
        this.context = context;

        init();
    }

    public FullscreenVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        init();
    }

    public FullscreenVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;

        init();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resize();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Log.d(TAG, "onSaveInstanceState");
        return super.onSaveInstanceState();
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        Log.d(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow - detachedByFullscreen: " + detachedByFullscreen);

        super.onDetachedFromWindow();

        if (!detachedByFullscreen) {
            if (mediaPlayer != null) {
                this.mediaPlayer.setOnPreparedListener(null);
                this.mediaPlayer.setOnErrorListener(null);
                this.mediaPlayer.setOnSeekCompleteListener(null);
                this.mediaPlayer.setOnCompletionListener(null);
                this.mediaPlayer.setOnInfoListener(null);

                if (mediaPlayer.isPlaying())
                    mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            videoIsReady = false;
            surfaceIsReady = false;
            currentState = State.END;
        }

        detachedByFullscreen = false;
    }

    @Override
    synchronized public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called = " + currentState);

        mediaPlayer.setDisplay(surfaceHolder);

        // If is not prepared yet - tryToPrepare()
        if (!surfaceIsReady) {
            surfaceIsReady = true;
            if (currentState != State.PREPARED &&
                    currentState != State.PAUSED &&
                    currentState != State.STARTED &&
                    currentState != State.PLAYBACKCOMPLETED)
                tryToPrepare();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged called");
        resize();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed called");
        if (mediaPlayer != null && mediaPlayer.isPlaying())
            mediaPlayer.pause();

        surfaceIsReady = false;
    }

    @Override
    synchronized public void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "onPrepared called");
        videoIsReady = true;
        tryToPrepare();
    }

    /**
     * Restore the last State before seekTo()
     *
     * @param mp the MediaPlayer that issued the seek operation
     */
    @Override
    public void onSeekComplete(MediaPlayer mp) {
        Log.d(TAG, "onSeekComplete");

        stopLoading();
        if (lastState != null) {
            switch (lastState) {
                case STARTED: {
                    start();
                    break;
                }
                case PLAYBACKCOMPLETED: {
                    currentState = State.PLAYBACKCOMPLETED;
                    break;
                }
                case PREPARED: {
                    currentState = State.PREPARED;
                    break;
                }
            }
        }

        if (this.seekCompleteListener != null)
            this.seekCompleteListener.onSeekComplete(mp);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (this.mediaPlayer != null) {
            if (this.currentState != State.ERROR) {
                Log.d(TAG, "onCompletion");
                if (!this.mediaPlayer.isLooping())
                    this.currentState = State.PLAYBACKCOMPLETED;
                else
                    start();
            }
        }

        if (this.completionListener != null)
            this.completionListener.onCompletion(mp);
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
        Log.d(TAG, "onInfo " + what);

        if (this.infoListener != null)
            return this.infoListener.onInfo(mediaPlayer, what, extra);

        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.d(TAG, "onError called - " + what + " - " + extra);

        stopLoading();
        this.currentState = State.ERROR;

        if (this.errorListener != null)
            return this.errorListener.onError(mp, what, extra);
        return false;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        Log.d(TAG, "onVideoSizeChanged = " + width + " - " + height);

        if (this.initialMovieWidth == 0 && this.initialMovieHeight == 0) {
            initialMovieWidth = width;
            initialMovieHeight = height;
            resize();
        }
    }

    /**
     * Initializes the default configuration
     */
    protected void init() {
        if (isInEditMode())
            return;

        this.shouldAutoplay = false;
        this.currentState = State.IDLE;
        this.fullscreen = false;
        this.initialConfigOrientation = -1;
        this.setBackgroundColor(Color.BLACK);

        initObjects();
    }

    /**
     * Initializes all objects FullscreenVideoView depends on
     */
    protected void initObjects() {
        this.mediaPlayer = new MediaPlayer();

        this.surfaceView = new SurfaceView(context);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        layoutParams.addRule(CENTER_IN_PARENT);
        this.surfaceView.setLayoutParams(layoutParams);
        addView(this.surfaceView);

        this.surfaceHolder = this.surfaceView.getHolder();
        //noinspection deprecation
        this.surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        this.surfaceHolder.addCallback(this);

        // Try not reset loadingView
        if (this.loadingView == null)
            this.loadingView = new ProgressBar(context);

        layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(CENTER_IN_PARENT);
        this.loadingView.setLayoutParams(layoutParams);
        addView(this.loadingView);
    }

    /**
     * Releases all objects FullscreenVideoView depends on
     */
    protected void releaseObjects() {
        if (this.surfaceHolder != null) {
            this.surfaceHolder.removeCallback(this);
            this.surfaceHolder = null;
        }

        if (this.mediaPlayer != null) {
            this.mediaPlayer.release();
            this.mediaPlayer = null;
        }

        if (this.surfaceView != null)
            removeView(this.surfaceView);

        if (this.loadingView != null)
            removeView(this.loadingView);
    }

    /**
     * Calls prepare() method of MediaPlayer
     */
    protected void prepare() throws IllegalStateException {
        startLoading();

        this.videoIsReady = false;
        this.initialMovieHeight = -1;
        this.initialMovieWidth = -1;

        this.mediaPlayer.setOnPreparedListener(this);
        this.mediaPlayer.setOnErrorListener(this);
        this.mediaPlayer.setOnSeekCompleteListener(this);
        this.mediaPlayer.setOnInfoListener(this);
        this.mediaPlayer.setOnVideoSizeChangedListener(this);
        this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        this.currentState = State.PREPARING;
        this.mediaPlayer.prepareAsync();
    }

    /**
     * Try to call state PREPARED
     * Only if SurfaceView is already created and MediaPlayer is prepared
     * Video is loaded and is ok to play.
     */
    protected void tryToPrepare() {
        if (this.surfaceIsReady && this.videoIsReady) {
            if (this.mediaPlayer != null) {
                this.initialMovieWidth = this.mediaPlayer.getVideoWidth();
                this.initialMovieHeight = this.mediaPlayer.getVideoHeight();
            }

            resize();
            stopLoading();
            currentState = State.PREPARED;

            if (shouldAutoplay)
                start();

            if (this.preparedListener != null)
                this.preparedListener.onPrepared(mediaPlayer);
        }
    }

    protected void startLoading() {
        if (this.loadingView != null)
            this.loadingView.setVisibility(View.VISIBLE);
    }

    protected void stopLoading() {
        if (this.loadingView != null)
            this.loadingView.setVisibility(View.GONE);
    }

    /**
     * Get the current {@link FullscreenVideoView.State}.
     *
     * @return Current {@link FullscreenVideoView.State}
     */
    synchronized public State getCurrentState() {
        return currentState;
    }

    /**
     * Returns if VideoView is in fullscreen mode
     *
     * @return true if is in fullscreen mode otherwise false
     * @since 1.1
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * Turn VideoView fulllscreen mode on or off.
     *
     * @param fullscreen true to turn on fullscreen mode or false to turn off
     * @throws RuntimeException In case of mediaPlayer doesn't exist or illegal state exception
     * @since 1.1
     */
    public void setFullscreen(final boolean fullscreen) throws RuntimeException {

        if (mediaPlayer == null)
            throw new RuntimeException("Media Player is not initialized");

        if (this.currentState != State.ERROR) {
            if (FullscreenVideoView.this.fullscreen == fullscreen) return;
            FullscreenVideoView.this.fullscreen = fullscreen;

            final boolean wasPlaying = mediaPlayer.isPlaying();
            if (wasPlaying)
                pause();

            if (FullscreenVideoView.this.fullscreen) {
                if (activity != null)
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

                View rootView = getRootView();
                View v = rootView.findViewById(android.R.id.content);
                ViewParent viewParent = getParent();
                if (viewParent instanceof ViewGroup) {
                    if (parentView == null)
                        parentView = (ViewGroup) viewParent;

                    // Prevents MediaPlayer to became invalidated and released
                    detachedByFullscreen = true;

                    // Saves the last state (LayoutParams) of view to restore after
                    currentLayoutParams = FullscreenVideoView.this.getLayoutParams();

                    parentView.removeView(FullscreenVideoView.this);
                } else
                    Log.e(TAG, "Parent View is not a ViewGroup");

                if (v instanceof ViewGroup) {
                    ((ViewGroup) v).addView(FullscreenVideoView.this);
                } else
                    Log.e(TAG, "RootView is not a ViewGroup");
            } else {
                if (activity != null)
                    activity.setRequestedOrientation(initialConfigOrientation);

                ViewParent viewParent = getParent();
                if (viewParent instanceof ViewGroup) {
                    // Check if parent view is still available
                    boolean parentHasParent = false;
                    if (parentView != null && parentView.getParent() != null) {
                        parentHasParent = true;
                        detachedByFullscreen = true;
                    }

                    ((ViewGroup) viewParent).removeView(FullscreenVideoView.this);
                    if (parentHasParent) {
                        parentView.addView(FullscreenVideoView.this);
                        FullscreenVideoView.this.setLayoutParams(currentLayoutParams);
                    }
                }
            }

            resize();

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (wasPlaying && mediaPlayer != null)
                        start();
                }
            });
        }
    }

    /**
     * Binds an Activity to VideoView. This is necessary to keep tracking on orientation changes
     *
     * @param activity The activity that VideoView is related to
     */
    public void setActivity(Activity activity) {
        this.activity = activity;
        this.initialConfigOrientation = activity.getRequestedOrientation();
    }

    public void resize() {
        if (initialMovieHeight == -1 || initialMovieWidth == -1 || surfaceView == null)
            return;

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {

                View currentParent = (View) getParent();
                if (currentParent != null) {
                    float videoProportion = (float) initialMovieWidth / (float) initialMovieHeight;

                    int screenWidth = currentParent.getWidth();
                    int screenHeight = currentParent.getHeight();
                    float screenProportion = (float) screenWidth / (float) screenHeight;

                    int newWidth, newHeight;
                    if (videoProportion > screenProportion) {
                        newWidth = screenWidth;
                        newHeight = (int) ((float) screenWidth / videoProportion);
                    } else {
                        newWidth = (int) (videoProportion * (float) screenHeight);
                        newHeight = screenHeight;
                    }

                    ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
                    lp.width = newWidth;
                    lp.height = newHeight;
                    surfaceView.setLayoutParams(lp);

                    Log.d(TAG, "Resizing: initialMovieWidth: " + initialMovieWidth + " - initialMovieHeight: " + initialMovieHeight);
                    Log.d(TAG, "Resizing: screenWidth: " + screenWidth + " - screenHeight: " + screenHeight);
                }
            }
        });
    }

    /**
     * Tells if application should autoplay videos as soon as it is prepared
     *
     * @return true if application are going to play videos as soon as it is prepared
     */
    public boolean isShouldAutoplay() {
        return shouldAutoplay;
    }

    /**
     * Tells application that it should begin playing as soon as buffering
     * is ok
     *
     * @param shouldAutoplay If true, call start() method when getCurrentState() == PREPARED. Default is false.
     */
    public void setShouldAutoplay(boolean shouldAutoplay) {
        this.shouldAutoplay = shouldAutoplay;
    }

    /**
     * Toggles view to fullscreen mode
     * It saves currentState and calls pause() method.
     * When fullscreen is finished, it calls the saved currentState before pause()
     * In practice, it only affects STARTED state.
     * If currenteState was STARTED when fullscreen() is called, it calls start() method
     * after fullscreen() has ended.
     *
     * @deprecated As of release 1.1.0, replaced by {@link #setFullscreen(boolean)}
     */
    @Deprecated
    public void fullscreen() throws IllegalStateException {
        setFullscreen(!fullscreen);
    }

    /**
     * MediaPlayer method (getCurrentPosition)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#getCurrentPosition%28%29">getCurrentPosition</a>
     */
    public int getCurrentPosition() {
        if (mediaPlayer != null)
            return mediaPlayer.getCurrentPosition();
        else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (getDuration)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#getDuration%28%29">getDuration</a>
     */
    public int getDuration() {
        if (mediaPlayer != null)
            return mediaPlayer.getDuration();
        else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (getVideoHeight)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#getVideoHeight%28%29">getVideoHeight</a>
     */
    public int getVideoHeight() {
        if (mediaPlayer != null)
            return mediaPlayer.getVideoHeight();
        else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (getVideoWidth)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#getVideoWidth%28%29">getVideoWidth</a>
     */
    public int getVideoWidth() {
        if (mediaPlayer != null)
            return mediaPlayer.getVideoWidth();
        else throw new RuntimeException("Media Player is not initialized");
    }


    /**
     * MediaPlayer method (isLooping)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#isLooping%28%29">isLooping</a>
     */
    public boolean isLooping() {
        if (mediaPlayer != null)
            return mediaPlayer.isLooping();
        else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (isPlaying)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#isPlaying%28%29">isPlaying</a>
     */
    public boolean isPlaying() throws IllegalStateException {
        if (mediaPlayer != null)
            return mediaPlayer.isPlaying();
        else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (pause)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#pause%28%29">pause</a>
     */
    public void pause() throws IllegalStateException {
        Log.d(TAG, "pause");
        if (mediaPlayer != null) {
            currentState = State.PAUSED;
            mediaPlayer.pause();
        } else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * Due to a lack of access of SurfaceView, it rebuilds mediaPlayer and all
     * views to update SurfaceView canvas
     */
    public void reset() {
        Log.d(TAG, "reset");

        if (mediaPlayer != null) {
            this.currentState = State.IDLE;
            releaseObjects();
            initObjects();

        } else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (start)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#start%28%29">start</a>
     */
    public void start() throws IllegalStateException {
        Log.d(TAG, "start");

        if (mediaPlayer != null) {
            currentState = State.STARTED;
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.start();
        } else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (stop)
     *
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#stop%28%29">stop</a>
     */
    public void stop() throws IllegalStateException {
        Log.d(TAG, "stop");

        if (mediaPlayer != null) {
            currentState = State.STOPPED;
            mediaPlayer.stop();
        } else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * MediaPlayer method (seekTo)
     * It calls pause() method before calling MediaPlayer.seekTo()
     *
     * @param msec the offset in milliseconds from the start to seek to
     * @throws IllegalStateException if the internal player engine has not been initialized
     * @see <a href="http://developer.android.com/reference/android/media/MediaPlayer.html#seekTo%28%29">seekTo</a>
     */
    public void seekTo(int msec) throws IllegalStateException {
        Log.d(TAG, "seekTo = " + msec);

        if (mediaPlayer != null) {
            // No live streaming
            if (mediaPlayer.getDuration() > -1 && msec <= mediaPlayer.getDuration()) {
                lastState = currentState;
                pause();
                mediaPlayer.seekTo(msec);

                startLoading();
            }
        } else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnCompletionListener(OnCompletionListener l) {
        if (mediaPlayer != null)
            this.completionListener = l;
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnErrorListener(OnErrorListener l) {
        if (mediaPlayer != null)
            errorListener = l;
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener l) {
        if (mediaPlayer != null)
            mediaPlayer.setOnBufferingUpdateListener(l);
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnInfoListener(OnInfoListener l) {
        if (mediaPlayer != null)
            this.infoListener = l;
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnSeekCompleteListener(OnSeekCompleteListener l) {
        if (mediaPlayer != null)
            this.seekCompleteListener = l;
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener l) {
        if (mediaPlayer != null)
            mediaPlayer.setOnVideoSizeChangedListener(l);
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setOnPreparedListener(OnPreparedListener l) {
        if (mediaPlayer != null)
            this.preparedListener = l;
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setLooping(boolean looping) {
        if (mediaPlayer != null)
            mediaPlayer.setLooping(looping);
        else throw new RuntimeException("Media Player is not initialized");
    }

    public void setVolume(float leftVolume, float rightVolume) {
        if (mediaPlayer != null)
            mediaPlayer.setVolume(leftVolume, rightVolume);
        else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * VideoView method (setVideoPath)
     */
    public void setVideoPath(String path) throws IOException, IllegalStateException, SecurityException, IllegalArgumentException, RuntimeException {
        if (mediaPlayer != null) {
            if (currentState != State.IDLE)
                throw new IllegalStateException("FullscreenVideoView Invalid State: " + currentState);

            mediaPlayer.setDataSource(path);

            currentState = State.INITIALIZED;
            prepare();
        } else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * VideoView method (setVideoURI)
     */
    public void setVideoURI(Uri uri) throws IOException, IllegalStateException, SecurityException, IllegalArgumentException, RuntimeException {
        if (mediaPlayer != null) {
            if (currentState != State.IDLE)
                throw new IllegalStateException("FullscreenVideoView Invalid State: " + currentState);

            mediaPlayer.setDataSource(context, uri);

            currentState = State.INITIALIZED;
            prepare();
        } else throw new RuntimeException("Media Player is not initialized");
    }

    /**
     * Overwrite the default ProgressView to represent loading progress state
     * It is controlled by stopLoading and startLoading methods, that only sets it to VISIBLE and GONE
     * <p>
     * Remember to set RelativeLayout.LayoutParams before setting the view.
     *
     * @param v The custom View that will be used as progress view.
     *          Set it to null to remove the default one
     */
    public void setLoadingView(View v) {
        if (this.loadingView != null)
            removeView(this.loadingView);

        this.loadingView = v;
        if (this.loadingView != null)
            addView(this.loadingView);
    }
}
