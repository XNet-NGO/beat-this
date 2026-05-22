package com.beatthis.plugins.host

/**
 * Native bridge for shared memory audio buffers between host and AAP plugins.
 * Uses Android SharedMemory (ashmem) for zero-copy audio transfer.
 */
class NativePluginBridge {

    companion object {
        init { System.loadLibrary("plugin_bridge") }
    }

    /**
     * Create a shared memory audio buffer.
     * @return bridge index (0-15), or -1 on failure
     */
    external fun createBuffer(numFrames: Int, numChannels: Int): Int

    /** Get the shared memory file descriptor to pass to plugin process via Binder. */
    external fun getSharedMemFd(bridgeIndex: Int): Int

    /** Write audio data into shared buffer (host → plugin input). */
    external fun writeAudio(bridgeIndex: Int, audioData: FloatArray, frames: Int)

    /** Read processed audio from shared buffer (plugin output → host). */
    external fun readAudio(bridgeIndex: Int, audioData: FloatArray, frames: Int)

    /** Destroy buffer and free shared memory. */
    external fun destroyBuffer(bridgeIndex: Int)
}
