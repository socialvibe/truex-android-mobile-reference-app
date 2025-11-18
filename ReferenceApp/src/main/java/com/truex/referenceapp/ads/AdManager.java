package com.truex.referenceapp.ads;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.MediaSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages ad breaks using Client-Side Ad Insertion.
 */
public class AdManager {
    private static final String CLASSTAG = AdManager.class.getSimpleName();

    // Ad playlist with multiple ad breaks
    private List<AdBreak> adPlaylist;
    private AdBreak currentAdBreak;

    // Legacy fields for current ad break playback
    private List<Ad> ads;
    private MediaSource mediaSource;
    private int currentAdIndex;
    private AdBreakListener listener;
    private Context context;
    private InfillionAdManager infillionAdManager;
    private ViewGroup adViewGroup;
    private DataSource.Factory dataSourceFactory;
    private android.os.Handler failsafeHandler;
    private Runnable failsafeRunnable;

    // Time tolerance for ad break detection (±1 second)
    private static final long TIME_TOLERANCE_MS = 1000;

    public enum PlayerAction {
        PLAY,
        SEEK_AND_PAUSE
    }

    public interface AdBreakListener {
        void playMediaSource(MediaSource mediaSource);
        void controlPlayer(PlayerAction action, long seekPositionMs);
        void onAdBreakComplete();
        void onSkipToContent();
    }

    public AdManager(Context context, AdBreakListener listener, ViewGroup adViewGroup, DataSource.Factory dataSourceFactory) {
        this.context = context;
        this.listener = listener;
        this.adViewGroup = adViewGroup;
        this.dataSourceFactory = dataSourceFactory;
        this.ads = new ArrayList<>();
        this.currentAdIndex = 0;
        this.failsafeHandler = new android.os.Handler();
        this.adPlaylist = new ArrayList<>();
        this.currentAdBreak = null;
    }

    // Lifecycle methods to forward to InfillionAdManager
    public void onResume() {
        if (infillionAdManager != null) {
            infillionAdManager.onResume();
        }
    }

    public void onPause() {
        if (infillionAdManager != null) {
            infillionAdManager.onPause();
        }
    }

    public void onStop() {
        if (infillionAdManager != null) {
            infillionAdManager.onStop();
        }
        // Clean up when stopping to prevent memory leaks
        cleanupInfillionAdManager();
    }

    // Set the ad playlist from VMAP data
    public void setAdPlaylist(List<AdBreak> adBreaks) {
        Log.d(CLASSTAG, "setAdPlaylist: " + adBreaks.size() + " ad breaks");
        this.adPlaylist = new ArrayList<>(adBreaks);
    }

    /**
     * Get the ad break at the given playback time
     * Returns the ad break if within TIME_TOLERANCE_MS of its timeOffset and not completed
     */
    public AdBreak getAdBreakAt(long currentTimeMs) {
        for (AdBreak adBreak : adPlaylist) {
            long timeDiff = Math.abs(adBreak.getTimeOffsetMs() - currentTimeMs);
            if (timeDiff <= TIME_TOLERANCE_MS && !adBreak.isCompleted()) {
                return adBreak;
            }
        }
        return null;
    }

    /**
     * Set the current ad break to play
     */
    public void setCurrentAdBreak(AdBreak adBreak) {
        Log.d(CLASSTAG, "setCurrentAdBreak: " + adBreak.getBreakId());
        // Clean up any existing InfillionAdManager before setting new ad break
        cleanupInfillionAdManager();

        this.currentAdBreak = adBreak;
        this.ads = adBreak.getAds();
        this.currentAdIndex = 0;
        this.mediaSource = createMediaSource(ads);
    }

    public void startAdBreak() {
        Log.d(CLASSTAG, "startAdBreak");
        // Clean up any existing InfillionAdManager before starting new ad pod
        cleanupInfillionAdManager();

        // Mark the current ad break as started
        if (currentAdBreak != null) {
            currentAdBreak.setStarted(true);
            Log.d(CLASSTAG, "Ad break started: " + currentAdBreak.getBreakId());
        }

        currentAdIndex = 0;

        listener.playMediaSource(mediaSource);
        launchInfillionOverlayIfNecessary();
    }

    public boolean isPlayingInteractiveAd() {
        Ad currentAd = getCurrentAd();
        return currentAd != null && currentAd.isInfillionAd();
    }

    // You should call this from outside when a concatenated
    // segment finishes playing
    public void onPlaybackEnded() {
        Log.d(CLASSTAG, "onPlaybackEnded");

        // Mark the current ad break as completed
        if (currentAdBreak != null) {
            currentAdBreak.setCompleted(true);
            Log.d(CLASSTAG, "Ad break completed: " + currentAdBreak.getBreakId());
        }

        listener.onAdBreakComplete();
    }

    // You should call this from outside when the player
    // transitions to a new ad in a concatenated segment
    public void onMediaItemCompleted() {
        Log.d(CLASSTAG, "onMediaItemCompleted");
        Ad currentAd = getCurrentAd();
        if (currentAd == null) {
            return;
        }

        moveToNextAd();
    }

