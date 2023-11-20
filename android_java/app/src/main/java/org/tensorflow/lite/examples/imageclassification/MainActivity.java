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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/*
TODO: General Functionality
    - Start/Stop classification
    - Add model for classification

TODO: Experiment specification
    - runTestConfiguration
    - setTaskToDelegate
    - setTaskPeriod
 */

/** Entrypoint for app */
public class MainActivity extends AppCompatActivity {
//    PowerManager.OnThermalStatusChangedListener thermalStatusListener = null;
//    PowerManager pm;

    private PFManager pfManager;

    SimpleDateFormat dateFormat;
    String fileSeries;
    String performanceFileName = "Performance_Measurements";
    String[] thermalZonePaths;
    String[] cpuDevicePaths;
    String currentThermalStatus = "Unknown";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());
//        pm = (PowerManager) getSystemService(POWER_SERVICE);

        try {
            thermalZonePaths = getThermalZoneFilePaths("/sys/class/thermal");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            cpuDevicePaths = getCPUDeviceFiles("/sys/devices/system/cpu");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
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
            sb.append(',');
            sb.append("cpuUtilization");
            sb.append(',');
            sb.append("gpuUtilization");
            sb.append('\n');
            writer.write(sb.toString());
            System.out.println("Creating " + performanceFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                this.pfManager = new PFManager();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pfManager.setPerformanceFilePath(FILEPATH);
        }

        dataCollection();
    }

    @Override
    protected void onResume() {
        System.out.println("in resume");
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            thermalStatusListener = status -> {
//                int currentStatus = pm.getCurrentThermalStatus();
//                currentThermalStatus = getThermalStatusName(currentStatus);
//            };
//            pm.addThermalStatusListener(thermalStatusListener);
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Boolean result = pfManager.registerListener(this);
        }

        super.onResume();
    }

    protected void onPause() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatusListener != null) {
//            pm.removeThermalStatusListener(thermalStatusListener);
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pfManager.unregisterListener(this);
        }

        super.onPause();
    }

    private String getThermalStatusName(int status) {
        String currentStatus = "Unknown";
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
                        try {
                            processDataCollection();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                0, 2000);
    }

    public void processDataCollection() throws IOException {
        String currentFolder = Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath();
        String FILEPATH = currentFolder + File.separator + performanceFileName + fileSeries + ".csv";

        ArrayList<String> currentThermalData = processThermalData();
        ArrayList<Float> currentFrequencies = processFrequencyData();
        ArrayList<String> currentUtilizations = processUtilizationData();

        dateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
        String currTime = dateFormat.format(new Date());
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, true))) {
            String sb = currTime +
                    ',' +
                    currentThermalData.get(0) +
                    ',' +
                    currentThermalData.get(1) +
                    ',' +
                    currentThermalData.get(2) +
                    ',' +
                    currentThermalData.get(3) +
                    ',' +
                    currentFrequencies.get(0) +
                    ',' +
                    currentFrequencies.get(1) +
                    ',' +
                    currentUtilizations.get(0) +
                    ',' +
                    currentUtilizations.get(1) +
                    '\n';
            writer.write(sb);
            System.out.println("Writing to " + performanceFileName + " done!");
        } catch (FileNotFoundException | IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }
    }

    private ArrayList<String> processUtilizationData() {
        /*
        currentUtilizations = [cpuUtilization, gpuUtilization]
         */
        ArrayList<String> currentUtilizations = new ArrayList<>();

        // Get CPU Utilization
        String cpuUtilization = "0";
        try {
            cpuUtilization = getCPUUtilization();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentUtilizations.add(cpuUtilization);

        // Get GPU Utilization
        Float gpuUtilization = 0f;
        try {
            gpuUtilization = getGPUUtilization("/sys/class/kgsl/kgsl-3d0");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentUtilizations.add(Float.toString(gpuUtilization));

        return currentUtilizations;
    }

    private ArrayList<Float> processFrequencyData() {
        /*
        currentFrequencies = [cpuFrequency, gpuFrequency]
         */
        ArrayList<Float> currentFrequencies = new ArrayList<>();

        // Get CPU frequencies
        Float avgCPUFreq = 0f, gpuFreq = 0f;
        for (String cpuDevicePath: cpuDevicePaths) {
            try {
                avgCPUFreq += getCPUFrequency(cpuDevicePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        avgCPUFreq /= cpuDevicePaths.length;
        currentFrequencies.add(avgCPUFreq);

        // Get GPU Frequency
        try {
            gpuFreq = getGPUFrequency("/sys/class/kgsl/kgsl-3d0");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentFrequencies.add(gpuFreq);

        return currentFrequencies;
    }

    private ArrayList<String> processThermalData() throws IOException {
        /*
        currentThermalData = [thermalStatus, cpuTemperature, gpuTemperature, npuTemperature]
         */

        ArrayList<String> currentThermalData= new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentThermalStatus = pfManager.currentThermalStatus;
        }
        currentThermalData.add(currentThermalStatus);

        Float avgCPUTemp = 0f, avgGPUTemp = 0f, avgNPUTemp = 0f, currFileTemp = 0f;
        int cpuCount = 0, gpuCount = 0, npuCount = 0;
        for (String zoneFilePath: thermalZonePaths) {
            currFileTemp = getThermalZoneTemp(zoneFilePath);
            String currFileType = getThermalZoneType(zoneFilePath);
            if (currFileType.contains("cpu")) {
                avgCPUTemp += currFileTemp;
                cpuCount++;
            } else if (currFileType.contains("gpu")) {
                avgGPUTemp += currFileTemp;
                gpuCount++;
            } else if (currFileType.contains("npu")) {
                avgNPUTemp += currFileTemp;
                npuCount++;
            }
        }
        avgCPUTemp /= cpuCount;
        avgGPUTemp /= gpuCount;
        avgNPUTemp /= npuCount;
        currentThermalData.add(Float.toString(avgCPUTemp));
        currentThermalData.add(Float.toString(avgGPUTemp));
        currentThermalData.add(Float.toString(avgNPUTemp));

        return currentThermalData;
    }

    private static String[] getThermalZoneFilePaths(String thermalDir)
            throws IOException, InterruptedException {
        // thermalDir for Note10+ is usually "/sys/class/thermal"
        String cmd = String.format("ls %s | grep 'thermal_zone'", thermalDir);
        Process process = Runtime.getRuntime().exec( new String [] {"sh", "-c", cmd});
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String currFileName;
        ArrayList<String> thermalZonePaths = new ArrayList<>();
        while ((currFileName = reader.readLine()) != null) {
            String thermalZoneFilePath = thermalDir + "/" + currFileName + "/";
            String thermalZoneType = getThermalZoneType(thermalZoneFilePath);
            // Filter thermal zones to only track cpu, gpu, and npu
            if (thermalZoneType.contains("cpu") || thermalZoneType.contains("gpu") ||
                    thermalZoneType.contains("npu")) {
                thermalZonePaths.add(thermalZoneFilePath);
            }
        }

        reader.close();
        process.waitFor();
        return thermalZonePaths.toArray(new String[0]);
    }

    private static String getThermalZoneType(String thermalZonePath) throws IOException {
        String cmd = String.format("cat %s/type", thermalZonePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currLine = reader.readLine();
        if (currLine != null) {
            return currLine;
        }
        return "default_zone_type";
    }

    private static Float getThermalZoneTemp(String thermalZonePath) throws IOException {
        String cmd = String.format("cat %s/temp", thermalZonePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currTemp = reader.readLine();
        if (currTemp != null) {
            int tmpMCValue = Integer.parseInt(currTemp);
            if (tmpMCValue < 0) tmpMCValue = 0;
            return tmpMCValue / 1000f;
        }
        return (float) 0;
    }

    private static String[] getCPUDeviceFiles(String cpuDeviceDirs)
            throws IOException, InterruptedException {
        // cpuDeviceDirs for Note10+ is usually "/sys/devices/system/cpu"
        String cmd = String.format("ls %s | grep 'cpu[0-7]'", cpuDeviceDirs);
        Process process = Runtime.getRuntime().exec( new String [] {"sh", "-c", cmd});
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currLine;
        List<String> cpuDevicePaths = new ArrayList<>();
        while ((currLine = reader.readLine()) != null) {
            String cpuDeviceFilePath = cpuDeviceDirs + "/" + currLine + "/";
            cpuDevicePaths.add(cpuDeviceFilePath);
        }

        reader.close();
        process.waitFor();
        return cpuDevicePaths.toArray(new String[0]);
    }

    private static Float getCPUFrequency(String cpuDevicePath) throws IOException {
        String cmd = String.format("cat %s/cpufreq/scaling_cur_freq", cpuDevicePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currFreq = reader.readLine();
        if (currFreq != null) {
            float tempFreq = Float.parseFloat(currFreq);
            tempFreq /= 1000000.0;
            return tempFreq;
        }
        return (float) 0;
    }

    private static Float getGPUFrequency(String gpuDevicePath) throws IOException {
        String cmd = String.format("cat %s/clock_mhz", gpuDevicePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String currentGPUFreq = reader.readLine();
        if (currentGPUFreq != null) {
            // convert to hz
            float mhzGPUFreq = Float.parseFloat(currentGPUFreq);
            return mhzGPUFreq / 1000;
        }
        return (float) 0;
    }

    private static String getCPUUtilization() throws IOException {
        String cmd = String.format("top -s 6");
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String curr_cpu;
        while ((curr_cpu = reader.readLine()) != null) {
            if (curr_cpu.contains("org.tensorflow")) {
                while (curr_cpu.contains("  ")) {
                    curr_cpu = curr_cpu.replace("  ", " ");
                }
                curr_cpu = curr_cpu.replaceAll(" ", ",");
                break;
            }
        }
        List<String> cpu_util;
        if (curr_cpu != null) {
            cpu_util = Arrays.asList(curr_cpu.split(","));
        } else {
            cpu_util = Arrays.asList("0", "0", "0", "0");
        }
        return cpu_util.get(cpu_util.size() - 4);
    }

    private static Float getGPUUtilization(String gpuDevicePath) throws IOException {
        String cmd = String.format("cat %s/gpu_busy_percentage", gpuDevicePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String tempReader = reader.readLine();
        if (tempReader == null) return 0f;
        String[] currentUtilization = tempReader.split("%");
        String currentGPUUtilization = currentUtilization[0];

        if (currentGPUUtilization != null) {
            return Float.parseFloat(currentGPUUtilization);
        }
        return (float) 0;
    }

}
