package com.example.senpaidetector2;

import android.bluetooth.BluetoothGatt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uscatterbrain.DeviceProfile;
import com.example.uscatterbrain.ScatterProto;
import com.example.uscatterbrain.ScatterRoutingService;
import com.example.uscatterbrain.network.BlockHeaderPacket;
import com.example.uscatterbrain.network.LuidPacket;
import com.example.uscatterbrain.network.UpgradePacket;
import com.example.uscatterbrain.network.bluetoothLE.BluetoothLEModule;
import com.example.uscatterbrain.network.bluetoothLE.BluetoothLERadioModuleImpl;
import com.example.uscatterbrain.network.bluetoothLE.GattClientTransaction;
import com.example.uscatterbrain.network.bluetoothLE.GattServerConnectionConfig;
import com.example.uscatterbrain.network.wifidirect.WifiDirectRadioModule;
import com.google.protobuf.ByteString;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.BehaviorSubject;

import static com.example.uscatterbrain.network.bluetoothLE.BluetoothLERadioModuleImpl.SERVICE_UUID;

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
    private Button mManualButton;
    private Button mClientButton;
    private RxBleServer mServer;
    private boolean mBound;
    private Disposable p2pdisposable;
    private Disposable manualDisposable = null;
    private final ConcurrentHashMap<String, Observable<RxBleConnection>> connectionCache = new ConcurrentHashMap<>();
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

    private void tryP2pConnection(BluetoothLEModule.ConnectionRole role) {
        BluetoothLEModule.UpgradeRequest request = BluetoothLEModule.UpgradeRequest.create(
                role,
                UpgradePacket.newBuilder()
                        .setSessionID(1)
                        .setMetadata(WifiDirectRadioModule.UPGRADE_METADATA)
                        .setProvides(ScatterProto.Advertise.Provides.WIFIP2P)
                        .build());
        if (p2pdisposable != null) {
            p2pdisposable.dispose();
        }
        p2pdisposable = mService.getWifiDirect().bootstrapFromUpgrade(
                request,
                Observable.just(new WifiDirectRadioModule.BlockDataStream(
                        headerPacket,
                        Flowable.empty()
                ))
        ).subscribe(
                ok -> Log.v(TAG, "successfually transfered blockdata"),
                err -> {
                    Log.e(TAG, "failed to transfer blockdata: " + err);
                }
        );
    }

    public void manualServer(RxBleClient client) {
        mServer = RxBleServer.create(getApplicationContext());
        ServerConfig config = ServerConfig.newInstance(new Timeout(5, TimeUnit.SECONDS))
                .addService(BluetoothLERadioModuleImpl.mService);

        mServer.openServer(config)
                .flatMapCompletable(connection -> {
                    RxBleDevice device = client.getBleDevice(connection.getDevice().getAddress());
                    GattServerConnectionConfig.setDefaultReply(
                            connection,
                            BluetoothLERadioModuleImpl.UUID_LUID,
                            BluetoothGatt.GATT_SUCCESS
                    );
                    GattServerConnectionConfig.serverNotify(
                            connection,
                            LuidPacket.newBuilder().setLuid(UUID.randomUUID()).enableHashing().build(),
                            BluetoothLERadioModuleImpl.UUID_LUID
                    );
                    return establishConnection(device, new Timeout(10, TimeUnit.SECONDS))
                            .flatMapSingle(GattClientTransaction::readLuid)
                            .doOnNext(bytes -> Log.v(TAG, "received luid: " + bytes))
                            .ignoreElements();
                })
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        Log.v(TAG, "gatt server onSubscribe");
                        manualDisposable = d;
                    }

                    @Override
                    public void onComplete() {
                        Log.v(TAG, "gatt server onComplete");
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.e(TAG, "gatt server onError: " + e);
                    }
                });


    }

    private Observable<RxBleConnection> establishConnection(RxBleDevice device, Timeout timeout) {

        Observable<RxBleConnection> conn = connectionCache.get(device.getMacAddress());
        if (conn != null) {
            return conn;
        }
        BehaviorSubject<RxBleConnection> subject = BehaviorSubject.create();
        connectionCache.put(device.getMacAddress(), subject);
        return device.establishConnection(false, timeout)
                .doOnDispose(() -> connectionCache.remove(device.getMacAddress()))
                .doOnError(err -> connectionCache.remove(device.getMacAddress()))
                .doOnNext(connection -> {
                    Log.v(TAG, "successfully established connection");
                    subject.onNext(connection);
                });
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
                tryP2pConnection(BluetoothLEModule.ConnectionRole.ROLE_UKE);
            }
        });


        mConnectGroupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryP2pConnection(BluetoothLEModule.ConnectionRole.ROLE_SEME);
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


        mManualButton = (Button) findViewById(R.id.manualbutton);

        mManualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mServer = RxBleServer.create(getApplicationContext());
                ServerConfig config = ServerConfig.newInstance(new Timeout(5, TimeUnit.SECONDS))
                        .addService(BluetoothLERadioModuleImpl.mService);

                if (manualDisposable != null) {
                    manualDisposable.dispose();
                    manualDisposable = null;
                    mStatusTextView.setText("Discovering...");
                    mService.getRadioModule().startServer();
                    return;
                }

                mService.getRadioModule().stopDiscover();
                mService.getRadioModule().stopServer();
                mStatusTextView.setText("manual gatt");
                manualServer(RxBleClient.create(getApplicationContext()));
            }
        });

        mClientButton = findViewById(R.id.manual_client);

        mClientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (manualDisposable != null) {
                    manualDisposable.dispose();
                    manualDisposable = null;
                    mStatusTextView.setText("Discovering...");
                    mService.getRadioModule().startServer();
                    return;
                }

                mService.getRadioModule().stopDiscover();
                mService.getRadioModule().stopServer();
                mStatusTextView.setText("manual gatt client");

                RxBleClient client = RxBleClient.create(getApplicationContext());
                manualServer(client);

                client.scanBleDevices(
                        new ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                                .setShouldCheckLocationServicesState(true)
                                .build(),
                        new ScanFilter.Builder()
                                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                                .build())
                        .concatMap(conn -> {
                            return establishConnection(conn.getBleDevice(), new Timeout(10, TimeUnit.SECONDS));
                        }).subscribe();

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
