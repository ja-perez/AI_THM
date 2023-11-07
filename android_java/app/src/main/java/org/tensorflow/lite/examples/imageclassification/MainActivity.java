/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.imageclassification;

import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.tensorflow.lite.examples.imageclassification.databinding.ActivityMainBinding;

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import java.io.File;

/** Entrypoint for app */
public class MainActivity extends AppCompatActivity {
    PowerManager.OnThermalStatusChangedListener thermalStatusListener;
    PowerManager pm;

    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    String fileSeries = dateFormat.format(new Date());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());
        pm = (PowerManager) getSystemService(POWER_SERVICE);

        dataCollection();
    }

    protected void OnResume() {
        super.onResume();
        System.out.println("in resume");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalStatusListener = new PowerManager.OnThermalStatusChangedListener() {
                @Override
                public void onThermalStatusChanged(int status) {
                    processDataCollection();
                }
            };
            pm.addThermalStatusListener(thermalStatusListener);
        }
    }

    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.removeThermalStatusListener(thermalStatusListener);
        }
    }

    private String getThermalStatusName(int status) {
        String currentStatus = "None";
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE:
                currentStatus = "None";
                break;
            case PowerManager.THERMAL_STATUS_LIGHT:
                currentStatus = "Light";
                break;
            case PowerManager.THERMAL_STATUS_MODERATE:
                currentStatus = "Moderate";
                break;
            case PowerManager.THERMAL_STATUS_SEVERE:
                currentStatus = "Severe";
                break;
            case PowerManager.THERMAL_STATUS_CRITICAL:
                currentStatus = "Critical";
                break;
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                currentStatus = "Emergency";
                break;
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                currentStatus = "Shutdown";
                break;
        }
        return currentStatus;
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

    public void dataCollection() {
        Timer t = new Timer();
        t.scheduleAtFixedRate(
                new TimerTask() {
                    public void run() {
                        processDataCollection();
                    }
                },
                0, 2000);
    }

    public void processDataCollection() {
        String currentFolder = Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath();
        String FILEPATH = currentFolder + File.separator + "Performance_Measurements" + fileSeries + ".csv";
        System.out.println("Processing data");
        System.out.println("Output file path: " + FILEPATH);
        String currentStatus = "Unknown";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            int currentThermalStatus = pm.getCurrentThermalStatus();
            currentStatus = getThermalStatusName(currentThermalStatus);
        }
        System.out.println(currentStatus);
        float currentHeadRoom = 0f;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            currentHeadRoom = pm.getThermalHeadroom(2);
        }
        System.out.println(currentHeadRoom);

    }
}
