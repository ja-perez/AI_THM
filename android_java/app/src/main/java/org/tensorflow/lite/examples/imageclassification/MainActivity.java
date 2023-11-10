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
import org.tensorflow.lite.examples.imageclassification.databinding.FragmentCameraBinding;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import java.io.File;

/*
TODO: General Functionality
    - Start/Stop classification
    - Add model for classification

TODO: Data collection
    - get[CPU/GPU]Frequency
    - get[CPU/GPU/NPU]Temperature
    - getThroughput

TODO: Data processing
    - processThermalData
    - processFrequencyData
    - processThroughputData

TODO: Experiment specification
    - runTestConfiguration
    - setTaskToDelegate
    - setTaskPeriod
 */

/** Entrypoint for app */
public class MainActivity extends AppCompatActivity {
    PowerManager.OnThermalStatusChangedListener thermalStatusListener = null;
    PowerManager pm;

    SimpleDateFormat dateFormat;
    String fileSeries;
    String performanceFileName = "Performance_Measurements";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());
        pm = (PowerManager) getSystemService(POWER_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalStatusListener = status -> processDataCollection();
            pm.addThermalStatusListener(thermalStatusListener);
        }

        dateFormat = new SimpleDateFormat("HH:mm:ss");
        fileSeries = dateFormat.format(new Date());
        // Create file for data collection
        String currentFolder = Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath();
        String FILEPATH = currentFolder + File.separator + performanceFileName + fileSeries + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, false))) {
            StringBuilder sb = new StringBuilder();
            sb.append("time");
            sb.append(',');
            sb.append("thermalStatus");
            sb.append(',');
            sb.append("cpuTemperature");
            sb.append(',');
            sb.append("gpuTemperature");
            sb.append(',');
            sb.append("npuTemperature");
            sb.append(',');
            sb.append("cpuFrequency");
            sb.append(',');
            sb.append("gpuFrequency");
            sb.append('\n');
            writer.write(sb.toString());
            System.out.println("Creating " + performanceFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatusListener != null) {
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
                    @Override
                    public void run() {
                        processDataCollection();
                    }
                },
                0, 2000);
    }

    public void processDataCollection() {
        String currentFolder = Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath();
        String FILEPATH = currentFolder + File.separator + performanceFileName + fileSeries + ".csv";

        ArrayList<Float> currentFrequencies = processFrequencyData();
        ArrayList<String> currentThermalData = processThermalData();

        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, false))) {
            StringBuilder sb = new StringBuilder();
            sb.append("time");
            sb.append(',');
            sb.append(currentThermalData.get(0));
            sb.append(',');
            sb.append(currentThermalData.get(1));
            sb.append(',');
            sb.append(currentThermalData.get(2));
            sb.append(',');
            sb.append(currentThermalData.get(3));
            sb.append(',');
            sb.append(currentFrequencies.get(0));
            sb.append(',');
            sb.append(currentFrequencies.get(1));
            sb.append('\n');
            writer.write(sb.toString());
            System.out.println("Creating " + performanceFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }
    }

    private ArrayList<Float> processFrequencyData() {
        /*
        currentFrequencies = [cpuFrequency, gpuFrequency]
         */
        ArrayList<Float> currentFrequencies = new ArrayList<>();

        return currentFrequencies;
    }

    private ArrayList<String> processThermalData() {
        /*
        currentThermalData = [thermalStatus, cpuTemperature, gpuTemperature, npuTemperature]
         */

        ArrayList<String> currentThermalData= new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            int currentThermalStatus = pm.getCurrentThermalStatus();
            String currentStatus = getThermalStatusName(currentThermalStatus);
            currentThermalData.add(currentStatus);
        }

        return currentThermalData;
    }

}
