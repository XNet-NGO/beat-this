package com.beatthis.plugins.midi

/**
 * MIDI 2.0 UMP (Universal MIDI Packet) message builder for AAP plugin communication.
 * AAP uses UMP SysEx8 for parameter changes and standard UMP for notes.
 */
object Ump {

    // Message types (4-bit, upper nibble of first word)
    private const val MSG_TYPE_MIDI2_CHANNEL_VOICE = 0x4

    // Status codes for MIDI 2.0 Channel Voice Messages
    private const val STATUS_NOTE_ON = 0x9
    private const val STATUS_NOTE_OFF = 0x8
    private const val STATUS_CC = 0xB
    private const val STATUS_PITCH_BEND = 0xE
    private const val STATUS_PROGRAM_CHANGE = 0xC

    /** MIDI 2.0 Note On — 64-bit UMP */
    fun noteOn(channel: Int, note: Int, velocity: Int = 0xFFFF, group: Int = 0): LongArray {
        val word1 = (MSG_TYPE_MIDI2_CHANNEL_VOICE shl 28) or
                (group shl 24) or
                (STATUS_NOTE_ON shl 20) or
                (channel shl 16) or
                (note shl 8)
        val word2 = (velocity shl 16) // attribute type 0, attribute 0
        return longArrayOf(word1.toLong() shl 32 or word2.toLong())
    }

    /** MIDI 2.0 Note Off — 64-bit UMP */
    fun noteOff(channel: Int, note: Int, velocity: Int = 0xFFFF, group: Int = 0): LongArray {
        val word1 = (MSG_TYPE_MIDI2_CHANNEL_VOICE shl 28) or
                (group shl 24) or
                (STATUS_NOTE_OFF shl 20) or
                (channel shl 16) or
                (note shl 8)
        val word2 = (velocity shl 16)
        return longArrayOf(word1.toLong() shl 32 or word2.toLong())
    }

    /** MIDI 2.0 CC — 64-bit UMP with 32-bit value */
    fun controlChange(channel: Int, cc: Int, value: Int, group: Int = 0): LongArray {
        val word1 = (MSG_TYPE_MIDI2_CHANNEL_VOICE shl 28) or
                (group shl 24) or
                (STATUS_CC shl 20) or
                (channel shl 16) or
                (cc shl 8)
        val word2 = value
        return longArrayOf(word1.toLong() shl 32 or word2.toLong())
    }

    /** MIDI 2.0 Pitch Bend — 64-bit UMP with 32-bit value */
    fun pitchBend(channel: Int, value: Int, group: Int = 0): LongArray {
        val word1 = (MSG_TYPE_MIDI2_CHANNEL_VOICE shl 28) or
                (group shl 24) or
                (STATUS_PITCH_BEND shl 20) or
                (channel shl 16)
        val word2 = value
        return longArrayOf(word1.toLong() shl 32 or word2.toLong())
    }

    /**
     * AAP parameter change via MIDI 2.0 UMP SysEx8.
     * AAP uses SysEx8 with a specific manufacturer ID for parameter changes.
     */
    fun parameterChange(parameterId: Int, value: Float): ByteArray {
        // AAP SysEx8 format: [status][mfr_id x3][param_id x2][value x4]
        val buf = ByteArray(16)
        // Message type 0x5 (Data 128-bit), status 0x00
        buf[0] = 0x50.toByte()
        buf[1] = 0x0E.toByte() // size = 14 bytes payload
        // AAP manufacturer ID (0x7D = experimental/development)
        buf[2] = 0x7D.toByte()
        buf[3] = 0x00
        buf[4] = 0x00
        // Parameter ID (16-bit big-endian)
        buf[5] = (parameterId shr 8).toByte()
        buf[6] = (parameterId and 0xFF).toByte()
        // Value as IEEE 754 float (32-bit big-endian)
        val bits = java.lang.Float.floatToIntBits(value)
        buf[7] = (bits shr 24).toByte()
        buf[8] = (bits shr 16).toByte()
        buf[9] = (bits shr 8).toByte()
        buf[10] = (bits and 0xFF).toByte()
        return buf
    }
}
