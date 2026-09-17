package com.arcadia.shell.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.arcadia.shell.R
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.ChirpPlayer
import com.arcadia.shell.model.ChirperVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays chirps — the little voice a profile speaks with on a status change, and the preview the
 * chirper picker fires under the cursor.
 *
 * Kept off [UiSoundController]'s pool: those samples are all loaded up front because a cursor
 * click cannot afford to miss, while there are 31 chirp takes and a player only ever hears their
 * own voice plus whatever they audition. Takes load on first use and stay loaded.
 */
@Singleton
class ChirpSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: ShellPreferences,
) : ChirpPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var soundPool: SoundPool? = null

    /** Sound ids by resource id, so a take is loaded once however often it is heard. */
    private val loaded = mutableMapOf<Int, Int>()

    /** Last take index per voice, so consecutive chirps do not repeat the same sample. */
    private val lastTake = mutableMapOf<ChirperVoice, Int>()

    private var volume: Float = 1f

    init {
        scope.launch {
            preferences.settings
                .map { it.uiSfxVolume }
                .distinctUntilChanged()
                .collect { volume = it.coerceIn(0f, 1f) }
        }
    }

    override fun chirp(voice: ChirperVoice) {
        val v = volume
        if (v <= 0f) return
        val pool = ensurePool() ?: return
        val take = nextTake(voice)
        val resId = takeResId(voice, take) ?: return
        val soundId = loaded.getOrPut(resId) {
            runCatching { pool.load(context, resId, /* priority */ 1) }.getOrDefault(0)
        }
        if (soundId == 0) {
            loaded.remove(resId)
            return
        }
        // SoundPool.play returns 0 until the sample finishes decoding, so the very first chirp of
        // a voice can be silent. That is better than blocking the UI thread on a decode, and the
        // sample is ready by the next one.
        runCatching { pool.play(soundId, v, v, /* priority */ 1, /* loop */ 0, /* rate */ 1f) }
    }

    /** Drop decoded takes under memory pressure; they reload on the next chirp. */
    fun releaseForTrim() {
        runCatching { soundPool?.release() }
        soundPool = null
        loaded.clear()
    }

    private fun nextTake(voice: ChirperVoice): Int {
        if (voice.takes <= 1) return 1
        val previous = lastTake[voice]
        var take: Int
        do {
            take = (1..voice.takes).random()
        } while (take == previous)
        lastTake[voice] = take
        return take
    }

    private fun ensurePool(): SoundPool? {
        soundPool?.let { return it }
        val pool = runCatching {
            SoundPool.Builder()
                // A chirp never overlaps itself, but a preview can land on top of one already
                // playing while the cursor moves.
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_GAME for the same reason the UI clicks use it: handhelds and TVs
                        // often keep the system stream muted while game volume is up.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        }.getOrNull()
        soundPool = pool
        return pool
    }
}

/**
 * The generated ids, listed rather than looked up by name: `getIdentifier` is barred under R8 and
 * a missing take should be a compile error, not a silent chirp.
 */
private fun takeResId(voice: ChirperVoice, take: Int): Int? = when (voice) {
    ChirperVoice.UsagiShade -> when (take) {
        1 -> R.raw.chirp_usagishade_1
        2 -> R.raw.chirp_usagishade_2
        3 -> R.raw.chirp_usagishade_3
        4 -> R.raw.chirp_usagishade_4
        5 -> R.raw.chirp_usagishade_5
        else -> null
    }
    ChirperVoice.UsagiIshii -> R.raw.chirp_usagiishii_1.takeIf { take == 1 }
    ChirperVoice.IPrinceAngel -> when (take) {
        1 -> R.raw.chirp_iprinceangel_1
        2 -> R.raw.chirp_iprinceangel_2
        3 -> R.raw.chirp_iprinceangel_3
        4 -> R.raw.chirp_iprinceangel_4
        else -> null
    }
    ChirperVoice.Furogii -> when (take) {
        1 -> R.raw.chirp_furogii_1
        2 -> R.raw.chirp_furogii_2
        3 -> R.raw.chirp_furogii_3
        else -> null
    }
    ChirperVoice.Sora -> R.raw.chirp_sora_1.takeIf { take == 1 }
    ChirperVoice.Tonic -> when (take) {
        1 -> R.raw.chirp_tonic_1
        2 -> R.raw.chirp_tonic_2
        3 -> R.raw.chirp_tonic_3
        4 -> R.raw.chirp_tonic_4
        else -> null
    }
    ChirperVoice.Somarix -> when (take) {
        1 -> R.raw.chirp_somarix_1
        2 -> R.raw.chirp_somarix_2
        3 -> R.raw.chirp_somarix_3
        else -> null
    }
    ChirperVoice.Makoto -> when (take) {
        1 -> R.raw.chirp_makoto_1
        2 -> R.raw.chirp_makoto_2
        3 -> R.raw.chirp_makoto_3
        else -> null
    }
    ChirperVoice.Lyn -> when (take) {
        1 -> R.raw.chirp_lyn_1
        2 -> R.raw.chirp_lyn_2
        3 -> R.raw.chirp_lyn_3
        else -> null
    }
    ChirperVoice.Marlix -> when (take) {
        1 -> R.raw.chirp_marlix_1
        2 -> R.raw.chirp_marlix_2
        3 -> R.raw.chirp_marlix_3
        4 -> R.raw.chirp_marlix_4
        else -> null
    }
}
