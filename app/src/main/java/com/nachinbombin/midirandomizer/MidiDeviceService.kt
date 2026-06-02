package com.nachinbombin.midirandomizer

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver

class MidiDeviceService : MidiDeviceService() {
    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        // This app primarily sends MIDI, so we don't need to receive anything.
        // But we provide an empty receiver if an input port was declared.
        return arrayOf()
    }
}
