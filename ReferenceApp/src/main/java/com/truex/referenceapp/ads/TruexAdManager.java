package com.truex.referenceapp.ads;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;
import com.truex.referenceapp.player.PlaybackHandler;

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

    private ViewGroup viewGroup;

    // Default to showing the ad immediately while it is being fetched.
    // The HTML5 TAR shows a black screen with a spinner in this case, which is appropriate
    // for most publisher user situations.
    private static final boolean showAdImmediately = true;
    private static final boolean showAdAfterLoad = !showAdImmediately;

    public TruexAdManager(Context context, PlaybackHandler playbackHandler) {
        this.playbackHandler = playbackHandler;

        didReceiveCredit = false;

        // Set-up the true[X] ad renderer
        truexAdRenderer = new TruexAdRenderer(context);

        // Set-up the event listeners
        truexAdRenderer.addEventListener(TruexAdEvent.AD_FETCH_COMPLETED, this::adFetchCompleted);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_STARTED, this::adStarted);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_DISPLAYED, this::adDisplayed);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_COMPLETED, this::adCompleted);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_ERROR, this::adError);
        truexAdRenderer.addEventListener(TruexAdEvent.NO_ADS_AVAILABLE, this::noAds);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_FREE_POD, this::adFree);
        truexAdRenderer.addEventListener(TruexAdEvent.USER_CANCEL, this::userCancel);
        truexAdRenderer.addEventListener(TruexAdEvent.OPT_IN, this::optIn);
        truexAdRenderer.addEventListener(TruexAdEvent.OPT_OUT, this::optOut);
        truexAdRenderer.addEventListener(TruexAdEvent.SKIP_CARD_SHOWN, this::skipCardShown);
        truexAdRenderer.addEventListener(TruexAdEvent.POPUP_WEBSITE, this::popUp);
    }

    /**
     * Start displaying the true[X] engagement
     * @param viewGroup - the view group in which you would like to display the true[X] engagement
     * @param vastUrl - the vastUrl that comes from the ad service provider
     */
    public void startAd(ViewGroup viewGroup, String vastUrl) {
        this.viewGroup = viewGroup;

        // After viewing a Truex Ad experience, there will could be a lockout period
        // That prevents the same user from getting another Truex ad for some period of time
        // To get around this for development, replace the TruexAdOptions.userAdvertisingId
        // with some different/random value
        TruexAdOptions options = new TruexAdOptions();

        truexAdRenderer.init(vastUrl, options);
        if (showAdImmediately) {
            truexAdRenderer.start(viewGroup);
        }
    }

    /**
     * Inform the true[X] ad renderer that the application has resumed
     */
    public void onResume() {
        Log.d(CLASSTAG, "onResume");
        truexAdRenderer.resume();
    }

    /**
     * Inform the true[X] ad renderer that the application has paused
     */
    public void onPause() {
        Log.d(CLASSTAG, "onPause");
        truexAdRenderer.pause();

    }

    /**
     * Inform that the true[X] ad renderer that the application has been destroyed
     */
    public void onDestroy() {
        Log.d(CLASSTAG, "onDestroy");
        truexAdRenderer.stop();
    }

    /**
     * [4] - Integration Doc/Notes
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

    /**
     * Note: This event is triggered when the init call is finished, and the ad is fetched/ready
     */
    private void adFetchCompleted(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "adFetchCompleted");
        // Truex Ad Renderer ad playload has been received, and is ready to start() if not started yet.
    }

    /**
     * Note: This event is triggered when the ad starts to be displayed.
     */
    private void adStarted(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "adStarted");
    }

    /**
     * Note: This event is triggered when the ad visual assets are loaded and visible.
     * This then is a good time to switch to showing it if the host app is using its own ad
     * loading screen.
     */
    private void adDisplayed(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "adDisplayed");
        if (showAdAfterLoad) {
            // Ad is ready to be shown.
            Handler handler = new Handler();
            handler.post(() -> truexAdRenderer.start(viewGroup));
        }
    }

    /**
     * Note: This event is triggered when the engagement is completed,
     * either by the completion of the engagement or the user exiting the engagement
     */
    private void adCompleted(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "adCompleted");

        // We are now done with the engagement
        onCompletion();
    }

    /**
     * Note: This event is triggered when an error is encountered by the true[X] ad renderer
     */
    private void adError(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "adError");

        // There was an error trying to load the engagement
        onCompletion();
    }

    /**
     * Note: This event is triggered if the engagement fails to load,
     * as a result of there being no engagements available
     */
    private void noAds(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "noAds");

        // There are no engagements available
        onCompletion();
    }

    /**
     * [3] - Integration Doc/Notes
     * Note: This event is triggered when the viewer has earned their true[ATTENTION] credit. We
     * could skip over the linear ads here, so that when the ad is complete, all we would need
     * to do is resume the stream.
     */
    private void adFree(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "adFree");
        didReceiveCredit = true;
    }

    /**
     * Note: This event is triggered when a user cancels an interactive engagement
     */
    private void userCancel(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "userCancel");
    }

    /**
     * Note: This event is triggered when a user opts-in to an interactive engagement
     */
    private void optIn(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "optIn");
    }

    /**
     * Note: This event is triggered when a user opts-out of an interactive engagement,
     * either by time-out, or by choice
     */
    private void optOut(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "optOut");
    }

    /**
     * Note: This event is triggered when a skip card is being displayed to the user
     * This occurs when a user is able to skip ads
     */
    private void skipCardShown(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "skipCardShown");
    }

    /**
     * Note: This event is triggered when a pop up is to be displayed.  Publisher app is
     * responsible for pausing/resuming the Truex Ad Renderer
     */
    private void popUp(TruexAdEvent event, Map<String, ?> data) {
        Log.d(CLASSTAG, "popUp");
        String url = (String)data.get("url");
        playbackHandler.handlePopup(url);
    }
}

