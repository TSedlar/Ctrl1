package me.sedlar.ctrl1.native

import com.sun.jna.win32.StdCallLibrary.StdCallCallback

interface WndEnumProc : StdCallCallback {
    fun callback(hWnd: Int, lParam: Int): Boolean
}