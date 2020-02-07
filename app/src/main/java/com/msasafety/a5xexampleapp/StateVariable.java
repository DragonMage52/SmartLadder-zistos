package com.msasafety.a5xexampleapp;

import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import com.msasafety.a5xexampleapp.BuildConfig;

import oscP5.OscMessage;

import static android.content.Context.WIFI_SERVICE;

//State variable class to handle changing GPIO when variable value changes.
public class StateVariable {

    String id;
    int temp = 0;
    int meterBatteryLevel = 0;
    int mBatteryLevel = 0;
    double oxygenLevel = 0;
    double carbondioxideLevel = 0;
    double hydrogensulfideLevel = 0;
    double combExLevel = 0;
    int mPort = 0;

    public boolean booting = true;
    Timer mBootTimer;
    TimerTask mBootTask;

    Timer mEarlyTimer;
    TimerTask mEarlyTask;

    Timer mBatteryBlinkTimer;
    TimerTask mBatteryBlinkTask;

    int mBootState = 0;

    boolean mAlarmState = false;
    boolean mWarningState = true;

    boolean mManState = false;
    boolean mLadderState = false;
    boolean mBatteryState = false;
    boolean mBluetoothState = false;
    boolean mMeterState = false;
    boolean mEarlyState = false;
    boolean mMeterBatteryState = false;
    boolean mEarlyDoneState = false;
    boolean mIdleState = false;
    boolean mMeterBatteryDangerState = false;
    boolean mBatteryDangerState = false;

    boolean mAlarmOperator = false;
    boolean mAlarmMeterOff = false;
    boolean mAlarmMeterBattery = false;
    boolean mAlarmBattery = false;

    boolean mDateState = false;

    boolean mWifiState =false;

    Gpio mGood;
    Gpio mWarning;
    Gpio mAlarm;
    Gpio mBattery;
    Gpio mBluetooth;
    Gpio mHorn;

    Handler mBootHandler = new Handler();
    Handler mEarlyHandler = new Handler();
    Handler mUpdateStateHandler = new Handler();
    Handler mBatteryBlinkHandler = new Handler();

    MainActivity mThat;

    boolean mInterrupted = false;

    int mInsertionCount = 0;

    Date mLastCalibration;

    int mCalDueInterval = 0;

    String mAndroidVersion = Build.VERSION.RELEASE;


    public StateVariable(Gpio good, Gpio warning, Gpio alarm, Gpio battery, Gpio bluetooth, Gpio horn, String mID, MainActivity that) {
        mGood = good;
        mWarning = warning;
        mAlarm = alarm;
        mBattery = battery;
        mBluetooth = bluetooth;
        mHorn = horn;
        id = mID.replaceAll("[{}]", "");
        mThat = that;

        boot();
    }

    public void close() {
        try {
            mWarning.close();
            mAlarm.close();
            mGood.close();
            mBattery.close();
            mBluetooth.close();
            mHorn.close();
        } catch (IOException e) {
            Log.d("TEST", "Failed to close GPIO ports");
        }
    }


    public void updateState() {
        boolean tempAlarmState = (mMeterState && mBluetoothState) || (!mBluetoothState && mManState) || (mEarlyState && mManState) || mAlarmState || (!mEarlyDoneState && mManState) || mBatteryDangerState || (mMeterBatteryDangerState && mBluetoothState);
        boolean tempWarningState = (mLadderState && mEarlyState) || !mBluetoothState;
        boolean tempIdleState = mBluetoothState && !mLadderState && !mManState && !mAlarmState;
        boolean tempAlarmOperator = (!mEarlyDoneState && mManState) || mAlarmOperator;
        boolean tempAlarmMeterOff = !mBluetoothState && mManState || mAlarmMeterOff;
        boolean tempAlarmMeterBattery = (mMeterBatteryDangerState && mBluetoothState) || mAlarmMeterBattery;
        boolean tempAlarmBattery = mBatteryDangerState || mAlarmBattery;

        if(mAlarmOperator != tempAlarmOperator && tempAlarmBattery) {
            mThat.debugPrint("Alarm Operator");
        }
        if(mAlarmMeterOff != tempAlarmMeterOff && tempAlarmMeterOff) {
            mThat.debugPrint("Alarm Meter Off");
        }
        if(mAlarmMeterBattery != tempAlarmMeterBattery && tempAlarmMeterBattery) {
            mThat.debugPrint("Alarm Meter Battery");
        }
        if(mAlarmBattery != tempAlarmBattery && tempAlarmBattery){
            mThat.debugPrint("Alarm Battery");
        }

        mAlarmState = tempAlarmState;
        mWarningState = tempWarningState;
        mIdleState = tempIdleState;
        mAlarmOperator = tempAlarmOperator;
        mAlarmMeterOff = tempAlarmMeterOff;
        mAlarmMeterBattery = tempAlarmMeterBattery;
        mAlarmBattery = tempAlarmBattery;

        if (!booting) {
            mUpdateStateHandler.post(mUpdateStateRunnable);
        }
    }

