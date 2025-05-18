package com.example.arbenchapp.datatypes.preprocessing;

import com.example.arbenchapp.monitor.HardwareMonitor;

import java.io.File;

public class SplitInfo {
    private final File encoderFile;
    private final File[] decoderFiles;
    private final HardwareMonitor.SplitModelMonitor monitor;

    public SplitInfo(
            File encoderFile,
            File[] decoderFiles,
            HardwareMonitor.SplitModelMonitor monitor) {
        this.encoderFile = encoderFile;
        this.decoderFiles = decoderFiles;
        this.monitor = monitor;
    }

    public File getEncoderFile() { return encoderFile; }

    public File[] getDecoderFiles() { return decoderFiles; }

    public HardwareMonitor.SplitModelMonitor getMonitor() { return monitor; }
}
