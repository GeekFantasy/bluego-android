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
import android.os.ParcelUuid;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.geekfantasy.bluego.ble.BLEManager;
import com.geekfantasy.bluego.ble.OnBleConnectListener;
import com.geekfantasy.bluego.databinding.ActivityMainBinding;
import com.geekfantasy.bluego.permission.PermissionListener;
import com.geekfantasy.bluego.permission.PermissionRequest;
import com.geekfantasy.bluego.util.DefaultPreferrence;
import com.geekfantasy.bluego.util.TypeConversion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class MainActivity extends AppCompatActivity {
    public static final String TAG = "Bluego_Main";

    public static final String SERVICE_UUID = "0000ef00-0000-1000-8000-00805f9b34fb";
    public static final String CURR_MODE_UUID = "0000ef01-0000-1000-8000-00805f9b34fb";  // current mode read and write char
    public static final String MODE_SETTING_UUID = "0000ef02-0000-1000-8000-00805f9b34fb";  //mode setting read and write char
    private static final String DEVICE_BOND = "device_bond";
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String CURRENT_MODE = "current_mode";
    private static final String LAST_SYNC_TIME = "last_sync_time";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String MODE_PREFIX_AIR_MOUSE = "am_";
    private static final String MODE_PREFIX_MLTI_FUNC_SWITCH = "mfs_";
    private static final String MODE_PREFIX_GESTURE = "ges_";
    private static final String MODE_PREFIX_CUSTOM_1 = "cm1_";
    private static final String MODE_PREFIX_CUSTOM_2 = "cm2_";

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
    private static final int CONNECT_STATE_NO_DEVICE = 0X03;
    private final Animation animation = new RotateAnimation(0.0f, 360.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
    private ActivityMainBinding binding;
    private PickerView pickerView;
    private BLEManager bleManager;
    private boolean btConnected = false;
    private SharedPreferences device_bond_preference;
    private SharedPreferences device_mode_preference;

    private List<String> deniedPermissionList = new ArrayList<>();
    private final String[] requestPermissionArray = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private final String[] requestPermissionArrayAbove31 = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private final String[] requestPermissionArrayBelow30 = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
    };

    PickerView.Item currentPickerViewItem;

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
                    if (currentPickerViewItem != null) {
                        // tag the current item as saved to device
                        pickerView.setImageBadge(currentPickerViewItem.getTag(), R.drawable.selected);
                        pickerView.invalidate();

                        // save the current mode to shared preference
                        SharedPreferences.Editor editor = device_bond_preference.edit();
                        editor.putString(CURRENT_MODE, currentPickerViewItem.getTag());
                        if (!editor.commit()) {
                            Log.d(TAG, "当前模式本地保存失败");
                        }
                    }
                    //CURRENT_MODE
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
                            Log.i(TAG, "There is no ble manager initialed.");
                            return;
                        }

                        BluetoothManager bluetoothManager = bleManager.getBluetoothManager();
                        if (bluetoothManager != null) {
                            Log.d(TAG, "Successfully got bleManager!!");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                BluetoothDevice bluetoothDevice = bluetoothManager.getAdapter().getRemoteDevice(device_address);

                                if (bluetoothDevice != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                            requestPermissions(requestPermissionArrayAbove31);
                                        }
                                    }
                                    else{
                                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                                            requestPermissions(requestPermissionArrayBelow30);
                                        }
                                    }

                                    SharedPreferences.Editor editor = device_bond_preference.edit();
                                    editor.putString(DEVICE_ADDRESS, device_address);
                                    editor.apply();

                                    if(bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED){
                                        bluetoothDevice.createBond();
                                    }

                                    initAndConnectBleDevice();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(requestPermissionArrayAbove31);
        }
        else {
            requestPermissions(requestPermissionArrayBelow30);
        }

        initDefaultSettings();
    }


    private void initDefaultSettings() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if(pref != null && pref.getAll().isEmpty())
        {
            SharedPreferences.Editor editor = pref.edit();
            DefaultPreferrence defaultPreferrence = new DefaultPreferrence();

            for (DefaultPreferrence.StringPreferrence strpref :defaultPreferrence.strPreferrences) {
                editor.putString(strpref.key, strpref.strValue);
            }

            for (DefaultPreferrence.BoolPreferrence boolpref :defaultPreferrence.boolPreferrences) {
                editor.putBoolean(boolpref.key, boolpref.boolValue);
            }

            editor.apply();
        }
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
                        String mode_prefix = getCurrentModePrefix();
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
            private String getCurrentModePrefix() {
                String prefix = MODE_PREFIX_AIR_MOUSE;
                if(currentPickerViewItem != null)
                {
                    prefix = currentPickerViewItem.getTag();
                }
                return prefix;
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
        String[] tags = {MODE_PREFIX_AIR_MOUSE, MODE_PREFIX_GESTURE, MODE_PREFIX_MLTI_FUNC_SWITCH, MODE_PREFIX_CUSTOM_1, MODE_PREFIX_CUSTOM_2};

        List<PickerView.Item> lsItem = new ArrayList<>();
        for (int i = 0; i < images.length; i++) {
            PickerView.Item item = new PickerView.Item(images[i], labels[i], tags[i]);
            lsItem.add(item);
        }

        pickerView = findViewById(R.id.picker_view);
        pickerView.setOnSelectListener(new PickerView.onSelectListener() {
            @Override
            public void onSelect(PickerView.Item picItem) {
                Log.d(TAG, "PickerView onSelect() is called.");
                currentPickerViewItem = picItem;
            }
        });
        pickerView.setData(lsItem);

        if(device_bond_preference != null){
            String current_mode = device_bond_preference.getString(CURRENT_MODE, null);
            if(current_mode != null){
                pickerView.setImageBadge(current_mode, R.drawable.selected);
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
            setDeviceConnectState(CONNECT_STATE_NO_DEVICE);
            Log.i(TAG, "There no device bond ");
            return;
        }

        BluetoothManager bluetoothManager = bleManager.getBluetoothManager();
        BluetoothDevice bluetoothDevice = null;
        Log.d(TAG, "Successfully got bleManager!!");
        if (bluetoothManager != null) {

                bluetoothDevice = bluetoothManager.getAdapter().getRemoteDevice(device_address);
                Log.d(TAG, "Successfully got a bluetooth device!!");
        }

        BluetoothGatt btGatt = bleManager.connectBleDevice(this, bluetoothDevice, 15000, SERVICE_UUID, MODE_SETTING_UUID, MODE_SETTING_UUID, onBleConnectListener);

        if (bluetoothDevice == null || btGatt == null) {
            setDeviceConnectState(CONNECT_STATE_DISCONNECTED);
        }
    }

    private void requestPermissions(String[] permArray) {
        final PermissionRequest permissionRequest = new PermissionRequest();
        permissionRequest.requestRuntimePermission(this, permArray, new PermissionListener() {
            @Override
            public void onGranted() {
                Log.d(TAG, "所有权限已被授予");
            }
            @Override
            // if users selected no remind later, the the next time this callback is invoked.
            public void onDenied(List<String> deniedPermissions) {
                deniedPermissionList = deniedPermissions;
                for (String deniedPermission : deniedPermissionList) {
                    Log.e(TAG, "被拒绝权限：" + deniedPermission);
                }
            }
        });
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
                binding.connectState.setImageResource(R.drawable.connected_green);
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
                binding.connectState.setImageResource(R.drawable.disconnected_red);
                break;
            case CONNECT_STATE_NO_DEVICE:
                binding.connectStateText.setText(R.string.no_bond_device);
                binding.connectState.setVisibility(View.VISIBLE);
                binding.connectProgress.setVisibility(View.GONE);
                binding.connectStateText.setVisibility(View.VISIBLE);
                binding.connectState.setImageResource(R.drawable.disconnected_red);
                break;
        }
    }
}