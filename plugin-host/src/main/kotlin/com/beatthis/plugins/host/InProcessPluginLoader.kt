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
     * @param packageName The plugin's package name
     * @param pluginId The plugin ID (passed to createView)
     * @param instanceId The instance ID (passed to createView)
     * @param viewFactoryClass The fully qualified ViewFactory class name
     */
    fun createPluginView(
        packageName: String,
        pluginId: String,
        instanceId: Int,
        viewFactoryClass: String
    ): View {
        val classLoader = getOrCreateClassLoader(packageName)
        val pluginContext = createPluginContext(packageName)

        // Load native libs first
        loadNativeLibs(packageName)

        // Instantiate ViewFactory
        val cls = classLoader.loadClass(viewFactoryClass)
        val factory = cls.getConstructor().newInstance()

        // Call createView via reflection (avoids compile-time dependency on AAP)
        val createViewMethod = cls.getMethod("createView", Context::class.java, String::class.java, Int::class.javaPrimitiveType)
        return createViewMethod.invoke(factory, pluginContext, pluginId, instanceId) as View
    }

    private fun getOrCreateClassLoader(packageName: String): ClassLoader {
        return classLoaders.getOrPut(packageName) {
            val apkPath = getApkPath(packageName)
            val dexOutputDir = File(context.cacheDir, "plugin_dex/$packageName").also { it.mkdirs() }
            val nativeLibDir = getNativeLibDir(packageName)

            DexClassLoader(
                apkPath,
                dexOutputDir.absolutePath,
                nativeLibDir,
                context.classLoader
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

    private fun loadNativeLibs(packageName: String) {
        try {
            val libDir = File(getNativeLibDir(packageName) ?: return)
            if (!libDir.exists()) return

            // Load libs in dependency order — load smaller utility libs first
            val libs = libDir.listFiles { f -> f.name.endsWith(".so") }
                ?.sortedBy { it.length() } ?: return

            for (lib in libs) {
                try {
                    System.load(lib.absolutePath)
                } catch (_: UnsatisfiedLinkError) {
                    // Already loaded or dependency missing — skip
                }
            }
        } catch (_: Exception) {
            // Non-fatal — some plugins work without explicit native loading
        }
    }
}
