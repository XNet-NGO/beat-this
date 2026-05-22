package com.beatthis.engine.midi

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.Looper

/**
 * Handles USB and Bluetooth MIDI input via Android MIDI API.
 */
class MidiInputHandler(context: Context) {

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private var openDevice: MidiDevice? = null
    private var openPort: MidiOutputPort? = null
    var onNoteOn: ((channel: Int, note: Int, velocity: Int) -> Unit)? = null
    var onNoteOff: ((channel: Int, note: Int) -> Unit)? = null
    var onCC: ((channel: Int, cc: Int, value: Int) -> Unit)? = null

    /** Get list of available MIDI devices. */
    fun getDevices(): List<MidiDeviceInfo> =
        midiManager?.devices?.toList() ?: emptyList()

    /** Connect to a MIDI device's first output port. */
    fun connect(deviceInfo: MidiDeviceInfo) {
        midiManager?.openDevice(deviceInfo, { device ->
            openDevice = device
            val portInfo = deviceInfo.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            if (portInfo != null) {
                openPort = device.openOutputPort(portInfo.portNumber)
                openPort?.connect(receiver)
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** Disconnect current device. */
    fun disconnect() {
        openPort?.close()
        openDevice?.close()
        openPort = null
        openDevice = null
    }

    private val receiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            if (count < 2) return
            val status = data[offset].toInt() and 0xF0
            val channel = data[offset].toInt() and 0x0F
            when (status) {
                0x90 -> { // Note On
                    val note = data[offset + 1].toInt() and 0x7F
                    val vel = if (count > 2) data[offset + 2].toInt() and 0x7F else 0
                    if (vel > 0) onNoteOn?.invoke(channel, note, vel)
                    else onNoteOff?.invoke(channel, note)
                }
                0x80 -> { // Note Off
                    val note = data[offset + 1].toInt() and 0x7F
                    onNoteOff?.invoke(channel, note)
                }
                0xB0 -> { // CC
                    val cc = data[offset + 1].toInt() and 0x7F
                    val value = if (count > 2) data[offset + 2].toInt() and 0x7F else 0
                    onCC?.invoke(channel, cc, value)
                }
            }
        }
    }
}
