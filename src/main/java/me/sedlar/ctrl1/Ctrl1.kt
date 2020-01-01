package me.sedlar.ctrl1

import me.sedlar.ctrl1.api.CtrlPlugin
import me.sedlar.ctrl1.native.JavaLibraryPath
import org.jnativehook.GlobalScreen
import org.jnativehook.NativeInputEvent
import org.jnativehook.keyboard.NativeKeyEvent
import org.jnativehook.keyboard.NativeKeyListener
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.awt.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

private val pluginProperties = Properties()
private val pluginPropFile = File(JavaLibraryPath.SITE, "plugins.properties")

private fun loadPlugins(): List<CtrlPlugin> {
    val list = ArrayList<CtrlPlugin>()
    val pkg = "me.sedlar.ctrl1"
    val reflections = Reflections(pkg, SubTypesScanner())
    val pluginClasses = reflections.getSubTypesOf(CtrlPlugin::class.java)
    // Create category map
    pluginClasses.forEach { clazz ->
        try {
            val subPkg = "$pkg.plugins"

            if (!clazz.canonicalName.contains(subPkg)) {
                return@forEach
            }

            val plugin = clazz.constructors[0].newInstance() as CtrlPlugin
            list.add(plugin)

            println("Loaded plugin: ${clazz.simpleName}")
        } catch (e: InstantiationException) {
            e.printStackTrace()
        }
    }
    return list
}

private fun doExit() {
    println("Shutting down....")
    println("Writing properties...")
    writePluginProperties()
    println("Wrote properties")
    GlobalScreen.unregisterNativeHook()
    println("Unregistered NativeKeyListener")
    println("Shut down")
    Runtime.getRuntime().halt(0)
}

fun writePluginProperties() {
    pluginProperties.store(FileOutputStream(pluginPropFile), "Properties file for which plugins are enabled")
}

class Ctrl1

fun main() {
    println("Loading libraries...")
    JavaLibraryPath.extractAndUseResourceLibs()
    println("Libraries loaded")

    println("Loading plugins...")
    val plugins = loadPlugins()
    println("Loaded plugins")

    if (pluginPropFile.exists()) {
        pluginProperties.load(FileInputStream(pluginPropFile))
    }

    if (SystemTray.isSupported()) {
        val tray = SystemTray.getSystemTray()
        val trayImage = ImageIO.read(Ctrl1::class.java.getResource("/tray-icon.png"))
        val trayMenu = PopupMenu()

        plugins.forEach { plugin ->
            val box = CheckboxMenuItem(plugin.javaClass.simpleName, true)

            if (pluginProperties.keys.contains(plugin.javaClass.canonicalName)) {
                val enabled = pluginProperties[plugin.javaClass.canonicalName].toString().toBoolean()
                plugin.enabled = enabled
                box.state = enabled
            }

            box.addItemListener {
                plugin.enabled = box.state
                pluginProperties[plugin.javaClass.canonicalName] = box.state.toString()
                writePluginProperties()
            }

            trayMenu.add(box)
        }

        val exit = MenuItem("Exit")
        exit.addActionListener { doExit() }

        trayMenu.add(exit)

        val trayIcon = TrayIcon(trayImage, "Ctrl1", trayMenu)
        trayIcon.isImageAutoSize = true

        try {
            tray.add(trayIcon)
        } catch (e: AWTException) {
            System.err.println("TrayIcon could not be added.")
        }
    }

    LogManager.getLogManager().reset()
    val logger: Logger = Logger.getLogger(GlobalScreen::class.java.getPackage().name)
    logger.level = Level.OFF

    Runtime.getRuntime().addShutdownHook(Thread {
        doExit()
    })

    println("Registering NativeKeyListener..")
    GlobalScreen.registerNativeHook()
    GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent?) {
            e?.let { evt ->
                if (evt.modifiers == NativeInputEvent.CTRL_L_MASK) {
                    val keyText = NativeKeyEvent.getKeyText(evt.keyCode)
                    if (keyText == "1") {
                        println("Running plugins...")
                        for (plugin in plugins) {
                            try {
                                if (plugin.enabled && plugin.verify()) {
                                    println("Plugin activated: ${plugin.javaClass.simpleName}")
                                    Thread(plugin).start()
                                }
                            } catch (e: Error) {
                                e.printStackTrace()
                            }
                        }
                        println("Plugins ran")
                    }
                }
            }
        }

        override fun nativeKeyTyped(e: NativeKeyEvent?) {
        }

        override fun nativeKeyReleased(e: NativeKeyEvent?) {
        }
    })
    println("Registered NativeKeyListener")
}