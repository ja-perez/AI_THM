package org.tensorflow.lite.examples.imageclassification;

import android.os.Build;

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
import java.util.Timer;
import java.util.TimerTask;

public class DataProcessor {

    MainActivity mainActivity;
    String performanceFilePath;
    SimpleDateFormat dateFormat;
    String fileSeries;
    String performanceFileName = "Performance_Measurements";
    String[] thermalZonePaths;
    String[] thermalZoneTypesOfInterest = {"cpu", "gpu", "npu"};
    StringBuilder thermalZoneTypeHeaders;
    String[] cpuDevicePaths;
    Boolean isRooted;
    String rootAccess;

    public DataProcessor(MainActivity activity) {
        mainActivity = activity;

        try {
            // Run test for root access
            Runtime.getRuntime().exec("su");
            isRooted = true;
            rootAccess = "su -c";
        } catch (IOException e) {
            System.out.println(e.getMessage());
            isRooted = false;
            rootAccess = "";
        }

        dateFormat = new SimpleDateFormat("HH:mm:ss");
        fileSeries = dateFormat.format(new Date());
        String currentFolder = mainActivity.currentFolder;
        performanceFilePath = currentFolder + File.separator + performanceFileName + fileSeries + ".csv";

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

        // Create string for thermal zone type headers
        thermalZoneTypeHeaders = new StringBuilder();
        for (String thermalZoneType: thermalZoneTypesOfInterest) {
            thermalZoneTypeHeaders.append(thermalZoneType).append("Temperature,");
        }

        // Create file for data collection
        String FILEPATH = performanceFilePath;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(FILEPATH, false))) {
            String sb = "time" +
                    ',' +
                    "thermalStatus" +
                    ',' +
                    thermalZoneTypeHeaders +
                    "cpuFrequency" +
                    ',' +
                    "gpuFrequency" +
                    ',' +
                    "cpuUtilization" +
                    ',' +
                    "gpuUtilization" +
                    '\n';
            writer.write(sb);
            System.out.println("Creating " + performanceFileName + " done!");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        dataCollection();
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
        String FILEPATH = performanceFilePath;

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

            if (isRooted) {
                // For Pixel 8, ROOTED, the gpu frequency is stored in "/sys/class/misc/mali0/device/"
                // in file "cur_freq"
                gpuUtilization = getGPUUtilization(
                        "/sys/class/misc/mali0/device",
                        "utilization");
            } else {
                // For Note10+, the gpu utilization is stored in "/sys/class/kgsl/kgsl-3d0"
                // in file "busy_percentage"
                gpuUtilization = getGPUUtilization(
                        "/sys/class/kgsl/kgsl-3d0",
                        "gpu_busy_percentage");
            }


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

            if (isRooted) {
                // For Pixel 8, ROOTED, the gpu frequency is stored in "/sys/class/misc/mali0/device/"
                // in file "cur_freq"
                gpuFreq = getGPUFrequency("/sys/class/misc/mali0/device/", "cur_freq");
            } else {
                // For Note10+, the gpu frequency is stored in "/sys/class/kgsl/kgsl-3d0"
                // in file "cur_freq"
                gpuFreq = getGPUFrequency("/sys/class/kgsl/kgsl-3d0", "clock_mhz");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentFrequencies.add(gpuFreq);

        return currentFrequencies;
    }

    private ArrayList<String> processThermalData() throws IOException {
        /*
        currentThermalData = [thermalStatus, cpuTemperature, gpuTemperature, npuTemperature, tpuTemperature]
         */

        ArrayList<String> currentThermalData= new ArrayList<>();

        String currentThermalStatus = "Unknown";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentThermalStatus = mainActivity.currentThermalStatus;
        }
        currentThermalData.add(currentThermalStatus);

        Float avgCPUTemp = 0f, avgGPUTemp = 0f, avgNPUTemp = 0f, avgTPUTemp = 0f, currFileTemp = 0f;
        int cpuCount = 0, gpuCount = 0, npuCount = 0, tpuCount = 0;
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
            } else if (currFileType.contains("tpu")) {
                avgTPUTemp += currFileTemp;
                tpuCount++;
            }
        }
        avgCPUTemp /= cpuCount;
        avgGPUTemp /= gpuCount;
        avgNPUTemp /= npuCount;
        avgTPUTemp /= tpuCount;
        String validZones = thermalZoneTypeHeaders.toString();
        if (validZones.contains("cpu")) {
            currentThermalData.add(Float.toString(avgCPUTemp));
        }
        if (validZones.contains("gpu")) {
            currentThermalData.add(Float.toString(avgGPUTemp));
        }
        if (validZones.contains("npu")) {
            currentThermalData.add(Float.toString(avgNPUTemp));
        }
        if (validZones.contains("tpu")) {
            currentThermalData.add(Float.toString(avgTPUTemp));
        }

        return currentThermalData;
    }

    private String[] getThermalZoneFilePaths(String thermalDir)
            throws IOException, InterruptedException {
        // thermalDir for Note10+ is usually "/sys/class/thermal"
        String cmd = String.format("%s ls %s | grep 'thermal_zone'", rootAccess, thermalDir);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String currFileName;
        ArrayList<String> thermalZonePaths = new ArrayList<>();
        while ((currFileName = reader.readLine()) != null) {
            String thermalZoneFilePath = thermalDir + "/" + currFileName + "/";
            String thermalZoneType = getThermalZoneType(thermalZoneFilePath).toLowerCase();
            // Filter thermal zones to only track cpu, gpu, and npu
            for (String thermalZoneTypeOfInterest: thermalZoneTypesOfInterest) {
                if (thermalZoneType.contains(thermalZoneTypeOfInterest)) {
                    thermalZonePaths.add(thermalZoneFilePath);
                    break;
                }
            }
        }

        reader.close();
        process.waitFor();
        return thermalZonePaths.toArray(new String[0]);
    }

    private String getThermalZoneType(String thermalZonePath) throws IOException {
        String cmd = String.format("%s cat %stype", rootAccess, thermalZonePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currLine = reader.readLine();
        if (currLine != null) {
            return currLine;
        }
        return "default_zone_type";
    }

    private Float getThermalZoneTemp(String thermalZonePath) throws IOException {
        String cmd = String.format("%s cat %stemp", rootAccess, thermalZonePath);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String currTemp = reader.readLine();
        if (currTemp != null) {
            int tmpMCValue = Integer.parseInt(currTemp);
            if (tmpMCValue < 0) tmpMCValue = 0;
            return tmpMCValue / 1000f;
        }
        return (float) -1;
    }

    private String[] getCPUDeviceFiles(String cpuDeviceDirs)
            throws IOException, InterruptedException {
        // cpuDeviceDirs for Note10+ is usually "/sys/devices/system/cpu"
        String cmd = String.format("%s ls %s | grep 'cpu[0-9]'", rootAccess, cpuDeviceDirs);
        Process process = Runtime.getRuntime().exec(cmd);
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

    private Float getCPUFrequency(String cpuDevicePath) throws IOException {
        String cmd = String.format("%s cat %s/cpufreq/scaling_cur_freq", rootAccess, cpuDevicePath);
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

    private Float getGPUFrequency(String gpuDevicePath, String freqFileName) throws IOException {
        String cmd = String.format("%s cat %s/%s", rootAccess, gpuDevicePath, freqFileName);
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
        String cmd = "top -s 6";
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
            cpu_util = Arrays.asList("-1", "-1", "-1", "-1");
        }
        return cpu_util.get(cpu_util.size() - 4);
    }

    private Float getGPUUtilization(String gpuDevicePath, String utilFileName) throws IOException {
        String cmd = String.format("%s cat %s/%s", rootAccess, gpuDevicePath, utilFileName);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        String tempReader = reader.readLine();
        if (tempReader == null) return -1f;
        String[] currentUtilization = tempReader.split("%");
        String currentGPUUtilization = currentUtilization[0];

        if (currentGPUUtilization != null) {
            return Float.parseFloat(currentGPUUtilization);
        }
        return (float) 0;
    }
}
