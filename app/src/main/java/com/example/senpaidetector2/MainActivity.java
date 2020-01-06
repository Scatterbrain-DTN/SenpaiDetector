package com.example.senpaidetector2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.example.uscatterbrain.ScatterRoutingService;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";
    private ScatterRoutingService mService;
    private Switch mServiceToggle;
    private Switch mDiscoveryToggle;
    private TextView mStatusTextView;
    private TextView mLogsTextView;
    private Button mRefreshLogsButton;
    private boolean mBound;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScatterRoutingService.ScatterBinder binder = (ScatterRoutingService.ScatterBinder) service;
            mService = binder.getService();
            mService.startService();
            mBound = true;
            mServiceToggle.setChecked(true);
            mStatusTextView.setText("RUNNING");
            Log.v(TAG, "connected to ScatterRoutingService binder");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mStatusTextView.setText("STOPPED");
            mServiceToggle.setChecked(false);
        }
    };

    private void updateLogs() {
        mLogsTextView.setText("log update pending...");
        try {
            Process proc = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String l = bufferedReader.readLine();
            while (l != null) {
                mLogsTextView.append(l);
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

        mStatusTextView = (TextView) findViewById(R.id.status);

        mLogsTextView = (TextView) findViewById(R.id.logstextview);

        mServiceToggle = (Switch) findViewById(R.id.servicetoggle);
        mServiceToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!isChecked) {
                    if(mBound) {
                        mService.stopService();
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

        mDiscoveryToggle = (Switch) findViewById(R.id.discoverytoggle);
        mDiscoveryToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(mBound) {
                    if (!isChecked) {
                        mService.scanOn(null);
                        if(mBound)
                            mStatusTextView.setText("RUNNING");
                        else
                            mStatusTextView.setText("STOPPED");
                    } else {
                        mService.scanOn(null);
                        mStatusTextView.setText("DISCOVERING");
                    }
                }
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
