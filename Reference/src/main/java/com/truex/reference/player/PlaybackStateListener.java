package com.truex.reference.player;

public interface PlaybackStateListener {
    void onPlayerDidStart();
    void onPlayerDidResume();
    void onPlayerDidPause();
    void onPlayerDidComplete();
}
