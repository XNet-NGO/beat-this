package com.beatthis.ui.mixer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.beatthis.engine.Track
import com.beatthis.engine.TrackManager
import com.beatthis.engine.TrackType

@Composable
fun MixerScreen() {
    // Demo tracks for now — will be wired to real TrackManager
    val tracks = remember {
        listOf(
            Track(1, "Kick", TrackType.DRUM),
            Track(2, "Bass", TrackType.MIDI),
            Track(3, "Keys", TrackType.MIDI),
            Track(4, "Vocals", TrackType.AUDIO),
            Track(5, "Guitar", TrackType.AUDIO),
        )
    }
    MixerView(tracks = tracks)
}
