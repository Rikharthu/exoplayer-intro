/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
* limitations under the License.
 */
package com.example.exoplayer;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;


/**
 * A fullscreen activity to play audio or video streams.
 */
public class PlayerActivity extends AppCompatActivity {

    // Adaptive playback implies estimating the available network based on measured download speed
    private static final DefaultBandwidthMeter BANDWIDTH_METER =
            new DefaultBandwidthMeter();

    SimpleExoPlayerView mPlayerView;

    private SimpleExoPlayer mPlayer;
    private boolean mPlayWhenReady;
    private int mCurrentWindow;
    private long mPlaybackPosition;
    private ComponentListener mComponentListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mPlayerView = (SimpleExoPlayerView) findViewById(R.id.video_view);
        mComponentListener = new ComponentListener();
    }

    @Override
    protected void onStart() {
        super.onStart();
        /* Starting with API 24 Android supports multiple windows. As our app can be visible but not
         * active in split window mode, we need to initialize the player in onStart */
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        /* Before API 24 we wait as long as possible until we grab resources, so we wait until onResume
         * before initializing the player */
        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        /* Starting with API 24 onStop is guaranteed to be called and in paused mode our activity
         * is eventually still visible */
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        /* Before API 24 there is no guarantee of onStop being called, so we have to release resources
         * ASAP in onPause */
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    /**
     * Start a pure full screen experience
     */
    @SuppressLint("InlinedApi")
    private void hideSystemUi() {
        mPlayerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void initializePlayer() {
        if (mPlayer == null) {
            // A factory to create an AdaptiveVideoTrackSelection
            // Decides which is the appropriate video format for the next loaded chunk
            // gets information from BandwidthMeter (it in turn gets infromation from the MediaSource)
            TrackSelection.Factory adaptiveTrackSelectionFactory =
                    new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);

            mPlayer = ExoPlayerFactory.newSimpleInstance(
                    new DefaultRenderersFactory(this),   // creates renderers for timestamp synchornized rendering
                    new DefaultTrackSelector(adaptiveTrackSelectionFactory),    // selects from available audio, video and text tracks
                    new DefaultLoadControl());                  // manages buffering of the player

            mPlayer.addListener(mComponentListener);
            mPlayer.setVideoDebugListener(mComponentListener);
            mPlayer.setAudioDebugListener(mComponentListener);

            mPlayerView.setPlayer(mPlayer);

            mPlayer.setPlayWhenReady(mPlayWhenReady);
            mPlayer.seekTo(mCurrentWindow, mPlaybackPosition);

            // Create a MediaSource
//            Uri uri = Uri.parse(getString(R.string.media_url_mp3));
//            Uri uri = Uri.parse(getString(R.string.media_url_mp4));
//            MediaSource mediaSource = buildConcatenatingMediaSource(uri);
            Uri uri = Uri.parse(getString(R.string.media_url_dash));
            MediaSource mediaSource = buildAdaptiveMediaSource(uri);
            mPlayer.prepare(mediaSource, true, false);
        }
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            // persist player state
            mPlaybackPosition = mPlayer.getCurrentPosition();
            mCurrentWindow = mPlayer.getCurrentWindowIndex();
            mPlayWhenReady = mPlayer.getPlayWhenReady();
            // release player resources
            mPlayer.removeListener(mComponentListener);
            mPlayer.setVideoDebugListener(null);
            mPlayer.setAudioDebugListener(null);
            mPlayer.release();
            mPlayer = null;
        }
    }

    private MediaSource buildConcatenatingMediaSource(Uri uri) {
        // these are reused for both media sources we create below
        DefaultExtractorsFactory extractorsFactory =
                new DefaultExtractorsFactory();
        DefaultHttpDataSourceFactory dataSourceFactory =
                new DefaultHttpDataSourceFactory("user-agent");

        ExtractorMediaSource videoSource =
                new ExtractorMediaSource(uri, dataSourceFactory,
                        extractorsFactory, null, null);

        Uri audioUri = Uri.parse(getString(R.string.media_url_mp3));
        ExtractorMediaSource audioSource =
                new ExtractorMediaSource(audioUri, dataSourceFactory,
                        extractorsFactory, null, null);

        return new ConcatenatingMediaSource(audioSource, videoSource);
    }

    private MediaSource buildAdaptiveMediaSource(Uri uri) {
        /*
        DASH is a widely used adaptive streaming format. Streaming DASH with ExoPlayer,
        means building an appropriate adaptive MediaSource. To switch our app to DASH we
        build a DashMediaSource by changing our buildMediaSource method as follows:
        */
        DataSource.Factory dataSourceFactory =
                new DefaultHttpDataSourceFactory("ua",
                        BANDWIDTH_METER // makes sure that bandwidth meter is informed about downloaded bytes
                );
        DashChunkSource.Factory dashChunkSourceFactory =
                new DefaultDashChunkSource.Factory(dataSourceFactory);

        return new DashMediaSource(uri, dataSourceFactory,
                dashChunkSourceFactory, null, null);
    }

    private class ComponentListener implements Player.EventListener, VideoRendererEventListener, AudioRendererEventListener {
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onLoadingChanged(boolean isLoading) {

        }

        // called when the player either transitions from one playback state to another or if playback
        // is paused or set to play
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            String stateString;
            switch (playbackState) {
                case Player.STATE_IDLE:
                    // The player has been instantiated but has not being prepared with a MediaSource yet
                    stateString = "ExoPlayer.STATE_IDLE      -";
                    break;
                case Player.STATE_BUFFERING:
                    // The player is not able to immediately play from the current position
                    // because not enough data is buffered
                    stateString = "ExoPlayer.STATE_BUFFERING -";
                    break;
                case Player.STATE_READY:
                    // The player is able to immediately play from the current position
                    stateString = "ExoPlayer.STATE_READY     -";
                    break;
                case Player.STATE_ENDED:
                    // The player has finished playing the media
                    stateString = "ExoPlayer.STATE_ENDED     -";
                    break;
                default:
                    stateString = "UNKNOWN_STATE             -";
                    break;
            }
            Log.d("PlayerActivity", "changed state to " + stateString
                    + " playWhenReady: " + playWhenReady);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {

        }

        @Override
        public void onPositionDiscontinuity() {

        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }

        // --- VideoDebugListener ---
        @Override
        public void onVideoEnabled(DecoderCounters counters) {

        }

        @Override
        public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {

        }

        @Override
        public void onVideoInputFormatChanged(Format format) {

        }

        @Override
        public void onDroppedFrames(int count, long elapsedMs) {

        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

        }

        @Override
        public void onRenderedFirstFrame(Surface surface) {

        }

        @Override
        public void onVideoDisabled(DecoderCounters counters) {

        }

        //--- AudioDebugListener ---
        @Override
        public void onAudioEnabled(DecoderCounters counters) {

        }

        @Override
        public void onAudioSessionId(int audioSessionId) {

        }

        @Override
        public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {

        }

        @Override
        public void onAudioInputFormatChanged(Format format) {

        }

        @Override
        public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {

        }

        @Override
        public void onAudioDisabled(DecoderCounters counters) {

        }
    }
    /*
    Collecting signals of negative impact on Quality of Experience (QoE)
    With all these listeners we are well armed to collect enough signals to monitor the quality of
    experience we are delivering. Here are a couple of listener callbacks which are useful
    for this purpose:

    ExoPlayer.EventListener.onPlaybackStateChanged() is called with STATE_BUFFERING.
    Entering the buffering state happens naturally once at the very beginning and after
    a user requested a seek to a position yet not available (eg. backward seeking).
    All other occurrences of STATE_BUFFERING must be considered harmful for QoE.

    ExoPlayer.EventListener.onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray
    trackSelections) informs about track changes. At least when track selection is
    downgraded to a lower quality we want to know about it.

    ExoPlayer.EventListener.onPlayerError(ExoPlaybackException error) indicates a severe
    negative impact on Quality of Experience.

    ExoPlayer.VideoListener.onFirstFrameRendered delivers the point in time from which on the
    video rendering initially stated. With this you can calculate the so called initial latency
    of playback. This is an important property to measure Quality of Experience.

    VideoRendererEventListener.onDroppedFrames(int count, long elapsedMs) informs a
    bout dropped video frames.

    AudioRendererEventListener.onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs)
    tells you about audio underruns which is a severe negative impact on Quality of Experience.
     */
}
