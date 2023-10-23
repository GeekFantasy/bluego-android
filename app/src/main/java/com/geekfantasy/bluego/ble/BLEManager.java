package com.geekfantasy.bluego.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.geekfantasy.bluego.BLEDevice;
import com.geekfantasy.bluego.util.ClsUtils;
import com.geekfantasy.bluego.util.TypeConversion;

/**
 * 作者：yeqianyun on 2019/11/6 17:47
 * 邮箱：1612706976@qq.com
 * <p>
 * 1、扫描设备
 * 2、配对设备
 * 3、解除设备配对
 * 4、连接设备
 * 6、发现服务
 * 7、打开读写功能
 * 8、数据通讯（发送数据、接收数据）
 * 9、断开连接
 */
public class BLEManager {
    private static final String TAG = "BLEManager";

    private static final long MAX_CONNECT_TIME = 10000;  //连接超时时间10s


    private Context mContext;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetooth4Adapter;
    private BluetoothGatt mBluetoothGatt;  //当前连接的gatt
    private String serviceUUID, readUUID, writeUUID;
    private BluetoothGattService bluetoothGattService;   //服务
    private BluetoothGattCharacteristic readCharacteristic;  //读特征
    private BluetoothGattCharacteristic writeCharacteristic; //写特征
    private OnDeviceSearchListener onDeviceSearchListener;  //设备扫描结果监听
    private OnBleConnectListener onBleConnectListener;   //连接监听
    private BluetoothDevice curConnDevice;  //当前连接的设备
    private boolean isConnectIng = false;  //是否正在连接中

    private final Handler mHandler = new Handler();

