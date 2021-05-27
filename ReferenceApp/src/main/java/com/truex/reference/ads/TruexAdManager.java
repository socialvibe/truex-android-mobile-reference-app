package com.truex.reference.ads;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import com.truex.adrenderer.IEventHandler;
import com.truex.adrenderer.TruexAdRenderer;
import com.truex.reference.player.PlaybackHandler;

import org.json.JSONObject;

import java.util.Map;

/**
 * This class holds a reference to the true[X] ad renderer and handles all of the event handling
 * for the example integration application. This class interacts with the video player by resuming
 * the content when the engagement is complete.
 */
public class TruexAdManager {
    private static final String CLASSTAG = TruexAdManager.class.getSimpleName();

    private PlaybackHandler playbackHandler;
    private boolean didReceiveCredit;
    private TruexAdRenderer truexAdRenderer;

    public TruexAdManager(Context context, PlaybackHandler playbackHandler) {
        this.playbackHandler = playbackHandler;
        didReceiveCredit = false;

        // Set-up the true[X] ad renderer
        truexAdRenderer = new TruexAdRenderer(context);

        truexAdRenderer.addEventListener("AD_STARTED", this.adStarted);
        truexAdRenderer.addEventListener("AD_COMPLETED", this.adCompleted);
        truexAdRenderer.addEventListener("AD_ERROR", this.adError);
        truexAdRenderer.addEventListener("NO_ADS_AVAILABLE", this.noAds);
        truexAdRenderer.addEventListener("AD_FREE_POD", this.adFree);
        truexAdRenderer.addEventListener("POPUP_WEBSITE", this.popup);
    }

    /**
     * Start displaying the true[X] engagement
     * @param viewGroup - the view group in which you would like to display the true[X] engagement
     */
    public void startAd(ViewGroup viewGroup) {
        String vastConfigUrl = "https://qa-get.truex.com/f7e02f55ada3e9d2e7e7f22158ce135f9fba6317/vast/config?dimension_2=1&stream_position=midroll";
        JSONObject options = new JSONObject();
        try {
            options.put("vast_config_url", vastConfigUrl);
            options.put("placement_hash", "temporary_hash");
        } catch (Exception e) {}

        String slotType = "PREROLL";

        truexAdRenderer.init(options, slotType);
        truexAdRenderer.start(viewGroup);
    }

    /**
     * Inform the true[X] ad renderer that the application has resumed
     */
    public void onResume() {
        truexAdRenderer.resume();
    }

    /**
     * Inform the true[X] ad renderer that the application has paused
     */
    public void onPause() {
        truexAdRenderer.pause();
    }

    /**
     * Inform that the true[X] ad renderer that the application has stopped
     */
    public void onStop() {
        truexAdRenderer.stop();
    }

    /**
     * This method should be called once the true[X] ad manager is done
     */
    private void onCompletion() {
        if (didReceiveCredit) {
            // The user received true[ATTENTION] credit
            // Resume the content stream (and skip any linear ads)
            playbackHandler.resumeStream();
        } else {
            // The user did not receive credit
            // Continue the content stream and display linear ads
            playbackHandler.displayLinearAds();
        }
    }


    private IEventHandler adStarted = (Map<String, ?> data) -> {
        Log.e(CLASSTAG, "adStarted");
    };

    private IEventHandler adCompleted = (Map<String, ?> data) -> {
        Log.e(CLASSTAG, "adCompleted");
        onCompletion();
    };

    private IEventHandler adError = (Map<String, ?> data) -> {
        Log.e(CLASSTAG, "adError");

        // Error
        onCompletion();
    };

    private IEventHandler noAds = (Map<String, ?> data) -> {
        Log.e(CLASSTAG, "noAds");

        // There are no engagements available
        onCompletion();
    };

    private IEventHandler adFree = (Map<String, ?> data) -> {
        Log.e(CLASSTAG, "adFree");
        didReceiveCredit = true;
    };

    private IEventHandler popup = (Map<String, ?> data) -> {
        Log.e(CLASSTAG, "popup");
    };
}
