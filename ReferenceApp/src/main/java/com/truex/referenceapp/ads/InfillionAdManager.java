package com.truex.referenceapp.ads;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import com.truex.adrenderer.IEventEmitter;
import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;

import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;

/**
 * Manages the TruexAdRenderer for Infillion interactive ads (TrueX and IDVx).
 *
 * Infillion provides two types of interactive ad experiences:
 *
 * TrueX Ads:
 * - Present an interactive choice card where users opt-in to engage with branded content
 * - When users complete the interaction, they earn an ad credit that skips the entire ad break
 * - Fires AD_FREE_POD event when credit is earned
 * - Configuration: Uses VAST config URL (description field)
 *
 * IDVx Ads:
 * - Interactive ads that start automatically without requiring opt-in
 * - Play inline with other ads in the break sequence
 * - Never earn ad credits - always continue to next ad after completion
 * - Never fire AD_FREE_POD event
 * - Configuration: Uses adParameters JSON
 *
 * This class handles event processing from the TruexAdRenderer and notifies the completion callback
 * when the ad experience finishes, indicating whether credit was earned (TrueX only).
 */
public class InfillionAdManager {
    public static boolean supportUserCancelStream = true;

    private static final String CLASSTAG = InfillionAdManager.class.getSimpleName();

    private IEventEmitter.IEventHandler adEventHandler = this::adEventHandler;

    public interface CompletionCallback {
        void onAdComplete(boolean receivedCredit);
        void onPopup(String url);
    }

    private CompletionCallback completionCallback;
    private boolean didReceiveCredit;
    private TruexAdRenderer truexAdRenderer;

    private ViewGroup viewGroup;

    // Default to showing the ad immediately while it is being fetched.
    private static final boolean showAdImmediately = true;
    private static final boolean showAdAfterLoad = !showAdImmediately;

    public InfillionAdManager(Context context, CompletionCallback completionCallback) {
        this.completionCallback = completionCallback;

        didReceiveCredit = false;

        // Set-up the true[X] ad renderer
        truexAdRenderer = new TruexAdRenderer(context);

        // Set-up the event listeners
        truexAdRenderer.addEventListener(null, adEventHandler); // listen to all events.
        if (supportUserCancelStream) {
            // We use an explicit listener to allow the tar to know user cancel stream is supported.
            truexAdRenderer.addEventListener(TruexAdEvent.USER_CANCEL_STREAM, this::onCancelStream);
        }
    }

    /**
     * Start displaying the Infillion interactive engagement (TrueX or IDVx)
     *
     * TrueX ads:
     * - Pass the VAST config URL in vastConfigUrl parameter
     * - Set adParameters to null
     * - Set adType to AdType.TRUEX
     * - Flow: Shows choice card -> User opts in -> Interactive experience -> AD_FREE_POD on completion
     *
     * IDVx ads:
     * - Pass the adParameters JSON object
     * - Set vastConfigUrl to null
     * - Set adType to AdType.IDVX
     * - Flow: Starts interactive experience immediately -> Completes and continues to next ad
     *
     * @param viewGroup - the view group in which to display the interactive engagement
     * @param vastConfigUrl - VAST config URL for TrueX ads (null for IDVx)
     * @param adParameters - JSON configuration for IDVx ads (null for TrueX)
     * @param adType - TRUEX or IDVX
     */
    public void startAd(ViewGroup viewGroup, String vastConfigUrl, JSONObject adParameters, AdType adType) {
        Log.d(CLASSTAG, "startAd called - ViewGroup: " + viewGroup + ", URL: " + vastConfigUrl +
              ", Has adParameters: " + (adParameters != null) + ", adType: " + adType);
        this.viewGroup = viewGroup;

        TruexAdOptions options = new TruexAdOptions();
        // Only true[X] ads support user cancel stream, IDVx ads should not
        boolean isTrueXAd = (adType == AdType.TRUEX);
        options.supportsUserCancelStream = isTrueXAd && supportUserCancelStream;
        options.fallbackAdvertisingId = UUID.randomUUID().toString();

        Log.d(CLASSTAG, "Calling truexAdRenderer.init()");
        if (adParameters != null) {
            // IDVx ad - pass adParameters directly
            truexAdRenderer.init(adParameters, options);
        } else {
            // TrueX ad - use standard VAST URL
            truexAdRenderer.init(vastConfigUrl, options);
        }

        if (showAdImmediately) {
            Log.d(CLASSTAG, "Calling truexAdRenderer.start() with ViewGroup: " + viewGroup);
            truexAdRenderer.start(viewGroup);
        } else {
            Log.d(CLASSTAG, "showAdImmediately is false, not starting renderer yet");
        }
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
     * Cleanup and destroy the InfillionAdManager
     * Should be called when the ad is complete or when disposing
     */
    public void destroy() {
        Log.d(CLASSTAG, "Destroying InfillionAdManager");
        if (truexAdRenderer != null) {
            truexAdRenderer.removeEventListener(null, adEventHandler);
            truexAdRenderer.stop();
            truexAdRenderer = null;
        }
        completionCallback = null;
    }

    private void adEventHandler(TruexAdEvent event, Map<String, ?> data) {
        Log.i(CLASSTAG, "ad event: " + event);
        switch (event) {
            case AD_STARTED:
                // The ad has started.
                break;

            case SKIP_CARD_SHOWN:
                // The skip card was shown instead of an ad.
                break;

            case AD_DISPLAYED:
                if (showAdAfterLoad) {
                    // Ad is ready to be shown.
                    Handler handler = new Handler();
                    handler.post(() -> truexAdRenderer.start(viewGroup));
                }
                break;

            case USER_CANCEL_STREAM:
                // User backed out of the choice card, which means backing out of the entire video.
                // The user would like to cancel the stream
                // Handled below in onCancelStream()
                return;

            case AD_ERROR: // An ad error has occurred, forcing its closure
            case AD_COMPLETED: // The ad has completed.
            case NO_ADS_AVAILABLE: // No ads are available, resume playback of fallback ads.
                // Notify completion with credit status
                completionCallback.onAdComplete(didReceiveCredit);
                break;

            case AD_FREE_POD:
                // TrueX ads only: User completed the interactive experience and earned credit
                // This event allows skipping the entire ad break and returning to content
                // IDVx ads never fire this event - they always play inline and continue to next ad
                didReceiveCredit = true;
                break;

            case POPUP_WEBSITE:
                String url = (String)data.get("url");
                if (completionCallback != null) {
                    completionCallback.onPopup(url);
                }
                break;

            case OPT_IN:
                // User started the engagement experience
            case OPT_OUT:
                // User cancelled out of the choice card, either explicitly, or implicitly via a timeout.
            case USER_CANCEL:
                // User backed out of the ad, now showing the choice card again.
            default:
                break;
        }
    }

    /**
     * This method should be called if the user has opted to cancel the current stream
     */
    private void onCancelStream(TruexAdEvent event, Map<String, ?> data) {
        if (didReceiveCredit) {
            Log.i(CLASSTAG, "Cancelling stream with credit");
        } else {
            Log.i(CLASSTAG, "Cancelling stream without credit");
        }
        if (completionCallback == null) {
            return;
        }

        // For stream cancellation, we don't provide credit
        completionCallback.onAdComplete(false);
    }

}
