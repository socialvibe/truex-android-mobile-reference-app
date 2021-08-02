package com.truex.referenceapp.player;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import com.truex.referenceapp.ads.AdBreak;
import com.truex.referenceapp.ads.TruexAdManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PlayerFragment extends Fragment implements PlaybackHandler, PlaybackStateListener {
    private static final String CLASSTAG = "PlayerFragment";
    private static final String CONTENT_STREAM_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4";

    // This player view is used to display a fake stream that mimics actual video content
    private PlayerView playerView;

    private Boolean isPaused;

    // The data-source factory is used to build media-sources
    private DataSource.Factory dataSourceFactory;

    // We need to hold onto the ad manager so that the ad manager can listen for lifecycle events
    private TruexAdManager truexAdManager;

    // We need to identify whether or not the user is viewing ads or the content stream
    private DisplayMode displayMode;

    private List<AdBreak> adBreaks;
    private AdBreak currentAdBreak;

    // Timer/midroll properties
    private Handler progressHandler = new Handler();
    private Runnable checkForAds = null;
    private long resumePosition = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(CLASSTAG, "onViewCreated");

        super.onViewCreated(view, savedInstanceState);

        // Simulates the parsed result of making a service call to some ad provider and
        // getting useful information
        adBreaks = getAdPayload(R.raw.adbreaks_stub);

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

        cleanupProgressMonitor();
        closeStream();
    }

    /**
     * Called when the player starts displaying the fake content stream
     * Display the true[X] engagement
     */
    public void onPlayerDidStart() {
        playCurrentAds();
        startProgressMonitor();
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
            clearCurrentAdBreak();
        }
    }

    private void clearCurrentAdBreak() {
        if (this.currentAdBreak != null) {
            this.currentAdBreak.viewed = true;
            this.currentAdBreak = null;
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
        displayMode = DisplayMode.CONTENT_STREAM;

        clearCurrentAdBreak();
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

        List<String> adUrls = this.currentAdBreak.adUrls;
        List<String> playableAds = new ArrayList<>();
        for (int i = 0; i < adUrls.size(); i++) {
            String url = adUrls.get(i);
            if (!isTruexAdUrl(url)) {
                playableAds.add(url);
            }
        }

        for(int i = 0; i < playableAds.size(); i++) {
            Uri uri = Uri.parse(playableAds.get(i));
            MediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            ads[i] = source;
        }

        MediaSource adPod = new ConcatenatingMediaSource(ads);
        SimpleExoPlayer player = getPlayer();
        player.prepare(adPod);
        player.setPlayWhenReady(true);
        playerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void handlePopup(String url) {
        Log.i(CLASSTAG, "handlePopup: " + url);

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

    // [1] - Integration Doc/Notes
    private void playCurrentAds() {
        if (adBreaks == null || adBreaks.size() == 0) return;

        long position = getContentPosition();
        for (int i = 0; i < adBreaks.size(); i++) {
            AdBreak adBreak = adBreaks.get(i);
            if (position >= adBreak.timeOffsetMs && !adBreak.viewed) {
                this.currentAdBreak = adBreak;
                String firstAd = adBreak.getFirstAd();
                if (isTruexAdUrl(firstAd)) {
                    displayInteractiveAd(firstAd);
                } else {
                    displayLinearAds();
                }
                return;
            }
        }
    }

    // [2] - Integration Doc/Notes
    private void displayInteractiveAd(String vastUrl) {
        Log.d(CLASSTAG, "displayInteractiveAds");
        if (playerView.getPlayer() == null) {
            return;
        }

        // Pause the stream and display a true[X] engagement
        pauseStream();
        Long position = getContentPosition();
        if (position > 0) resumePosition = position;

        displayMode = DisplayMode.INTERACTIVE_AD;

        // Start the true[X] engagement
        ViewGroup viewGroup = (ViewGroup) getView();
        truexAdManager = new TruexAdManager(getContext(), this);
        truexAdManager.startAd(viewGroup, vastUrl);
    }

    private void displayContentStream() {
        Log.d(CLASSTAG, "displayContentStream");
        if (playerView.getPlayer() == null) {
            return;
        }

        displayMode = DisplayMode.CONTENT_STREAM;

        Uri uri = Uri.parse(CONTENT_STREAM_URL);
        MediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);

        SimpleExoPlayer player = getPlayer();
        player.prepare(source);
        if (resumePosition > 0) player.seekTo(resumePosition);
        player.setPlayWhenReady(true);
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

    // Simple way to track the player position to simulate a midroll experience
    private void startProgressMonitor() {
        if (checkForAds == null) {
            checkForAds = new Runnable() {
                @Override
                public void run() {
                    if (PlayerFragment.this.currentAdBreak == null && getContentPosition() > 0) playCurrentAds(); // Play any ads if available
                    if (progressHandler != null) {
                        progressHandler.postDelayed(checkForAds, 1000);
                    }
                }
            };
        }

        checkForAds.run();
    }

    private void cleanupProgressMonitor() {
        if (progressHandler != null) {
            if (checkForAds != null) progressHandler.removeCallbacks(checkForAds);
            progressHandler = null;
        }
    }

    private SimpleExoPlayer getPlayer() {
        return playerView != null ? (SimpleExoPlayer) playerView.getPlayer() : null;
    }

    private long getContentPosition() {
        SimpleExoPlayer player = getPlayer();
        return player != null ? player.getContentPosition() : 0;
    }

    private List<AdBreak> getAdPayload(Integer resourceId) {
        String rawFile = getRawFileContents(resourceId);
        try {
            JSONObject rawJson = new JSONObject(rawFile);
            JSONArray adBreaks = rawJson.getJSONArray("adBreaks");
            List<AdBreak> result = new ArrayList<>();

            for (int i = 0; i < adBreaks.length(); i++) {
                AdBreak adBreak = new AdBreak();
                adBreak.parseJson(adBreaks.getJSONObject(i));
                result.add(adBreak);
            }

            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getRawFileContents(int resourceId) {
        InputStream vastContentStream = getContext().getResources().openRawResource(resourceId);

        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(vastContentStream));

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return stringBuilder.toString();
    }

    private Boolean isTruexAdUrl(String url) {
        return url.contains("get.truex.com");
    }
}
