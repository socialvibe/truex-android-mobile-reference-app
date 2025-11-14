package com.truex.referenceapp.ads;

import org.json.JSONObject;

/**
 * Represents an individual ad within an ad break.
 *
 * Supports three ad types:
 *
 * 1. TrueX interactive ads (adSystem = "trueX")
 *    - Uses description field containing VAST config URL
 *    - Shows choice card, user opts in, can earn credit to skip entire ad break
 *    - mediaFile is fallback video if user opts out
 *
 * 2. IDVx interactive ads (adSystem = "IDVx")
 *    - Uses adParameters field containing JSON configuration
 *    - Starts automatically, plays inline with other ads
 *    - Never earns credit, always continues to next ad
 *    - mediaFile is placeholder video (paused during interaction)
 *
 * 3. Regular video ads (adSystem = other)
 *    - Uses mediaFile field for video URL
 *    - Standard non-interactive video ad
 */
public class Ad {
    public String adSystem;         // "trueX", "IDVx", or other (e.g., "GDFP")
    public String mediaFile;        // Video URL for ad playback
    public String description;      // VAST config URL for TrueX ads
    public JSONObject adParameters; // JSON configuration for IDVx ads
    public int duration;            // Ad duration in seconds
    public String adId;             // Unique ad identifier
    public AdType adType;           // TRUEX, IDVX, or REGULAR

    public Ad(String adSystem, String mediaFile, String description, JSONObject adParameters, int duration, String adId) {
        this.adSystem = adSystem;
        this.mediaFile = mediaFile;
        this.description = description;
        this.adParameters = adParameters;
        this.duration = duration;
        this.adId = adId;
        this.adType = determineAdType(adSystem);
    }

    private AdType determineAdType(String adSystem) {
        if ("trueX".equals(adSystem)) {
            return AdType.TRUEX;
        } else if ("IDVx".equals(adSystem)) {
            return AdType.IDVX;
        } else {
            return AdType.REGULAR;
        }
    }

    public boolean isInfillionAd() {
        return adType == AdType.TRUEX || adType == AdType.IDVX;
    }

    public boolean isRegularAd() {
        return adType == AdType.REGULAR;
    }

    /**
     * Get the VAST config URL for interactive ads (TrueX/IDVx)
     * For regular ads, returns null
     */
    public String getVastConfigUrl() {
        return description;
    }

    /**
     * Get the video URL for playback
     * This is used for the fallback video or regular ads
     */
    public String getAdUrl() {
        return mediaFile;
    }
}
