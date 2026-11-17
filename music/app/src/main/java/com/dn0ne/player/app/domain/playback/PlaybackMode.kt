package com.dn0ne.player.app.domain.playback

// yrs. music: Off and ShuffleOnce added at the end (saved by position), so shuffle and
// repeat can be set on their own.
enum class PlaybackMode {
    Repeat,
    RepeatOne,
    Shuffle,
    Off,
    ShuffleOnce;

    val shuffles: Boolean get() = this == Shuffle || this == ShuffleOnce
    val repeat: com.dn0ne.player.app.domain.playback.Repeat
        get() = when (this) {
            Repeat, Shuffle -> com.dn0ne.player.app.domain.playback.Repeat.All
            RepeatOne -> com.dn0ne.player.app.domain.playback.Repeat.One
            Off, ShuffleOnce -> com.dn0ne.player.app.domain.playback.Repeat.Off
        }

    companion object {
        fun of(shuffle: Boolean, repeat: com.dn0ne.player.app.domain.playback.Repeat): PlaybackMode = when (repeat) {
            com.dn0ne.player.app.domain.playback.Repeat.One -> RepeatOne
            com.dn0ne.player.app.domain.playback.Repeat.All -> if (shuffle) Shuffle else Repeat
            com.dn0ne.player.app.domain.playback.Repeat.Off -> if (shuffle) ShuffleOnce else Off
        }
    }
}

enum class Repeat { Off, All, One }
