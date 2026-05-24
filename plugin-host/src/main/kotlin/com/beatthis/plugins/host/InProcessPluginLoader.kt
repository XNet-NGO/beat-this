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

        // Load native libs via plugin's classloader — discover from APK
        val libNames = discoverNativeLibs(packageName)
        for (lib in libNames) {
            loadNativeFromApk(packageName, lib)
        }

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
     * Discover native lib names from the APK, ordered by dependency
     * (shared libs first, then framework, then plugin-specific).
     */
    private fun discoverNativeLibs(packageName: String): List<String> {
        val apkPath = getApkPath(packageName)
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val prefix = "lib/$abi/lib"
        val suffix = ".so"

        val libs = mutableListOf<String>()
        try {
            java.util.zip.ZipFile(apkPath).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith(prefix) && it.name.endsWith(suffix) }
                    .map { it.name.removePrefix(prefix).removeSuffix(suffix) }
                    .toList()
                    .let { all ->
                        // Load order: shared runtime → audio framework → plugin
                        val priority = listOf("c++_shared", "oboe", "androidaudioplugin", "androidaudioplugin-manager", "juce_jni", "aapmidideviceservice")
                        for (p in priority) { if (p in all) libs.add(p) }
                        for (lib in all) { if (lib !in libs) libs.add(lib) }
                    }
            }
        } catch (_: Exception) {}
        return libs
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
        } catch (_: UnsatisfiedLinkError) {
            // Already loaded — fine
        } catch (_: Exception) {
            // Reflection failed or lib not found — non-fatal
        }
    }
}
