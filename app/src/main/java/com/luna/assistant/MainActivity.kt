package com.luna.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnBattery: Button

    private val neededPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnToggle = findViewById(R.id.btnToggle)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnBattery = findViewById(R.id.btnBattery)

        setupLanguage(findViewById(R.id.rgLanguage))

        btnToggle.setOnClickListener {
            if (LunaService.isRunning) stopService(Intent(this, LunaService::class.java))
            else startLuna()
            tvStatus.postDelayed({ refresh() }, 300)
        }

        findViewById<Button>(R.id.btnTalk).setOnClickListener {
            if (!LunaService.isRunning) startLuna()
            if (LunaService.isRunning || hasMic()) {
                startService(Intent(this, LunaService::class.java).setAction(LunaService.ACTION_LISTEN_NOW))
            }
        }

        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            permissionLauncher.launch(neededPermissions)
        }

        btnOverlay.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        btnBattery.setOnClickListener { askToIgnoreBatteryOptimisation() }
    }

    override fun onResume() {
        super.onResume()
        LunaService.statusListener = { tvStatus.text = it; updateToggle() }
        refresh()
    }

    override fun onPause() {
        LunaService.statusListener = null
        super.onPause()
    }

    private fun startLuna() {
        if (!hasMic()) {
            Toast.makeText(this, "Please tap \"Grant permissions\" first", Toast.LENGTH_LONG).show()
            permissionLauncher.launch(neededPermissions)
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, LunaService::class.java))
    }

    private fun setupLanguage(group: RadioGroup) {
        val ids = mapOf(
            LunaLanguage.ENGLISH to R.id.rbEnglish,
            LunaLanguage.YORUBA to R.id.rbYoruba,
            LunaLanguage.IGBO to R.id.rbIgbo,
            LunaLanguage.HAUSA to R.id.rbHausa,
        )
        group.check(ids.getValue(Prefs.language(this)))
        group.setOnCheckedChangeListener { _, checkedId ->
            val lang = ids.entries.firstOrNull { it.value == checkedId }?.key ?: return@setOnCheckedChangeListener
            Prefs.setLanguage(this, lang)
            Toast.makeText(this, "Luna will now listen in ${lang.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        tvStatus.text = LunaService.lastStatus
        updateToggle()
        btnOverlay.text = if (Settings.canDrawOverlays(this)) "2. Allow Luna to open apps ✓" else "2. Allow Luna to open apps"
        btnBattery.text = if (isIgnoringBattery()) "3. Keep Luna running in the background ✓"
        else "3. Keep Luna running in the background"
    }

    private fun updateToggle() {
        btnToggle.text = if (LunaService.isRunning) "Stop Luna" else "Start Luna"
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBattery(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    @SuppressLint("BatteryLife")
    private fun askToIgnoreBatteryOptimisation() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
