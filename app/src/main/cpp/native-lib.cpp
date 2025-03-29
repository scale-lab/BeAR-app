//
// Created by funkm on 3/17/2025.
//

#include <jni.h>
#include <android/bitmap.h>
#include <onnxruntime/core/session/onnxruntime_cxx_api.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <map>

extern "C"
JNIEXPORT void JNICALL
Java_com_example_arbenchapp_util_ImageConversionUtil_convertToBitmapNative(
        JNIEnv *env,
        jclass clazz,
        jfloatArray data,
        jint layers,
        jint channels,
        jint height,
        jint width,
        jobject bitmap,
        jobjectArray colors) { // Add colors as a 2D array
    // Lock the Bitmap to get a pointer to its pixel data
    AndroidBitmapInfo bitmapInfo;
    void *pixels;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // Get the float array data
    jfloat *dataArray = env->GetFloatArrayElements(data, nullptr);

    // Get the color array
    jsize numColors = env->GetArrayLength(colors);
    int colorArray[numColors][3];
    for (int i = 0; i < numColors; i++) {
        jintArray color = (jintArray) env->GetObjectArrayElement(colors, i);
        jint *colorComponents = env->GetIntArrayElements(color, nullptr);
        colorArray[i][0] = colorComponents[0]; // Red
        colorArray[i][1] = colorComponents[1]; // Green
        colorArray[i][2] = colorComponents[2]; // Blue
        env->ReleaseIntArrayElements(color, colorComponents, 0);
    }

    // Iterate over height and width
    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            int chosenChannel = 0;
            float maxChannel = dataArray[0 * height * width + h * width + w];

            // Find the channel with the maximum value
            for (int c = 1; c < channels; c++) {
                float currentChannel = dataArray[c * height * width + h * width + w];
                if (currentChannel > maxChannel) {
                    chosenChannel = c;
                    maxChannel = currentChannel;
                }
            }

            // Get the color for the chosen channel
            int red = colorArray[chosenChannel][0];
            int green = colorArray[chosenChannel][1];
            int blue = colorArray[chosenChannel][2];

            // Set the pixel in the Bitmap (ARGB format)
            uint32_t *pixel = (uint32_t *) pixels + h * width + w;
            *pixel = 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
    }

    // Unlock the Bitmap and release the float array
    AndroidBitmap_unlockPixels(env, bitmap);
    env->ReleaseFloatArrayElements(data, dataArray, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_arbenchapp_util_ImageConversionUtil_convertToGrayscale(
        JNIEnv *env,
        jclass clazz,
        jfloatArray data, // 3D array: layers[channels][height][width]
        jint height,
        jint width,
        jobject bitmap) {
    // Lock the Bitmap to get a pointer to its pixel data
    AndroidBitmapInfo bitmapInfo;
    void *pixels;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    jfloat *flatData = env->GetFloatArrayElements(data, nullptr);

    // Iterate over height and width
    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            // Access the first channel (c = 0)
            int index = 0 * height * width + h * width + w;
            float value = flatData[index];

            // Convert the float value to grayscale (0-255)
            int g = (int) (value * 255.0);

            // Set the pixel in the Bitmap (ARGB format)
            uint32_t *pixel = (uint32_t *) ((uint8_t *) pixels + h * bitmapInfo.stride) + w;
            *pixel = 0xFF000000 | (g << 16) | (g << 8) | g;
        }
    }

    env->ReleaseFloatArrayElements(data, flatData, 0);

    // Unlock the Bitmap
    AndroidBitmap_unlockPixels(env, bitmap);
}

#define LOG_TAG "ONNX_Runtime"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool isValidModifiedUTF8(const char* str) {
    while (*str) {
        if ((*str & 0x80) == 0) { // ASCII character
            str++;
        } else if ((*str & 0xE0) == 0xC0) { // Two-byte sequence
            if ((str[1] & 0xC0) != 0x80) return false;
            str += 2;
        } else if ((*str & 0xF0) == 0xE0) { // Three-byte sequence
            if ((str[1] & 0xC0) != 0x80 || (str[2] & 0xC0) != 0x80) return false;
            str += 3;
        } else {
            return false; // Invalid sequence
        }
    }
    return true;
}

