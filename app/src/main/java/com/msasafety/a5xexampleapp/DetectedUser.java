package com.msasafety.a5xexampleapp;

import android.os.Handler;
import android.util.Log;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

public class DetectedUser {

    public String mIpAddress;
    public int mPortNumber;
    Handler mActiveHandler = new Handler();
    public static HashMap<String, DetectedUser> mDetectUsers;
    public ConnectThread mConnectThread;
    public SendThread mSendThread;

    public Socket mSocket;

    Handler mSendHandler = new Handler();
    StateVariable mStates;
    DataOutputStream mDataOut;

    public DetectedUser(StateVariable states) {
        mStates = states;
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

    public void connect(String identifier) {

        String[] separated = identifier.split(",");
        mIpAddress = separated[0];
        mPortNumber = Integer.parseInt(separated[1]);

        mConnectThread = new ConnectThread();
        mConnectThread.start();
    }

    public class ConnectThread extends Thread {
        @Override
        public void run() {
            InetAddress ip = null;

            try {
                ip = InetAddress.getByName(mIpAddress);
            } catch (UnknownHostException e) {
                Log.e("ServerMangeThread", "Failed to convert IP Address");
            }

            try {
                mSocket = new Socket(ip,mPortNumber);
                mDataOut = new DataOutputStream(mSocket.getOutputStream());
                Log.d("Test", "Connecting to " + ip);
            } catch (IOException e) {
                Log.e("ServerMangeThread", "Failed to open socket");
            }

            if(mSendThread != null) {
                mSendThread.close();
            }
            mSendThread = new SendThread();
            mSendThread.start();
        }
    }

    public class SendThread extends Thread {
        @Override
        public void run() {
            if (mSocket != null) {
                if (mSocket.isConnected()) {
                    try {
                        mDataOut.write(mStates.getBytes());
                        Log.d("SendThread", "sent");
                    } catch (IOException e) {
                        Log.e("SendThread", "Failed to open output stream");
                        try {
                            if(mSocket != null) {
                                mSocket.close();
                            }
                        } catch (IOException e1) {
                            Log.e("SendThread", "Failed to close");
                        }
                        mSocket = null;
                        mDetectUsers.remove(mIpAddress);
                        mSendHandler.removeCallbacks(sendRunnable);
                        return;
                    }

                    mSendHandler.postDelayed(sendRunnable, 2000);
                }
                else {
                    try {
                        mSocket.close();
                    } catch (IOException e) {
                        Log.e("SendThread", "Failed to close");
                    }
                    mSocket = null;
                    mDetectUsers.remove(mIpAddress);
                }
            }
        }

        public void close() {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.d("close SendThread", "Failed to close socket");
            }
        }
    }

    public Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
            SendThread sendThread = new SendThread();
            sendThread.start();
        }
    };
}


