package com.truex.referenceapp.player;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.truex.referenceapp.R;
import com.truex.referenceapp.ads.TruexAdManager;

public class PlayerFragment extends Fragment implements PlaybackHandler, PlaybackStateListener {
    private static final String CLASSTAG = "PlayerFragment";
    private static final String CONTENT_STREAM_URL = "https://media.truex.com/file_assets/2019-01-30/4ece0ae6-4e93-43a1-a873-936ccd3c7ede.mp4";
    private static final String AD_URL_ONE = "https://media.truex.com/file_assets/2019-01-30/eb27eae5-c9da-4a9b-9420-a83c986baa0b.mp4";
    private static final String AD_URL_TWO = "https://media.truex.com/file_assets/2019-01-30/7fe9da33-6b9e-446d-816d-e1aec51a3173.mp4";
    private static final String AD_URL_THREE = "https://media.truex.com/file_assets/2019-01-30/742eb926-6ec0-48b4-b1e6-093cee334dd1.mp4";

    // This player view is used to display a fake stream that mimics actual video content
    private PlayerView playerView;

    private Boolean isPaused;

    // The data-source factory is used to build media-sources
    private DataSource.Factory dataSourceFactory;

    // We need to hold onto the ad manager so that the ad manager can listen for lifecycle events
    private TruexAdManager truexAdManager;

    // We need to identify whether or not the user is viewing ads or the content stream
    private DisplayMode displayMode;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(CLASSTAG, "onViewCreated");

        super.onViewCreated(view, savedInstanceState);

        // Set-up the video content player
        setupExoPlayer();

        // Set-up the data-source factory
        setupDataSourceFactory();

        // Start the content stream
        displayContentStream();
    }

    @Override
    public void onResume() {
        Log.d(CLASSTAG, "onResume");

        super.onResume();
        this.isPaused = false;

        // We need to inform the true[X] ad manager that the application has resumed
        if (truexAdManager != null) {
            truexAdManager.onResume();
        }

        // Resume video playback
        if (playerView.getPlayer() != null && displayMode != DisplayMode.INTERACTIVE_AD) {
            playerView.getPlayer().setPlayWhenReady(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        this.isPaused = true;

        // We need to inform the true[X] ad manager that the application has paused
        if (truexAdManager != null) {
            truexAdManager.onPause();
        }

        // Pause video playback
        if (playerView.getPlayer() != null && displayMode != DisplayMode.INTERACTIVE_AD) {
            playerView.getPlayer().setPlayWhenReady(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (truexAdManager != null) {
            truexAdManager.onDestroy();
        }

        // Release the video player
        closeStream();
    }

    /**
     * Called when the player starts displaying the fake content stream
     * Display the true[X] engagement
     */
    public void onPlayerDidStart() {
        displayInteractiveAd();
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
        if (displayMode == DisplayMode.LINEAR_ADS) {
            displayContentStream();
        }
    }

    /**
     * This method resumes and displays the content stream
     * Note: We call this method whenever a true[X] engagement is completed
     */
    public void resumeStream() {
        Log.d(CLASSTAG, "resumeStream");
        if (playerView.getPlayer() == null) {
            return;
        }
        playerView.setVisibility(View.VISIBLE);

        if (!this.isPaused) {
            playerView.getPlayer().setPlayWhenReady(true);
        }
    }

    /**
     * This method pauses and hides the fake content stream
     * Note: We call this method whenever a true[X] engagement is completed
     */
    public void pauseStream() {
        Log.d(CLASSTAG, "pauseStream");
        if (playerView.getPlayer() == null) {
            return;
        }
        playerView.getPlayer().setPlayWhenReady(false);
        playerView.setVisibility(View.GONE);
    }

    /**
     * This method closes the stream and then returns to the tag selection view
     */
    public void cancelStream() {
        // Close the stream
        closeStream();

        // Return to the previous fragment
        if (getFragmentManager() != null && getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        }
    }

    /**
     * This method cancels the content stream and begins playing a linear ad
     * Note: We call this method whenever the user cancels an engagement without receiving credit
     */
    public void displayLinearAds() {
        Log.d(CLASSTAG, "displayLinearAds");
        if (playerView.getPlayer() == null) {
            return;
        }

        displayMode = DisplayMode.LINEAR_ADS;

        MediaSource[] ads = new MediaSource[3];

        String[] adUrls = {
                AD_URL_ONE, AD_URL_TWO, AD_URL_THREE
        };

        for(int i = 0; i < ads.length; i++) {
            Uri uri = Uri.parse(adUrls[i]);
            MediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            ads[i] = source;
        }

        MediaSource adPod = new ConcatenatingMediaSource(ads);
        ((SimpleExoPlayer)playerView.getPlayer()).prepare(adPod);
        playerView.getPlayer().setPlayWhenReady(true);
        playerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void handlePopup(String url) {
        Log.i(CLASSTAG, "handlePopup: " + url);

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

    private void displayInteractiveAd() {
        Log.d(CLASSTAG, "displayInteractiveAds");
        if (playerView.getPlayer() == null) {
            return;
        }

        // Pause the stream and display a true[X] engagement
        pauseStream();

        displayMode = DisplayMode.INTERACTIVE_AD;

        // Start the true[X] engagement
        ViewGroup viewGroup = (ViewGroup) getView();
        truexAdManager = new TruexAdManager(getContext(), this);
        truexAdManager.startAd(viewGroup);
    }

    private void displayContentStream() {
        Log.d(CLASSTAG, "displayContentStream");
        if (playerView.getPlayer() == null) {
            return;
        }

        displayMode = DisplayMode.CONTENT_STREAM;

        Uri uri = Uri.parse(CONTENT_STREAM_URL);
        MediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
        ((SimpleExoPlayer)playerView.getPlayer()).prepare(source);
        playerView.getPlayer().setPlayWhenReady(true);
    }

    private void setupExoPlayer() {
        TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(requireContext(), trackSelector);

        if (getView() != null) {
            playerView = getView().findViewById(R.id.player_view);
            playerView.setPlayer(player);
        }

        // Listen for player events so that we can load the true[X] ad manager when the video stream starts
        player.addListener(new PlayerEventListener(this, this));
    }

    private void setupDataSourceFactory() {
        String applicationName = requireContext().getApplicationInfo().loadLabel(requireContext().getPackageManager()).toString();
        String userAgent = Util.getUserAgent(getContext(), applicationName);
        dataSourceFactory = new DefaultDataSourceFactory(requireContext(), userAgent, null);
    }

    /**
     * This method cancels the content stream and releases the video content player
     * Note: We call this method when the application is stopped
     */
    public void closeStream() {
        Log.d(CLASSTAG, "cancelStream");
        if (playerView.getPlayer() == null) {
            return;
        }
        Player player = playerView.getPlayer();
        playerView.setPlayer(null);
        player.release();
    }
}