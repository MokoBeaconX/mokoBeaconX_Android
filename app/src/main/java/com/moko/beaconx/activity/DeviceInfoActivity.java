package com.moko.beaconx.activity;


import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.IdRes;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.moko.beaconx.R;
import com.moko.beaconx.service.MokoService;
import com.moko.support.MokoConstants;
import com.moko.support.MokoSupport;
import com.moko.support.entity.OrderType;
import com.moko.support.task.OrderTaskResponse;
import com.moko.support.task.WriteConfigTask;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class DeviceInfoActivity extends FragmentActivity implements RadioGroup.OnCheckedChangeListener {
    @Bind(R.id.frame_container)
    FrameLayout frameContainer;
    @Bind(R.id.radioBtn_slot)
    RadioButton radioBtnSlot;
    @Bind(R.id.radioBtn_setting)
    RadioButton radioBtnSetting;
    @Bind(R.id.radioBtn_device)
    RadioButton radioBtnDevice;
    @Bind(R.id.rg_options)
    RadioGroup rgOptions;
    private MokoService mMokoService;
    private FragmentManager fragmentManager;
    private SlotFragment slotFragment;
    private SettingFragment settingFragment;
    private DeviceFragment deviceFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);
        ButterKnife.bind(this);
        Intent intent = new Intent(this, MokoService.class);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
        fragmentManager = getFragmentManager();
        showDeviceFragment();
        showSettingFragment();
        showSlotFragment();
        rgOptions.setOnCheckedChangeListener(this);
        radioBtnSlot.setChecked(true);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMokoService = ((MokoService.LocalBinder) service).getService();
            // 注册广播接收器
            IntentFilter filter = new IntentFilter();
            filter.addAction(MokoConstants.ACTION_CONNECT_SUCCESS);
            filter.addAction(MokoConstants.ACTION_CONNECT_DISCONNECTED);
            filter.addAction(MokoConstants.ACTION_RESPONSE_SUCCESS);
            filter.addAction(MokoConstants.ACTION_RESPONSE_TIMEOUT);
            filter.addAction(MokoConstants.ACTION_RESPONSE_FINISH);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.setPriority(100);
            registerReceiver(mReceiver, filter);
            if (!MokoSupport.getInstance().isBluetoothOpen()) {
                // 蓝牙未打开，开启蓝牙
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, MokoConstants.REQUEST_CODE_ENABLE_BT);
            } else {
                // 获取可读数据
                showSyncingProgressDialog();
                mMokoService.sendOrder(mMokoService.getSlotType(), mMokoService.getDeviceMac(),
                        mMokoService.getDeviceName(), mMokoService.getConnectable(),
                        mMokoService.getManufacturer(), mMokoService.getDeviceModel(),
                        mMokoService.getProductDate(), mMokoService.getHardwareVersion(),
                        mMokoService.getFirmwareVersion(), mMokoService.getSoftwareVersion(),
                        mMokoService.getBattery());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            abortBroadcast();
            if (intent != null) {
                String action = intent.getAction();
                if (MokoConstants.ACTION_RESPONSE_TIMEOUT.equals(action)) {
                }
                if (MokoConstants.ACTION_RESPONSE_FINISH.equals(action)) {
                    dismissSyncProgressDialog();
                }
                if (MokoConstants.ACTION_RESPONSE_SUCCESS.equals(action)) {
                    OrderTaskResponse response = (OrderTaskResponse) intent.getSerializableExtra(MokoConstants.EXTRA_KEY_RESPONSE_ORDER_TASK);
                    OrderType orderType = response.orderType;
                    int responseType = response.responseType;
                    byte[] value = response.responseValue;
                    switch (orderType) {
                        case writeConfig:
                            if (value.length >= 2) {
                                int header = value[1] & 0xff;
                                switch (header) {
                                    case WriteConfigTask.CONFIG_TYPE_GET_SLOT:
                                        if (value.length >= 9) {
                                            slotFragment.updateSlotType(value);
                                        }
                                        break;
                                    case WriteConfigTask.CONFIG_TYPE_GET_MAC:
                                        if (value.length >= 10) {
                                            deviceFragment.setDeviceMac(value);
                                        }
                                        break;
                                    case WriteConfigTask.CONFIG_TYPE_GET_NAME:
                                        if (value.length >= 4) {
                                            settingFragment.setDeviceName(value);
                                        }
                                        break;
                                    case WriteConfigTask.CONFIG_TYPE_GET_CONNECTABLE:
                                        if (value.length >= 5) {
                                            settingFragment.setConnectable(value);
                                        }
                                        break;
                                }

                            }
                            break;
                        case manufacturer:
                            deviceFragment.setManufacturer(value);
                            break;
                        case deviceModel:
                            deviceFragment.setDeviceModel(value);
                            break;
                        case productDate:
                            deviceFragment.setProductDate(value);
                            break;
                        case hardwareVersion:
                            deviceFragment.setHardwareVersion(value);
                            break;
                        case firmwareVersion:
                            deviceFragment.setFirmwareVersion(value);
                            break;
                        case softwareVersion:
                            deviceFragment.setSoftwareVersion(value);
                            break;
                        case battery:
                            deviceFragment.setBattery(value);
                            break;
                    }
                }

                if (MokoConstants.ACTION_RESPONSE_NOTIFY.equals(action)) {
                    OrderType orderType = (OrderType) intent.getSerializableExtra(MokoConstants.EXTRA_KEY_RESPONSE_ORDER_TYPE);
                    byte[] value = intent.getByteArrayExtra(MokoConstants.EXTRA_KEY_RESPONSE_VALUE);
                }
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    switch (blueState) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            dismissSyncProgressDialog();
                            break;

                    }
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case MokoConstants.REQUEST_CODE_ENABLE_BT:

                    break;

            }
        } else {
            switch (requestCode) {
                case MokoConstants.REQUEST_CODE_ENABLE_BT:
                    // 未打开蓝牙
                    finish();
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unbindService(mServiceConnection);
    }

    private ProgressDialog syncingDialog;

    private void showSyncingProgressDialog() {
        syncingDialog = new ProgressDialog(this);
        syncingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        syncingDialog.setCanceledOnTouchOutside(false);
        syncingDialog.setCancelable(false);
        syncingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        syncingDialog.setMessage("Syncing...");
        if (!isFinishing() && syncingDialog != null && !syncingDialog.isShowing()) {
            syncingDialog.show();
        }
    }

    private void dismissSyncProgressDialog() {
        if (!isFinishing() && syncingDialog != null && syncingDialog.isShowing()) {
            syncingDialog.dismiss();
        }
    }

    @OnClick({R.id.tv_back})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.tv_back:
                back();
                break;
        }
    }

    private void back() {
        MokoSupport.getInstance().disConnectBle();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            back();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showSlotFragment() {
        if (slotFragment == null) {
            slotFragment = SlotFragment.newInstance();
            fragmentManager.beginTransaction().add(R.id.frame_container, slotFragment).commit();
        } else {
            fragmentManager.beginTransaction().hide(settingFragment).hide(deviceFragment).show(slotFragment).commit();
        }
    }

    private void showSettingFragment() {
        if (settingFragment == null) {
            settingFragment = SettingFragment.newInstance();
            fragmentManager.beginTransaction().add(R.id.frame_container, settingFragment).commit();
        } else {
            fragmentManager.beginTransaction().hide(slotFragment).hide(deviceFragment).show(settingFragment).commit();
        }
    }

    private void showDeviceFragment() {
        if (deviceFragment == null) {
            deviceFragment = DeviceFragment.newInstance();
            fragmentManager.beginTransaction().add(R.id.frame_container, deviceFragment).commit();
        } else {
            fragmentManager.beginTransaction().hide(slotFragment).hide(settingFragment).show(deviceFragment).commit();
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        switch (checkedId) {
            case R.id.radioBtn_slot:
                showSlotFragment();
                break;
            case R.id.radioBtn_setting:
                showSettingFragment();
                break;
            case R.id.radioBtn_device:
                showDeviceFragment();
                break;
        }
    }
}