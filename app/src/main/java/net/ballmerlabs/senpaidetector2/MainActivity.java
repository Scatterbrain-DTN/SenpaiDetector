package net.ballmerlabs.senpaidetector2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.senpaidetector2.R;

import net.ballmerlabs.scatterbrainsdk.ScatterMessage;
import net.ballmerlabs.scatterbrainsdk.ScatterbrainAPI;
import net.ballmerlabs.uscatterbrain.ScatterRoutingService;
import net.ballmerlabs.uscatterbrain.network.BlockHeaderPacket;
import net.ballmerlabs.uscatterbrain.network.bluetoothLE.CachedLEConnection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";
    private Switch mServiceToggle;
    private Switch mDiscoveryToggle;
    private TextView mStatusTextView;
    private TextView mLogsTextView;
    private Button mRefreshLogsButton;
    private Button mScanButton;
    private ScatterbrainAPI binder;
    private boolean mBound;
    private final ConcurrentHashMap<String, Observable<CachedLEConnection>> connectionCache = new ConcurrentHashMap<>();
    private static final BlockHeaderPacket headerPacket = BlockHeaderPacket.newBuilder()
            .setApplication("fmef".getBytes())
            .setBlockSize(512)
            .setHashes(new ArrayList<>())
            .setSessionID(1)
            .setExtension("fmef")
            .setSig(null)
            .setToDisk(true)
            .setFromFingerprint(null)
            .setToFingerprint(null)
            .build();

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = ScatterbrainAPI.Stub.asInterface(service);
            try {
                File f = File.createTempFile("fmef", "gleep");
                byte[] contents = new byte[128];
                new Random().nextBytes(contents);
                new FileOutputStream(f).write(contents);

                binder.sendMessage(ScatterMessage.newBuilder()
                        .setApplication("fmef")
                        .setFile(f, ParcelFileDescriptor.MODE_READ_WRITE)
                        .setTo(null)
                        .setFrom(null)
                        .build()
                );

                byte[] body = new byte[128];
                new Random().nextBytes(body);
                binder.sendMessage(ScatterMessage.newBuilder()
                        .setApplication("fmef")
                        .setBody(body)
                        .setFrom(null)
                        .setTo(null)
                        .build()
                );
            } catch (RemoteException e) {
                Log.e(TAG, "remoteExceptions: " + e);
            } catch (IOException e) {
                Log.e(TAG, "api ioexception " + e);
            }
            mBound = true;
            mServiceToggle.setChecked(true);
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
        try {
            binder.startDiscovery();
        } catch (RemoteException e) {
            Log.e(TAG, "remoteexception: " + e);
        }
    }

    private void stopScan() {
        try {
            binder.stopDiscovery();
        } catch (RemoteException e) {
            Log.e(TAG, "remoteexception " + e);
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

        mLogsTextView = (TextView) findViewById(R.id.logstextview);
        mStatusTextView = (TextView) findViewById(R.id.statustext);
        mLogsTextView.setMovementMethod(new ScrollingMovementMethod());

        mServiceToggle = (Switch) findViewById(R.id.servicetoggle);
        mServiceToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!isChecked) {
                    if(mBound) {
                        mStatusTextView.setText("STOPPED");
                        unbindService(mServiceConnection);
                    }
                } else {
                    Intent startIntent = new Intent(getApplicationContext(), ScatterRoutingService.class);
                    startService(startIntent);
                    Intent bindIntent = new Intent(getApplicationContext(), ScatterRoutingService.class);
                    bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                }
            }
        });


        Button manualButton = findViewById(R.id.serveronlybutton);
        manualButton.setOnClickListener(click -> {
            try {
                binder.startPassive();
            } catch (RemoteException e) {
                Log.e(TAG, "remoteException, failed to start passive mode");
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