    public BLEManager() {
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    /**
     * 初始化
     *
     * @param context
     */
    public boolean initBle(Context context) {
        mContext = context;
        if (!checkBle(context)) {
            return false;
        }
        else {
            return true;
        }
    }

    ////////////////////////////////////  扫描设备  ///////////////////////////////////////////////
    //扫描设备回调
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] bytes) {
            //在onLeScan()回调中尽量做少的操作，可以将扫描到的设备扔到另一个线程中处理
            if (bluetoothDevice == null)
                return;

            if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return;

            if (bluetoothDevice.getName() != null) {
                Log.d(TAG, bluetoothDevice.getName() + "-->" + bluetoothDevice.getAddress());
            }
            else {
                Log.d(TAG, "null" + "-->" + bluetoothDevice.getAddress());
            }

            BLEDevice bleDevice = new BLEDevice(bluetoothDevice, rssi);
            if (onDeviceSearchListener != null) {
                onDeviceSearchListener.onDeviceFound(bleDevice);  //扫描到设备回调
            }
        }
    };

    /**
     * 设置时间段 扫描设备
     *
     * @param onDeviceSearchListener 设备扫描监听
     * @param scanTime               扫描时间
     */
    public void startDiscoveryDevice(OnDeviceSearchListener onDeviceSearchListener, String uuid, long scanTime) {
        if (bluetooth4Adapter == null) {
            Log.e(TAG, "startDiscoveryDevice-->bluetooth4Adapter == null");
            return;
        }

        this.onDeviceSearchListener = onDeviceSearchListener;

        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_SCAN)) return;

        if (!uuid.isEmpty()) {
            bluetooth4Adapter.startLeScan(new UUID[]{UUID.fromString(uuid)}, leScanCallback);
            Log.d(TAG, "已开始扫描设备");
        }
        else {
            bluetooth4Adapter.startLeScan(leScanCallback);
            Log.d(TAG, "已开始扫描设备");
        }

        //设定最长扫描时间
        mHandler.postDelayed(stopScanRunnable, scanTime);
    }

    private Runnable stopScanRunnable = new Runnable() {
        @Override
        public void run() {
            if (onDeviceSearchListener != null) {
                onDeviceSearchListener.onDiscoveryOutTime();  //扫描超时回调
            }
            //scanTime之后还没有扫描到设备，就停止扫描。
            stopDiscoveryDevice();
        }
    };

    //////////////////////////////////////  停止扫描  /////////////////////////////////////////////

    /**
     * 停止扫描
     */
    public void stopDiscoveryDevice() {
        mHandler.removeCallbacks(stopScanRunnable);

        if (bluetooth4Adapter == null) {
            Log.e(TAG, "stopDiscoveryDevice-->bluetooth4Adapter == null");
            return;
        }

        if (leScanCallback == null) {
            Log.e(TAG, "stopDiscoveryDevice-->leScanCallback == null");
            return;
        }

        Log.d(TAG, "停止扫描设备");

        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_SCAN)) return;

        bluetooth4Adapter.stopLeScan(leScanCallback);
    }

    private boolean checkBlueToothPermission(String btPermission) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(mContext, btPermission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, btPermission + "is not granted.");
                return true;
            }
        }
        else {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Manifest.permission.BLUETOOTH_SCAN is not granted.");
                return true;
            }
        }
        return true;
    }

    /////////////////////////////////////  执行连接  //////////////////////////////////////////////
    //连接/通讯结果回调
    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        //连接状态回调-连接成功/断开连接
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "status:" + status);
            Log.d(TAG, "newState:" + newState);

            switch (status) {
                case BluetoothGatt.GATT_SUCCESS:
                    Log.w(TAG, "BluetoothGatt.GATT_SUCCESS");
                    break;
                case BluetoothGatt.GATT_FAILURE:
                    Log.w(TAG, "BluetoothGatt.GATT_FAILURE");
                    break;
                case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                    Log.w(TAG, "BluetoothGatt.GATT_CONNECTION_CONGESTED");
                    break;
                case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                    Log.w(TAG, "BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION");
                    break;
                case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                    Log.w(TAG, "BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION");
                    break;
                case BluetoothGatt.GATT_INVALID_OFFSET:
                    Log.w(TAG, "BluetoothGatt.GATT_INVALID_OFFSET");
                    break;
                case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                    Log.w(TAG, "BluetoothGatt.GATT_READ_NOT_PERMITTED");
                    break;
                case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                    Log.w(TAG, "BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED");
                    break;
            }

            BluetoothDevice bluetoothDevice = gatt.getDevice();
            if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
                return;
            }

            Log.d(TAG, "连接的设备：" + bluetoothDevice.getName() + "  " + bluetoothDevice.getAddress());

            isConnectIng = false;
            //移除连接超时--这个放在这里不是很合适，因为断开连接时，是没有这个callback的
            mHandler.removeCallbacks(connectOutTimeRunnable);

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.w(TAG, "连接成功");
                //连接成功去发现服务
                gatt.discoverServices();
                //设置发现服务超时时间
                mHandler.postDelayed(serviceDiscoverOutTimeRunnable, MAX_CONNECT_TIME);

                if (onBleConnectListener != null) {
                    onBleConnectListener.onConnectSuccess(gatt, bluetoothDevice, status);   //连接成功回调
                }
            }
            else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                //清空系统缓存
                ClsUtils.refreshDeviceCache(gatt);
                Log.e(TAG, "断开连接status:" + status);
                gatt.close();  //断开连接释放连接

                if (status == 133) {
                    //无法连接
                    if (onBleConnectListener != null) {
                        gatt.close();
                        onBleConnectListener.onConnectFailure(gatt, bluetoothDevice, "连接异常！", status);  //133连接异常 异常断开
                        Log.e(TAG, "连接失败status：" + status + "  " + bluetoothDevice.getAddress());
                    }
                }
                else if (status == 62) {
                    //成功连接没有发现服务断开
                    if (onBleConnectListener != null) {
                        gatt.close();
                        onBleConnectListener.onConnectFailure(gatt, bluetoothDevice, "连接成功服务未发现断开！", status); //62没有发现服务 异常断开
                        Log.e(TAG, "连接成功服务未发现断开status:" + status);
                    }

                }
                else if (status == 0) {
                    if (onBleConnectListener != null) {
                        onBleConnectListener.onDisConnectSuccess(gatt, bluetoothDevice, status); //0正常断开 回调
                    }
                }
                else if (status == 8) {
                    //因为距离远或者电池无法供电断开连接
                    // 已经成功发现服务
                    if (onBleConnectListener != null) {
                        onBleConnectListener.onDisConnectSuccess(gatt, bluetoothDevice, status); //8断电断开  回调
                    }
                }
                else if (status == 34) {
                    if (onBleConnectListener != null) {
                        onBleConnectListener.onDisConnectSuccess(gatt, bluetoothDevice, status); //34断开
                    }
                }
                else {
                    //其它断开连接
                    if (onBleConnectListener != null) {
                        onBleConnectListener.onDisConnectSuccess(gatt, bluetoothDevice, status); //其它断开
                    }
                }
            }
            else if (newState == BluetoothGatt.STATE_CONNECTING) {
                Log.d(TAG, "正在连接...");
                if (onBleConnectListener != null) {
                    onBleConnectListener.onConnecting(gatt, bluetoothDevice);  //正在连接回调
                }
            }
            else if (newState == BluetoothGatt.STATE_DISCONNECTING) {
                Log.d(TAG, "正在断开...");
                if (onBleConnectListener != null) {
                    onBleConnectListener.onDisConnecting(gatt, bluetoothDevice); //正在断开回调
                }
            }
        }

        //发现服务
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            //移除发现服务超时
            mHandler.removeCallbacks(serviceDiscoverOutTimeRunnable);
            Log.d(TAG, "移除发现服务超时");

            Log.d(TAG, "发现服务");

            //配置服务信息
            if (setupService(gatt, serviceUUID, readUUID, writeUUID)) {
                if (onBleConnectListener != null) {
                    onBleConnectListener.onServiceDiscoverySucceed(gatt, gatt.getDevice(), status);  //成功发现服务回调
                }
            }
            else {
                if (onBleConnectListener != null) {
                    onBleConnectListener.onServiceDiscoveryFailed(gatt, gatt.getDevice(), "获取服务特征异常");  //发现服务失败回调
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            Log.d(TAG, "读status: " + status);
        }

        //向蓝牙设备写入数据结果回调
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (characteristic.getValue() == null) {
                Log.e(TAG, "characteristic.getValue() == null");
                return;
            }
            //将收到的字节数组转换成十六进制字符串
            String msg = TypeConversion.bytes2HexString(characteristic.getValue(), characteristic.getValue().length);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //写入成功
                Log.w(TAG, "写入成功：" + msg);
                if (onBleConnectListener != null) {
                    onBleConnectListener.onWriteSuccess(gatt, gatt.getDevice(), characteristic.getValue());  //写入成功回调
                }

            }
            else if (status == BluetoothGatt.GATT_FAILURE) {
                //写入失败
                Log.e(TAG, "写入失败：" + msg);
                if (onBleConnectListener != null) {
                    onBleConnectListener.onWriteFailure(gatt, gatt.getDevice(), characteristic.getValue(), "写入失败");  //写入失败回调
                }
            }
            else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                //没有权限
                Log.e(TAG, "没有权限！");
            }
        }

        //读取蓝牙设备发出来的数据回调
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            //接收数据
            byte[] bytes = characteristic.getValue();
            Log.w(TAG, "收到数据str:" + TypeConversion.bytes2HexString(bytes, bytes.length));
            if (onBleConnectListener != null) {
                onBleConnectListener.onReceiveMessage(gatt, gatt.getDevice(), characteristic, characteristic.getValue());  //接收数据回调
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.d(TAG, "onReliableWriteCompleted");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "读取RSSI值成功，RSSI值：" + rssi + ",status" + status);
                if (onBleConnectListener != null) {
                    onBleConnectListener.onReadRssi(gatt, rssi, status);  //成功读取连接的信号强度回调
                }
            }
            else if (status == BluetoothGatt.GATT_FAILURE) {
                Log.w(TAG, "读取RSSI值失败，status：" + status);
            }
        }

        //修改MTU值结果回调
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            ///设置mtu值，即bluetoothGatt.requestMtu()时触发，提示该操作是否成功
            if (status == BluetoothGatt.GATT_SUCCESS) {  //设置MTU成功
                //MTU默认取的是23，当收到 onMtuChanged 后，会根据传递的值修改MTU，注意由于传输用掉3字节，因此传递的值需要减3。
                //mtu - 3
                Log.w(TAG, "设置MTU成功，新的MTU值：" + (mtu - 3) + ",status" + status);
                if (onBleConnectListener != null) {
                    onBleConnectListener.onMTUSetSuccess("设置后新的MTU值 = " + (mtu - 3) + "   status = " + status, mtu - 3);  //MTU设置成功
                }

            }
            else if (status == BluetoothGatt.GATT_FAILURE) {  //设置MTU失败
                Log.e(TAG, "设置MTU值失败：" + (mtu - 3) + ",status" + status);
                if (onBleConnectListener != null) {
                    onBleConnectListener.onMTUSetFailure("设置MTU值失败：" + (mtu - 3) + "   status：" + status);  //MTU设置失败
                }
            }

        }
    };

    /**
     * 通过蓝牙设备连接
     *
     * @param context              上下文
     * @param bluetoothDevice      蓝牙设备
     * @param outTime              连接超时时间
     * @param onBleConnectListener 蓝牙连接监听者
     */
    public BluetoothGatt connectBleDevice(Context context, BluetoothDevice bluetoothDevice, long outTime, String serviceUUID, String readUUID, String writeUUID, OnBleConnectListener onBleConnectListener) {
        if (bluetoothDevice == null) {
            Log.e(TAG, "connectBleDevice()-->bluetoothDevice == null");
            return null;
        }
        if (isConnectIng) {
            Log.e(TAG, "connectBleDevice()-->isConnectIng = true");
            return null;
        }
        this.serviceUUID = serviceUUID;
        this.readUUID = readUUID;
        this.writeUUID = writeUUID;
        this.onBleConnectListener = onBleConnectListener;

        this.curConnDevice = bluetoothDevice;

        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
            return null;
        }

        Log.d(TAG, "开始准备连接：" + bluetoothDevice.getName() + "-->" + bluetoothDevice.getAddress());
        //出现 BluetoothGatt.android.os.DeadObjectException 蓝牙没有打开
        try {
            mBluetoothGatt = bluetoothDevice.connectGatt(context, false, bluetoothGattCallback);
            mBluetoothGatt.connect();
            isConnectIng = true;
        }
        catch (Exception e) {
            Log.e(TAG, "e:" + e.getMessage());
        }

        //设置连接超时时间10s
        mHandler.postDelayed(connectOutTimeRunnable, outTime);

        return mBluetoothGatt;
    }

    //连接超时
    private Runnable connectOutTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBluetoothGatt == null) {
                Log.e(TAG, "connectOuttimeRunnable-->mBluetoothGatt == null");
                return;
            }

            isConnectIng = false;
            if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
                return;
            }
            mBluetoothGatt.disconnect();

            //连接超时当作连接失败回调
            if (onBleConnectListener != null) {
                onBleConnectListener.onConnectFailure(mBluetoothGatt, curConnDevice, "连接超时！", -1);  //连接失败回调
            }
        }
    };

    //发现服务超时
    private Runnable serviceDiscoverOutTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBluetoothGatt == null) {
                Log.e(TAG, "connectOuttimeRunnable-->mBluetoothGatt == null");
                return;
            }

            isConnectIng = false;
            if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
                return;
            }
            mBluetoothGatt.disconnect();

            //发现服务超时当作连接失败回调
            if (onBleConnectListener != null) {
                onBleConnectListener.onConnectFailure(mBluetoothGatt, curConnDevice, "发现服务超时！", -1);  //连接失败回调
            }
        }
    };

    /**
     * 获取特定服务及特征
     * 1个serviceUUID -- 1个readUUID -- 1个writeUUID
     *
     * @param bluetoothGatt
     * @param serviceUUID
     * @param readUUID
     * @param writeUUID
     * @return
     */
    private boolean setupService(BluetoothGatt bluetoothGatt, String serviceUUID, String readUUID, String writeUUID) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "setupService()-->bluetoothGatt == null");
            return false;
        }

        if (serviceUUID == null) {
            Log.e(TAG, "setupService()-->serviceUUID == null");
            return false;
        }

        for (BluetoothGattService service : bluetoothGatt.getServices()) {
            Log.d(TAG, "service = " + service.getUuid().toString());
            if (service.getUuid().toString().equals(serviceUUID)) {
                bluetoothGattService = service;
            }
        }
        //通过上面方法获取bluetoothGattService
        if (bluetoothGattService == null) {
            //找不到该服务就立即断开连接
            Log.e(TAG, "setupService()-->bluetoothGattService == null");
            return false;
        }
        Log.d(TAG, "setupService()-->bluetoothGattService = " + bluetoothGattService);

        if (readUUID == null || writeUUID == null) {
            Log.e(TAG, "setupService()-->readUUID == null || writeUUID == null");
            return false;
        }

        for (BluetoothGattCharacteristic characteristic : bluetoothGattService.getCharacteristics()) {
            if (characteristic.getUuid().toString().equals(readUUID)) {  //读特征
                readCharacteristic = characteristic;
            }
            if (characteristic.getUuid().toString().equals(writeUUID)) {  //写特征
                writeCharacteristic = characteristic;
            }
        }
        if (readCharacteristic == null) {
            Log.e(TAG, "setupService()-->readCharacteristic == null");
            return false;
        }
        if (writeCharacteristic == null) {
            Log.e(TAG, "setupService()-->writeCharacteristic == null");
            return false;
        }
        //打开读通知
        enableNotification(true, bluetoothGatt, readCharacteristic);

        //重点中重点，需要重新设置
        //TODO 一下看起来作用跟上一句是一样的，为什么要单独采用这种方式来写？？
        List<BluetoothGattDescriptor> descriptors = writeCharacteristic.getDescriptors();
        for (BluetoothGattDescriptor descriptor : descriptors) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
                return false;
            }
            bluetoothGatt.writeDescriptor(descriptor);
        }

        //延迟2s，保证所有通知都能及时打开 --  this will not delay the current method(thread)
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

            }
        }, 2000);

        return true;
    }

    /////////////////////////////////////////  打开通知  //////////////////////////////////////////

    /**
     * 设置读特征接收通知
     *
     * @param enable         为true打开通知
     * @param gatt           连接
     * @param characteristic 特征
     */
    public void enableNotification(boolean enable, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (gatt == null) {
            Log.e(TAG, "enableNotification-->gatt == null");
            return;
        }
        if (characteristic == null) {
            Log.e(TAG, "enableNotification-->characteristic == null");
            return;
        }
        //这一步必须要有，否则接收不到通知
        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
            return;
        }
        gatt.setCharacteristicNotification(characteristic, enable);
    }


    ///////////////////////////////////  发送数据  ///////////////////////////////////////////////

    /**
     * 发送消息  byte[]数组
     *
     * @param msg 消息
     * @return true  false
     */
    public boolean sendMessage(String msg) {
        return sendMessage(TypeConversion.hexString2Bytes(msg));
    }

    /**
     * 发送消息  byte[]数组
     *
     * @param msg 消息
     * @return true  false
     */
    public boolean sendMessage(byte[] msg) {
        if (writeCharacteristic == null) {
            Log.e(TAG, "sendMessage(byte[])-->writeGattCharacteristic == null");
            return false;
        }

        if (mBluetoothGatt == null) {
            Log.e(TAG, "sendMessage(byte[])-->mBluetoothGatt == null");
            return false;
        }

        boolean b = writeCharacteristic.setValue(msg);
        Log.d(TAG, "写特征设置值结果：" + b);
        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
            return false;
        }
        return mBluetoothGatt.writeCharacteristic(writeCharacteristic);
    }


    ///////////////////////////////////  断开连接  ///////////////////////////////////////////////

    /**
     * 断开连接
     */
    public void disConnectDevice() {
        if (mBluetoothGatt == null) {
            Log.e(TAG, "disConnectDevice-->bluetoothGatt == null");
            return;
        }

        //系统断开
        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
            return;
        }
        mBluetoothGatt.disconnect();
        //close()方法应该放在断开回调处，放在此处，会没有回调信息
