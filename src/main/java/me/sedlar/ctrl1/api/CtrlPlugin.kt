package me.sedlar.ctrl1.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.image.BufferedImage

private val BLANK_IMAGE = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

abstract class CtrlPlugin(private var windowTitles: Array<String>) : Runnable {

    var enabled: Boolean = true

    abstract fun verify(): Boolean

    private var canvasHandle: WinDef.HWND? = null

    private fun isSafeWindow(title: String): Boolean {
        for (t in windowTitles) {
            if (title.contains(t)) {
                return true
            }
        }
        return false
    }

    fun doSafeAction(action: () -> Unit) {
        val u32 = User32.INSTANCE

        val activeHandle = u32.GetForegroundWindow()

        val windowText = CharArray(512)
        u32.GetWindowText(activeHandle, windowText, 512)
        val wText: String? = Native.toString(windowText)

        if (wText != null && isSafeWindow(wText)) {
            canvasHandle = u32.FindWindowEx(activeHandle, WinDef.HWND(Pointer.NULL), "SunAwtCanvas", null)
            canvasHandle?.let { action() }
        }
    }

    fun snapshot(): BufferedImage {
        var img = BLANK_IMAGE
        doSafeAction {
            img = GDI32Util.getScreenshot(canvasHandle)
        }
        return img
    }
}