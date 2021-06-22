package com.truex.referenceapp.player;

public interface PlaybackHandler {
    void resumeStream();
    void closeStream();
    void displayLinearAds();
    void handlePopup(String url);
}
