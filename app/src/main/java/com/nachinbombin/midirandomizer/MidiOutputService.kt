package com.nachinbombin.midirandomizer

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import android.util.Log

class MidiOutputService : MidiDeviceService() {

    companion object {
        private const val TAG = "MidiOutputService"
        private var instance: MidiOutputService? = null

        fun getInstance(): MidiOutputService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MidiOutputService created")
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return emptyArray()
    }

    /**
     * Sends MIDI data to all connected clients on the virtual output port.
     */
    fun sendMidiToClients(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
        val receivers = getOutputPortReceivers()
        if (receivers.isEmpty()) {
            Log.v(TAG, "No clients connected to virtual output port")
            return
        }
        for (receiver in receivers) {
            try {
                receiver.send(data, offset, count, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to receiver", e)
            }
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
