package com.truex.referenceapp.player;

public interface PlaybackStateListener {
    void onPlayerDidStart();
    void onPlayerDidResume();
    void onPlayerDidPause();
    void onPlayerDidComplete();
}
