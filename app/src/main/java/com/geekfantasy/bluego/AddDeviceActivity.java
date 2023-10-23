package com.geekfantasy.bluego;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.geekfantasy.bluego.ble.BLEManager;
import com.geekfantasy.bluego.permission.*;
import com.geekfantasy.bluego.ble.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = "Bluego_Add_Device";

    //bt_patch(mtu).bin

    public static final String SERVICE_UUID = "00001812-0000-1000-8000-00805f9b34fb";  //蓝牙通讯服务

    private List<String> deniedPermissionList = new ArrayList<>();

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

    private LinearLayout llAddDeviceHeader;
    private ProgressBar pbSearchDevices;
    private TextView tvSearchDevice;
    private TextView tvSearchTips;
    private LinearLayout llAddDeviceMain;
    private LinearLayout llNoDeviceFound;
    private TextView tvNoDevice;
    private TextView tvRetry;
    private ListView lvDevices;
    private Button bnCancelAddDevice;

    private LVDevicesAdapter lvDevicesAdapter;
    private Context mContext;
    private BLEManager bleManager;
    private BLEBroadcastReceiver bleBroadcastReceiver;
    private BluetoothDevice curBluetoothDevice;  //当前连接的设备
    //当前设备连接状态
    private boolean btConnected = false;

    private String[] requestPermissionArrayAbove31 = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private String[] requestPermissionArrayBelow30 = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
    };

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case START_DISCOVERY:
                    Log.d(TAG, "开始搜索设备...");
                    break;
                case STOP_DISCOVERY:
                    Log.d(TAG, "停止搜索设备...");
                    break;
                case DISCOVERY_DEVICE:  //扫描到设备
                    BLEDevice bleDevice = (BLEDevice) msg.obj;
                    lvDevicesAdapter.addDevice(bleDevice);
                    if(lvDevices.getVisibility() == View.GONE)
                        lvDevices.setVisibility(View.VISIBLE);
                    break;
                case DISCOVERY_OUT_TIME:  //超时扫描到设备
                    pbSearchDevices.setVisibility(View.GONE);
                    if (lvDevicesAdapter.getCount() <= 0) {
                        ArrayList<BluetoothDevice> bondDevices = bleManager.GetBondDevices(UUID.fromString(SERVICE_UUID));
                        if(bondDevices.size() > 0){
                            for (BluetoothDevice device : bondDevices) {
                                lvDevicesAdapter.addDevice(new BLEDevice(device, 100));
                                if(lvDevices.getVisibility() == View.GONE)
                                    lvDevices.setVisibility(View.VISIBLE);
                            }
                            tvSearchDevice.setText("扫描完毕");
                            tvSearchTips.setText("请点击选择要绑定的设备");
                        }
                        else {
                            lvDevices.setVisibility(View.GONE);
                            tvSearchTips.setVisibility(View.GONE);
                            llNoDeviceFound.setVisibility(View.VISIBLE);
                        }
                    }
                    else {
                        tvSearchDevice.setText("扫描完毕");
                        tvSearchTips.setText("请点击选择要绑定的设备");
                    }
                    break;
                case SELECT_DEVICE:
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) msg.obj;
                    curBluetoothDevice = bluetoothDevice;
                    break;
                case CONNECT_FAILURE:
                    Log.d(TAG, "连接失败");
                    btConnected = false;
                    break;
                case CONNECT_SUCCESS:
                    Log.d(TAG, "连接成功");
                    break;
                case DISCONNECT_SUCCESS:
                    Log.d(TAG, "断开成功");
                    btConnected = false;
                    break;
                case SEND_FAILURE:
                    byte[] sendBufFail = (byte[]) msg.obj;
                    break;
                case SEND_SUCCESS:
                    byte[] sendBufSuc = (byte[]) msg.obj;
                    break;
                case RECEIVE_FAILURE:
                    String receiveError = (String) msg.obj;
                    break;
                case RECEIVE_SUCCESS:
                    byte[] recBufSuc = (byte[]) msg.obj;
                    break;
                case BT_CLOSED:
                    Log.d(TAG, "系统蓝牙已关闭");
                    break;
                case BT_OPENED:
                    Log.d(TAG, "系统蓝牙已打开");
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);
        mContext = this;

        initView();
        iniListener();
        initData();
        initBLEBroadcastReceiver();
        initPermissions();

        // Start to search bt devices
        llNoDeviceFound.setVisibility(View.GONE);
        lvDevices.setVisibility(View.VISIBLE);
        pbSearchDevices.setVisibility(View.VISIBLE);

        searchBtDevice();
    }

    private void initView() {
        llAddDeviceHeader = findViewById(R.id.ll_add_device_header);
        pbSearchDevices = findViewById(R.id.pb_search_devices);
        tvSearchDevice = findViewById(R.id.tv_search_device);
        tvSearchTips = findViewById(R.id.tv_search_tips);
        llAddDeviceMain = findViewById(R.id.ll_add_device_main);
        llNoDeviceFound = findViewById(R.id.ll_no_device_found);
        tvNoDevice = findViewById(R.id.tv_no_device);
        tvRetry = findViewById(R.id.tv_retry);
        lvDevices = findViewById(R.id.lv_devices);
        bnCancelAddDevice = findViewById(R.id.bn_cancel_add_device);
    }

    /**
     * 初始化监听
     */
    private void iniListener() {
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                BLEDevice bleDevice = (BLEDevice) lvDevicesAdapter.getItem(i);
                BluetoothDevice bluetoothDevice = bleDevice.getBluetoothDevice();

                if (bleManager != null) {
                    bleManager.stopDiscoveryDevice();
                }
                Intent intent = getIntent();
                intent.putExtra("device-address", bluetoothDevice.getAddress());
                setResult(Activity.RESULT_OK, intent);
                Log.d(TAG, "Bind devices address:" + bluetoothDevice.getAddress());
                finish();
            }
        });
        tvRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                llNoDeviceFound.setVisibility(View.GONE);
                pbSearchDevices.setVisibility(View.VISIBLE);
                searchBtDevice();
            }
        });

        bnCancelAddDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = getIntent();
                setResult(Activity.RESULT_CANCELED, intent);
                Log.d(TAG, "Cancel binding devices!!!");
                finish();
            }
        });
    }

    /**
     * 初始化数据
     */
    private void initData() {
        lvDevicesAdapter = new LVDevicesAdapter(this);
        lvDevices.setAdapter(lvDevicesAdapter);

        bleManager = new BLEManager();
        if (!bleManager.initBle(mContext)) {
            Toast.makeText(mContext, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
        }
        else {
            if (!bleManager.isEnable()) {
                bleManager.openBluetooth(mContext, false);
            }
        }
    }

    /**
     * 注册广播
     */
    private void initBLEBroadcastReceiver() {
        bleBroadcastReceiver = new BLEBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bleBroadcastReceiver, intentFilter);
    }

    /**
     * 初始化权限
     */
    private void initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //if above Android 6.0
            final PermissionRequest permissionRequest = new PermissionRequest();

            String[] permArray;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permArray = requestPermissionArrayAbove31;
            }
            else{
                permArray = requestPermissionArrayBelow30;
            }

            permissionRequest.requestRuntimePermission(this, permArray, new PermissionListener() {
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
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bleBroadcastReceiver);
    }

    //////////////////////////////////  搜索设备  /////////////////////////////////////////////////
    private void searchBtDevice() {
        if (bleManager == null) {
            Log.d(TAG, "searchBtDevice()-->bleManager == null");
            return;
        }

        if (bleManager.isDiscovery()) {
            bleManager.stopDiscoveryDevice();
        }

        if (lvDevicesAdapter != null) {
            lvDevicesAdapter.clear();
        }

        bleManager.startDiscoveryDevice(onDeviceSearchListener, SERVICE_UUID, 10000);
    }


    private OnDeviceSearchListener onDeviceSearchListener = new OnDeviceSearchListener() {
        @Override
        public void onDeviceFound(BLEDevice bleDevice) {
            Message message = new Message();
            message.what = DISCOVERY_DEVICE;
            message.obj = bleDevice;
            mHandler.sendMessage(message);
        }

        @Override
        public void onDiscoveryOutTime() {
            Message message = new Message();
            message.what = DISCOVERY_OUT_TIME;
            mHandler.sendMessage(message);
        }
    };

    private OnBleConnectListener onBleConnectListener = new OnBleConnectListener() {
        @Override
        public void onConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            // Since the communication works after services discovered, so send Success below onServiceDiscoverySucceed
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
            //Since the communication works after service discovered, so send Success here
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

    private class BLEBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                Message message = new Message();
                message.what = START_DISCOVERY;
                mHandler.sendMessage(message);
            }
            else if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                Message message = new Message();
                message.what = STOP_DISCOVERY;
                mHandler.sendMessage(message);
            }
            else if (TextUtils.equals(action, BluetoothAdapter.ACTION_STATE_CHANGED)) {

                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Message message = new Message();
                    message.what = BT_CLOSED;
                    mHandler.sendMessage(message);
                }
                else if (state == BluetoothAdapter.STATE_ON) {
                    Message message = new Message();
                    message.what = BT_OPENED;
                    mHandler.sendMessage(message);
                }
            }
        }
    }
}