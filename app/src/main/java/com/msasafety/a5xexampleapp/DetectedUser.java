package com.msasafety.a5xexampleapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;

import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

import netP5.NetAddress;
import oscP5.OscP5;

public class DetectedUser {

    public String mIpAddress;
    public int mPortNumber;
    static MainActivity mThat;
    StateVariable mStates;

    Handler sendHandler;

    public static HashMap<String, DetectedUser> mDetectUsers;

    public DetectedUser(StateVariable states) {
        mStates = states;
    }

    public void connect(String identifier) {
        String[] separated = identifier.split(",");
        mIpAddress = separated[0];
        mPortNumber = Integer.parseInt(separated[1]);

        SendThread sendThread = new SendThread();
        sendThread.start();
    }

    public class SendThread extends Thread {

        OscP5 oscP5;
        NetAddress remoteLocation;

        public SendThread() {

            int listenPort = 14125;

            try {
                DatagramSocket s = new DatagramSocket();
                listenPort = s.getLocalPort();
                s.close();
            } catch (SocketException e) {
                Log.d("TEST", "Failed to open test UDP port");
            }
            oscP5 = new OscP5(this, listenPort);

            remoteLocation = new NetAddress(mIpAddress, mPortNumber);
        }

        @Override
        public void run() {
            Looper.prepare();
            sendHandler = new Handler();
            sendHandler.post(sendRunnable);
            Looper.loop();
        }

        Runnable sendRunnable = new Runnable() {
            @Override
            public void run() {
                oscP5.send(mStates.getBytes(), remoteLocation);
                sendHandler.postDelayed(sendRunnable, 2000);
            }
        };
    }
}

/*public class DetectedUser {

    public String mIpAddress;
    public int mPortNumber;
    Handler mActiveHandler = new Handler();
    public static HashMap<String, DetectedUser> mDetectUsers;
    public ConnectThread mConnectThread;
    public SendThread mSendThread;
    public ListenThread mListenThread;

    public Socket mSocket;

    static MainActivity mThat;

    Handler mSendHandler = new Handler();
    StateVariable mStates;
    DataOutputStream mDataOut;

    boolean mTriedReconnect = false;
    String mIdentifer = "";

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

        mThat.debugPrint("Connecting to " + identifier);
    }

    public void reConnect() {
        mTriedReconnect = true;
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

            mSendThread = new SendThread(mStates.getBytes());
            mSendThread.start();

            if(!mStates.mDateState) {
                ArrayMap<String, String> arrayMap = new ArrayMap<>();
                arrayMap.put("command", "date");
                Gson gson = new Gson();
                String json = gson.toJson(arrayMap);
                mSendThread = new SendThread(json.getBytes());
                mSendThread.start();
            }

            mListenThread = new ListenThread();
            mListenThread.start();
        }
    }

    public class SendThread extends Thread {

        byte [] mMessage;

        public SendThread(byte [] message) {
            mMessage = message;
        }

        @Override
        public void run() {
            if (mSocket != null) {
                if (!mSocket.isClosed()) {
                    try {
                        mDataOut.write(mMessage);
                        Log.d("SendThread", "sent");
                    } catch (IOException e) {
                        Log.e("SendThread", "Failed to open output stream");
                        try {
                            if(mSocket != null) {
                                mSocket.close();
                                mListenThread.close();
                            }
                        } catch (IOException e1) {
                            Log.e("SendThread", "Failed to close");
                        }

                        mSendHandler.removeCallbacks(sendRunnable);

                        if(!mTriedReconnect) {
                            reConnect();
                        }
                        else {
                            //mSocket = null;
                            mDetectUsers.remove(mIpAddress);
                            mThat.debugPrint("Lost " + mIpAddress);

                            return;
                        }
                    }

                    mSendHandler.postDelayed(sendRunnable, 2000);
                }
                else {
                    try {
                        mSocket.close();
                    } catch (IOException e) {
                        Log.e("SendThread", "Failed to close");
                    }
                    //mSocket = null;
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
            SendThread sendThread = new SendThread(mStates.getBytes());
            sendThread.start();
        }
    };

    public class ListenThread extends Thread {

        DataInputStream dataIn;
        boolean run = false;

        @Override
        public void run() {

            byte buffer [] = new byte[5000];
            int test;

            try {
                dataIn = new DataInputStream(mSocket.getInputStream());
                run = true;
            } catch (IOException e) {
                Log.e("ListenThread","Failed to open data input stream");
                close();
            }

            while(run) {
                try {
                    if((test = dataIn.read(buffer)) > 0) {
                        String text = new String(buffer, 0, test);
                        Log.d("Received data", text);

                        if (text.equals("Log")) {
                            FileInputStream inputStream = mThat.getApplicationContext().openFileInput("events.log");
                            byte[] log = new byte[100000];
                            int totalBytesRead = inputStream.read(log);
                            inputStream.close();

                            ArrayMap<String, String> arrayMap = new ArrayMap<>();
                            arrayMap.put("command", "log");
                            String strLog = new String(log, 0, totalBytesRead, "UTF-8");
                            arrayMap.put("log", strLog);
                            Gson gson = new Gson();
                            String json = gson.toJson(arrayMap);

                            SendThread sendThread = new SendThread(json.getBytes());
                            sendThread.start();
                        }
                        else if(text.equals("Insertion")) {
                            mStates.mInsertionCount = 0;
                            SharedPreferences.Editor prefsEditor = mThat.mPrefs.edit();
                            prefsEditor.putInt("Insertion", 0);
                            prefsEditor.commit();
                        }
                        else if(text.equals("Clear")) {
                            try {
                                String message = "Cleared Log\n";
                                FileOutputStream outputStream = mThat.getApplicationContext().openFileOutput("events.log", Context.MODE_PRIVATE);
                                outputStream.write(message.getBytes());
                                outputStream.close();

                            } catch (IOException e) {
                                Log.e("debugPrint", "Failed to write to Log file");
                            }
                        }
                        else if(text.contains("Date")) {
                            mThat.i2c_writeRTC(text.substring(text.indexOf(",")+1));
                            mStates.mDateState = true;
                        }
                        else if(text.equals("close")) {
                            close();
                        }
                    }
                } catch (IOException e) {
                    Log.e("ClientManageThread", "Failed to open input stream");
                }
            }
            try {
                close();

            } catch (Exception e) {
                Log.e("ClientMangeThread", "Failed to close socket");
            }
        }

        public void close() {
            run = false;
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e("close ClientMangeThread", "Failed to close socket");
            }

            mDetectUsers.remove(mIpAddress);
            mSendHandler.removeCallbacks(sendRunnable);
        }
    }
}*/


