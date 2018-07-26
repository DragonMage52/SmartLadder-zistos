package com.msasafety.a5xexampleapp;

import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;
import com.google.gson.Gson;
import com.jflavio1.wificonnector.WifiConnector;
import com.msasafety.a5x.library.A5xBroadcasts;
import com.msasafety.a5x.library.A5xCurrentStatus;
import com.msasafety.a5x.library.A5xInstrumentEvent;
import com.msasafety.a5x.library.A5xInstrumentStatus;
import com.msasafety.a5x.library.A5xSensorEvent;
import com.msasafety.a5x.library.A5xSensorStatus;
import com.msasafety.a5x.library.A5xService;
import com.msasafety.a5x.library.activities.PairingActivity;
import com.msasafety.a5x.library.activities.PairingFragment;
import com.msasafety.interop.networking.devicehandling.BtDevice;
import com.msasafety.interop.networking.devicehandling.IDevice;
import com.msasafety.interop.networking.devicehandling.IDeviceAdapter;
import com.msasafety.interop.networking.devicehandling.IDeviceDiscovery;
import com.rafakob.nsdhelper.NsdHelper;
import com.rafakob.nsdhelper.NsdListener;
import com.rafakob.nsdhelper.NsdService;
import com.rafakob.nsdhelper.NsdType;
import com.thanosfisherman.wifiutils.WifiUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import co.lujun.lmbluetoothsdk.BluetoothController;

