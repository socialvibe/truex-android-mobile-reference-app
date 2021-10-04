package com.truex.referenceapp.unlock;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import com.truex.adrenderer.IEventEmitter;
import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;
import com.truex.referenceapp.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UnlockFragment extends Fragment implements View.OnClickListener {
    private static final String CLASSTAG = UnlockFragment.class.getSimpleName();
    private TruexAdRenderer truexAdRenderer;
    private Context context;
    private Map vastMap = null;
    private Boolean vastReady = false;
    private final String AD_SERVER = "https://qa-get.truex.com/5075c46a8e5a48a206318d4ecfb5cc70101e0bcf/vast/solo?dimension_2=1&stream_position=midroll&stream_id=[stream_id]&network_user_id=[user_id]";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        context = getContext();
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_unlock, container, false);
        Button button = view.findViewById(R.id.unlockWithTruex);
        button.setOnClickListener(this);

        // Helper function to fetch ad to vastMap
        // This should be pointing to your ad server, where a true[X] ad is booked.
        fetchAd(AD_SERVER);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.unlockWithTruex:
                unlockButtonClicked();
                break;
        }
    }

    private void unlockButtonClicked() {
        Switch lockingSwitch = getView().findViewById(R.id.lockingSwitch);
        if (lockingSwitch.isChecked()) {
            toast("Already Unlocked");
            return;
        }

        if (!vastReady) {
            toast("Not Ready-- Downloading VAST");
            return;
        }

        // [1] - Integration Doc/Notes
        // Here we use a fake ad manager, which parse the VAST XML directly into a Map.
        try {
            // Just checking the 1st ad here to simplify the flow in this example
            // currentAd = vastMap["Ad"][0]["InLine"][0];
            Map currentAd = get(get(vastMap, "Ad", 0), "InLine", 0);
            // adSystem = currentAd["AdSystem"][0]["CDATA"];
            String adSystem = (String)get(currentAd, "AdSystem", 0).get("CDATA");
            Boolean isTruexAd = (adSystem.startsWith("trueX"));

            if (isTruexAd) {
                // creative = currentAd["Creatives"][0]["Creative"][0];
                Map creative = get(get(currentAd, "Creatives", 0), "Creative", 0);
                // adParametersString = creative["Linear"][0]["AdParameters"][0]["CDATA"];
                String adParametersString = (String)get(get(creative, "Linear", 0), "AdParameters", 0).get("CDATA");
                JSONObject adParameters = new JSONObject(adParametersString);
                startTruexAdRenderer(adParameters);
            } else {
                toast("Not true[X] ad");
            }
        } catch (JSONException e) {
            toast("Error parsing vastConfig response as JSON");
        }
    }

    private void userEarnedCredit() {
        Switch lockingSwitch = getView().findViewById(R.id.lockingSwitch);
        lockingSwitch.setChecked(true);
        lockingSwitch.setText("Unlocked");
    }

    // MARK: - TrueX Ad Renderer
    private void startTruexAdRenderer(JSONObject vastConfigJSON) {
        if (truexAdRenderer != null) {
            truexAdRenderer.destroy();
            truexAdRenderer = null;
        }

        // Set-up the true[X] ad renderer
        truexAdRenderer = new TruexAdRenderer(getContext());

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

        // init and start TruexAdRenderer
        TruexAdOptions options = new TruexAdOptions();
        ViewGroup viewGroup = (ViewGroup) getView().findViewById(R.id.unlockScreenLayout);
        truexAdRenderer.init(vastConfigJSON, options, () -> {
            truexAdRenderer.start(viewGroup);
        });
    }

    /**
     * Inform the true[X] ad renderer that the application has resumed
     */
    @Override
    public void onResume() {
        Log.d(CLASSTAG, "onResume");
        if (truexAdRenderer != null) {
            truexAdRenderer.resume();
        }
        super.onResume();
    }

    /**
     * Inform the true[X] ad renderer that the application has paused
     */
    @Override
    public void onPause() {
        Log.d(CLASSTAG, "onPause");
        if (truexAdRenderer != null) {
            truexAdRenderer.pause();
        }
        super.onPause();
    }

    /**
     * Inform that the true[X] ad renderer that the application has been destroyed
     */
    @Override
    public void onDestroy() {
        Log.d(CLASSTAG, "onDestroy");
        if (truexAdRenderer != null) {
            truexAdRenderer.destroy();
        }
        super.onDestroy();
    }

    /*
       Note: This event is triggered when the init call is finished, and the ad is fetched/ready
     */
    private IEventEmitter.IEventHandler adFetchCompleted = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adFetchCompleted");
        toast("adFetchCompleted");
        // Truex Ad Renderer is ready to start() if not started in the init callback
    };

    /*
       Note: This event is triggered when the ad starts
     */
    private IEventEmitter.IEventHandler adStarted = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adStarted");
        toast("adStarted");
    };

    /*
     * [4] - Integration Doc/Notes
       Note: This event is triggered when the engagement is completed,
       either by the completion of the engagement or the user exiting the engagement
     */
    private IEventEmitter.IEventHandler adCompleted = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adCompleted");
        toast("adCompleted");
    };

    /*
     * [4] - Integration Doc/Notes
       Note: This event is triggered when an error is encountered by the true[X] ad renderer
     */
    private IEventEmitter.IEventHandler adError = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adError");
        toast("adError");
    };

    /*
     * [4] - Integration Doc/Notes
       Note: This event is triggered if the engagement fails to load,
       as a result of there being no engagements available
     */
    private IEventEmitter.IEventHandler noAds = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "noAds");
        toast("noAds");
    };

    /*
       [3] - Integration Doc/Notes
       Note: This event is triggered when the viewer has earned their true[ATTENTION] credit. We
       could skip over the linear ads here, so that when the ad is complete, all we would need
       to do is resume the stream.
     */
    private IEventEmitter.IEventHandler adFree = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "adFree");
        toast("adFree");

        userEarnedCredit();
    };

    /*
       Note: This event is triggered when a user cancels an interactive engagement
     */
    private IEventEmitter.IEventHandler userCancel = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "userCancel");
        toast("userCancel");
    };

    /*
       Note: This event is triggered when a user opts-in to an interactive engagement
     */
    private IEventEmitter.IEventHandler optIn = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "optIn");
        toast("optIn");
    };

    /*
       Note: This event is triggered when a user opts-out of an interactive engagement,
       either by time-out, or by choice
     */
    private IEventEmitter.IEventHandler optOut = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "optOut");
        toast("optOut");
    };

    /*
       Note: This event is triggered when a skip card is being displayed to the user
       This occurs when a user is able to skip ads
     */
    private IEventEmitter.IEventHandler skipCardShown = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "skipCardShown");
        toast("skipCardShown");
    };

    /*
        Note: This event is triggered when a pop up is to be displayed.  Publisher app is
        responsible for pausing/resuming the Truex Ad Renderer
    */
    private IEventEmitter.IEventHandler popUp = (TruexAdEvent event, Map<String, ?> data) -> {
        Log.d(CLASSTAG, "popUp");
        toast("popUp");

        String url = (String)data.get("url");

        // Be sure to pause and resume TruexAdRenderer if you are handling this request with an in-app webview.
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    };



    // MARK: - Helper Functions / Fake Ad Framework
    // These are just helper code to make this sample work. Following are not part of the intergration
    private void fetchAd(final String rawUrlString) {
        String urlString = rawUrlString;
        // replacing user_id with random UUID here for testing, please use the real user ID from the system.
        // Usually this will be filled out by your ad server, or you will fill in your internal ID
        for (String s : Arrays.asList("[stream_id]", "[user_id]")) {
            urlString = urlString.replace(s, UUID.randomUUID().toString());
        }
        fetchXmlToVastMap(urlString);
    }

    private void fetchXmlToVastMap(final String urlString) {
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
                    XmlPullParser parser = parserFactory.newPullParser();
                    InputStream is = new URL(urlString).openStream();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    parser.setInput(is, null);

                    int eventType = parser.getEventType();
                    Map current = null;
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        String elementName = "";
                        Map parent;
                        switch (eventType) {
                            case XmlPullParser.START_TAG:
                                elementName = parser.getName();
                                parent = current;

                                current = new HashMap();
                                int attributeCount = parser.getAttributeCount();
                                for (int i = 0; i < attributeCount; i++) {
                                    current.put(parser.getAttributeName(i), parser.getAttributeValue(i));
                                }

                                if (parent != null) {
                                    current.put("parent", parent);
                                    ArrayList siblings = (ArrayList) parent.get(elementName);
                                    if (siblings == null) {
                                        parent.put(elementName, new ArrayList());
                                        siblings = (ArrayList) parent.get(elementName);
                                    }
                                    siblings.add(current);
                                }

                                // setting the root
                                if (vastMap == null) {
                                    vastMap = current;
                                }
                                break;

                            case XmlPullParser.TEXT:
                                current.put("CDATA", parser.getText());
                                break;

                            case XmlPullParser.END_TAG:
                                if (current != null) {
                                    parent = (Map) current.get("parent");
                                    current.remove("parent");
                                    current = parent;
                                }
                                break;

                            default:
                                Log.v(CLASSTAG, "default: " + XmlPullParser.TYPES[parser.getEventType()]);

                        }
                        eventType = parser.next();
                    }
                    vastReady = true;

                    Boolean DEBUG = false;
                    if (DEBUG) {
                        JSONObject json = new JSONObject(vastMap);
                        Log.v(CLASSTAG, json.toString());
                    }
                } catch (Error | XmlPullParserException | MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread asyncThread = new Thread(runnable);
        asyncThread.start();
    }

    private Map get(Map map, String key, int index) {
        if (map != null) {
            ArrayList list = (ArrayList)map.get(key);
            if (list != null) {
                return (Map) list.get(index);
            }
        }
        return null;
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}