package com.golshadi.majid.Utils.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtils {

    public static File create(String folderPath, String fileName) throws IOException {
        File folder = new File(folderPath);
        File file = new File(folder, fileName);
        if (!(folder.mkdirs() || folder.isDirectory()))
            throw new IllegalArgumentException("Invalid directory: " + folder);
        if (!file.createNewFile())
            throw new IllegalStateException("Can't create file: " + file);
        return file;
    }

    public static void forceCreate(String folder, String fileName){
        File dirs = new File(folder);
        dirs.mkdirs();

        File file = new File(folder, fileName);

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void delete(String folder, String fileName){
        File file = new File(folder, fileName);
        file.delete();
    }

    public static long size(String folder, String fileName){
        File file = new File(folder, fileName);
        return file.length();
    }

    public static FileOutputStream getOutputStream(String folder, String fileName){
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(
                    new File(folder, fileName));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return fileOut;
    }

    public static FileInputStream getInputStream(String folder, String fileName){
        FileInputStream fileIn = null;
        try {
            fileIn = new FileInputStream(
                    new File(folder, fileName));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return fileIn;
    }

//    public static String address(String folder, String file){
//        return folder+"/"+file;
//    }
}
