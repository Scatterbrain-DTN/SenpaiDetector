package com.example.senpaidetector2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uscatterbrain.DeviceProfile;
import com.example.uscatterbrain.ScatterRoutingService;
import com.example.uscatterbrain.network.BlockHeaderPacket;
import com.example.uscatterbrain.network.bluetoothLE.BluetoothLEModule;
import com.example.uscatterbrain.network.wifidirect.WifiDirectRadioModule;
import com.google.protobuf.ByteString;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";
    private ScatterRoutingService mService;
    private Switch mServiceToggle;
    private Switch mDiscoveryToggle;
    private TextView mStatusTextView;
    private TextView mLogsTextView;
    private Button mRefreshLogsButton;
    private Button mScanButton;
    private Button mConnectGroupButton;
    private Button mCreateGroupButton;
    private boolean mBound;
    private static final BlockHeaderPacket headerPacket = BlockHeaderPacket.newBuilder()
            .setApplication("fmef".getBytes())
            .setBlockSize(512)
            .setHashes(new ArrayList<>())
            .setSessionID(1)
            .setSig(ByteString.copyFrom(new byte[8]))
            .setToDisk(true)
            .setFromFingerprint(ByteString.copyFrom(new byte[8]))
            .setToFingerprint(ByteString.copyFrom(new byte[8]))
            .build();

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScatterRoutingService.ScatterBinder binder = (ScatterRoutingService.ScatterBinder) service;
            mService = binder.getService();
            DeviceProfile dp = new DeviceProfile(DeviceProfile.HardwareServices.BLUETOOTHLE, UUID.randomUUID());
            mService.setProfile(dp);
            mBound = true;
            Disposable d = mService.getRadioModule().getOnUpgrade()
                    .subscribe(upgradeRequest ->
                            mService.getWifiDirect().bootstrapFromUpgrade(
                                    upgradeRequest,
                                    Observable.just(new WifiDirectRadioModule.BlockDataStream(
                                            headerPacket,
                                            Flowable.empty()
                                    ))
                            ).subscribe(
                                    ok -> Log.v(TAG, "successfually transfered blockdata"),
                                    err -> Log.e(TAG, "failed to transfer blockdata: " + err)
                            ),
                            error -> Log.v(TAG, "getOnUpgrade returned error " + error)
                    );
            mServiceToggle.setChecked(true);
            mStatusTextView.setText("RUNNING");
            mService.scanOn(null);
            mService.advertiseOn();
            mService.getRadioModule().startServer();
            mStatusTextView.setText("DISCOVERING");
            Log.v(TAG, "connected to ScatterRoutingService binder");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mStatusTextView.setText("STOPPED");
            mServiceToggle.setChecked(false);
        }
    };

    private void scan() {
        if (mService != null && mBound) {
            mService.getRadioModule().startDiscover(BluetoothLEModule.discoveryOptions.OPT_DISCOVER_ONCE);
        }
    }

    private void stopScan() {
        if (mService != null && mBound) {
            mService.getRadioModule().stopDiscover();
        }
    }

    private void updateLogs() {
        mLogsTextView.setText("");
        try {
            Process proc = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String l = bufferedReader.readLine();
            while (l != null) {
                mLogsTextView.append(l + "\n");
                l = bufferedReader.readLine();
            }
        } catch(Exception e) {
            Log.e(TAG, "failed to update logs: " + e.toString());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBound = false;

        mCreateGroupButton = (Button) findViewById(R.id.creategroupbutton);
        mConnectGroupButton = (Button) findViewById(R.id.connectgroupbutton);

        mCreateGroupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.getWifiDirect().createGroup();
            }
        });


        mConnectGroupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.getWifiDirect().connectToGroup();
            }
        });

        mStatusTextView = (TextView) findViewById(R.id.status);

        mLogsTextView = (TextView) findViewById(R.id.logstextview);
        mLogsTextView.setMovementMethod(new ScrollingMovementMethod());

        mServiceToggle = (Switch) findViewById(R.id.servicetoggle);
        mServiceToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!isChecked) {
                    if(mBound) {
                        mService.scanOff(null);
                        mStatusTextView.setText("STOPPED");
                        unbindService(mServiceConnection);
                    }
                } else {
                    Intent bindIntent = new Intent(getApplicationContext(), ScatterRoutingService.class);
                    bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                }
            }
        });

        mServiceToggle.setChecked(true);

        mScanButton= (Button) findViewById(R.id.scanbutton);

        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLogsTextView.setText("Scanning...");
                scan();
            }
        });

        mRefreshLogsButton = (Button) findViewById(R.id.updatelogsbutton);
        mRefreshLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateLogs();
            }
        });


    }

    @Override
    public void onStop() {
        super.onStop();
        if(mBound) {
            unbindService(mServiceConnection);
        }
    }
}
