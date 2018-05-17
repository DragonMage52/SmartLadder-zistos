package com.msasafety.a5xexampleapp;

import android.os.Handler;
import android.util.Log;

import com.google.android.things.pio.Gpio;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

//State variable class to handle changing GPIO when variable value changes.
public class StateVariable {

    String id;
    double weight = 0;
    int temp = 0;
    int meterBatteryLevel = 0;
    double oxygenLevel = 0;
    double carbondioxideLevel = 0;
    double hydrogensulfideLevel = 0;
    double combExLevel = 0;

    public boolean booting = true;
    Timer mBootTimer;
    TimerTask mBootTask;

    Timer mEarlyTimer;
    TimerTask mEarlyTask;
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

    Gpio mGood;
    Gpio mWarning;
    Gpio mAlarm;
    Gpio mBattery;
    Gpio mBluetooth;
    Gpio mHorn;

    Handler mBootHandler = new Handler();
    Handler mEarlyHandler = new Handler();
    Handler mUpdateStateHandler = new Handler();

    MainActivity mThat;

    boolean mInterrupted = false;


    public StateVariable(Gpio good, Gpio warning, Gpio alarm, Gpio battery, Gpio bluetooth, Gpio horn, String mID, MainActivity that) {
        mGood = good;
        mWarning = warning;
        mAlarm = alarm;
        mBattery = battery;
        mBluetooth = bluetooth;
        mHorn = horn;
        id = mID;
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
        }
        catch (IOException e) {
            Log.d("TEST", "Failed to close GPIO ports");
        }
    }


    public void updateState() {
        mAlarmState = mMeterState || (!mBluetoothState && (mManState || mLadderState)) || (mEarlyState && mManState) | mAlarmState | (!mEarlyDoneState && mManState);
        mWarningState = mBatteryState || (mLadderState && mEarlyState) || !mBluetoothState | mMeterBatteryState;

        if(!booting) {
            mUpdateStateHandler.post(mUpdateStateRunnable);
        }
    }

    public void reset() {
        mMeterState = false;
        mAlarmState = false;
        updateState();
    }

    public void setMeterState(boolean state) {
        if(mMeterState != state) {
            if(!mEarlyState) {
                mMeterState = state;
                updateState();
            }
        }
    }

    public void setBatteryState(boolean state) {
        if(mBatteryState != state) {
            mBatteryState = state;
            updateState();
        }
    }

    public void setBluetoothState(boolean state) {
        if(mBluetoothState != state) {
            mBluetoothState = state;
            updateState();
        }
    }

    public void setManState(boolean state) {
        if(mManState != state) {
            mManState = state;
            updateState();
        }
    }

    public void setLadderState(boolean state) {
        if(mLadderState != state) {
            mLadderState = state;
            if(state) {
                startEarlyEntry();
            }
            else {
                stopEarlyEntry();
                mEarlyDoneState = false;
            }
            updateState();
        }
    }

    public void setMeterBatteryState(boolean state) {
        if(mMeterBatteryState != state) {
            mMeterBatteryState = state;
            updateState();
        }
    }


    private Runnable mUpdateStateRunnable = new Runnable() {
        @Override
        public void run() {

            try {
                if(mAlarmState) {
                    mHorn.setValue(true);
                    mGood.setValue(false);
                    mAlarm.setValue(true);
                    mWarning.setValue(true);
                }
                else if(mWarningState) {
                    mHorn.setValue(false);
                    mGood.setValue(false);
                    mAlarm.setValue(false);
                    mWarning.setValue(false);
                }
                else {
                    mHorn.setValue(false);
                    mGood.setValue(true);
                    mAlarm.setValue(false);
                    mWarning.setValue(true);
                }

                if(mBluetoothState) {
                    mBluetooth.setValue(true);
                }
                else {
                    mBluetooth.setValue(false);
                }

                if(mBatteryState) {
                    mBattery.setValue(true);
                }
                else {
                    mBattery.setValue(false);
                }
            } catch (IOException e) {
                Log.e("TEST", "Error on PeripheralIO API", e);
            }
        }
    };

    public byte[] getBytes() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss aa");
        String message = id + "," + mWarningState + "," + mAlarmState + "," + mLadderState + "," + mManState + "," + temp + "," +  meterBatteryLevel + "," +oxygenLevel + "," + hydrogensulfideLevel + "," + carbondioxideLevel + "," + combExLevel + "," + dateFormat.format(Calendar.getInstance().getTime());
        return message.getBytes();
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
                        }
                        else {
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
                                        mHorn.setValue(true);
                                        mBootState = 4;
                                        break;

                                    case 4:
                                        mHorn.setValue(false);
                                        mBootState = 3;
                                        break;

                                    case 5:
                                        mHorn.setValue(true);
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
        mEarlyTimer.schedule(mEarlyTask, 5000, 30000);
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
                        mEarlyDoneState = true;
                        stopEarlyEntry();
                    }
                });
            }
        };
    }

}
