// yrs. music: modified from Lotus by dn0ne (GPL-3.0). This app has no internet access, so the
// services that used to download lyrics (LRCLIB) and track details (MusicBrainz) answer locally.
package com.dn0ne.player.app.data.remote

import com.dn0ne.player.app.data.remote.lyrics.LyricsProvider
import com.dn0ne.player.app.data.remote.metadata.MetadataProvider
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.metadata.MetadataSearchResult
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.track.Track

/** Lyrics come only from the music files themselves (tags or a .lrc beside the song). */
class OfflineLyricsProvider : LyricsProvider {
    override suspend fun getLyrics(track: Track): Result<Lyrics, DataError.Network> = Result.Error(DataError.Network.NoInternet)
    override suspend fun postLyrics(track: Track, lyrics: Lyrics): Result<Unit, DataError.Network> = Result.Error(DataError.Network.NoInternet)
}

/** Track details are edited by hand; nothing is looked up online. */
class OfflineMetadataProvider : MetadataProvider {
    override suspend fun searchMetadata(query: String, trackDuration: Long): Result<List<MetadataSearchResult>, DataError> = Result.Error(DataError.Network.NoInternet)
    override suspend fun getCoverArtBytes(searchResult: MetadataSearchResult): Result<ByteArray, DataError> = Result.Error(DataError.Network.NoInternet)
}