    private Ad getCurrentAd() {
        if (currentAdIndex < ads.size()) {
            return ads.get(currentAdIndex);
        }
        return null;
    }

    // Note that this method does not drive the ads - it only:
    //  1. advances the internal state to reflect the progress made by the player
    //  2. shows the Infillion overlay if an Infillion ad is playing
    private void moveToNextAd() {
        // Move to next ad in concatenated segment
        currentAdIndex++;
        if (currentAdIndex >= ads.size()) {
            listener.onAdBreakComplete();
        }
        else {
            // show the renderer if the new ad is Infillion
            launchInfillionOverlayIfNecessary();
        }
    }

    private void showInfillionRenderer(Ad adItem) {
        Log.d(CLASSTAG, "showInfillionRenderer - adId: " + adItem.adId + ", type: " + adItem.adType);
        if (adViewGroup == null) {
            onInfillionAdComplete(false);
            return;
        }

        // Clean up any existing InfillionAdManager before creating a new one
        cleanupInfillionAdManager();

        InfillionAdManager.CompletionCallback callback = new InfillionAdManager.CompletionCallback() {
            @Override
            public void onAdComplete(boolean receivedCredit) {
                onInfillionAdComplete(receivedCredit);
            }

            @Override
            public void onPopup(String url) {
                // Handle popup through listener if needed
                Log.d(CLASSTAG, "Popup requested: " + url);
            }
        };

        infillionAdManager = new InfillionAdManager(context, callback);
        infillionAdManager.startAd(adViewGroup, adItem.getVastConfigUrl(), adItem.adParameters, adItem.adType);

        // Start failsafe timer for IDVx ads (2x duration)
        if (adItem.isInfillionAd()) {
            startFailsafeTimer(adItem);
        }
    }

    private void cleanupInfillionAdManager() {
        if (infillionAdManager != null) {
            infillionAdManager.destroy();
            infillionAdManager = null;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private MediaSource createMediaSource(List<Ad> ads) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder()
            .useDefaultMediaSourceFactory(context);

        for (Ad ad : ads) {
            MediaItem mediaItem = MediaItem.fromUri(ad.getAdUrl());
            // Add with placeholder duration to handle loading times
            builder.add(mediaItem, ad.duration * 1000L);
        }

        return builder.build();
    }

    private void launchInfillionOverlayIfNecessary() {
        Ad currentAd = getCurrentAd();
        if (currentAd == null || !currentAd.isInfillionAd()) {
            return;
        }

        // Seek to just before the end of the IDVx placeholder video and pause
        long endPosition = calculateEndPositionOfCurrentAd();
        listener.controlPlayer(PlayerAction.SEEK_AND_PAUSE, endPosition - 100);
        showInfillionRenderer(currentAd);
    }

    private void onInfillionAdComplete(boolean receivedCredit) {
        Log.d(CLASSTAG, "onInfillionAdComplete - receivedCredit: " + receivedCredit);
        // Cancel failsafe timer if running
        cancelFailsafeTimer();

        // Clean up the completed InfillionAdManager
        cleanupInfillionAdManager();

        if (receivedCredit) {
            // TrueX ads only: User earned credit by completing the interactive experience
            // Skip all remaining ads in the pod and return immediately to content
            // Mark the current ad break as completed
            if (currentAdBreak != null) {
                currentAdBreak.setCompleted(true);
                Log.d(CLASSTAG, "Ad break skipped (credit earned): " + currentAdBreak.getBreakId());
            }
            listener.onSkipToContent();
        }
        else {
            // Two cases reach here:
            // 1. TrueX ad: User opted out without earning credit -> play remaining ads
            // 2. IDVx ad: Normal completion (IDVx never earns credit) -> play next ad in sequence
            // Resume playback - the player will trigger moveToNextAd()
            // and this will finalize the transition to the next ad.
            listener.controlPlayer(PlayerAction.PLAY, 0);
        }
    }

    private long calculateEndPositionOfCurrentAd() {
        // Calculate position where current ad ends in concatenated timeline
        long positionMs = 0;
        for (int i = 0; i <= currentAdIndex; i++) {
            if (i < ads.size()) {
                positionMs += ads.get(i).duration * 1000L;
            }
        }

        return positionMs;
    }

    private void startFailsafeTimer(Ad idvxAd) {
        // Create failsafe timer for 2x the ad duration
        long failsafeTimeoutMs = idvxAd.duration * 2000L;

        failsafeRunnable = () -> {
            // Force completion without credit
            onInfillionAdComplete(false);
        };

        failsafeHandler.postDelayed(failsafeRunnable, failsafeTimeoutMs);
    }

    private void cancelFailsafeTimer() {
        if (failsafeHandler != null && failsafeRunnable != null) {
            failsafeHandler.removeCallbacks(failsafeRunnable);
            failsafeRunnable = null;
        }
    }
}