//        mBluetoothGatt.close();
    }


    /**
     * 检测手机是否支持4.0蓝牙
     *
     * @param context 上下文
     * @return true--支持4.0  false--不支持4.0
     */
    private boolean checkBle(Context context) {
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return false;
        }
        bluetooth4Adapter = bluetoothManager.getAdapter();
        if (bluetooth4Adapter == null) {
            return false;
        }
        else {
            Log.d(TAG, "该设备支持蓝牙4.0");
            return true;
        }

    }

    /**
     * 获取蓝牙状态
     */
    public boolean isEnable() {
        if (bluetooth4Adapter == null) {
            return false;
        }
        return bluetooth4Adapter.isEnabled();
    }

    /**
     * 打开蓝牙
     *
     * @param forceOpen true 直接打开蓝牙  false 提示用户打开
     */
    public void openBluetooth(Context context, boolean forceOpen) {
        if (!isEnable()) {
            if (forceOpen) {
                Log.d(TAG, "直接打开手机蓝牙");
                if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
                    return;
                }
                bluetooth4Adapter.enable();  //BLUETOOTH_ADMIN权限
            }
            else {
                Log.d(TAG, "提示用户去打开手机蓝牙");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                context.startActivity(enableBtIntent);
            }
        }
        else {
            Log.d(TAG, "手机蓝牙状态已开");
        }
    }

    /**
     * 直接关闭蓝牙 This gracefully shuts down all Bluetooth connections, stops Bluetooth system services, and powers down the underlying Bluetooth hardware.
     */
    public void closeBluetooth() {
        if (bluetooth4Adapter == null)
            return;

        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
            return;
        }
        bluetooth4Adapter.disable();
    }


    /**
     * 本地蓝牙是否处于正在扫描状态
     *
     * @return true false
     */
    public boolean isDiscovery() {
        if (bluetooth4Adapter == null) {
            return false;
        }

        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_SCAN)) return false;

        return bluetooth4Adapter.isDiscovering();
    }

    public ArrayList<BluetoothDevice> GetBondDevices(UUID matchUuid) {

        ArrayList<BluetoothDevice> devicesList = new ArrayList<>();

        if (!checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Manifest.permission.BLUETOOTH_CONNECT is not granted.");
            return devicesList;
        }

        Set<BluetoothDevice> btDevices = bluetooth4Adapter.getBondedDevices();
        for (BluetoothDevice btDevice : btDevices) {
            ParcelUuid[] uuids = btDevice.getUuids();

            for (ParcelUuid uuid : uuids) {
                if(uuid.getUuid().equals(matchUuid)){
                    devicesList.add(btDevice);
                    break;
                }
            }
        }

        return devicesList;
    }
}
