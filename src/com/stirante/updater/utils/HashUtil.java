package com.stirante.updater.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by stirante
 */
public class HashUtil {

    public static String fileHash(File f) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(f);
            byte[] dataBytes = new byte[1024];
            int i;
            while ((i = fis.read(dataBytes)) != -1) {
                md5.update(dataBytes, 0, i);
            }
            fis.close();
            byte[] bytes = md5.digest();
            StringBuilder sb = new StringBuilder("");
            for (byte b : bytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return "";
        }
    }

}