    public void reset() {
        mMeterState = false;
        mAlarmState = false;
        mAlarmOperator = false;
        mAlarmMeterOff = false;
        mAlarmMeterBattery = false;
        mAlarmBattery = false;
        updateState();
        updateBatteryBlink();
    }

    public void setMeterState(boolean state) {
        if (mMeterState != state) {
            if (!mEarlyState) {
                mMeterState = state;
                updateState();
            }
        }
    }

    public void setBatteryState(boolean state) {
        if (mBatteryState != state) {
            mBatteryState = state;
            updateState();

            if(mBatteryState) {
                mThat.debugPrint("Local Battery Low");
            }
        }
    }

    public void setBluetoothState(boolean state) {
        if (mBluetoothState != state) {
            mBluetoothState = state;
            updateState();

            if(mBluetoothState) {
                mThat.debugPrint("Bluetooth Connected");
            }
            else {
                mThat.debugPrint("Bluetooth Disconnected");
            }
        }
    }

    public void setManState(boolean state) {
        if (mManState != state) {
            mManState = state;
            updateState();

            if(mManState) {
                mThat.debugPrint("Man Detected");
            }
        }
    }

    public void setLadderState(boolean state) {
        if (mLadderState != state) {
            mLadderState = state;
            if (state) {
                mInsertionCount++;
                startEarlyEntry();
                mThat.debugPrint("Starting Early Entry");
            } else {
                stopEarlyEntry();
                mEarlyDoneState = false;
                mThat.debugPrint("Ladder Removed");
            }
            updateState();
        }
    }

    public void setMeterBatteryState(boolean state) {
        if (mMeterBatteryState != state) {
            mMeterBatteryState = state;
            updateState();

            if(mMeterBatteryState) {
                mThat.debugPrint("Meter Battery Low");
            }
        }
    }

    public void setMeterBatteryLevel(int level) {
        meterBatteryLevel = level;
        if (meterBatteryLevel < 10) {
            mMeterBatteryDangerState = true;
            setMeterBatteryState(false);
        } else if (meterBatteryLevel < 25) {
            setMeterBatteryState(true);
            mMeterBatteryDangerState = false;
        } else {
            setMeterBatteryState(false);
            mMeterBatteryDangerState = false;
        }
        updateBatteryBlink();
        updateState();
    }

    public void setBatteryLevel(int level) {
        mBatteryLevel = level;
        if (mBatteryLevel < 10) {
            mBatteryDangerState = true;
            setBatteryState(false);
        } else if (mBatteryLevel < 25) {
            setBatteryState(true);
            mBatteryDangerState = false;
        } else {
            setBatteryState(false);
            mBatteryDangerState = false;
        }
        updateBatteryBlink();
        updateState();
    }

    public void updateBatteryBlink() {
        if (mBatteryBlinkTimer == null) {
            if (mBatteryDangerState || (mMeterBatteryDangerState && mBluetoothState)) {
                startBatteryBlink();
            }
        } else {
            if (!mBatteryDangerState && (!mMeterBatteryDangerState || !mBluetoothState)) {
                stopBatteryBlink();
            }
        }
    }

    private Runnable mUpdateStateRunnable = new Runnable() {
        @Override
        public void run() {

            if (mIdleState) {
                try {
                    mGood.setValue(false);
                    mWarning.setValue(true);
                    mAlarm.setValue(false);
                    mBluetooth.setValue(true);
                    mHorn.setValue(false);
                } catch (IOException e) {
                    Log.e("mUpdateStateRunnable", "Error on PeripheralIO API", e);
                }
            } else {
                try {
                    if (mAlarmState) {
                        mHorn.setValue(true);
                        mGood.setValue(false);
                        mAlarm.setValue(true);
                        mWarning.setValue(true);
                    } else if (mWarningState) {
                        mHorn.setValue(false);
                        mGood.setValue(false);
                        mAlarm.setValue(false);
                        mWarning.setValue(false);
                    } else {
                        mHorn.setValue(false);
                        mGood.setValue(true);
                        mAlarm.setValue(false);
                        mWarning.setValue(true);
                    }

                    if (mBluetoothState) {
                        mBluetooth.setValue(true);
                    } else {
                        mBluetooth.setValue(false);
                    }

                    if (mBatteryState || mMeterBatteryState) {
                        mBattery.setValue(true);
                    } else {
                        mBattery.setValue(false);
                    }
                } catch (IOException e) {
                    Log.e("mUpdateStateRunnable", "Error on PeripheralIO API", e);
                }
            }
        }
    };

