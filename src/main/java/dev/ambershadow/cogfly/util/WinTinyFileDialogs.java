package dev.ambershadow.cogfly.util;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface WinTinyFileDialogs extends Library {
    Pointer tinyfd_openFileDialog(
            String title,
            String defaultPathAndFile,
            int numOfFilterPatterns,
            String[] filterPatterns,
            String singleFilterDescription,
            int allowMultipleSelects
    );
}