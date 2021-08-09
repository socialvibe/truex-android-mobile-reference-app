# true[X] Reference App

## Reference app for Android Mobile TruexAdRenderer integration

This is an Android Mobile application exposing direct calls into `TruexAdRenderer` (also referenced as TAR in the walkthrough and code) instances, enabling functional testing as well as prototyping. The application is set up with a simple activity and the calls in the `MainActivity` should be self-explanatory.  This samples an integration where the ads are handled on the client side, but it can be extended to other ad solutions such as server stitched ads.


### Access the true[X] Ad Renderer Library
Add the maven repository to your build.gradle

```
repositories {
    maven {
        url "https://s3.amazonaws.com/android.truex.com/maven"
    }
}
```

Add the TAR dependency to your project
```
    // We recommend using a specific version, but using the latest patch release for any critical hotfixes
    implementation 'com.truex:TruexAdRenderer-Mobile:2.0.+
```

## Steps
The following steps are a guideline for the TAR integration.  This assumes you have setup the TAR dependency above to access the renderer.  The starting/key points referenced in each step can be searched in the code for reference.  EG.  Searching for [2], will direct you to the engagement start.

### [1] - Look for true[X] companions for a given ad (PlayerFragment::playCurrentAds)
For simplicity, this sample app uses stubbed and parsed ad data (`adbreaks_stub.json`).  But this can be replaced with an ad call and then parsing the ad payload.  The important part here is determining if a given ad, which should be the first ad in a pod, is a true[X] ad.  This can vary depending on how the ads are returned by the server.  But some variations include checking if part of the payload contains a true[X] string, as seen in `isTruexAdUrl()`.

### [2] - Prepare to enter the engagement (PlayerFragment::displayInteractiveAd, TruexAdManager)
Once as have a valid true[X] ad, first we pause playback (displayInteractiveAd::pauseStream).  Then we initialize TAR by means of the `TruexAdManager`, via `TruexAdManager.startAd()`.  Note the `PlaybackHandler` passed in the constructor, which will be responsible for interfacing with the TAR events later on.  The key line that starts the true[X] Ad Experience is:
`truexAdRenderer.init(vastUrl, options, () -> { truexAdRenderer.start(viewGroup); });`
This tells the renderer to initialize and verify it has a valid ad payload, and it fires a callback when it is ready to start.  The host app is responsible for when to start the ad experience, but able to do it immediately, per the example above.  After the renderer has started, it will communicate various events.

### [3] - Ad Events - AD_FREE_POD (TruexAdManager::adFree, TruexAdEvent)
There are a handful of key ad events that TAR will message to the host app.  The first key event is AD_FREE_POD.  Once a user fulfills the requirements to earn credit for the ad, also called true[ATTENTION], this event is fired, and a flag keeps track of this in the code.  It is important to note that AD_FREE_POD can be acquired before the user exits the engagement, and the host app should wait for a terminating event to proceed.

### [4] - Ad Events - Terminating Events (TruexAdManager::onCompletion, TruexAdEvent)
There are three ways the renderer can finish:

1. There were no ads available. (`NO_ADS_AVAILABLE`)
2. The ad had an error. (`AD_ERROR`)
3. The viewer has completed the engagement. (`AD_COMPLETED`)

In `onCompletion`, note that if the user has gained credit from earlier, we want to skip all the other ad returned in the pod and resume the stream.  Otherwise we need to play the other ads in the pod.  In all three of these cases, the renderer will have removed itself from view.

### [5] - Ad Events - Other (TruexAdManager, TruexAdEvent)
See the code for other ad events that are fired.  Some events are for any custom purposes if needed, otherwise there is nothing the host app is required to do (eg. AD_STARTED).  The POPUP_WEBSITE event is for handling any user interactions that would prompt a pop up.  It is important to pause/resume the ad renderer based off the user actions to preserve proper states when switching to another app, as shown in the code.

## Setup

### Pre-Requisites

* [Install Android Studio](https://developer.android.com/studio/)

### Install Steps

* Clone the `master` branch of the `ReferenceApp` repository
    * `git clone https://github.com/socialvibe/truex-android-mobile-reference-app.git`

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