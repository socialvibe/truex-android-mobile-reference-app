# true[X] Reference App

## Reference app for Android Mobile TruexAdRenderer integration

This is an Android Mobile application demonstrating integration of Infillion's interactive ad renderer with Client-Side Ad Insertion (CSAI). The application showcases how to integrate both **TrueX** and **IDVx** interactive ads into a mobile video player using ExoPlayer (Media3).

## What are Infillion Interactive Ads?

Infillion interactive ads provide engaging advertising experiences on mobile platforms. There are two types:

### TrueX Ads
TrueX ads present users with an **interactive choice card** where they can **opt-in** to engage with branded content. The user makes an active choice whether to interact with the ad. When users complete the interaction, they earn an **ad credit that skips the entire ad break**, allowing them to return immediately to their content. This creates a win-win: viewers get ad-free content, and advertisers get highly engaged audiences.

**Key features:**
- **Opt-in via choice card** - Users actively choose to engage
- **Skips entire ad break** - Successful engagement bypasses all remaining ads
- **Configuration**: Uses the `description` field containing a VAST config URL (e.g., `get.truex.com/...`)

### IDVx Ads
IDVx ads are **interactive ads** that start **automatically without requiring opt-in**. Unlike TrueX ads which require users to opt-in via a choice card, IDVx ads begin playing automatically. While no opt-in is required to start, users can interact with the ad content throughout its duration. IDVx ads **play inline with other ads** in the break sequence. After an IDVx ad completes, the next ad in the pod plays.

**Key features:**
- **Automatic start** - No opt-in required, begins playing automatically
- **Interactive throughout** - Users can interact with ad content for its duration
- **Plays inline** - Completes and continues to next ad in sequence
- **Configuration**: Uses the `adParameters` field containing JSON configuration

## Client-Side Ad Insertion (CSAI)

This reference app demonstrates the CSAI pattern where ads are managed separately from the main video stream. When an ad break's `timeOffset` is reached, the main video pauses, ads play separately, and the main video resumes from the exact pause point. No time adjustments are needed since ads aren't stitched into the timeline.


### Access the true[X] Ad Renderer Library
Add the maven repository to your build.gradle

```
    maven {
        url "https://s3.amazonaws.com/android.truex.com/tar/prod/maven"
    }
```

Add the TAR dependency to your project
```
    // We recommend using a specific version, but using the latest patch release for any critical hotfixes
    implementation 'com.truex:TruexAdRenderer-Android:2.8.2'
```

## Ad Configuration

Ad playlist configuration is maintained in `ReferenceApp/src/main/res/raw/adbreaks_stub.json`. Key fields in the data file:

* `breakId`: Unique identifier for the ad break (e.g., "preroll", "midroll-1")
* `timeOffsetMs`: Content video time when the ad break should trigger (e.g., 0 for preroll, 600000 for 10 minutes)
* `ads`: Array of ad objects in the break
  * `adSystem`: Type of ad - "trueX", "IDVx", or "GDFP" (regular video ads)
  * `mediaFile`: URL for standard video ad fallback
  * `description`: For TrueX ads, contains the VAST config URL
  * `adParameters`: For IDVx ads, contains JSON configuration
  * `duration`: Length of the ad in seconds

**Example TrueX ad:**
```json
{
  "id": "truex-preroll",
  "adSystem": "trueX",
  "description": "https://get.truex.com/...",
  "mediaFile": "https://qa-media.truex.com/video/truex-slate-30s.mp4",
  "duration": 30
}
```

**Example IDVx ad:**
```json
{
  "id": "idvx-preroll",
  "adSystem": "IDVx",
  "adParameters": { ... },
  "mediaFile": "https://qa-media.truex.com/video/truexloadingplaceholder-30s.mp4",
  "duration": 30
}
```

## Integration Steps

The following steps are a guideline for integrating Infillion interactive ads. This assumes you have setup the TAR dependency to access the renderer. The starting/key points referenced in each step can be searched in the code for reference.

### [1] - Detect Infillion interactive ads (AdProvider, Ad.java)
For simplicity, this sample app uses stubbed ad data (`adbreaks_stub.json`). In production, this would be replaced with an ad server call and VMAP/VAST parsing. The app identifies ad types based on the `adSystem` field:
- `"trueX"` → TrueX interactive choice card ad
- `"IDVx"` → IDVx interactive inline ad
- Other values → Regular video ad

See `Ad.determineAdType()` for the type detection logic.

### [2] - Start interactive ad (AdManager::showInfillionRenderer, InfillionAdManager)
When an Infillion interactive ad (TrueX or IDVx) is encountered, the `AdManager` pauses the content video and creates an `InfillionAdManager` to handle the interactive experience. The key initialization is:

```java
InfillionAdManager infillionAdManager = new InfillionAdManager(context, callback);
// For TrueX ads - use VAST URL
infillionAdManager.startAd(viewGroup, vastConfigUrl, null, AdType.TRUEX);
// For IDVx ads - use adParameters JSON
infillionAdManager.startAd(viewGroup, null, adParameters, AdType.IDVX);
```

