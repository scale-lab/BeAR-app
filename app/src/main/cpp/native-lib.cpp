//
// Created by funkm on 3/17/2025.
//

#include <jni.h>
#include <android/bitmap.h>

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
