package com.example.arbenchapp.improvemodels;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParallelInference {
    private final int numThreads;
    private final Module[] models;
    private final ExecutorService executor;

    public ParallelInference(String modelPath, int numThreads) {
        this.numThreads = numThreads;
        this.models = new Module[numThreads];
        this.executor = Executors.newFixedThreadPool(numThreads);

        // Load model instance for each thread
        for (int i = 0; i < numThreads; i++) {
            models[i] = Module.load(modelPath);
        }
    }

    public Future<Tensor> inferAsync(final Tensor input, final int threadIndex) {
        return executor.submit(() -> {
            // Each thread uses its own model instance
            Module model = models[threadIndex % numThreads];
            return model.forward(IValue.from(input)).toTensor();
        });
    }

    public void shutdown() {
        executor.shutdown();
        for (Module model : models) {
            model.destroy();
        }
    }
}
