package me.sedlar.ctrl1

import me.sedlar.ctrl1.api.CtrlPlugin
import me.sedlar.ctrl1.native.JavaLibraryPath
import org.jnativehook.GlobalScreen
import org.jnativehook.NativeInputEvent
import org.jnativehook.keyboard.NativeKeyEvent
import org.jnativehook.keyboard.NativeKeyListener
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

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

fun main() {
    println("Loading libraries...")
    JavaLibraryPath.extractAndUseResourceLibs()
    println("Libraries loaded")

    println("Loading plugins...")
    val plugins = loadPlugins()
    println("Loaded plugins")

    LogManager.getLogManager().reset()
    val logger: Logger = Logger.getLogger(GlobalScreen::class.java.getPackage().name)
    logger.level = Level.OFF

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down....")
        GlobalScreen.unregisterNativeHook()
        println("Unregistered NativeKeyListener")
        println("Shut down")
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
                                if (plugin.verify()) {
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