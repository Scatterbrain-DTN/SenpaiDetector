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
import android.widget.CompoundButton;
import android.widget.Switch;

import com.example.uscatterbrain.ScatterRoutingService;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";
    private ScatterRoutingService mService;
    private Switch mServiceToggle;
    private boolean mBound;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScatterRoutingService.ScatterBinder binder = (ScatterRoutingService.ScatterBinder) service;
            mService = binder.getService();
            mBound = true;
            mServiceToggle.setChecked(true);
            Log.v(TAG, "connected to ScatterRoutingService binder");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mServiceToggle.setChecked(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBound = false;

        mServiceToggle = (Switch) findViewById(R.id.servicetoggle);
        mServiceToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    if(mBound) {
                        mServiceToggle.setChecked(true);
                    }
                } else {
                    Intent bindIntent = new Intent(getApplicationContext(), ScatterRoutingService.class);
                    bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                    mServiceToggle.setChecked(false);
                }
            }
        });
    }
}
