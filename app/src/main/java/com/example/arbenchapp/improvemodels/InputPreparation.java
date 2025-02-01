package com.example.arbenchapp.improvemodels;

import org.pytorch.Tensor;

import java.nio.FloatBuffer;

public class InputPreparation {
    public static Tensor[] prepareInputBatch(float[][] rawData, int batchSize) {
        Tensor[] inputs = new Tensor[batchSize];

        for (int i = 0; i < batchSize; i++) {
            float[] data = rawData[i];
            long[] shape = {1, data.length}; // Adjust shape based on your model's input requirements

            FloatBuffer buffer = Tensor.allocateFloatBuffer(data.length);
            buffer.put(data);
            ((FloatBuffer) buffer).rewind();

            inputs[i] = Tensor.fromBlob(buffer, shape);
        }

        return inputs;
    }
}
