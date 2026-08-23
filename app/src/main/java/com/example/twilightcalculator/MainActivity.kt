package com.example.twilightcalculator

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dateText: TextView
    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText
    private lateinit var txSun: TextView
    private lateinit var txCivil: TextView
    private lateinit var txNautical: TextView
    private lateinit var txAstro: TextView
    private lateinit var txMoonNight: TextView
    private lateinit var chartMonthTitle: TextView
    private lateinit var prevMonth: MaterialButton
    private lateinit var nextMonth: MaterialButton
    private lateinit var tableBody: LinearLayout

    /** Выбранная для расчёта дата. */
    private var selectedDate: LocalDate = LocalDate.now()

    /** Первое число месяца, показываемого в таблице (независимо от [selectedDate]). */
    private var chartMonth: LocalDate = LocalDate.now().withDayOfMonth(1)

    // --- Сохранение последнего местоположения ---
    private val prefs by lazy {
        getSharedPreferences("twilight_prefs", android.content.Context.MODE_PRIVATE)
    }
    private val savedLat: String? get() = prefs.getString(KEY_LAT, null)
    private val savedLng: String? get() = prefs.getString(KEY_LNG, null)

    /** Сохраняет текущие координаты как «последние использованные». */
    private fun saveLocation() {
        val lat = latInput.text.toString().trim()
        val lng = lngInput.text.toString().trim()
        prefs.edit()
            .putString(KEY_LAT, lat)
            .putString(KEY_LNG, lng)
            .apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dateText = findViewById(R.id.dateText)
        latInput = findViewById(R.id.latInput)
        lngInput = findViewById(R.id.lngInput)
        txSun = findViewById(R.id.txSun)
        txCivil = findViewById(R.id.txCivil)
        txNautical = findViewById(R.id.txNautical)
        txAstro = findViewById(R.id.txAstro)
        txMoonNight = findViewById(R.id.txMoonNight)
        chartMonthTitle = findViewById(R.id.chartMonthTitle)
        prevMonth = findViewById(R.id.prevMonth)
        nextMonth = findViewById(R.id.nextMonth)
        tableBody = findViewById(R.id.tableBody)

        val calcButton = findViewById<MaterialButton>(R.id.calcButton)
        val locationButton = findViewById<MaterialButton>(R.id.locationButton)
        val dateButton = findViewById<MaterialButton>(R.id.dateButton)

        dateText.text = getString(R.string.date_now, date(selectedDate))
        // Восстанавливаем последние координаты, иначе — значения по умолчанию.
        latInput.setText(savedLat ?: getString(R.string.default_lat))
        lngInput.setText(savedLng ?: getString(R.string.default_lng))

        calcButton.setOnClickListener { calculate() }
        locationButton.setOnClickListener { useCurrentLocation() }
        dateButton.setOnClickListener { showDatePicker() }

        prevMonth.setOnClickListener {
            chartMonth = chartMonth.minusMonths(1)
            renderDarkNightChart()
        }
        nextMonth.setOnClickListener {
            chartMonth = chartMonth.plusMonths(1)
            renderDarkNightChart()
        }
        renderDarkNightChart()
    }

    private fun showDatePicker() {
        val c = Calendar.getInstance().apply {
            set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                // Показываем на графике месяц выбранной даты — иначе числа на
                // графике (независимый месяц) будут съезжать относительно карточки.
                chartMonth = selectedDate.withDayOfMonth(1)
                dateText.text = getString(R.string.date_now, date(selectedDate))
                calculate()
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Человекочитаемая дата в формате ISO, например 2026-08-26. */
    private fun date(d: LocalDate): String = d.toString()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fillFromKnownLocation()
            } else {
                dateText.text = getString(R.string.location_denied)
            }
        }
    }

    private fun calculate() {
        val lat = latInput.text.toString().trim().toDoubleOrNull()
        val lng = lngInput.text.toString().trim().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            txSun.text = getString(R.string.error_coords)
            return
        }

        // Запоминаем последние использованные координаты.
        saveLocation()

        val zone = ZoneId.systemDefault()
        val times = SunTimes.dailyTimes(selectedDate, lat, lng, zone)
        val sky = Sky.astroNight(selectedDate, lat, lng, zone)

        // --- Солнце ---
        val hours = times.daylightHours()
        txSun.text = if (hours != null) {
            getString(
                R.string.sun_head,
                SunTimes.fmt(times.sunrise),
                SunTimes.fmt(times.sunset),
                fmtDecimal(hours)
            )
        } else {
            getString(R.string.sun_head_short, "—")
        }

        // --- Сумерки (рассвет · закат) ---
        txCivil.text = twilightInterval(times.civilDawn, times.civilDusk)
        txNautical.text = twilightInterval(times.nauticalDawn, times.nauticalDusk)
        txAstro.text = twilightInterval(times.astroDawn, times.astroDusk)

        // --- Луна и астрономическая ночь ---
        txMoonNight.text = formatMoonNight(sky)

        // --- График тёмной ночи на месяц ---
        renderDarkNightChart()

        // Обновляем заголовок.
        dateText.text = getString(R.string.date_now, date(selectedDate))
    }

    private fun twilightInterval(dawn: LocalTime?, dusk: LocalTime?): String =
        getString(R.string.tw_interval, SunTimes.fmt(dawn), SunTimes.fmt(dusk))

    /** Заполняет таблицу тёмной ночи для месяца [chartMonth] и обновляет заголовок. */
    private fun renderDarkNightChart() {
        val lat = latInput.text.toString().trim().toDoubleOrNull()
        val lng = lngInput.text.toString().trim().toDoubleOrNull()
        if (lat == null || lng == null) return

        val zone = ZoneId.systemDefault()
        val daysInMonth = chartMonth.lengthOfMonth()
        chartMonthTitle.text = getString(
            R.string.chart_month_title,
            russianMonth(chartMonth), chartMonth.year
        )

        // Очищаем предыдущие строки.
        tableBody.removeAllViews()

        val dateColor = ContextCompat.getColor(this, R.color.text_primary)
        val startColor = ContextCompat.getColor(this, R.color.tw_nautical)
        val endColor = ContextCompat.getColor(this, R.color.tw_civil)
        val weekendColor = ContextCompat.getColor(this, R.color.weekend_red)
        val fillColor = Color.parseColor("#5540468C") // полупрозрачный тёмный для полоски

        // Длительность тёмной ночи по каждому дню, чтобы найти максимум за месяц.
        val durations = ArrayList<Int>()
        val skies = ArrayList<List<Sky.TimeWindow>>()
        for (i in 0 until daysInMonth) {
            val d = chartMonth.plusDays(i.toLong())
            val sky = Sky.astroNight(d, lat, lng, zone)
            val dark = if (sky.hasAstroNight) sky.darkWindows else emptyList()
            skies.add(dark)
            durations.add(dark.sumOf { windowMinutes(it) })
        }
        val maxDur = durations.maxOrNull() ?: 0

        for (i in 0 until daysInMonth) {
            val d = chartMonth.plusDays(i.toLong())
            val dark = skies[i]

            val startTxt = if (dark.isNotEmpty()) SunTimes.fmt(dark.first().start) else "—"
            val endTxt = if (dark.isNotEmpty()) SunTimes.fmt(dark.last().end) else "—"

            // Доля заполнения строки: от 0 (нет темноты) до 1 (самая длинная ночь).
            val frac = if (maxDur > 0) durations[i].toFloat() / maxDur.toFloat() else 0f

            // День недели; выходные (сб/вс) — красным.
            val isWeekend = d.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                d.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val dayColor = if (isWeekend) weekendColor else dateColor
            val dayTxt = "${d.dayOfMonth} ${weekdayShort(d)}"

            tableBody.addView(darkRow(dayTxt, startTxt, endTxt,
                frac, dayColor, startColor, endColor, fillColor))
        }
    }

    /** Короткое название дня недели (пн, вт, …). */
    private fun weekdayShort(d: LocalDate): String = when (d.dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "пн"
        java.time.DayOfWeek.TUESDAY -> "вт"
        java.time.DayOfWeek.WEDNESDAY -> "ср"
        java.time.DayOfWeek.THURSDAY -> "чт"
        java.time.DayOfWeek.FRIDAY -> "пт"
        java.time.DayOfWeek.SATURDAY -> "сб"
        java.time.DayOfWeek.SUNDAY -> "вс"
    }

    /**
     * Строка таблицы: залитая «полоска темноты» слева (доля [frac])+ текст поверх.
     */
    private fun darkRow(
        dateTxt: String,
        startTxt: String,
        endTxt: String,
        frac: Float,
        dateColor: Int,
        startColor: Int,
        endColor: Int,
        fillColor: Int
    ): View {
        val frame = FrameLayout(this)
        frame.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        frame.setPadding(0, dp(2), 0, dp(2))
        frame.minimumHeight = dp(30)

        // Полоска-фон: левая доля залита, остальное прозрачно.
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val filled = View(this).apply { setBackgroundColor(fillColor) }
        val empty = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val fracPts = (frac * 1000).toInt().coerceIn(0, 1000)
        bar.addView(filled, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fracPts.toFloat()))
        bar.addView(empty, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (1000 - fracPts).toFloat()))
        frame.addView(bar)

        // Кнопки текста поверх, центрированные по вертикали.
        val textRow = LinearLayout(this)
        textRow.orientation = LinearLayout.HORIZONTAL
        textRow.gravity = Gravity.CENTER_VERTICAL
        textRow.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        textRow.addView(column(dateTxt, 9, 14f, false, dateColor))
        textRow.addView(column(startTxt, 9, 14f, true, startColor))
        textRow.addView(column(endTxt, 9, 14f, true, endColor))
        frame.addView(textRow)

        return frame
    }

    /** Продолжительность тёмного окна в минутах (учитывает переход через полночь). */
    private fun windowMinutes(w: Sky.TimeWindow): Int {
        val start = w.start.hour * 60 + w.start.minute
        val end = w.end.hour * 60 + w.end.minute
        return if (end >= start) end - start else (end + 24 * 60) - start
    }

    /** Создаёт TextView-колонку строки таблицы. */
    private fun column(
        text: String,
        weight: Int,
        textSize: Float,
        alignEnd: Boolean,
        color: Int
    ): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(textSize)
        tv.setTextColor(color)
        tv.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, weight.toFloat()
        )
        if (alignEnd) tv.gravity = Gravity.END
        return tv
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Русское название месяца по-настоящему (именительный падеж). */
    private fun russianMonth(d: LocalDate): String = when (d.monthValue) {
        1 -> "Январь"
        2 -> "Февраль"
        3 -> "Март"
        4 -> "Апрель"
        5 -> "Май"
        6 -> "Июнь"
        7 -> "Июль"
        8 -> "Август"
        9 -> "Сентябрь"
        10 -> "Октябрь"
        11 -> "Ноябрь"
        else -> "Декабрь"
    }

    private fun formatMoonNight(sky: Sky.NightInfo): String {
        val phase = sky.moonPhase
        // «Тёмная ночь» — та же, что на графике: окна, когда Луна за горизонтом.
        val (darkStart, darkEnd) = darkNightInterval(sky)
        return getString(
            R.string.moon_head_fmt,
            phase.name,
            phase.illuminationPercent,
            listOfTimes(sky.moonRises), listOfTimes(sky.moonSets),
            fmtTimeOrDash(darkStart),
            fmtTimeOrDash(darkEnd),
            nightSummary(sky)
        )
    }

    /**
     * Интервал «истинной тёмной ночи»: от начала первого тёмного окна до конца
     * последнего (совпадает с графиком). Тёмной ночи нет — null/null.
     */
    private fun darkNightInterval(sky: Sky.NightInfo): Pair<LocalTime?, LocalTime?> {
        if (!sky.hasAstroNight || sky.darkWindows.isEmpty()) return null to null
        return sky.darkWindows.first().start to sky.darkWindows.last().end
    }

    private fun fmtTimeOrDash(time: LocalTime?): String =
        if (time == null) "—" else fmtTime(time)

    /** Что именно происходит с тёмной ночью. */
    private fun nightSummary(sky: Sky.NightInfo): String {
        if (!sky.hasAstroNight) return getString(R.string.moon_never)
        if (sky.darkWindows.isEmpty()) return getString(R.string.moon_always_up)
        // Одним непрерывным интервалом: от начала первого тёмного окна до конца
        // последнего (совпадает с заголовком и графиком).
        return getString(R.string.moon_window,
            fmtTime(sky.darkWindows.first().start),
            fmtTime(sky.darkWindows.last().end))
    }

    private fun listOfTimes(times: List<LocalTime>): String =
        times.joinToString(", ") { fmtTime(it) }.ifEmpty { getString(R.string.moon_rise_set_none) }

    private fun fmtTime(t: LocalTime): String = SunTimes.fmt(t)

    private fun fmtDecimal(v: Double): String =
        String.format(Locale.US, "%.1f ч", v)

    // --- Геолокация ---
    private fun useCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION
            )
        } else {
            fillFromKnownLocation()
        }
    }

    private fun fillFromKnownLocation() {
        val loc = lastKnownLocation()
        if (loc != null) {
            latInput.setText(String.format(Locale.US, "%.6f", loc.latitude))
            lngInput.setText(String.format(Locale.US, "%.6f", loc.longitude))
            dateText.text = getString(
                R.string.location_ok,
                String.format(Locale.US, "%.4f", loc.latitude),
                String.format(Locale.US, "%.4f", loc.longitude)
            )
            // Пересчитываем результаты под новые координаты.
            calculate()
        } else {
            dateText.text = getString(R.string.location_not_found)
        }
    }

    private fun lastKnownLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                    best = loc
                }
            } catch (_: SecurityException) {
                // нет разрешения — пропускаем провайдера
            }
        }
        return best
    }

    companion object {
        private const val REQUEST_LOCATION = 100
        private const val KEY_LAT = "last_lat"
        private const val KEY_LNG = "last_lng"
    }
}