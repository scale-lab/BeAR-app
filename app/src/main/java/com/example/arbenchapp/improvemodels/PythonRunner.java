package com.example.arbenchapp.improvemodels;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.example.arbenchapp.datatypes.MTLBoxStruct;

import org.pytorch.Tensor;

import java.io.ByteArrayOutputStream;
import java.nio.FloatBuffer;
import java.util.Map;

public class PythonRunner {
    private static final String TAG = "PythonRunner";
    private final Context context;
    private Python py;

    public PythonRunner(Context context) {
        this.context = context;
        initializePython();
    }

    private void initializePython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
        py = Python.getInstance();
    }

    public MTLBoxStruct runFromScript(Object input, String scriptName) {
        long startTime = System.currentTimeMillis();

        try {
            // Get the Python module
            PyObject module = py.getModule(scriptName.replace(".py", ""));

            // Convert input to Python object
            PyObject pythonInput = convertInputToPyObject(input);

            // Call the Python function
            PyObject result = module.callAttr("main_function", pythonInput);

            // Process the result
            return processResult(result, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            Log.e(TAG, "Error running Python script", e);
            return null;
        }
    }

    private PyObject convertInputToPyObject(Object input) {
        if (input instanceof Tensor) {
            return tensorToPyObject((Tensor) input);
        } else if (input instanceof Bitmap) {
            return bitmapToPyObject((Bitmap) input);
        } else {
            throw new IllegalArgumentException("Unsupported input type: " + input.getClass().getName());
        }
    }

    private PyObject tensorToPyObject(Tensor tensor) {
        // Get tensor dimensions
        long[] shape = tensor.shape();

        // Get tensor data
        FloatBuffer floatBuffer = FloatBuffer.allocate((int) (shape[0] * shape[1] * shape[2] * shape[3]));
        tensor.getDataAsFloatArray();

        // Convert to numpy array
        PyObject numpy = py.getModule("numpy");
        PyObject pyArray = numpy.callAttr("array", floatBuffer.array());
        return numpy.callAttr("reshape", pyArray, shape);
    }

    private PyObject bitmapToPyObject(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        PyObject numpy = py.getModule("numpy");
        PyObject pyBytes = PyObject.fromJava(byteArray);

        // Convert to numpy array
        return numpy.callAttr("frombuffer", pyBytes, "uint8");
    }

    private MTLBoxStruct processResult(PyObject result, long executionTime) {
        try {
            if (result == null) {
                return null;
            }

            // Check if result is a dictionary (for future extensibility)
            if (result.type().toString().contains("dict")) {
                Map<PyObject, PyObject> resultMap = result.asMap();
                // Process dictionary results
                // Add handling for additional return types here
                return processDictionaryResult(resultMap, executionTime);
            }

            // If result is directly an image/tensor
            Bitmap resultBitmap = convertPyObjectToBitmap(result);
            return new MTLBoxStruct(resultBitmap, (double) executionTime);

        } catch (Exception e) {
            Log.e(TAG, "Error processing Python result", e);
            return null;
        }
    }

    private MTLBoxStruct processDictionaryResult(Map<PyObject, PyObject> resultMap, long executionTime) {
        // Handle different return types
        PyObject imageData = resultMap.get(PyObject.fromJava("image"));
        if (imageData != null) {
            Bitmap bitmap = convertPyObjectToBitmap(imageData);
            return new MTLBoxStruct(bitmap, (double) executionTime);
        }

        // Add handling for other return types here

        return null;
    }

    private Bitmap convertPyObjectToBitmap(PyObject pyObject) {
        // Handle different Python image formats
        String type = pyObject.type().toString();

        if (type.contains("ndarray")) {
            // Convert numpy array to bitmap
            byte[] bytes = pyObject.toJava(byte[].class);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else if (type.contains("tensor")) {
            // Convert PyTorch tensor to bitmap
            // Implement conversion based on your tensor format
            return null;
        }

        throw new IllegalArgumentException("Unsupported Python image type: " + type);
    }
}
