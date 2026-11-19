package com.example.citymockhelper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var isMocking = false

    private var activeTargetLat = 0.0
    private var activeTargetLng = 0.0

    private lateinit var etTargetLat: EditText
    private lateinit var etTargetLng: EditText
    private lateinit var etCityLat: EditText
    private lateinit var etCityLng: EditText
    private lateinit var etRadiusKm: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        etTargetLat = findViewById(R.id.etTargetLat)
        etTargetLng = findViewById(R.id.etTargetLng)
        etCityLat = findViewById(R.id.etCityLat)
        etCityLng = findViewById(R.id.etCityLng)
        etRadiusKm = findViewById(R.id.etRadiusKm)
        tvStatus = findViewById(R.id.tvStatus)

        val btnStart = findViewById<Button>(R.id.btnStartMock)
        val btnStop = findViewById<Button>(R.id.btnStopMock)

        btnStart.setOnClickListener {
            validateAndCheckCity()
        }

        btnStop.setOnClickListener {
            stopMockLocation()
            tvStatus.text = "Status: Mock Stopped"
        }
    }

    private fun validateAndCheckCity() {
        val targetLat = etTargetLat.text.toString().toDoubleOrNull()
        val targetLng = etTargetLng.text.toString().toDoubleOrNull()
        val cityLat = etCityLat.text.toString().toDoubleOrNull()
        val cityLng = etCityLng.text.toString().toDoubleOrNull()
        val radiusKm = etRadiusKm.text.toString().toDoubleOrNull()

        if (targetLat == null || targetLng == null || cityLat == null || cityLng == null || radiusKm == null) {
            Toast.makeText(this, "Sare coordinates aur radius sahi se bharein.", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }

        // Pehle existing mock stop karein taaki real GPS fetch ho sake
        stopMockLocation()

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation.addOnSuccessListener { realLocation ->
            if (realLocation != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    realLocation.latitude, realLocation.longitude,
                    cityLat, cityLng,
                    results
                )

                val distanceKm = results[0] / 1000.0
                if (distanceKm <= radiusKm) {
                    activeTargetLat = targetLat
                    activeTargetLng = targetLng
                    tvStatus.text = "Status: Within City (${String.format("%.1f", distanceKm)} km). Mocking ($activeTargetLat, $activeTargetLng)"
                    startMockLocation()
                } else {
                    tvStatus.text = "Status: City se bahar (${String.format("%.1f", distanceKm)} km > $radiusKm km). Mock blocked."
                    Toast.makeText(this, "Aap allowed city radius ke bahar hain.", Toast.LENGTH_LONG).show()
                }
            } else {
                tvStatus.text = "Status: Real location nahi mili. Google Maps khol kar ek baar location lock karein."
            }
        }
    }

    private fun startMockLocation() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, false, false, false, true, true, true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, false, false, false, true, true, true,
                    1, 1
                )
            }
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            isMocking = true
            pushMockLoop()
        } catch (e: SecurityException) {
            tvStatus.text = "Status: Developer Options -> Select mock location app me is app ko chunein."
            Toast.makeText(this, "Developer Options me Mock Location App select karein!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            tvStatus.text = "Status: Error - ${e.localizedMessage}"
        }
    }

    private fun pushMockLoop() {
        if (!isMocking) return

        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = activeTargetLat
            longitude = activeTargetLng
            altitude = 80.0
            time = System.currentTimeMillis()
            accuracy = 3.0f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

        try {
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
        } catch (_: Exception) {
        }

        handler.postDelayed({
            pushMockLoop()
        }, 1000)
    }

    private fun stopMockLocation() {
        isMocking = false
        handler.removeCallbacksAndMessages(null)
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMockLocation()
    }
}
