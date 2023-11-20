/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.imageclassification;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.PowerManager;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

// A manager class that managers PF APIs in Java code.
// The class managers thermal throttle status listener and other PF related tasks.
@RequiresApi(api = VERSION_CODES.Q)
public class PFManager implements PowerManager.OnThermalStatusChangedListener {
    String performanceFilePath = "";
    String performanceFileName = "Performance_Measurements";
    String currentThermalStatus = "None";
    String[] thermalZonePaths = getThermalZoneFilePaths("/sys/class/thermal");
    String[] cpuDevicePaths = getCPUDeviceFiles("/sys/devices/system/cpu");
    public void setPerformanceFilePath(String filePath) {
        performanceFilePath = filePath;
    }

    public PFManager() throws IOException, InterruptedException {
    }

    // Thermal status change listener.
    public void onThermalStatusChanged(int i) {
        try {
            System.out.println("Thermal Status: " + i);
            currentThermalStatus = getThermalStatusName(i);
            processDataCollection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean registerListener(Context context) {
        // Retrieve power manager and register thermal state change callback.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.addThermalStatusListener(this);
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean unregisterListener(Context context) {
        // Remove the thermal state change listener on pause.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.removeThermalStatusListener(this);
            }
            return true;
        } else {
            return false;
        }
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

    public void processDataCollection() throws IOException {
        String FILEPATH = performanceFilePath;

        ArrayList<String> currentThermalData = processThermalData();
        ArrayList<Float> currentFrequencies = processFrequencyData();

        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
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
                    '\n';
            writer.write(sb);
            System.out.println("Writing to " + performanceFileName + " done!");
        } catch (FileNotFoundException | IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
        }
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
}