import static com.msasafety.interop.networking.bluetooth.BluetoothUtilities.REQUEST_ENABLE_BT;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter mBluetoothAdapter;
    BtDevice mDevice;

    StateVariable mStates;

    PeripheralManager mManager;

    Gpio mReset;
    Gpio mMan;
    Gpio mLadder;

    static final String mResetPin = "BCM5";
    static final String mGoodPin = "BCM19";
    static final String mWarningPin = "BCM20";
    static final String mAlarmPin = "BCM4";
    static final String mBluetoothPin = "BCM24";
    static final String mBatteryPin = "BCM25";
    static final String mManPin = "BCM27";
    static final String mLadderPin = "BCM22";
    static final String mHornPin = "BCM12";

    String mNetworkSSID = "";
    String mNetworkPass = "";
    String mName = "";
    NsdManager mNsdManager;

    boolean mResolving = false;
    NsdServiceInfo mAppService;

    Timer mTimer;
    TimerTask mTimerTask;

    Handler mTimerHandler = new Handler();

    SharedPreferences mPrefs;

    MainActivity mActivity;

    int discoveryOption = 0;

    WifiConnector mConnector;

    I2cDevice mSmartBattery;
    short mFullChargeCapacity = 0;

    Timer mBatteryTimer;
    TimerTask mBatteryTask;

    Handler mBatteryHandler = new Handler();

    TextView mTextDebug;
    ScrollView mScrollDebug;

    Timer mLadderTimer;
    TimerTask mLadderTask;

    Handler mLadderHandler = new Handler();

    Timer mManTimer;
    TimerTask mManTask;

    Handler mManHandler = new Handler();

    ListenThread mListenThread;

    Handler mSendHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNsdManager = (NsdManager) getApplicationContext().getSystemService(Context.NSD_SERVICE);
        mPrefs = getPreferences(MODE_PRIVATE);

        mActivity = this;

        mTextDebug = (TextView) findViewById(R.id.TEXT_STATUS_ID);
        mScrollDebug = (ScrollView) findViewById(R.id.SCROLLER_ID);

        Gson gson = new Gson();
        mName = mPrefs.getString("Name", "");
        mNetworkSSID = mPrefs.getString("SSID", "");
        mNetworkPass = mPrefs.getString("Password", "");

        debugPrint("Pulled Name: " + mName + ", SSID: " + mNetworkSSID + ", Password:: " + mNetworkPass);

        //Check if GPIO ports are available
        try {
            mManager = PeripheralManager.getInstance();
            List<String> portList = mManager.getGpioList();
            if (portList.isEmpty()) {
                Log.d("TEST", "No GPIO port available on this device");
                debugPrint("No GPIO port available on this device");
            }

            Gpio Good;
            Gpio Warning;
            Gpio Alarm;
            Gpio Battery;
            Gpio Bluetooth;
            Gpio Horn;

            //Open and set GPIO ports
            mReset = mManager.openGpio(mResetPin);
            Good = mManager.openGpio(mGoodPin);
            Warning = mManager.openGpio(mWarningPin);
            Alarm = mManager.openGpio(mAlarmPin);
            Battery = mManager.openGpio(mBatteryPin);
            Bluetooth = mManager.openGpio(mBluetoothPin);
            Horn = mManager.openGpio(mHornPin);
            mMan = mManager.openGpio(mManPin);
            mLadder = mManager.openGpio(mLadderPin);

            mReset.setDirection(Gpio.DIRECTION_IN);
            mReset.setActiveType(Gpio.ACTIVE_LOW);
            mReset.setEdgeTriggerType(Gpio.EDGE_BOTH);
            mReset.registerGpioCallback(mResetGpioCallback);

            mMan.setDirection(Gpio.DIRECTION_IN);
            mMan.setActiveType(Gpio.ACTIVE_LOW);
            mMan.setEdgeTriggerType(Gpio.EDGE_BOTH);
            mMan.registerGpioCallback(mManGpioCallback);

            mLadder.setDirection(Gpio.DIRECTION_IN);
            mLadder.setActiveType(Gpio.ACTIVE_LOW);
            mLadder.setEdgeTriggerType(Gpio.EDGE_BOTH);
            mLadder.registerGpioCallback(mLadderGpioCallback);

            Good.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Warning.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Alarm.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Battery.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Bluetooth.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Horn.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);


            mStates = new StateVariable(Good, Warning, Alarm, Battery, Bluetooth, Horn, mName, this);

            List<String> deviceList = mManager.getI2cBusList();
            if (deviceList.isEmpty()) {
                Log.i("TEST", "No I2C bus available on this device.");
                debugPrint("No I2C bus available on this device.");

            } else {
                Log.i("TEST", "List of available devices: " + deviceList);
                debugPrint("List of available devices: " + deviceList);
            }
            mSmartBattery = mManager.openI2cDevice("I2C1", 0x0B);

        } catch (IOException e) {
            Log.d("TEST", "Unable to access GPIO", e);
            debugPrint("Unable to access GPIO");
        }


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterBroadcastReceiver();

        Intent intent = A5xService.createDisconnectIntent(mDevice);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        try {
            mReset.close();
            mLadder.close();
            mMan.close();
        } catch (IOException e) {
            Log.d("TEST", "Failed to close GPIO ports");
            debugPrint("Failed to close GPIO ports");

        }

        try {
            mSmartBattery.close();
        } catch (IOException e) {
            e.printStackTrace();
            mTextDebug.append(e.getMessage() + "\n");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void booted() {
        //Enable Wifi-Calls WifiEnableCheck with result.
        WifiUtils.withContext(getApplicationContext()).enableWifi(this::WifiEnableCheck);
        WifiUtils.enableLog(true);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Gson gson = new Gson();
        String json = mPrefs.getString("Bluetooth Device", "");
        mDevice = gson.fromJson(json, BtDevice.class);

        if (mDevice != null) {
            debugPrint("Connecting to Bluetooth Device Name: " + mDevice.getName());
            startService(mDevice);
            registerBroadcastReceiver();
        }

        byte command = 0x10;
        mFullChargeCapacity = i2c_read(command);

        startBatteryTimer();

    }

    public void debugPrint(String message) {
        if (mTextDebug == null) {
            return;
        }

        //Long tsLong = System.currentTimeMillis() / 1000;
        //String ts = tsLong.toString();

        Log.d("debug print", message);


        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss aa");
        String date = dateFormat.format(Calendar.getInstance().getTime());


        String log = date + ": " + message + '\n';

        try {
            FileOutputStream outputStream = openFileOutput("events.log", Context.MODE_APPEND);
            outputStream.write(log.getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextDebug.append(log);
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        mScrollDebug.post(new Runnable() {
            public void run() {
                mScrollDebug.smoothScrollTo(0, mTextDebug.getBottom());
            }
        });
    }

    public short i2c_read(byte command) {
        if (mSmartBattery == null) {
            stopBatteryTimerTask();
        }

        byte address = (0x0B << 1) | 1;
        byte[] buffer = {1, address};
        byte[] function = {command};
        byte[] start = {1};
        byte[] read = new byte[2];
        short returned = 0;

        try {
            mSmartBattery.write(buffer, buffer.length);
            mSmartBattery.write(function, function.length);
            buffer[1] = (0x0B << 1);
            mSmartBattery.write(buffer, buffer.length);
            mSmartBattery.read(read, read.length);

            returned = (short) ((read[1] << 8) | (read[0] & 0xFF));
            Log.d("TEST", "returned = " + returned);
            debugPrint("I2C read: " + returned);

        } catch (IOException e) {
            Log.d("TEST", "Failed i2c read/write");
            debugPrint("Failed i2c read/write");
            try {
                mSmartBattery.close();
            } catch (IOException e1) {
                Log.d("TEST", "Failed i2c close after read/write fail");
                debugPrint("Failed i2c close after read/write fail");
            }
            try {
                mSmartBattery = mManager.openI2cDevice("I2C1", 0x0B);
            } catch (IOException e1) {
                Log.d("TEST", "Failed i2c reopen");
                debugPrint("Failed i2c reopen");

            }
        }
        return returned;
    }

    public void bluetoothDiscovery(int option) {
        discoveryOption = option;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        IntentFilter mFilterFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, mFilterFound);
        IntentFilter mFilterFinished = new IntentFilter(mBluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mFinished, mFilterFinished);

        if(option == 1) {
            debugPrint("Starting Bluetooth Discovery for Meter Pairing");
        }
        else if(option == 2) {
            debugPrint("Starting Bluetooth Discovery for Phone Pairing");
        }
        debugPrint("Starting Bluetooth Discovery");
        mBluetoothAdapter.startDiscovery();
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            debugPrint("Getting Bluetooth Device Intent");
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                String deviceName = device.getName();

                Log.d("TEST", "Found: " + deviceName);
                debugPrint("Bluetooth Found: " + deviceName);

                if (deviceName != null) {
                    if (discoveryOption == 1) {
                        if (deviceName.indexOf("A4X-") != -1) {
                            Log.d("TEST", "Found A4X");
                            debugPrint("Found A4X");
                            mBluetoothAdapter.cancelDiscovery();
                            //Connect to device.
                            mDevice = new BtDevice(device.getAddress(), device.getName());

                            SharedPreferences.Editor prefsEditor = mPrefs.edit();
                            Gson gson = new Gson();
                            String json = gson.toJson(mDevice);
                            prefsEditor.putString("Bluetooth Device", json);
                            prefsEditor.commit();

                            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                            if (pairedDevices.size() > 0) {
                                for (BluetoothDevice pairedDevice : pairedDevices) {
                                    try {
                                        if (pairedDevice.getName() != mDevice.getName()) {
                                            Method m = pairedDevice.getClass()
                                                    .getMethod("removeBond", (Class[]) null);
                                            m.invoke(device, (Object[]) null);
                                        }
                                    } catch (Exception e) {
                                        Log.e("TEST", e.getMessage());
                                    }
                                }
                            }

                            //startService(mDevice);
                            //registerBroadcastReceiver();
                            mStates.mBootState = 2;
                            debugPrint("Continuing to Normal Boot");
                            mStates.stopBoot(0);
                        }
                    } else if (discoveryOption == 2) {
                        if (deviceName.indexOf("ZistosSafeAirLadderApp") != -1) {
                            Log.d("TEST", "Found ZistosSafeAirLadderApp");
                            debugPrint("Found ZistosSafeAirLadderApp");
                            mBluetoothAdapter.cancelDiscovery();
                            mDevice = new BtDevice(device.getAddress(), device.getName());

                            ConnectThread connectThread = new ConnectThread(device, mActivity);
                            connectThread.start();
                        }
                    }
                }
            }
        }
    };

    //Discovery Finished.
    private final BroadcastReceiver mFinished = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("TEST", "Discovery Finished");
            debugPrint("Discovery Finished");
            //Restart discovery is no device found, else connect to the device.
            if (mDevice == null) {
                debugPrint("Restarting Bluetooth Discovery");
                mBluetoothAdapter.startDiscovery();
            }

        }
    };

    //Get status from Meter.
    private final BroadcastReceiver mA4XReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            IDevice device;
            switch (intent.getAction()) {
                case A5xBroadcasts.A5X_CURRENT_STATUS:
                    device = intent.getParcelableExtra(A5xBroadcasts.EXTRA_A5X_DEVICE);
                    A5xCurrentStatus mStatus = intent.getParcelableExtra(A5xBroadcasts.EXTRA_A5X_STATUS);
                    if (device.equals(mDevice))
                        updateStatus(mStatus);
                    break;
                case A5xBroadcasts.A5X_SENSOR_EVENT:
                    A5xSensorEvent event = intent.getParcelableExtra(A5xBroadcasts.A5X_SENSOR_EVENT);
                    if (event != null)
                        Log.d("TEST", "Sensor Event Type: " + event.getType());
                    break;
                case A5xBroadcasts.A5X_INST_EVENT:
                    A5xInstrumentEvent instrumentEvent = intent.getParcelableExtra(A5xBroadcasts.A5X_INST_EVENT);
                    if (instrumentEvent != null)
                        Log.d("TEST", "Instrument Event Type: " + instrumentEvent.getType());
                    break;
                case A5xService.A5X_SERVICE_DEVICE_REMOVED:
                    device = intent.getParcelableExtra(A5xBroadcasts.EXTRA_A5X_DEVICE);
                    if (device.equals(mDevice)) {
                        mDevice = null;

                    }
                    mStates.setBluetoothState(false);
                    break;
            }
        }
    };

    public void saveWifiSettings(String name, String ssid, String password) {

        mStates.id = name;
        mNetworkSSID = ssid;
        mNetworkPass = password;

        SharedPreferences.Editor prefsEditor = mPrefs.edit();
        Gson gson = new Gson();
        prefsEditor.putString("Name", name);
        prefsEditor.putString("SSID", ssid);
        prefsEditor.putString("Password", password);
        prefsEditor.commit();
        mStates.mBootState = 5;
    }

    //Wifi Enable callback method.
    public void WifiEnableCheck(boolean isSuccess) {
        if (isSuccess) {
            Log.d("TEST", "Wifi Enable Success");
            debugPrint("Wifi Enable Success");
            if (mNetworkPass != "" && mNetworkSSID != "") {
                //WifiUtils.withContext(getApplicationContext()).scanWifi(this::WifiGetScanResults).start();
                WifiUtils.withContext(getApplicationContext())
                        .connectWith(mNetworkSSID, mNetworkPass)
                        .onConnectionResult(this::WifiConnectCheck)
                        .start();

            }
        } else {
            Log.d("TEST", "Wifi Enable Failed");
            debugPrint("Wifi Enable Failed");
        }
    }

    //Wifi Connected callback method.
    public void WifiConnectCheck(boolean isSuccess) {
        if (isSuccess) {
            Log.d("TEST", "Wifi Connect Success");
            debugPrint("Wifi Connect Success");
            //mNsdManager.discoverServices("_zvs._udp.", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
            mSendHandler.post(sendRunnable);
        } else {
            Log.d("TEST", "Wifi Connect Failed");
            debugPrint("Wifi Connect Failed");
            WifiUtils.withContext(getApplicationContext())
                    .connectWith(mNetworkSSID, mNetworkPass)
                    .onConnectionResult(this::WifiConnectCheck)
                    .start();

        }
    }

    //Callback method for Wifi Scan
    private void WifiGetScanResults(@NonNull final List<ScanResult> results) {
        if (results.isEmpty()) {
            Log.d("TEST", "SCAN RESULTS IT'S EMPTY");
            return;
        }

        int i = 0;
        for (ScanResult s : results) {
            if (s.SSID.equals(mNetworkSSID)) {
                final int pos = i;
                WifiUtils.withContext(getApplicationContext())
                        .connectWithScanResult(mNetworkPass, scanResults -> results.get(pos))
                        .onConnectionResult(this::WifiConnectCheck)
                        .start();
            }
            i++;
        }

        Log.d("TEST", "GOT SCAN RESULTS " + results);
    }

    //Start Meter service.
    public void startService(IDevice device) {
        Intent i = A5xService.createServiceIntent(MainActivity.this, device);
        startService(i);
    }

    //Register Intent receiver for Meter.
    public void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(A5xBroadcasts.A5X_CURRENT_STATUS);
        filter.addAction(A5xService.A5X_SERVICE_DEVICE_REMOVED);
        filter.addAction(A5xBroadcasts.A5X_INST_EVENT);
        filter.addAction(A5xBroadcasts.A5X_SENSOR_EVENT);
        filter.addAction(A5xBroadcasts.A5X_COMMAND_RESULTS);
        filter.addAction(A5xBroadcasts.A5X_SET_READING_FREQUENCY_RESULTS);

        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mA4XReceiver, filter);

        getDeviceStatuses(mDevice);
    }

    public void unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mA4XReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mFinished);
    }

    //Get meter status.
    public void getDeviceStatuses(IDevice device) {
        Intent intent = new Intent(A5xBroadcasts.A5X_REQUEST_CURRENT_STATUS);
        intent.putExtra(A5xBroadcasts.EXTRA_A5X_DEVICE, device);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    //Update GPIO based on meter update.
    public void updateStatus(A5xCurrentStatus mStatus) {
        if (mStatus == null) {
            return;
        }

        if (mStatus.isConnected()) {
            mStates.setBluetoothState(true);
            if (mStatus.getLastStatus() != null) {

                A5xInstrumentStatus lastStatus = mStatus.getLastStatus();
                //If alarm or high Temperature
                mStates.temp = lastStatus.getTemperature() * 9 / 5 + 32;
                mStates.setMeterBatteryLevel(lastStatus.getBatteryPercentRemaning());

                /*if (mStates.temp >= 120) {
                    mStates.setMeterState(true);
                }*/

                for (int i = 0; i < mStatus.getLastStatus().getNumberInstalledSensors(); i++) {
                    A5xSensorStatus sensorStatus = mStatus.getLastStatus().getSensor(i);

                    switch (i) {
                        case 0:
                            mStates.carbondioxideLevel = sensorStatus.getReading();
                            if (sensorStatus.hasAlarmOrWarning()) {
                                mStates.setMeterState(true);
                            }
                            break;

                        case 1:
                            mStates.oxygenLevel = sensorStatus.getReading();
                            if (sensorStatus.hasAlarmOrWarning()) {
                                mStates.setMeterState(true);
                            }
                            break;
                        case 2:
                            mStates.hydrogensulfideLevel = sensorStatus.getReading();
                            if (sensorStatus.hasAlarmOrWarning()) {
                                mStates.setMeterState(true);
                            }
                            break;
                        case 3:
                            mStates.combExLevel = sensorStatus.getReading();
                            if (sensorStatus.hasAlarmOrWarning()) {
                                mStates.setMeterState(true);
                            }
                            break;
                    }
                }
            }
        } else {
            sendPing();
            mStates.setBluetoothState(false);
        }
    }

    private void sendPing() {
        Intent intent = new Intent(A5xBroadcasts.A5X_REQUEST_PING);
        intent.putExtra(A5xBroadcasts.EXTRA_A5X_DEVICE, mDevice);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    //GPIO callback method for reset swtich.
    private GpioCallback mResetGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                if (gpio.getValue()) {
                    if (mStates.booting && !mStates.mInterrupted) {
                        debugPrint("Booting into Meter Pairing");
                        mStates.stopBoot(1);
                        bluetoothDiscovery(1);
                    } else if (!mStates.mInterrupted) {
                        mStates.reset();
                    }
                } else if (!mStates.mInterrupted) {
                    if (mStates.booting) {
                        debugPrint("Booting into Phone Pairing");
                        mStates.stopBoot(2);
                        bluetoothDiscovery(2);
                    }
                }
            } catch (IOException e) {
                Log.d("TEST", "Failed to read reset switch");
                debugPrint("Failed to read reset switch");
            }

            return true;
        }
    };

    private GpioCallback mManGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                if (gpio.getValue()) {
                    startManTimer();
                    Log.d("TEST", "Starting Man Timer");
                    //mStates.setManState(true);
                } else {
                    if (mManTimer != null) {
                        stopManTimer();
                    }
                    mStates.setManState(false);
                }
            } catch (IOException e) {
                Log.d("TEST", "Failed to read man switch");
                debugPrint("Failed to read man switch");
            }

            return true;
        }
    };

    private GpioCallback mLadderGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                if (gpio.getValue()) {
                    if (mLadderTimer != null) {
                        stopLadderTimer();
                    }
                    mStates.setLadderState(true);
                } else {
                    startLadderTimer();
                    //mStates.setLadderState(false);
                }
            } catch (IOException e) {
                Log.d("TEST", "Failed to read ladder switch");
                debugPrint("Failed to read ladder switch");
            }
            return true;
        }
    };

    public NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

        // Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
            Log.d("TEST", "Service discovery started");
            debugPrint("Service discovery started");
        }

        @Override
        public void onServiceFound(NsdServiceInfo service) {
            // A service was found! Do something with it.
            Log.d("TEST", "Service discovery success" + service);
            debugPrint("Service discovery success" + service);
            if (!mResolving) {
                mResolving = true;
                try {
                    mNsdManager.resolveService(service, mResolveListener);
                } catch (IllegalArgumentException e) {
                    Log.d("TEST", "listener already in use");
                    debugPrint("listener already in use");
                }
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e("TEST", "service lost" + service);
            debugPrint("service lost" + service);
            stoptimertask();
            mAppService = null;
            mResolving = false;
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);

            if (mListenThread != null) {
                mListenThread.cancel();
            }
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.i("TEST", "Discovery stopped: " + serviceType);
            debugPrint("Discovery stopped: " + serviceType);
            if (mAppService == null) {
                mNsdManager.discoverServices("_zvs._udp.", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
            }
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.e("TEST", "Discovery failed: Error code:" + errorCode);
            debugPrint("Discovery failed: Error code:" + errorCode);
            //mNsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.e("TEST", "Discovery failed: Error code:" + errorCode);
            debugPrint("Discovery failed: Error code:" + errorCode);
            //mNsdManager.stopServiceDiscovery(this);
        }
    };

    private NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e("TEST", "Resolve failed" + errorCode);
            debugPrint("Resolve failed" + errorCode);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.e("TEST", "Resolve Succeeded. " + serviceInfo);
            debugPrint("Resolve Succeeded. " + serviceInfo);
            mResolving = false;
            mAppService = serviceInfo;

            //startTimer();

            mListenThread = new ListenThread();
            mListenThread.start();

        }
    };

    public void initializeManTimerTask() {
        mManTask = new TimerTask() {
            @Override
            public void run() {
                mManHandler.post(new Runnable() {
                    public void run() {
                        try {
                            if (mMan.getValue()) {
                                mStates.setManState(true);
                            } else {
                                mStates.setManState(false);
                            }
                        } catch (IOException e) {
                            Log.d("TEST", "Failed to read man switch");
                            debugPrint("Failed to read man switch");
                        }
                        stopManTimer();
                    }
                });
            }
        };
    }

    public void startManTimer() {
        //set a new Timer
        if (mManTimer != null) {
            stopManTimer();
        }
        mManTimer = new Timer();

        //initialize the TimerTask's job
        initializeManTimerTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mManTimer.schedule(mManTask, 1000, 1000);
    }

    public void stopManTimer() {
        //stop the timer, if it's not already null
        if (mManTimer != null) {
            mManTimer.cancel();
            mManTimer = null;
        }
    }

    public void initializeLadderTimerTask() {
        mLadderTask = new TimerTask() {
            @Override
            public void run() {
                mLadderHandler.post(new Runnable() {
                    public void run() {
                        try {
                            if (mLadder.getValue()) {
                                mStates.setLadderState(true);
                            } else {
                                mStates.setLadderState(false);
                            }
                        } catch (IOException e) {
                            Log.d("TEST", "Failed to read ladder switch");
                            debugPrint("Failed to read ladder switch");
                        }
                        stopLadderTimer();
                    }
                });
            }
        };
    }

    public void startLadderTimer() {
        //set a new Timer
        if (mLadderTimer != null) {
            stopLadderTimer();
        }
        mLadderTimer = new Timer();

        //initialize the TimerTask's job
        initializeLadderTimerTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mLadderTimer.schedule(mLadderTask, 2000, 1000);
    }

    public void stopLadderTimer() {
        //stop the timer, if it's not already null
        if (mLadderTimer != null) {
            mLadderTimer.cancel();
            mLadderTimer = null;
        }
    }

    public Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
            byte[] message = mStates.getBytes();
            try {
                debugPrint("Sending via Wifi " + new String(message, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            try {
                InetAddress group = InetAddress.getByName("239.52.8.234");
                DatagramPacket packet = new DatagramPacket(message, message.length, group, 52867);
                SendThread sendThread = new SendThread(packet);
                sendThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mSendHandler.postDelayed(sendRunnable, 2000);

        }
    };

    public void startTimer() {
        //set a new Timer
        mTimer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mTimer.schedule(mTimerTask, 2000, 2000);
    }

    public void stoptimertask() {
        //stop the timer, if it's not already null
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }


    public void initializeTimerTask() {

        mTimerTask = new TimerTask() {
            public void run() {

                mTimerHandler.post(new Runnable() {
                    public void run() {

                        byte[] message = mStates.getBytes();
                        try {
                            debugPrint("Sending via Wifi " + new String(message, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        DatagramPacket packet = new DatagramPacket(message, message.length);
                        if (mAppService != null ) {
                            if(mAppService.getHost() != null && mAppService.getPort() != 0){
                                packet.setAddress(mAppService.getHost());
                                packet.setPort(mAppService.getPort());
                                SendThread sendThread = new SendThread(packet);
                                sendThread.start();
                            }
                            else {
                                stoptimertask();
                            }
                        } else {
                            stoptimertask();
                        }
                    }
                });
            }
        };
    }

    public void startBatteryTimer() {
        //set a new Timer
        mBatteryTimer = new Timer();

        //initialize the TimerTask's job
        initializeBatteryTimerTask();

        //schedule the timer, after the first 100ms the TimerTask will run every 10000ms
        mBatteryTimer.schedule(mBatteryTask, 5000, 10000); //
    }

    public void stopBatteryTimerTask() {
        //stop the timer, if it's not already null
        if (mBatteryTimer != null) {
            mBatteryTimer.cancel();
            mBatteryTimer = null;
        }
    }


    public void initializeBatteryTimerTask() {

        mBatteryTask = new TimerTask() {
            public void run() {

                mBatteryHandler.post(new Runnable() {
                    public void run() {
                        byte command = 0x0f;
                        if (mFullChargeCapacity == 0) {
                            command = 0x10;
                        }
                        short capacity = i2c_read(command);
                        if (mFullChargeCapacity == 0) {
                            mFullChargeCapacity = capacity;
                        } else if (capacity != 0) {
                            //mStates.mBatteryLevel = (int)  (100.0 * ((1.0 * capacity) / (mFullChargeCapacity)));
                            mStates.setBatteryLevel((int) (100.0 * ((1.0 * capacity) / (mFullChargeCapacity))));
                        }
                        Log.d("TEST", "battery level: " + mStates.mBatteryLevel);
                        debugPrint("battery level: " + mStates.mBatteryLevel);
                    }
                });
            }
        };
    }

    class SendThread extends Thread {

        private DatagramPacket mPacket;

        public SendThread(DatagramPacket packet) {
            mPacket = packet;
        }

        public void run() {
            try {
                //DatagramSocket udpSocket = new DatagramSocket();
                MulticastSocket socket = new MulticastSocket();
                socket.send(mPacket);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ListenThread extends Thread {

        private boolean run = false;

        public ListenThread() {

        }

        public void run() {
            run = true;
            while (run) {
                try {
                    DatagramSocket udpInSocket = new DatagramSocket();
                    mStates.mPort = udpInSocket.getLocalPort();
                    byte[] message = new byte[1000];
                    DatagramPacket packetIn = new DatagramPacket(message, message.length);
                    udpInSocket.receive(packetIn);
                    String text = new String(message, 0, packetIn.getLength());
                    Log.d("TEST", "UDP Received:" + text);

                    if (text.equals("Log")) {
                        FileInputStream inputStream = openFileInput("events.log");
                        byte[] log = new byte[5000];
                        inputStream.read(log);
                        inputStream.close();

                        ArrayMap<String, String> arrayMap = new ArrayMap<>();
                        arrayMap.put("command", "log");
                        String strLog = new String(log, 0, log.length);
                        arrayMap.put("log", strLog);
                        Gson gson = new Gson();
                        String json = gson.toJson(arrayMap);

                        DatagramPacket packetOut = new DatagramPacket(json.getBytes(), json.getBytes().length);
                        if (mAppService != null) {
                            packetOut.setAddress(mAppService.getHost());
                            packetOut.setPort(mAppService.getPort());
                        }
                        SendThread sendThread = new SendThread(packetOut);
                        sendThread.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel() {
            run = false;
        }
    }
}


class ConnectThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final BluetoothDevice mmDevice;
    private MainActivity Activity;

    public ConnectThread(BluetoothDevice device, MainActivity activity) {
        // Use a temporary object that is later assigned to mmSocket
        // because mmSocket is final.
        BluetoothSocket tmp = null;
        mmDevice = device;
        Activity = activity;

        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // MY_UUID is the app's UUID string, also used in the server code.
            tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("3a44c900-519a-11e8-b566-0800200c9a66"));
            //UUID.fromString("4f651c40-519a-11e8-b566-0800200c9a66")
            //Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class });
            //tmp = (BluetoothSocket) m.invoke(device,1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mmSocket = tmp;
    }

    public void run() {
        // Cancel discovery because it otherwise slows down the connection.
        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mmSocket.connect();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            try {
                mmSocket.close();
            } catch (IOException closeException) {
                Log.e("TEST", "Could not close the client socket", closeException);
                Activity.debugPrint("Could not close the client socket");
            }
            return;
        }

        // The connection attempt succeeded. Perform work associated with
        // the connection in a separate thread.
        try {
            byte[] inBuffer = new byte[250];
            InputStream in = mmSocket.getInputStream();
            int count = in.read(inBuffer);
            String message = new String(inBuffer, 0, count);
            Log.d("TEST", "Read: " + message);
            Activity.debugPrint("Read: " + message);

            Gson gson = new Gson();
            ArrayMap<String, String> arrayMap = gson.fromJson(message, ArrayMap.class);

            //String[] separated = message.split(",");

            Activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Activity.saveWifiSettings(arrayMap.get("name"), arrayMap.get("ssid"), arrayMap.get("password"));
                    Activity.mStates.stopBoot(0);
                }
            });

            cancel();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e("TEST", "Could not close the client socket", e);
        }
    }
}


