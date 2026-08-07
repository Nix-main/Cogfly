package dev.ambershadow.cogfly.util.jna;

import com.sun.jna.Library;

public interface WinTinyFileDialogs extends Library {

    String tinyfd_openFileDialog(
            String title,
            String defaultPathAndFile,
            int numOfFilterPatterns,
            String[] filterPatterns,
            String singleFilterDescription,
            int allowMultipleSelects
    );
}