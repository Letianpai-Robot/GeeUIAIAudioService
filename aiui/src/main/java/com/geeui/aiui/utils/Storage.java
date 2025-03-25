package com.geeui.aiui.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * 存储repo
 */
public class Storage {
    private Context mContext;
    public Storage(Context context) {
       mContext = context;
    }

    public String readAssetFile(String filename){
        try {
            InputStream input = mContext.getAssets().open(filename);
            return readStringFromStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String readSDFile(String filePath) {
        try {
            InputStream input = new FileInputStream(filePath);
            String content = readStringFromStream(input);
            input.close();
            return content;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void writeSDFile(String filePath, byte[] content) {
        try {
            File targetFile = new File(filePath);
            if(!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            OutputStream output = new FileOutputStream(targetFile);
            output.write(content);
            output.flush();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    private String readStringFromStream(InputStream input) throws IOException {
        StringBuilder result = new StringBuilder();
        byte[] buffer = new byte[1024];
        int read;
        while((read = input.read(buffer)) > 0){
            result.append(new String(buffer, 0, read));
        }

        return result.toString();
    }

    public boolean copyAssetFolder(String srcName, String dstName) {
        try {
            boolean result = true;
            String fileList[] = mContext.getAssets().list(srcName);
            if (fileList == null) return false;

            if (fileList.length == 0) {
                result = copyAssetFile(srcName, dstName);
            } else {
                File file = new File(dstName);
                result = file.mkdirs();
                for (String filename : fileList) {
                    result &= copyAssetFolder(srcName + File.separator + filename, dstName + File.separator + filename);
                }
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean copyAssetFile(String srcName, String dstName) {
        try {
            InputStream in = mContext.getAssets().open(srcName);
            File outFile = new File(dstName);
            if(!outFile.getParentFile().exists()) {
                outFile.getParentFile().mkdirs();
            }
            OutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
