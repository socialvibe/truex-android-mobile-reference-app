package com.truex.referenceapp.player;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.truex.referenceapp.R;
import com.truex.referenceapp.ads.AdBreak;
import com.truex.referenceapp.ads.AdManager;
import com.truex.referenceapp.ads.AdProvider;

@UnstableApi
public class PlayerFragment extends Fragment implements PlaybackStateListener, AdManager.AdBreakListener {
    private static final String CLASSTAG = "PlayerFragment";
    private static final String CONTENT_STREAM_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4";

    // This player view is used to display a fake stream that mimics actual video content
    private PlayerView playerView;
    private ExoPlayer player;

    // The data-source factory is used to build media-sources
    private DataSource.Factory dataSourceFactory;

    // Preloaded content source for faster startup
    private MediaSource preloadedContentSource;

    // Ad pod management
    private AdManager adManager;
    private AdProvider adProvider;

    // Position tracking for midroll detection
    private long lastCheckedPositionMs = -1;
    private android.os.Handler positionCheckHandler;
    private Runnable positionCheckRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Force playback in landscape.
        Activity activity = getActivity();
        if (activity != null) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onDetach() {
        // Restore portrait orientation for normal usage.
        Activity activity = getActivity();
        if (activity != null) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
        super.onDetach();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(CLASSTAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);

        setupExoPlayer();
        setupDataSourceFactory();
        setupAdProvider();
        setupAdManager();
        preloadContentStream();
        displayContentStream();
    }

