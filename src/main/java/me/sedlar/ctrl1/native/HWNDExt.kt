package me.sedlar.ctrl1.native

import com.sun.jna.platform.win32.WinDef

fun WinDef.HWND.hasPointer(): Boolean {
    return this.hashCode() != 0
}