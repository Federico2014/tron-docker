package org.tron.utils;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.List;

public class Utils {

  public static boolean deleteDir(File dir) {
    if (dir.isDirectory()) {
      String[] children = dir.list();
      for (int i = 0; i < children.length; i++) {
        boolean success = deleteDir(new File(dir, children[i]));
        if (!success) {
          return false;
        }
      }
    }
    return dir.delete();
  }

  public static byte[] getActiveWitness(List<ByteString> witnesses) {
    byte[] ba = new byte[witnesses.size() * Constant.ADDRESS_BYTE_ARRAY_LENGTH];
    int i = 0;
    for (ByteString address : witnesses) {
      System.arraycopy(address.toByteArray(), 0,
          ba, i * Constant.ADDRESS_BYTE_ARRAY_LENGTH, Constant.ADDRESS_BYTE_ARRAY_LENGTH);
      i++;
    }
    return ba;
  }
}
