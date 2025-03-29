package com.example.arbenchapp.util;

import android.content.Context;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public final class FileUtil {
    private FileUtil() {}

    public static String copyAssetToFile(String assetName, Context context) {
        File file = new File(context.getFilesDir(), assetName);
        try (InputStream inputStream = context.getAssets().open(assetName);
             OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
