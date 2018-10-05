package com.msasafety.a5xexampleapp;

import android.os.Handler;

import java.util.HashMap;

public class DetectedUser {

    public String mIpAddress;
    public int mPortNumber;
    Handler mActiveHandler = new Handler();
    public static HashMap<String, DetectedUser> mDetectUsers;

    public DetectedUser(String rawMessage) {

        String[] separated = rawMessage.split(",");
        mIpAddress = separated[0];
        mPortNumber = Integer.parseInt(separated[1]);

        refresh();
    }

    public void refresh() {
        mActiveHandler.removeCallbacks(activeRunnable);
        mActiveHandler.postDelayed(activeRunnable, 60000);
    }

    public Runnable activeRunnable = new Runnable() {
        @Override
        public void run() {
            mDetectUsers.remove(mIpAddress);
        }
    };
}
