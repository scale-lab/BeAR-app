package com.example.arbenchapp;

import com.example.arbenchapp.datatypes.Settings;
import org.python.util.PythonInterpreter;

public class MTLBox {

    private final Settings settings;

    public MTLBox(Settings settings) {
        this.settings = settings;
    }

    public Settings viewSettings() {
        return this.settings;
    }

    public void run() {
        System.out.println("HELLO RUN!");
        switch (this.settings.getRunType()) {
            case NONE:
                return;
            case CONV2D:
                conv2d();
            case TORCH:
                return;
            default:
                conv2d();
        }
    }

    public void conv2d() {
        System.out.println("CONV2D RUN!!");
        try (PythonInterpreter pyInterp = new PythonInterpreter()) {
            pyInterp.exec("print('Hello World!')");
        }
    }

}
