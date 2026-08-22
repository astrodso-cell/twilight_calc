package com.example.twilightcalculator

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dateText: TextView
    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText
    private lateinit var txSun: TextView
    private lateinit var txTwilight: TextView
    private lateinit var txMoonNight: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dateText = findViewById(R.id.dateText)
        latInput = findViewById(R.id.latInput)
        lngInput = findViewById(R.id.lngInput)
        txSun = findViewById(R.id.txSun)
        txTwilight = findViewById(R.id.txTwilight)
        txMoonNight = findViewById(R.id.txMoonNight)

        val calcButton = findViewById<MaterialButton>(R.id.calcButton)
        val locationButton = findViewById<MaterialButton>(R.id.locationButton)

        dateText.text = getString(R.string.date_now, java.time.LocalDate.now().toString())
        latInput.setText(getString(R.string.default_lat))
        lngInput.setText(getString(R.string.default_lng))

        calcButton.setOnClickListener { calculate() }
        locationButton.setOnClickListener { useCurrentLocation() }
    }

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

        val today = java.time.LocalDate.now()
        val zone = ZoneId.systemDefault()
        val times = SunTimes.dailyTimes(today, lat, lng, zone)
        val sky = Sky.astroNight(today, lat, lng, zone)

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

        // --- Сумерки ---
        txTwilight.text = getString(
            R.string.tw_head,
            SunTimes.fmt(times.civilDawn), SunTimes.fmt(times.civilDusk),
            SunTimes.fmt(times.nauticalDawn), SunTimes.fmt(times.nauticalDusk),
            SunTimes.fmt(times.astroDawn), SunTimes.fmt(times.astroDusk)
        )

        // --- Луна и астрономическая ночь ---
        txMoonNight.text = formatMoonNight(sky)

        // Обновляем заголовок.
        dateText.text = getString(R.string.date_now, today.toString())
    }

    private fun formatMoonNight(sky: Sky.NightInfo): String {
        val phase = sky.moonPhase
        return getString(
            R.string.moon_head_fmt,
            phase.name,
            phase.illuminationPercent,
            listOfTimes(sky.moonRises), listOfTimes(sky.moonSets),
            fmtTimeOrDash(sky.astroNightStart),
            fmtTimeOrDash(sky.astroNightEnd),
            nightSummary(sky)
        )
    }

    private fun fmtTimeOrDash(time: LocalTime?): String =
        if (time == null) "—" else fmtTime(time)

    /** Что именно происходит с тёмной ночью. */
    private fun nightSummary(sky: Sky.NightInfo): String {
        if (!sky.hasAstroNight) return getString(R.string.moon_never)
        if (sky.darkWindows.isEmpty()) return getString(R.string.moon_always_up)
        if (sky.darkWindows.size == 1 &&
            sky.darkWindows[0].start == sky.astroNightStart &&
            sky.darkWindows[0].end == sky.astroNightEnd
        ) {
            return getString(R.string.moon_none)
        }
        val sb = StringBuilder()
        for (w in sky.darkWindows) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(getString(
                R.string.moon_window,
                fmtTime(w.start),
                fmtTime(w.end)
            ))
        }
        return sb.toString()
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
    }
}