The `InfillionAdManager` creates a `TruexAdRenderer` instance and initializes it:
```java
truexAdRenderer.init(vastConfigUrl, options);  // TrueX ads
// OR
truexAdRenderer.init(adParameters, options);    // IDVx ads
truexAdRenderer.start(viewGroup);
```

**TrueX flow:**
1. Choice card is displayed
2. User opts in to engagement or opts out
3. If opt-in: interactive experience is shown
4. On completion with credit: entire ad break is skipped

**IDVx flow:**
1. Interactive experience starts immediately (no choice card)
2. User can interact throughout the ad duration
3. On completion: next ad in the pod plays

The `init` call tells the renderer to initialize and verify it has a valid ad payload. The `start` call displays the ad, showing a loading spinner while the ad queries the payload and loads the display page.

The host app can control when to start the ad experience. If it needs to display its own loading screen, it can defer calling `start` until the `AD_DISPLAYED` event. See the `showAdImmediately` and `showAdAfterLoad` flags in `InfillionAdManager`.

### [3] - Ad Events - AD_FREE_POD (InfillionAdManager::adEventHandler, TruexAdEvent)
The TruexAdRenderer sends various ad events to the host app. The most important event is **AD_FREE_POD**, which applies **only to TrueX ads**.

When a user completes the required interaction in a TrueX ad (also called earning true[ATTENTION]), the `AD_FREE_POD` event is fired. The `InfillionAdManager` tracks this with the `didReceiveCredit` flag.

**Important notes:**
- **TrueX ads only**: AD_FREE_POD indicates the user earned credit to skip the entire ad break
- **IDVx ads never earn credit**: IDVx ads complete normally without triggering AD_FREE_POD
- AD_FREE_POD can be acquired before the user exits the engagement
- The host app should wait for a terminating event (AD_COMPLETED, AD_ERROR, NO_ADS_AVAILABLE) before taking action

### [4] - Ad Events - Terminating Events (AdManager::onInfillionAdComplete, TruexAdEvent)
There are three ways the renderer can finish:

1. There were no ads available (`NO_ADS_AVAILABLE`)
2. The ad had an error (`AD_ERROR`)
3. The viewer has completed the engagement (`AD_COMPLETED`)

When the interactive ad completes, the `AdManager.onInfillionAdComplete()` method handles the next steps based on whether credit was earned:

**TrueX ad with credit earned:**
- Skip all remaining ads in the pod
- Mark the ad break as completed
- Return immediately to content via `onSkipToContent()`

**TrueX ad without credit (opted out):**
- Resume playback of remaining ads in the pod
- Next ad starts playing automatically

**IDVx ad (never earns credit):**
- Resume playback of remaining ads in the pod
- Next ad in the sequence starts playing automatically

In all cases, the TruexAdRenderer will have removed itself from view when these terminating events occur.

### [5] - Ad Events - Other Events (InfillionAdManager, TruexAdEvent)
The TruexAdRenderer fires additional ad events that the host app can handle:

- **AD_STARTED**: The interactive ad has started displaying
- **AD_DISPLAYED**: The ad is fully loaded and ready to be shown (useful if deferring `start()` call)
- **SKIP_CARD_SHOWN**: The skip card was shown instead of an ad (TrueX only)
- **OPT_IN**: User opted in to the engagement (TrueX only)
- **OPT_OUT**: User opted out of the choice card (TrueX only)
- **USER_CANCEL**: User backed out of the ad experience
- **USER_CANCEL_STREAM**: User wants to cancel the entire video stream (if `supportsUserCancelStream` is enabled)
- **POPUP_WEBSITE**: User action triggered a popup URL

Most events are informational and don't require action from the host app. The **POPUP_WEBSITE** event provides a URL that the app can open in a browser.

**Lifecycle management**: It's important to pause/resume the ad renderer based on app lifecycle events to preserve proper states when switching to another app. See `InfillionAdManager.onResume()`, `onPause()`, and `onStop()` methods.

## Setup

### Pre-Requisites

* [Install Android Studio](https://developer.android.com/studio/)

### Install Steps

* Clone the `master` branch of the `ReferenceApp` repository
    * `git clone https://github.com/socialvibe/truex-android-mobile-reference-app.git`
    * `develop` branch should not be used as it may not be production ready

* Open ReferenceApp with Android Studio
    * Open Android Studio
    * Select `Open an existing Android Studio project` and select the ReferenceApp folder

### Run Steps

#### Run on Virtual Device
* Create a Virtual Device
    * Select `Run 'app'` or `Debug 'app'` in Android Studio
    * Select `Create New Virtual Device`
    * Select the an appropriate mobile device
    * Select a system image
    * Select `Finish` to finish creating the Virtual Device
* Select `Run 'app'` or `Debug 'app'` in Android Studio
* Select the virtual device and press `OK`