// Helper function to convert Java float array to C++ vector
std::vector<float> javaFloatArrayToVector(JNIEnv* env, jfloatArray javaArray) {
    jsize length = env->GetArrayLength(javaArray);
    jfloat* elements = env->GetFloatArrayElements(javaArray, nullptr);
    std::vector<float> vector(elements, elements + length);
    env->ReleaseFloatArrayElements(javaArray, elements, JNI_ABORT);
    return vector;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_arbenchapp_monitor_HardwareMonitor_00024PyTorchModelMonitor_runInferenceNative(
        JNIEnv* env,
        jclass clazz,
        jstring modelPath, // Path to the ONNX model
        jobject inputMap,  // Map<String, float[]> from Java
        jint height,
        jint width
) {
    try {
        // Initialize ONNX Runtime environment
        Ort::Env envOrt(ORT_LOGGING_LEVEL_WARNING, "ONNX_Runtime");
        Ort::SessionOptions sessionOptions;

        // Load the ONNX model
        const char* modelPathStr = env->GetStringUTFChars(modelPath, nullptr);
        Ort::Session session(envOrt, modelPathStr, sessionOptions);
        env->ReleaseStringUTFChars(modelPath, modelPathStr);

        // Convert Java Map<String, float[]> to C++ map
        jclass mapClass = env->GetObjectClass(inputMap);
        jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jobject entrySet = env->CallObjectMethod(inputMap, entrySetMethod);

        jclass setClass = env->GetObjectClass(entrySet);
        jmethodID iteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

        jclass iteratorClass = env->GetObjectClass(iterator);
        jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

        jclass entryClass = env->FindClass("java/util/Map$Entry");
        jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        std::map<std::string, Ort::Value> inputTensors;
        while (env->CallBooleanMethod(iterator, hasNextMethod)) {
            jobject entry = env->CallObjectMethod(iterator, nextMethod);
            jstring key = (jstring)env->CallObjectMethod(entry, getKeyMethod);
            jfloatArray value = (jfloatArray)env->CallObjectMethod(entry, getValueMethod);

            const char* keyStr = env->GetStringUTFChars(key, nullptr);
            std::vector<float> valueVector = javaFloatArrayToVector(env, value);

            // Create Ort::Value for input tensor
            Ort::MemoryInfo memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeCPU);
            std::vector<int64_t> inputShape = {1, 3, height, width}; // Example shape
            Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
                    memoryInfo,                     // OrtMemoryInfo*
                    valueVector.data(),             // Pointer to data
                    valueVector.size() * sizeof(float), // Size of data in bytes
                    inputShape.data(),              // Pointer to shape array
                    inputShape.size()               // Number of dimensions
            );

            inputTensors.emplace(keyStr, std::move(inputTensor));
            env->ReleaseStringUTFChars(key, keyStr);
        }

        // Prepare input and output names
        std::vector<const char*> inputNames;
        std::vector<Ort::Value> inputValues;
        for (auto& entry : inputTensors) {
            inputNames.push_back(entry.first.c_str());
            inputValues.push_back(std::move(entry.second));
        }

        // Get output names from the session
        size_t numOutputNodes = session.GetOutputCount();
        std::vector<std::string> outputNamesStorage(numOutputNodes);
        std::vector<const char*> outputNames(numOutputNodes);
        Ort::AllocatorWithDefaultOptions allocator;
        LOGE("MIAMI_ New Run");
        for (size_t i = 0; i < numOutputNodes; i++) {
            auto outputName = session.GetOutputNameAllocated(i, allocator);
            outputNamesStorage[i] = outputName.get();
            outputNames[i] = outputNamesStorage[i].c_str();
            LOGE("MIAMI_ OUTPUT NAME %i: %s, is valid? %i", i, outputNames[i], isValidModifiedUTF8(outputNames[i]));
        }

        // Run inference
        auto outputs = session.Run(Ort::RunOptions{nullptr}, inputNames.data(), inputValues.data(), inputNames.size(), outputNames.data(), outputNames.size());

        // Convert outputs to Java Map<String, float[]>
        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
        jmethodID hashMapPut = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        jobject outputMap = env->NewObject(hashMapClass, hashMapInit);

        for (size_t i = 0; i < outputs.size(); i++) {
            Ort::Value& outputTensor = outputs[i];
            auto* outputData = outputTensor.GetTensorMutableData<float>();
            size_t outputSize = outputTensor.GetTensorTypeAndShapeInfo().GetElementCount();

            jfloatArray outputArray = env->NewFloatArray(outputSize);
            env->SetFloatArrayRegion(outputArray, 0, outputSize, outputData);

            LOGE("MIAMI_ Before creating jstring");
            jstring outputName = env->NewStringUTF(outputNames[i]);
            LOGE("MIAMI_ After creating jstring");
            LOGE("MIAMI_ Created jstring outputName %s", outputName);
            env->CallObjectMethod(outputMap, hashMapPut, outputName, outputArray);
        }

        return outputMap;
    } catch (const Ort::Exception& e) {
        LOGE("ONNX Runtime Error: %s", e.what());
        return nullptr;
    }
}
