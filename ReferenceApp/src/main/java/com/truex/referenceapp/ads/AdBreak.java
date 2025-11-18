package com.truex.referenceapp.ads;

import java.util.List;

/**
 * Represents an ad break (preroll, midroll, postroll) with state tracking.
 */
public class AdBreak {
    private String breakId;
    private int timeOffsetMs;
    private List<Ad> ads;

    // State tracking
    private boolean started = false;
    private boolean completed = false;
    private int currentAdIndex = 0;

    public AdBreak(String breakId, int timeOffsetMs, List<Ad> ads) {
        this.breakId = breakId;
        this.timeOffsetMs = timeOffsetMs;
        this.ads = ads;
    }

    public String getBreakId() {
        return breakId;
    }

    public int getTimeOffsetMs() {
        return timeOffsetMs;
    }

    public List<Ad> getAds() {
        return ads;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getCurrentAdIndex() {
        return currentAdIndex;
    }

    /**
     * Get the current ad in the sequence
     */
    public Ad getCurrentAd() {
        if (currentAdIndex < ads.size()) {
            return ads.get(currentAdIndex);
        }
        return null;
    }

    /**
     * Move to the next ad in the sequence and return it
     */
    public Ad getNextAd() {
        currentAdIndex++;
        return getCurrentAd();
    }

    /**
     * Reset the ad break to play from the beginning
     */
    public void reset() {
        started = false;
        completed = false;
        currentAdIndex = 0;
    }
}
