package com.example.twilightcalculator

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Сводный расчёт неба для даты: астрономическая ночь (Солнце ниже -18°)
 * и «истинная тёмная ночь» — промежутки, когда дополнительно Луна находится
 * за горизонтом (подходит для астрономических наблюдений).
 */
object Sky {

    data class TimeWindow(val start: LocalTime, val end: LocalTime)

    data class NightInfo(
        val hasAstroNight: Boolean,      // наступает ли астрономическая ночь в принципе
        val astroNightStart: LocalTime?, // начало ночи (вечером, может быть прошлый/этот день)
        val astroNightEnd: LocalTime?,   // конец ночи (утром следующего дня)
        val darkWindows: List<TimeWindow>, // тёмные окна без Луны внутри ночи
        val moonRises: List<LocalTime>,    // восходы Луны внутри ночи
        val moonSets: List<LocalTime>,     // заходы Луны внутри ночи
        val moonPhase: Moon.PhaseInfo
    )

    /** Порог «Луна за горизонтом», градусы (учёт рефракции, ~ -0.566°). */
    private const val MOON_DOWN_THRESHOLD = -0.566

    fun astroNight(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): NightInfo {
        val tonight = SunTimes.dailyTimes(date, latitude, longitude, zone)
        val tomorrow = SunTimes.dailyTimes(date.plusDays(1), latitude, longitude, zone)

        // Астрономическая ночь: от вечернего астросумеречного (заката на -18°)
        // до утреннего (восхода следующего дня на -18°).
        val dusk = tonight.astroDusk
        val dawn = tomorrow.astroDawn
        val phase = Moon.phase(date, zone)

        if (dusk == null || dawn == null) {
            return NightInfo(false, null, null, emptyList(), emptyList(), emptyList(), phase)
        }

        val startEpoch = Moon.utcEpochMinute(date, dusk, zone)
        val endEpoch = Moon.utcEpochMinute(date.plusDays(1), dawn, zone)

        val darkWindows = ArrayList<TimeWindow>()
        val moonRises = ArrayList<LocalTime>()
        val moonSets = ArrayList<LocalTime>()

        // Сканируем ночь с шагом 1 мин по высоте Луны.
        var prevDown = Moon.altitudeAt(startEpoch, latitude, longitude) < MOON_DOWN_THRESHOLD
        var segStart: Long? = if (prevDown) startEpoch else null

        var e = startEpoch + 1
        while (e < endEpoch) {
            val down = Moon.altitudeAt(e, latitude, longitude) < MOON_DOWN_THRESHOLD
            if (prevDown && !down) {
                // Луна поднялась над горизонтом -> восход.
                moonRises.add(Moon.localTimeAt(e, zone))
                segStart?.let {
                    closeWindow(it, e - 1, darkWindows, zone)
                }
                segStart = null
            } else if (!prevDown && down) {
                // Луна опустилась под горизонт -> заход.
                moonSets.add(Moon.localTimeAt(e, zone))
                segStart = e
            }
            prevDown = down
            e++
        }
        segStart?.let {
            closeWindow(it, endEpoch - 1, darkWindows, zone)
        }

        // Сливаем близкие окна и убираем слишком короткие (артефакты).
        val merged = mergeWindows(darkWindows)
        return NightInfo(
            hasAstroNight = true,
            astroNightStart = dusk,
            astroNightEnd = dawn,
            darkWindows = merged.filter { !it.short() },
            moonRises = moonRises,
            moonSets = moonSets,
            moonPhase = phase
        )
    }

    private fun closeWindow(
        startEpoch: Long,
        endEpoch: Long,
        dest: MutableList<TimeWindow>,
        zone: ZoneId
    ) {
        dest.add(TimeWindow(Moon.localTimeAt(startEpoch, zone), Moon.localTimeAt(endEpoch, zone)))
    }

    private fun TimeWindow.short(): Boolean {
        var dur = java.time.Duration.between(start, end).toMinutes()
        if (dur < 0) dur += 24 * 60 // окно может пересечь полночь
        return dur < 10
    }

    private fun mergeWindows(raw: List<TimeWindow>): List<TimeWindow> {
        if (raw.isEmpty()) return raw
        val sorted = raw.sortedBy { it.start }
        val out = ArrayList<TimeWindow>()
        var cur = sorted[0]
        for (w in sorted.drop(1)) {
            if (w.start <= cur.end.plusMinutes(10)) {
                // окна перекрываются или почти примыкают -> сливаем
                cur = if (w.end.isAfter(cur.end)) TimeWindow(cur.start, w.end)
                else cur
            } else {
                out.add(cur)
                cur = w
            }
        }
        out.add(cur)
        return out
    }
}