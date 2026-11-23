package com.ngdstudio.mindimania.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import com.ngdstudio.mindimania.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.EnumMap
import kotlin.random.Random

enum class Sfx(@param:RawRes val res: Int) {
    CARD_SLIDE(R.raw.sfx_card_slide),
    SNAP1(R.raw.sfx_card_snap1),
    SNAP2(R.raw.sfx_card_snap2),
    SNAP3(R.raw.sfx_card_snap3),
    DEAL(R.raw.sfx_deal),
    SHUFFLE(R.raw.sfx_shuffle),
    DRAW(R.raw.sfx_draw),
    TAP(R.raw.sfx_tap),
    TOGGLE(R.raw.sfx_toggle),
    ERROR(R.raw.sfx_error),
    BLOCK(R.raw.sfx_block),
    REVERSE(R.raw.sfx_reverse),
    PLUS(R.raw.sfx_plus),
    STACK_TICK(R.raw.sfx_stack_tick),
    PRISM(R.raw.sfx_prism),
    STORM(R.raw.sfx_storm),
    ECLIPSE(R.raw.sfx_eclipse),
    MANIA(R.raw.sfx_mania),
    CATCH(R.raw.sfx_catch),
    OVERLOAD(R.raw.sfx_overload),
    COIN(R.raw.sfx_coin),
    CHEST(R.raw.sfx_chest),
    WIN(R.raw.sfx_win),
    LOSE(R.raw.sfx_lose),
    SWAP(R.raw.sfx_swap),
    PURGE(R.raw.sfx_purge),
    TURN(R.raw.sfx_turn),
    WHOOSH(R.raw.sfx_whoosh),
    SELECT(R.raw.sfx_select),
}

class SoundManager(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = EnumMap<Sfx, Int>(Sfx::class.java)
    private val loaded = HashSet<Int>()

    @Volatile var volume: Float = 0.9f

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded += sampleId } }
        val app = context.applicationContext
        Sfx.entries.forEach { ids[it] = pool.load(app, it.res, 1) }
    }

    fun play(sfx: Sfx, gain: Float = 1f, rate: Float = 1f, vary: Boolean = true) {
        if (volume <= 0.001f) return
        val id = ids[sfx] ?: return
        synchronized(loaded) { if (id !in loaded) return }
        val v = (volume * gain).coerceIn(0f, 1f)
        val r = if (vary) rate * (0.96f + Random.nextFloat() * 0.08f) else rate
        pool.play(id, v, v, 1, 0, r.coerceIn(0.5f, 2f))
    }

    fun snap(gain: Float = 1f) = play(listOf(Sfx.SNAP1, Sfx.SNAP2, Sfx.SNAP3).random(), gain)
}

enum class Track { MENU, DAY, NIGHT }

/** Looping background music. Day and Night table themes run in sync so they can cross-fade musically. */
class MusicPlayer(private val context: Context) {
    private var menu: MediaPlayer? = null
    private var day: MediaPlayer? = null
    private var night: MediaPlayer? = null
    private var current: Track? = null
    private var dayMix = 1f
    private var fadeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var paused = false

    @Volatile var volume: Float = 0.6f
        set(value) {
            field = value
            applyVolumes()
        }

    private fun create(@RawRes res: Int): MediaPlayer? = runCatching {
        MediaPlayer.create(context.applicationContext, res)?.apply {
            isLooping = true
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        }
    }.getOrNull()

    fun play(track: Track) {
        if (track == current) return
        if (track != Track.MENU && current != null && current != Track.MENU && day != null) {
            crossTo(track)
            current = track
            return
        }
        stopAll()
        current = track
        when (track) {
            Track.MENU -> {
                menu = create(R.raw.music_menu)
                applyVolumes()
                if (!paused) menu?.start()
            }
            Track.DAY, Track.NIGHT -> {
                day = create(R.raw.music_day)
                night = create(R.raw.music_night)
                dayMix = if (track == Track.DAY) 1f else 0f
                applyVolumes()
                syncDayNight()
            }
        }
    }

    private fun crossTo(track: Track) {
        val target = if (track == Track.DAY) 1f else 0f
        if (target == dayMix) return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val start = dayMix
            val steps = 30
            for (i in 1..steps) {
                dayMix = start + (target - start) * i / steps
                applyVolumes()
                syncDayNight()
                delay(50)
            }
        }
    }

    /**
     * Day and Night are the same length and stay in step, but only the one you can hear is decoded: both run only
     * while cross-fading. A track that starts joins at the other one's position.
     */
    private fun syncDayNight() {
        if (paused) return
        val d = day ?: return
        val n = night ?: return
        runCatching {
            if (dayMix > 0f && !d.isPlaying) {
                if (n.isPlaying) d.seekTo(n.currentPosition)
                d.start()
            }
            if (dayMix < 1f && !n.isPlaying) {
                if (d.isPlaying) n.seekTo(d.currentPosition)
                n.start()
            }
            if (dayMix <= 0f && d.isPlaying) d.pause()
            if (dayMix >= 1f && n.isPlaying) n.pause()
        }
    }

    private fun applyVolumes() {
        val v = volume.coerceIn(0f, 1f)
        runCatching {
            menu?.setVolume(v * 0.85f, v * 0.85f)
            day?.setVolume(v * 0.7f * dayMix, v * 0.7f * dayMix)
            night?.setVolume(v * 0.7f * (1 - dayMix), v * 0.7f * (1 - dayMix))
        }
    }

    fun pause() {
        paused = true
        runCatching { menu?.takeIf { it.isPlaying }?.pause() }
        runCatching { day?.takeIf { it.isPlaying }?.pause() }
        runCatching { night?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume() {
        paused = false
        runCatching { menu?.start() }
        syncDayNight()
    }

    private fun stopAll() {
        fadeJob?.cancel()
        listOf(menu, day, night).forEach { mp -> runCatching { mp?.stop(); mp?.release() } }
        menu = null; day = null; night = null
    }
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    @Volatile var enabled: Boolean = true

    private fun vibrate(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    fun tick() = vibrate(12, 60)
    fun light() = vibrate(20, 90)
    fun medium() = vibrate(35, 150)
    fun heavy() = vibrate(60, 255)
}