    @Override
    public void onResume() {
        Log.d(CLASSTAG, "onResume");
        super.onResume();

        // Forward to ad manager for any active ads
        if (adManager != null) {
            adManager.onResume();
        }

        // Resume video playback (but not during interactive ads)
        if (player != null && (adManager == null || !adManager.isPlayingInteractiveAd())) {
            player.setPlayWhenReady(true);
            // Restart position checking for content playback
            startPositionChecking();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop position checking when paused
        stopPositionChecking();

        // Forward to ad manager for any active ads
        if (adManager != null) {
            adManager.onPause();
        }

        // Pause video playback (but not during interactive ads)
        if (player != null && (adManager == null || !adManager.isPlayingInteractiveAd())) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop position checking
        stopPositionChecking();

        // Forward to ad manager for cleanup
        if (adManager != null) {
            adManager.onStop();
        }

        // Release the video player
        closeVideoPlayer();
    }

    private void closeVideoPlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    /**
     * Called when the player starts displaying the fake content stream
     * Check for preroll ad break
     */
    public void onPlayerDidStart() {
        Log.d(CLASSTAG, "onPlayerDidStart");

        // Check for preroll (timeOffset <= 0)
        AdBreak prerollBreak = adManager.getAdBreakAt(0);
        if (prerollBreak != null && !prerollBreak.isStarted()) {
            Log.d(CLASSTAG, "Preroll detected, starting ad break");
            adManager.setCurrentAdBreak(prerollBreak);
            adManager.startAdBreak();
        }
    }

    /**
     * Called when the media stream is resumed
     */
    public void onPlayerDidResume() {
        Log.d(CLASSTAG, "onPlayerDidResume");
    }

    /**
     * Called when the media stream is paused
     */
    public void onPlayerDidPause() {
        Log.d(CLASSTAG, "onPlayerDidPause");
    }

    /**
     * Called when the media stream is complete
     */
    public void onPlayerDidComplete() {
        Log.d(CLASSTAG, "onPlayerDidComplete");
    }

    private void preloadContentStream() {
        // Create and prepare content source in background
        Uri uri = Uri.parse(CONTENT_STREAM_URL);
        preloadedContentSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
    }

    private void displayContentStream() {
        Log.d(CLASSTAG, "displayContentStream");
        if (player == null || preloadedContentSource == null) return;

        // Restore player view visibility
        playerView.setVisibility(View.VISIBLE);

        // Re-enable player controls for content playback
        playerView.setUseController(true);

        // Use preloaded content source for faster startup
        player.setPlayWhenReady(true);
        player.setMediaSource(preloadedContentSource);
        player.prepare();

        // Add position tracking for midroll detection
        addPositionTrackingListener();
        startPositionChecking();
    }

    /**
     * Add a listener to track playback position and detect ad breaks
     */
    private void addPositionTrackingListener() {
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    checkForAdBreak();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    checkForAdBreak();
                }
            }
        });
    }

    /**
     * Check if current playback position matches an ad break timeOffset
     */
    private void checkForAdBreak() {
        if (player == null || adManager == null) {
            return;
        }

        long currentPositionMs = player.getCurrentPosition();

        // Only check if position has changed by at least 1 second
        if (Math.abs(currentPositionMs - lastCheckedPositionMs) < 1000) {
            return;
        }

        lastCheckedPositionMs = currentPositionMs;

        // Check if we've hit an ad break timeOffset
        AdBreak adBreak = adManager.getAdBreakAt(currentPositionMs);

        if (adBreak != null && !adBreak.isStarted()) {
            Log.d(CLASSTAG, "Ad break detected at " + currentPositionMs + "ms: " + adBreak.getBreakId());
            // Stop position checking during ad break
            stopPositionChecking();
            // Pause content and start ad break
            if (player != null) {
                player.pause();
            }
            adManager.setCurrentAdBreak(adBreak);
            adManager.startAdBreak();
        }
    }

    private void setupExoPlayer() {
        if (getContext() == null) return;

        player = new ExoPlayer.Builder(requireContext()).build();

        if (getView() != null) {
            playerView = getView().findViewById(R.id.player_view);
            playerView.setPlayer(player);
        }

        // Listen for player events so that we can load the true[X] ad manager when the video stream starts
        player.addListener(new PlayerEventListener(this));
    }

    private void setupDataSourceFactory() {
        if (getContext() == null) return;

        String applicationName = requireContext().getApplicationInfo()
            .loadLabel(requireContext().getPackageManager()).toString();
        String userAgent = Util.getUserAgent(getContext(), applicationName);
        dataSourceFactory = new DefaultDataSource.Factory(
            requireContext(),
            new DefaultHttpDataSource.Factory().setUserAgent(userAgent)
        );
    }

    private void setupAdProvider() {
        if (getContext() == null) return;

        adProvider = new AdProvider(getContext(), R.raw.adbreaks_stub);
    }

    private void setupAdManager() {
        if (getContext() == null || getView() == null) return;

        ViewGroup adViewGroup = (ViewGroup) getView().findViewById(R.id.player_layout);
        adManager = new AdManager(getContext(), this, adViewGroup, dataSourceFactory);

        // Set the ad playlist from VMAP data
        adManager.setAdPlaylist(adProvider.getAllAdBreaks());
    }

    // AdManager.AdBreakListener implementation

    @Override
    public void playMediaSource(MediaSource mediaSource) {
        Log.d(CLASSTAG, "playMediaSource");
        if (player == null) return;

        // Disable player controls during ad playback
        playerView.setUseController(false);

        // Play the media source
        player.setPlayWhenReady(true);
        player.setMediaSource(mediaSource);
        player.prepare();
        playerView.setVisibility(View.VISIBLE);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState != Player.STATE_ENDED) {
                    return;
                }

                player.removeListener(this);
                adManager.onPlaybackEnded();
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                @NonNull Player.PositionInfo newPosition,
                                                int reason) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    adManager.onMediaItemCompleted();
                }
            }
        });
    }

    @Override
    public void controlPlayer(AdManager.PlayerAction action, long seekPositionMs) {
        Log.d(CLASSTAG, "controlPlayer: " + action + ", seekPosition: " + seekPositionMs);
        if (player == null) return;

        switch (action) {
            case PLAY:
                playerView.hideController();
                player.setPlayWhenReady(true);
                playerView.setVisibility(View.VISIBLE);
                break;
            case SEEK_AND_PAUSE:
                playerView.setVisibility(View.INVISIBLE);
                player.seekTo(seekPositionMs);
                player.setPlayWhenReady(false);
                break;
        }
    }

    @Override
    public void onSkipToContent() {
        Log.d(CLASSTAG, "onSkipToContent");
        // When credit is earned, we need to fully switch back to content stream
        // not just resume the ad player, otherwise ExoPlayer will continue with next ad
        displayContentStream();
    }

    @Override
    public void onAdBreakComplete() {
        Log.d(CLASSTAG, "onAdBreakComplete");
        // Ad break completed normally, display content stream
        displayContentStream();
    }

    /**
     * Start periodic position checking
     */
    private void startPositionChecking() {
        stopPositionChecking(); // Stop any existing checker first

        positionCheckHandler = new android.os.Handler();
        positionCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkForAdBreak();
                // Check every 500ms during content playback
                if (positionCheckHandler != null) {
                    positionCheckHandler.postDelayed(this, 500);
                }
            }
        };
        positionCheckHandler.post(positionCheckRunnable);
    }

    /**
     * Stop periodic position checking
     */
    private void stopPositionChecking() {
        if (positionCheckHandler != null && positionCheckRunnable != null) {
            positionCheckHandler.removeCallbacks(positionCheckRunnable);
            positionCheckRunnable = null;
            positionCheckHandler = null;
        }
    }
}
