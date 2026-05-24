package com.beatthis.plugins.host

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Loads an AAP plugin's UI in-process by:
 * 1. Getting the plugin APK path
 * 2. Loading its dex classes via DexClassLoader
 * 3. Loading its native .so libraries
 * 4. Instantiating the ViewFactory class
 * 5. Calling createView() to get an Android View
 */
class InProcessPluginLoader(private val context: Context) {

    private val classLoaders = mutableMapOf<String, ClassLoader>()

    /**
     * Load the plugin's ViewFactory and create its UI View.
     */
    fun createPluginView(
        packageName: String,
        pluginId: String,
        instanceId: Int,
        viewFactoryClass: String
    ): View {
        val pluginContext = createPluginContext(packageName)
        val classLoader = getOrCreateClassLoader(packageName, pluginContext)

        // Load aap-core native lib first (required by ViewFactory internals)
        loadNativeFromApk(packageName, "androidaudioplugin")
        loadNativeFromApk(packageName, "androidaudioplugin-ui")

        // Instantiate ViewFactory
        val cls = classLoader.loadClass(viewFactoryClass)
        val factory = cls.getConstructor().newInstance()

        // Call createView via reflection
        val createViewMethod = cls.getMethod("createView", Context::class.java, String::class.java, Int::class.javaPrimitiveType)
        return createViewMethod.invoke(factory, pluginContext, pluginId, instanceId) as View
    }

    private fun getOrCreateClassLoader(packageName: String, pluginContext: Context): ClassLoader {
        return classLoaders.getOrPut(packageName) {
            val apkPath = getApkPath(packageName)
            val dexOutputDir = File(context.cacheDir, "plugin_dex/$packageName").also { it.mkdirs() }
            val nativeLibDir = getNativeLibDir(packageName)

            DexClassLoader(
                apkPath,
                dexOutputDir.absolutePath,
                nativeLibDir,
                pluginContext.classLoader
            )
        }
    }

    private fun createPluginContext(packageName: String): Context {
        return context.createPackageContext(
            packageName,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        )
    }

    private fun getApkPath(packageName: String): String {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        return appInfo.sourceDir
    }

    private fun getNativeLibDir(packageName: String): String? {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        return appInfo.nativeLibraryDir
    }

    /**
     * Load a native library from the plugin's APK.
     * Uses the plugin's classloader which handles extractNativeLibs=false.
     */
    private fun loadNativeFromApk(packageName: String, libName: String) {
        try {
            val pluginContext = createPluginContext(packageName)
            val classLoader = pluginContext.classLoader

            // Use Runtime.loadLibrary0 via the plugin's classloader
            val runtime = Runtime.getRuntime()
            val loadLib = Runtime::class.java.getDeclaredMethod("loadLibrary0", ClassLoader::class.java, String::class.java)
            loadLib.isAccessible = true
            loadLib.invoke(runtime, classLoader, libName)
        } catch (_: Exception) {
            // Try direct path as fallback
            try {
                val libDir = getNativeLibDir(packageName)
                if (libDir != null) {
                    System.load("$libDir/lib$libName.so")
                }
            } catch (_: Exception) {}
        }
    }
}
