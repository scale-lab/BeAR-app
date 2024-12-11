package com.example.arbenchapp.datatypes;

public class Settings {

    private final RunType runType;

    public Settings(RunType runType) {
        this.runType = runType;
    }

    public RunType getRunType() {
        return runType;
    }
}
