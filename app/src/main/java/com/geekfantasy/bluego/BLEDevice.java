package com.geekfantasy.bluego;

import android.bluetooth.BluetoothDevice;

/**
 * 作者：yeqianyun on 2019/11/6 17:22
 * 邮箱：1612706976@qq.com
 *
 * BLE蓝牙设备
 */
public class BLEDevice {
    private BluetoothDevice bluetoothDevice;  //蓝牙设备
    private int RSSI;  //蓝牙信号

    public BLEDevice(BluetoothDevice bluetoothDevice, int RSSI) {
        this.bluetoothDevice = bluetoothDevice;
        this.RSSI = RSSI;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public int getRSSI() {
        return RSSI;
    }

    public void setRSSI(int RSSI) {
        this.RSSI = RSSI;
    }

    // add this to avoid adding duplicated searched devices to device list
    @Override
    public boolean equals(Object o) {
        if (o instanceof BLEDevice) {
            BLEDevice bleObj = (BLEDevice)o;
            return this.bluetoothDevice.equals(bleObj.bluetoothDevice);
        }
        return super.equals(o);
    }
}
