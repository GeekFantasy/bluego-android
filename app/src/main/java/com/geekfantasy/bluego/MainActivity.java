package com.geekfantasy.bluego;

import static com.geekfantasy.bluego.ModeSettingHelper.getPreferenceData;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.preference.PreferenceManager;

import com.geekfantasy.bluego.ble.BLEManager;
import com.geekfantasy.bluego.ble.OnBleConnectListener;
import com.geekfantasy.bluego.databinding.ActivityMainBinding;
import com.geekfantasy.bluego.permission.PermissionListener;
import com.geekfantasy.bluego.permission.PermissionRequest;
import com.geekfantasy.bluego.util.TypeConversion;

import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "Bluego_Main";

    public static final String SERVICE_UUID = "0000ef00-0000-1000-8000-00805f9b34fb";
    public static final String CURR_MODE_UUID = "0000ef01-0000-1000-8000-00805f9b34fb";  // current mode read and write char
    public static final String MODE_SETTING_UUID = "0000ef02-0000-1000-8000-00805f9b34fb";  //mode setting read and write char
    private static final String DEVICE_BOND = "device_bond";
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String LAST_SYNC_TIME = "last_sync_time";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String MODE_PREFIX_AIR_MOUSE = "am_";
    private static final String MODE_PREFIX_MLTI_FUNC_SWITCH = "mfs_";
    private static final String MODE_PREFIX_GESTURE = "ges_";
    private static final String MODE_PREFIX_CUSTOME_1 = "cm1_";
    private static final String MODE_PREFIX_CUSTOME_2 = "cm2_";

    private static final int CONNECT_CONNECTING = 0x00;
    private static final int CONNECT_SUCCESS = 0x01;
    private static final int CONNECT_FAILURE = 0x02;
    private static final int DISCONNECT_SUCCESS = 0x03;
    private static final int SEND_SUCCESS = 0x04;
    private static final int SEND_FAILURE = 0x05;
    private static final int RECEIVE_SUCCESS = 0x06;
    private static final int RECEIVE_FAILURE = 0x07;
    private static final int START_DISCOVERY = 0x08;
    private static final int STOP_DISCOVERY = 0x09;
    private static final int DISCOVERY_DEVICE = 0x0A;
    private static final int DISCOVERY_OUT_TIME = 0x0B;
    private static final int SELECT_DEVICE = 0x0C;
    private static final int BT_OPENED = 0x0D;
    private static final int BT_CLOSED = 0x0E;

    private static final int CONNECT_STATE_CONNECTED = 0X00;
    private static final int CONNECT_STATE_CONNECTING = 0X01;
    private static final int CONNECT_STATE_DISCONNECTED = 0X02;
    private static final int CONNECT_STATE_NO_DEIVCE = 0X03;
    private final Animation animation = new RotateAnimation(0.0f, 360.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    private ActivityMainBinding binding;
    private PickerView pickerView;
    private BLEManager bleManager;
    private boolean btConnected = false;
    private SharedPreferences device_bond_preference;
    private SharedPreferences device_mode_preference;

    private List<String> deniedPermissionList = new ArrayList<>();
    private String[] requestPermissionArray = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CONNECT_CONNECTING:
                    setDeviceConnectState(CONNECT_STATE_CONNECTING);
                    break;
                case CONNECT_FAILURE: //连接失败
                    Log.d(TAG, "连接失败");
                    btConnected = false;
                    setDeviceConnectState(CONNECT_STATE_DISCONNECTED);
                    break;

                case CONNECT_SUCCESS:  //连接成功
                    Log.d(TAG, "连接成功");
                    btConnected = true;
                    setDeviceConnectState(CONNECT_STATE_CONNECTED);
                    break;

                case DISCONNECT_SUCCESS:
                    Log.d(TAG, "断开成功");
                    btConnected = false;
                    setDeviceConnectState(CONNECT_STATE_DISCONNECTED);
                    break;

                case SEND_FAILURE: //发送失败
                    byte[] sendBufFail = (byte[]) msg.obj;
                    String sendFail = TypeConversion.bytes2HexString(sendBufFail, sendBufFail.length);
                    Toast.makeText(MainActivity.this, R.string.mode_setting_failed, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "数据发送失败，长度" + sendBufFail.length + "--> " + sendFail);
                    break;

                case SEND_SUCCESS:  //发送成功
                    byte[] sendBufSuc = (byte[]) msg.obj;
                    String sendResult = TypeConversion.bytes2HexString(sendBufSuc, sendBufSuc.length);
                    binding.fabSync.clearAnimation();
                    Toast.makeText(MainActivity.this, R.string.mode_setting_success, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "发送数据成功，长度" + sendBufSuc.length + "--> " + sendResult);
                    break;

                case RECEIVE_FAILURE: //接收失败
                    String receiveError = (String) msg.obj;
                    binding.fabSync.clearAnimation();
                    Log.d(TAG, "数据接收失败" + receiveError);
                    break;

                case RECEIVE_SUCCESS:  //接收成功
                    byte[] recBufSuc = (byte[]) msg.obj;
                    String receiveResult = TypeConversion.bytes2HexString(recBufSuc, recBufSuc.length);
                    Log.d(TAG, "接收数据成功，长度" + recBufSuc.length + "--> " + receiveResult);
                    break;

                case BT_CLOSED:
                    Log.d(TAG, "系统蓝牙已关闭");
                    btConnected = false;
                    Toast.makeText(MainActivity.this, R.string.sys_bt_disabled, Toast.LENGTH_SHORT).show();
                    setDeviceConnectState(CONNECT_STATE_DISCONNECTED);
                    break;

                case BT_OPENED:
                    Log.d(TAG, "系统蓝牙已打开");
                    Toast.makeText(MainActivity.this, R.string.sys_bt_enabled, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private final OnBleConnectListener onBleConnectListener = new OnBleConnectListener() {
        @Override
        public void onConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {
            Message message = new Message();
            message.what = CONNECT_CONNECTING;
            mHandler.sendMessage(message);
        }

        @Override
        public void onConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            // Since the communication works after serviced discovered, so name connection success on service discovered
        }

        @Override
        public void onConnectFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, String exception, int status) {
            Message message = new Message();
            message.what = CONNECT_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onDisConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onDisConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            Message message = new Message();
            message.what = DISCONNECT_SUCCESS;
            message.obj = status;
            mHandler.sendMessage(message);
        }

        @Override
        public void onServiceDiscoverySucceed(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            // Since the communication works after serviced discovered, so name connection success here
            Message message = new Message();
            message.what = CONNECT_SUCCESS;
            mHandler.sendMessage(message);
        }

        @Override
        public void onServiceDiscoveryFailed(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, String failMsg) {
            Message message = new Message();
            message.what = CONNECT_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReceiveMessage(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, BluetoothGattCharacteristic characteristic, byte[] msg) {
            Message message = new Message();
            message.what = RECEIVE_SUCCESS;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReceiveError(String errorMsg) {
            Message message = new Message();
            message.what = RECEIVE_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onWriteSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, byte[] msg) {
            Message message = new Message();
            message.what = SEND_SUCCESS;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onWriteFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, byte[] msg, String errorMsg) {
            Message message = new Message();
            message.what = SEND_FAILURE;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReadRssi(BluetoothGatt bluetoothGatt, int Rssi, int status) {

        }

        @Override
        public void onMTUSetSuccess(String successMTU, int newMtu) {

        }

        @Override
        public void onMTUSetFailure(String failMTU) {

        }
    };
    ActivityResultLauncher<Intent> mStartForBondDevice = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent intent = result.getData();
                        String device_address = intent.getStringExtra("device-address");
                        Log.d(TAG, "Bond device address:" + device_address);

                        if (bleManager == null) {
                            Log.i(TAG, "There is not ble manager initialed.");
                            return;
                        }

                        BluetoothManager bluetoothManager = bleManager.getBluetoothManager();
                        Log.d(TAG, "Successfully got bleManager!!");

                        if (bluetoothManager != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                                BluetoothDevice bluetoothDevice = bluetoothManager.getAdapter().getRemoteLeDevice(device_address, BluetoothDevice.ADDRESS_TYPE_PUBLIC);
                                if (bluetoothDevice != null) {
                                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                        requestPermissions();
                                    }
                                    if (bluetoothDevice.createBond()) {
                                        Log.d(TAG, "Successfully create bond to ble device with name:" + bluetoothDevice.getName());
                                        SharedPreferences.Editor editor = device_bond_preference.edit();
                                        editor.putString(DEVICE_ADDRESS, device_address);
                                        editor.commit();

                                        initAndConnectBleDevice();
                                    }
                                    else {
                                        Log.d(TAG, "Failed to create bond to ble device!!");
                                    }
                                }
                            }
                        }
                    }
                    else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        Log.d(TAG, "No device selected to bind!!!");
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate() is called.");

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        device_bond_preference = getSharedPreferences(DEVICE_BOND, Context.MODE_PRIVATE);
        device_mode_preference = PreferenceManager.getDefaultSharedPreferences(this);

        initBleManager();
        initAndConnectBleDevice();
        initModePickerView();
        initFabSync();
        requestPermissions();
    }

    private void initFabSync() {
        animation.setDuration(1000);
        animation.setRepeatMode(Animation.RESTART);
        animation.setRepeatCount(Animation.INFINITE);

        binding.fabSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btConnected) {
                    if (device_mode_preference != null) {
                        Map<String, ?> preferences = device_mode_preference.getAll();
                        String mode_prefix = getModePrefixString();
                        byte[] buff = getPreferenceData(preferences, mode_prefix);
                        Log.d(TAG, "Send message to ble device, mode_prefix:"+ mode_prefix + " message:" + new String(buff));
                        if(bleManager.sendMessage(buff)){
                            binding.fabSync.startAnimation(animation);
                        }
                        else
                        {
                            Toast.makeText(MainActivity.this, "mode_setting_failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else {
                        Toast.makeText(MainActivity.this, R.string.cannot_read_mode_setting, Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    Toast.makeText(MainActivity.this, R.string.no_device_connected, Toast.LENGTH_SHORT).show();
                }
            }

            @NonNull
            private String getModePrefixString() {
                int curr_mode_idx = pickerView.getSelectedItemIndex();
                String mode_prefix;
                switch (curr_mode_idx)
                {
                    case 0:
                        mode_prefix = MODE_PREFIX_AIR_MOUSE;
                        break;
                    case 1:
                        mode_prefix = mode_prefix = MODE_PREFIX_GESTURE;
                        break;
                    case 2:
                        mode_prefix = MODE_PREFIX_MLTI_FUNC_SWITCH;
                        break;
                    case 3:
                        mode_prefix = MODE_PREFIX_CUSTOME_1;
                        break;
                    default:
                        mode_prefix = MODE_PREFIX_CUSTOME_2;
                        break;
                }
                return mode_prefix;
            }
        });
    }

    private void initModePickerView() {
        Resources res = getResources();
        Integer[] images = {R.drawable.air_mouse, R.drawable.hand_gesture, R.drawable.multi_fun_button, R.drawable.custom, R.drawable.others};
        String[] labels = {res.getString(R.string.air_mouse_mode),
                res.getString(R.string.gesture_mode),
                res.getString(R.string.multi_func_switch_mode),
                res.getString(R.string.custom_1_mode),
                res.getString(R.string.custom_2_mode)};

        List<PickerView.Item> lsItem = new ArrayList<>();
        for (int i = 0; i < images.length; i++) {
            PickerView.Item item = new PickerView.Item(images[i], labels[i]);
            lsItem.add(item);
        }

        pickerView = findViewById(R.id.picker_view);
        pickerView.setData(lsItem);
        pickerView.setOnSelectListener(new PickerView.onSelectListener() {
            @Override
            public void onSelect(PickerView.Item pic) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() is called.");
        if (device_bond_preference != null) {
            String last_sync_time = device_bond_preference.getString(LAST_SYNC_TIME, null);
            if (last_sync_time != null) {
                SimpleDateFormat format = new SimpleDateFormat(DATE_TIME_FORMAT);
                long last_time = 0, time_now = 0, time_diff = 0;
                try {
                    last_time = format.parse(last_sync_time).getTime();
                    time_now = new Time(System.currentTimeMillis()).getTime();
                    time_diff = time_now - last_time;
                }
                catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                finally {
                    // 如果没有上次访问时间，或者距离上次访问超过3分钟
                    if (last_time == 0 || time_diff > 180000) {
                        //Todo code need here
                    }
                }
            }
        }
    }

    private void initBleManager() {
        bleManager = new BLEManager();
        if (!bleManager.initBle(MainActivity.this)) {
            Toast.makeText(MainActivity.this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
        }
        else {
            if (!bleManager.isEnable()) {
                Log.d(TAG, "Successfully open bluetooth!!");
                bleManager.openBluetooth(MainActivity.this, false);
            }
        }
    }

    private void initAndConnectBleDevice() {
        String device_address = device_bond_preference.getString(DEVICE_ADDRESS, null);

        if (device_address != null) {
            Log.i(TAG, "There is a device bond with address: " + device_address);
        }
        else {
            setDeviceConnectState(CONNECT_STATE_NO_DEIVCE);
            Log.i(TAG, "There no device bond ");
            return;
        }

        BluetoothManager bluetoothManager = bleManager.getBluetoothManager();
        BluetoothDevice bluetoothDevice = null;
        Log.d(TAG, "Successfully got bleManager!!");
        if (bluetoothManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothDevice = bluetoothManager.getAdapter().getRemoteLeDevice(device_address, BluetoothDevice.ADDRESS_TYPE_PUBLIC);
                Log.d(TAG, "Successfully got a bluetooth device!!");
                if (bluetoothDevice != null) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions();
                    }
                    Log.d(TAG, "Got the bond device name: " + bluetoothDevice.getName());
                }
            }
        }

        BluetoothGatt btGatt = bleManager.connectBleDevice(this, bluetoothDevice, 15000, SERVICE_UUID, MODE_SETTING_UUID, MODE_SETTING_UUID, onBleConnectListener);

        if (bluetoothDevice == null || btGatt == null) {
            setDeviceConnectState(CONNECT_STATE_DISCONNECTED);
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //if above Android 6.0
            final PermissionRequest permissionRequest = new PermissionRequest();
            permissionRequest.requestRuntimePermission(this, requestPermissionArray, new PermissionListener() {
                @Override
                public void onGranted() {
                    Log.d(TAG, "所有权限已被授予");
                }
                @Override // if users selected no remind later, the the next time this callback is invoked.
                public void onDenied(List<String> deniedPermissions) {
                    deniedPermissionList = deniedPermissions;
                    for (String deniedPermission : deniedPermissionList) {
                        Log.e(TAG, "被拒绝权限：" + deniedPermission);
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.add_device) {
            mStartForBondDevice.launch(new Intent(this, AddDeviceActivity.class));
        }
        else if (id == R.id.mode_setting) {
            startActivity(new Intent(MainActivity.this, ModeSettingActivity.class));
        }
        else if (id == android.R.id.home) {
            Intent intent = new Intent(this, AddDeviceActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    private void setDeviceConnectState(int connectType) {
        switch (connectType) {
            case CONNECT_STATE_CONNECTED:
                binding.connectStateText.setText(R.string.device_connected);
                binding.connectStateText.setVisibility(View.VISIBLE);
                binding.connectProgress.setVisibility(View.GONE);
                binding.connectState.setImageResource(R.drawable.connected);
                binding.connectState.setVisibility(View.VISIBLE);
                break;
            case CONNECT_STATE_CONNECTING:
                binding.connectStateText.setText(R.string.device_connecting);
                binding.connectStateText.setVisibility(View.VISIBLE);
                binding.connectProgress.setVisibility(View.VISIBLE);
                binding.connectState.setVisibility(View.GONE);
                break;
            case CONNECT_STATE_DISCONNECTED:
                binding.connectProgress.setVisibility(View.GONE);
                binding.connectState.setVisibility(View.VISIBLE);
                binding.connectStateText.setVisibility(View.VISIBLE);
                binding.connectStateText.setText(R.string.no_device_connected);
                binding.connectState.setImageResource(R.drawable.disconnected);
                break;
            case CONNECT_STATE_NO_DEIVCE:
                binding.connectStateText.setText(R.string.no_bond_device);
                binding.connectState.setVisibility(View.VISIBLE);
                binding.connectProgress.setVisibility(View.GONE);
                binding.connectStateText.setVisibility(View.VISIBLE);
                binding.connectState.setImageResource(R.drawable.disconnected);
                break;
        }
    }
}