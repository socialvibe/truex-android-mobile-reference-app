package com.truex.referenceapp.ads;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import com.truex.adrenderer.IEventEmitter.IEventHandler;
import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;
import com.truex.referenceapp.player.PlaybackHandler;

import java.util.Map;
import java.util.UUID;

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

        // Set-up the event listeners
        truexAdRenderer.addEventListener(TruexAdEvent.AD_FETCH_COMPLETED, this.adFetchCompleted);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_STARTED, this.adStarted);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_COMPLETED, this.adCompleted);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_ERROR, this.adError);
        truexAdRenderer.addEventListener(TruexAdEvent.NO_ADS_AVAILABLE, this.noAds);
        truexAdRenderer.addEventListener(TruexAdEvent.AD_FREE_POD, this.adFree);
        truexAdRenderer.addEventListener(TruexAdEvent.USER_CANCEL, this.userCancel);
        truexAdRenderer.addEventListener(TruexAdEvent.OPT_IN, this.optIn);
        truexAdRenderer.addEventListener(TruexAdEvent.OPT_OUT, this.optOut);
        truexAdRenderer.addEventListener(TruexAdEvent.SKIP_CARD_SHOWN, this.skipCardShown);
        truexAdRenderer.addEventListener(TruexAdEvent.POPUP_WEBSITE, this.popUp);
    }

    /**
     * Start displaying the true[X] engagement
     * @param viewGroup - the view group in which you would like to display the true[X] engagement
     */
    public void startAd(ViewGroup viewGroup) {
        String vastConfigUrl = "https://qa-get.truex.com/f7e02f55ada3e9d2e7e7f22158ce135f9fba6317/vast/config?dimension_2=1&stream_position=midroll";

        TruexAdOptions options = new TruexAdOptions();
        options.supportsUserCancelStream = true;
        options.userAdvertisingId = UUID.randomUUID().toString();

        // Alternatively, see various overloaded TAR init for passing a VastJson directly
        truexAdRenderer.init(vastConfigUrl, options, () -> { truexAdRenderer.start(viewGroup); });
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
     * Inform that the true[X] ad renderer that the application has stopped
     */
    public void onStop() {
        Log.d(CLASSTAG, "onStop");
    }

    /**
     * Inform that the true[X] ad renderer that the application has been destroyed
     */
    public void onDestroy() {
        Log.d(CLASSTAG, "onDestroy");
        truexAdRenderer.destroy();
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

    /*
       Note: This event is triggered when the init call is finished, and the ad is fetched/ready
     */
    private IEventHandler adFetchCompleted = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adFetchCompleted");
    };

    /*
       Note: This event is triggered when the ad starts
     */
    private IEventHandler adStarted = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adStarted");
    };

    /*
       Note: This event is triggered when the engagement is completed,
       either by the completion of the engagement or the user exiting the engagement
     */
    private IEventHandler adCompleted = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adCompleted");

        // We are now done with the engagement
        onCompletion();
    };

    /*
       Note: This event is triggered when an error is encountered by the true[X] ad renderer
     */
    private IEventHandler adError = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adError");

        // There was an error trying to load the enagement
        onCompletion();
    };

    /*
       Note: This event is triggered if the engagement fails to load,
       as a result of there being no engagements available
     */
    private IEventHandler noAds = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "noAds");

        // There are no engagements available
        onCompletion();
    };

    /*
       Note: This event is triggered when the viewer has earned their true[ATTENTION] credit. We
       could skip over the linear ads here, so that when the ad is complete, all we would need
       to do is resume the stream.
     */
    private IEventHandler adFree = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adFree");
        didReceiveCredit = true;
    };

    /*
       Note: This event is triggered when a user cancels an interactive engagement
     */
    private IEventHandler userCancel = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "userCancel");
    };

    /*
       Note: This event is triggered when a user opts-in to an interactive engagement
     */
    private IEventHandler optIn = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "optIn");
    };

    /*
       Note: This event is triggered when a user opts-out of an interactive engagement,
       either by time-out, or by choice
     */
    private IEventHandler optOut = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "optOut");
    };

    /*
       Note: This event is triggered when a skip card is being displayed to the user
       This occurs when a user is able to skip ads
     */
    private IEventHandler skipCardShown = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "skipCardShown");
    };

    /*
        Note: This event is triggered when a pop up is to be displayed.  Publisher app is
        responsible for pausing/resuming the Truex Ad Renderer
    */
    private IEventHandler popUp = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "popUp");

        String url = (String)data.get("url");
        playbackHandler.handlePopup(url);
    };
}
