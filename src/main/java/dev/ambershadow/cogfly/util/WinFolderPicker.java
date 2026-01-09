package dev.ambershadow.cogfly.util;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
public interface WinFolderPicker extends Library {
    Pointer pickFolder();
}
