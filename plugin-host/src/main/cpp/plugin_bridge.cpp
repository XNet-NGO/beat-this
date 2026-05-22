#include <jni.h>
#include <android/sharedmem.h>
#include <sys/mman.h>
#include <string.h>
#include <unistd.h>

/**
 * Native bridge for AAP plugin audio routing.
 * Manages shared memory buffers for passing audio between host and plugin process.
 */

#define MAX_CHANNELS 2
#define MAX_BUFFER_FRAMES 4096

struct PluginAudioBridge {
    int sharedMemFd;
    float* buffer;
    int numFrames;
    int numChannels;
    size_t bufferSize;
};

static PluginAudioBridge bridges[16]; // up to 16 plugin instances
static int bridgeCount = 0;

extern "C" {

/**
 * Create a shared memory buffer for audio exchange with a plugin.
 * Returns the bridge index, or -1 on failure.
 */
JNIEXPORT jint JNICALL
Java_com_beatthis_plugins_host_NativePluginBridge_createBuffer(
    JNIEnv* env, jobject thiz, jint numFrames, jint numChannels) {

    if (bridgeCount >= 16) return -1;

    size_t size = numFrames * numChannels * sizeof(float);
    int fd = ASharedMemory_create("aap_audio_buffer", size);
    if (fd < 0) return -1;

    float* buf = (float*)mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (buf == MAP_FAILED) {
        close(fd);
        return -1;
    }

    memset(buf, 0, size);

    int idx = bridgeCount++;
    bridges[idx].sharedMemFd = fd;
    bridges[idx].buffer = buf;
    bridges[idx].numFrames = numFrames;
    bridges[idx].numChannels = numChannels;
    bridges[idx].bufferSize = size;

    return idx;
}

/**
 * Get the file descriptor for passing to the plugin process via Binder.
 */
JNIEXPORT jint JNICALL
Java_com_beatthis_plugins_host_NativePluginBridge_getSharedMemFd(
    JNIEnv* env, jobject thiz, jint bridgeIndex) {

    if (bridgeIndex < 0 || bridgeIndex >= bridgeCount) return -1;
    return bridges[bridgeIndex].sharedMemFd;
}

/**
 * Write audio from MWEngine into the shared buffer (host → plugin).
 */
JNIEXPORT void JNICALL
Java_com_beatthis_plugins_host_NativePluginBridge_writeAudio(
    JNIEnv* env, jobject thiz, jint bridgeIndex, jfloatArray audioData, jint frames) {

    if (bridgeIndex < 0 || bridgeIndex >= bridgeCount) return;
    PluginAudioBridge& b = bridges[bridgeIndex];

    int count = frames * b.numChannels;
    if (count > (int)(b.bufferSize / sizeof(float))) count = b.bufferSize / sizeof(float);

    env->GetFloatArrayRegion(audioData, 0, count, b.buffer);
}

/**
 * Read processed audio back from the shared buffer (plugin → host).
 */
JNIEXPORT void JNICALL
Java_com_beatthis_plugins_host_NativePluginBridge_readAudio(
    JNIEnv* env, jobject thiz, jint bridgeIndex, jfloatArray audioData, jint frames) {

    if (bridgeIndex < 0 || bridgeIndex >= bridgeCount) return;
    PluginAudioBridge& b = bridges[bridgeIndex];

    int count = frames * b.numChannels;
    if (count > (int)(b.bufferSize / sizeof(float))) count = b.bufferSize / sizeof(float);

    env->SetFloatArrayRegion(audioData, 0, count, b.buffer);
}

/**
 * Destroy a bridge and free shared memory.
 */
JNIEXPORT void JNICALL
Java_com_beatthis_plugins_host_NativePluginBridge_destroyBuffer(
    JNIEnv* env, jobject thiz, jint bridgeIndex) {

    if (bridgeIndex < 0 || bridgeIndex >= bridgeCount) return;
    PluginAudioBridge& b = bridges[bridgeIndex];

    if (b.buffer) {
        munmap(b.buffer, b.bufferSize);
        b.buffer = nullptr;
    }
    if (b.sharedMemFd >= 0) {
        close(b.sharedMemFd);
        b.sharedMemFd = -1;
    }
}

} // extern "C"
