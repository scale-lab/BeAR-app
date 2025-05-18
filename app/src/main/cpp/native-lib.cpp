//
// Created by funkm on 3/17/2025.
//

#include <jni.h>
#include <android/bitmap.h>
#include <arm_neon.h>
#include <android/log.h>

#define LOG_TAG "BitmapProcessing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

inline void processPixel(uint32_t pixel, int bgRed, int bgGreen, int bgBlue,
                         float* rChannel, float* gChannel, float* bChannel) {
    constexpr float scale = 1.0f / (255.0f * 255.0f);
    const uint32_t alpha = (pixel >> 24) & 0xFF;
    const uint32_t red = (pixel >> 16) & 0xFF;
    const uint32_t green = (pixel >> 8) & 0xFF;
    const uint32_t blue = pixel & 0xFF;

    *rChannel = (red * alpha + bgRed * (255 - alpha)) * scale;
    *gChannel = (green * alpha + bgGreen * (255 - alpha)) * scale;
    *bChannel = (blue * alpha + bgBlue * (255 - alpha)) * scale;
}

extern "C" {
JNIEXPORT void JNICALL
Java_com_example_arbenchapp_util_ImageConversionUtil_nativeProcessPixels(
        JNIEnv *env,
        jclass clazz,
        jobject bitmap,
        jobject floatBuffer,
        jint bgColor,
        jint totalPixels) {
    AndroidBitmapInfo bitmapInfo;
    void *pixels;
    int result;

    // Get bitmap info and lock pixels
    if ((result = AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) < 0) {
        LOGD("AndroidBitmap_getInfo failed: %d", result);
        return;
    }
    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Unsupported bitmap format");
        return;
    }
    if ((result = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGD("AndroidBitmap_lockPixels failed: %d", result);
        return;
    }

    // Extract background color components
    const int bgRed = (bgColor >> 16) & 0xFF;
    const int bgGreen = (bgColor >> 8) & 0xFF;
    const int bgBlue = bgColor & 0xFF;

    // Get direct buffer access
    float *floatBufferPtr = (float *) env->GetDirectBufferAddress(floatBuffer);
    auto *pixelPtr = static_cast<uint32_t *>(pixels);

    // Process all pixels
    for (int i = 0; i < totalPixels; i++) {
        const uint32_t pixel = pixelPtr[i];
        float *rChannel = floatBufferPtr + i;
        float *gChannel = rChannel + totalPixels;
        float *bChannel = gChannel + totalPixels;

        processPixel(pixel, bgRed, bgGreen, bgBlue, rChannel, gChannel, bChannel);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
}

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
Java_com_example_arbenchapp_util_ImageConversionUtil_convertWithGradient(
        JNIEnv *env,
        jclass clazz,
        jfloatArray data,
        jint layers,
        jint channels,
        jint height,
        jint width,
        jobject bitmap) { // Add colors as a 2D array
    // Lock the Bitmap to get a pointer to its pixel data
    AndroidBitmapInfo bitmapInfo;
    void *pixels;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // Get the float array data
    jfloat *dataArray = env->GetFloatArrayElements(data, nullptr);

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
            float ratio = (float) chosenChannel / (float) channels;
            int red = ratio < 0.5 ? 255 - (int) (2.0 * (0.5 - ratio) * 255.0) : 0;
            int green = ratio < 0.5 ? (int) (2.0 * ratio * 255.0) : (int) (((-2.0 * ratio) + 2) *
                                                                           255.0);
            int blue = ratio < 0.5 ? 0 : (int) (2.0 * (ratio - 0.5) * 255.0);

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
Java_com_example_arbenchapp_util_ImageConversionUtil_convertWithGradientBW(
        JNIEnv *env,
        jclass clazz,
        jfloatArray data,
        jint height,
        jint width,
        jobject bitmap) { // Add colors as a 2D array
    // Lock the Bitmap to get a pointer to its pixel data
    AndroidBitmapInfo bitmapInfo;
    void *pixels;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // Get the float array data
    jfloat *dataArray = env->GetFloatArrayElements(data, nullptr);

    // Iterate over height and width
    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            int index = 0 * height * width + h * width + w;
            float value = dataArray[index];

            // Get the color for the chosen channel
            int red = value < 0.5 ? 255 - (int) (2.0 * (0.5 - value) * 255.0) : 0;
            int green = value < 0.5 ? (int) (2.0 * value * 255.0) : (int) (((-2.0 * value) + 2) *
                                                                           255.0);
            int blue = value < 0.5 ? 0 : (int) (2.0 * (value - 0.5) * 255.0);

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
