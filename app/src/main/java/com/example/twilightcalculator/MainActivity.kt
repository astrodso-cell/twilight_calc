package com.example.twilightcalculator

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dateText: TextView
    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dateText = findViewById(R.id.dateText)
        latInput = findViewById(R.id.latInput)
        lngInput = findViewById(R.id.lngInput)
        resultText = findViewById(R.id.resultText)

        val calcButton = findViewById<Button>(R.id.calcButton)
        val locationButton = findViewById<Button>(R.id.locationButton)

        // Значения по умолчанию: Москва.
        dateText.text = getString(R.string.date_now, LocalDate.now().toString())
        latInput.setText("55.7558")
        lngInput.setText("37.6173")

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
                resultText.text = getString(R.string.location_denied)
            }
        }
    }

    private fun calculate() {
        val lat = latInput.text.toString().trim().toDoubleOrNull()
        val lng = lngInput.text.toString().trim().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            resultText.text = getString(R.string.error_coords)
            return
        }

        val today = LocalDate.now()
        val times = SunTimes.dailyTimes(today, lat, lng, ZoneId.systemDefault())

        val sb = StringBuilder()
        sb.append(getString(R.string.header_result, today)).append("\n\n")
        sb.append(timeRow(getString(R.string.sunrise), times.sunrise))
        sb.append(timeRow(getString(R.string.sunset), times.sunset))
        sb.append("\n")
        sb.append(timeRow(getString(R.string.civil_dawn), times.civilDawn))
        sb.append(timeRow(getString(R.string.civil_dusk), times.civilDusk))
        sb.append("\n")
        sb.append(timeRow(getString(R.string.nautical_dawn), times.nauticalDawn))
        sb.append(timeRow(getString(R.string.nautical_dusk), times.nauticalDusk))
        sb.append("\n")
        sb.append(timeRow(getString(R.string.astro_dawn), times.astroDawn))
        sb.append(timeRow(getString(R.string.astro_dusk), times.astroDusk))
        sb.append("\n")
        val hours = times.daylightHours()
        sb.append(if (hours != null) getString(R.string.daylight, hours)
                  else getString(R.string.daylight_none)).append("\n")

        resultText.text = sb.toString()
    }

    private fun timeRow(label: String, t: java.time.LocalTime?): String =
        "$label: ${SunTimes.fmt(t)}\n"

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
            resultText.text = getString(
                R.string.location_ok,
                String.format(Locale.US, "%.4f", loc.latitude),
                String.format(Locale.US, "%.4f", loc.longitude)
            )
        } else {
            resultText.text = getString(R.string.location_not_found)
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