    static int i = 0;

    public OscMessage getBytes() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

        OscMessage message = new OscMessage("update");

        WifiManager wm = (WifiManager) mThat.getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                       ip = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("SocketException", ex.toString());
        }


        message.add(id);
        message.add(temp + "");
        message.add(meterBatteryLevel + "");
        message.add(mBatteryLevel + "");
        message.add(oxygenLevel + "");
        message.add(carbondioxideLevel + "");
        message.add(hydrogensulfideLevel + "");
        message.add(combExLevel + "");
        message.add(mAlarmState);
        message.add(mWarningState);
        message.add(mManState);
        message.add(mLadderState);
        message.add(mBatteryState);
        message.add(mBluetoothState);
        message.add(mMeterState);
        message.add(mEarlyState);
        message.add(mMeterBatteryState);
        message.add(mEarlyDoneState);
        message.add(mIdleState);
        message.add(mMeterBatteryDangerState);
        message.add(mBatteryDangerState);
        message.add(mAlarmOperator);
        message.add(mAlarmMeterOff);
        message.add(mInsertionCount);
        message.add(BuildConfig.VERSION_NAME);
        if(mLastCalibration != null) {
            message.add(mCalDueInterval + "");
            message.add(dateFormat.format(mLastCalibration));
        }
        else {
            message.add("");
            message.add("");
        }
        message.add(ip);
        message.add(mAndroidVersion);

        return message;

        /*ArrayMap<String, String> arrayMap = new ArrayMap<>();
        arrayMap.put("id", id);
        arrayMap.put("temp", temp + "");
        arrayMap.put("meterBatteryLevel", meterBatteryLevel + "");
        arrayMap.put("batteryLevel", mBatteryLevel + "");
        arrayMap.put("oxygenLevel", oxygenLevel + "");
        arrayMap.put("carbondioxideLevel", carbondioxideLevel + "");
        arrayMap.put("hydrogensulfideLevel", hydrogensulfideLevel + "");
        arrayMap.put("combExLevel", combExLevel + "");
        arrayMap.put("alarmState", mAlarmState + "");
        arrayMap.put("warningState", mWarningState + "");
        arrayMap.put("manState", mManState + "");
        arrayMap.put("ladderState", mLadderState + "");
        arrayMap.put("batteryState", mBatteryState + "");
        arrayMap.put("bluetoothState", mBluetoothState + "");
        arrayMap.put("meterState", mMeterState + "");
        arrayMap.put("earlyState", mEarlyState + "");
        arrayMap.put("meterbatteryState", mMeterBatteryState + "");
        arrayMap.put("earlydoneState", mEarlyDoneState + "");
        arrayMap.put("idleState", mIdleState + "");
        arrayMap.put("meterbatterydangerState", mMeterBatteryDangerState + "");
        arrayMap.put("batterydangerState", mBatteryDangerState + "");
        //arrayMap.put("date", dateFormat.format(Calendar.getInstance().getTime()));
        arrayMap.put("alarmOperator", mAlarmOperator + "");
        arrayMap.put("alarmmeterOff", mAlarmMeterOff + "");
        arrayMap.put("alarmmeterBattery", mAlarmMeterBattery + "");
        arrayMap.put("alarmBattery", mAlarmBattery + "");
        arrayMap.put("port", mPort + "");
        arrayMap.put("insertion", mInsertionCount + "");
        arrayMap.put("version", BuildConfig.VERSION_NAME + "");
        if(mLastCalibration != null) {
            arrayMap.put("lastcalibration", dateFormat.format(mLastCalibration));
            arrayMap.put("caldueinterval", mCalDueInterval + "");
        }
        arrayMap.put("command", "update");

        Gson gson = new Gson();
        String json = gson.toJson(arrayMap);

        //String message = id + "," + mWarningState + "," + mAlarmState + "," + mLadderState + "," + mManState + "," + temp + "," +  meterBatteryLevel + "," + mBatteryLevel + "," + oxygenLevel + "," + hydrogensulfideLevel + "," + carbondioxideLevel + "," + combExLevel + "," + dateFormat.format(Calendar.getInstance().getTime());
        return json.getBytes();*/
    }

    public void boot() {
        //set a new Timer
        mBootTimer = new Timer();

        //initialize the TimerTask's job
        initializeBootTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mBootTimer.schedule(mBootTask, 100, 500);

    }

    public void stopBoot(int interrupt) {
        //stop the timer, if it's not already null
        switch (interrupt) {
            case 0:
                booting = false;
                mInterrupted = false;
                if (mBootTimer != null) {
                    mBootTimer.cancel();
                    mBootTimer = null;
                }
                updateState();
                mThat.booted();
                break;

            case 1:
                mInterrupted = true;
                //mThat.bluetoothDiscovery();
                mBootState = 0;
                break;

            case 2:
                mInterrupted = true;
                mBootState = 3;
                break;
        }


    }

    public void initializeBootTask() {

        mBootTask = new TimerTask() {
            public void run() {

                mBootHandler.post(new Runnable() {
                    public void run() {

                        if (!mInterrupted) {

                            try {
                                switch (mBootState) {
                                    case 0:
                                        mWarning.setValue(true);
                                        break;
                                    case 1:
                                        mGood.setValue(true);
                                        break;
                                    case 2:
                                        mGood.setValue(false);
                                        mWarning.setValue(false);
                                        break;
                                    case 3:
                                        mWarning.setValue(true);
                                        mAlarm.setValue(true);
                                        break;
                                    case 4:
                                        mAlarm.setValue(false);
                                        mBattery.setValue(true);
                                        break;
                                    case 5:
                                        mBattery.setValue(false);
                                        mBluetooth.setValue(true);
                                        break;
                                    case 6:
                                        mBluetooth.setValue(false);
                                        mHorn.setValue(true);
                                        if (mInterrupted) {
                                            mBootState = 7;
                                        }
                                        break;
                                    case 7:
                                        mHorn.setValue(false);
                                        if (mInterrupted) {
                                            mBootState = 6;
                                        }
                                        break;
                                    case 8:
                                        stopBoot(0);
                                        break;

                                }
                            } catch (IOException e) {
                                Log.d("TEST", "Failed GPIO at boot");
                            }
                            mBootState++;
                        } else {
                            try {
                                switch (mBootState) {
                                    case 0:
                                        mBluetooth.setValue(true);
                                        mBootState = 1;
                                        break;

                                    case 1:
                                        mBluetooth.setValue(false);
                                        mBootState = 0;
                                        break;

                                    case 2:
                                        mBluetooth.setValue(true);
                                        if (mBootTimer != null) {
                                            mBootTimer.cancel();
                                            mBootTimer = null;
                                        }
                                        break;

                                    case 3:
                                        mBattery.setValue(true);
                                        mBootState = 4;
                                        break;

                                    case 4:
                                        mBattery.setValue(false);
                                        mBootState = 3;
                                        break;

                                    case 5:
                                        mBattery.setValue(true);
                                        if (mBootTimer != null) {
                                            mBootTimer.cancel();
                                            mBootTimer = null;
                                        }
                                        break;

                                }
                            } catch (IOException e) {
                                Log.d("TEST", "Failed GPIO at boot");
                            }
                        }
                    }
                });
            }
        };
    }

    public void startEarlyEntry() {
        mEarlyState = true;

        //set a new Timer
        mEarlyTimer = new Timer();

        //initialize the TimerTask's job
        initializeEarlyTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mEarlyTimer.schedule(mEarlyTask, 30000, 30000);
    }

    public void stopEarlyEntry() {
        if (mEarlyTimer != null) {
            mEarlyTimer.cancel();
            mEarlyTimer = null;
        }
        mEarlyState = false;
        updateState();
    }

    public void initializeEarlyTask() {

        mEarlyTask = new TimerTask() {
            public void run() {

                mEarlyHandler.post(new Runnable() {
                    public void run() {
                        if(mBluetoothState) {
                            mEarlyDoneState = true;
                            stopEarlyEntry();
                            mThat.debugPrint("Early Entry Done");
                        }
                    }
                });
            }
        };
    }


    public void startBatteryBlink() {
        //set a new Timer
        mBatteryBlinkTimer = new Timer();

        //initialize the TimerTask's job
        initializeBatteryBlinkTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mBatteryBlinkTimer.schedule(mBatteryBlinkTask, 500, 500);
    }

    public void stopBatteryBlink() {
        if (mBatteryBlinkTimer != null) {
            mBatteryBlinkTimer.cancel();
            mBatteryBlinkTimer = null;
        }
        updateState();
    }

    public void initializeBatteryBlinkTask() {

        mBatteryBlinkTask = new TimerTask() {
            public void run() {

                mBatteryBlinkHandler.post(new Runnable() {
                    public void run() {
                        try {
                            mBattery.setValue(!mBattery.getValue());
                        } catch (IOException e) {
                            Log.d("TEST", "Failed battery LED GPIO");
                        }
                    }
                });
            }
        };
    }
}
