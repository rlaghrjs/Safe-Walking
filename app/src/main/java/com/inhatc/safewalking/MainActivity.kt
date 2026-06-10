package com.inhatc.safewalking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //////////////// 디버깅용 임시코드/////////////////
        insertDummyDataOnce()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 첫 실행 시 기본 화면을 홈 화면으로 설정
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // 탭 버튼 클릭 리스너 설정
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_report -> ReportFragment()
                else -> HomeFragment()
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, selectedFragment)
                .commit()

            true
        }

        // 권한 체크 및 백그라운드 서비스 시작
        checkOverlayPermission()

    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                checkActivityRecognitionPermission()
            }
        } else {
            checkActivityRecognitionPermission()
        }
    }

    private fun checkActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 103)
            } else {
                startSafeWalkingService()
            }
        } else {
            startSafeWalkingService()
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 102)
            } else {
                checkNotificationPermission()
            }
        } else {
            checkNotificationPermission()
        }
    }

    private fun startSafeWalkingService() {
        val intent = Intent(this, SafeWalkingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkActivityRecognitionPermission()
                } else {
                    Toast.makeText(this, "알림 권한을 허용해야 백그라운드 감지가 작동합니다.", Toast.LENGTH_SHORT).show()
                }
            }
            103 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSafeWalkingService()
                } else {
                    Toast.makeText(this, "신체 활동 권한을 허용해야 걸음 감지가 정상 작동합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
                checkNotificationPermission()
            } else {
                Toast.makeText(this, "오버레이 권한이 없으면 화면 차단 경고가 작동하지 않습니다.", Toast.LENGTH_SHORT).show()
                checkNotificationPermission()
            }
        }
    }

    ////디버깅용 임시함수/////
    private fun insertDummyDataOnce() {
        Thread {
            val dao = AppDatabase.getDatabase(this).smombieDao()

            // 이미 오늘 데이터가 있으면 중복 삽입 방지
            if (dao.getSessionsByDate("2026-06-11").isNotEmpty()) return@Thread

            val dummySessions = listOf(
                SmombieSession(date="2026-06-05", startTime="08:12:10", duration=12000, warningCount=1, riskLevel="주의"),
                SmombieSession(date="2026-06-05", startTime="18:30:25", duration=31000, warningCount=2, riskLevel="위험"),

                SmombieSession(date = "2026-06-06", startTime="09:05:11", duration=8000, warningCount=1, riskLevel="주의"),

                SmombieSession(date="2026-06-07", startTime="13:22:40", duration=22000, warningCount=1, riskLevel="위험"),
                SmombieSession(date="2026-06-07", startTime="19:41:03", duration=43000, warningCount=3, riskLevel="매우 위험"),

                SmombieSession(date="2026-06-08", startTime="07:55:30", duration=15000, warningCount=1, riskLevel="주의"),
                SmombieSession(date="2026-06-08", startTime="12:11:09", duration=28000, warningCount=2, riskLevel="위험"),

                SmombieSession(date="2026-06-09", startTime="10:25:19", duration=9000, warningCount=1, riskLevel="주의"),

                SmombieSession(date="2026-06-10", startTime="11:33:45", duration=18000, warningCount=1, riskLevel="주의"),
                SmombieSession(date="2026-06-10", startTime="20:04:12", duration=52000, warningCount=3, riskLevel="매우 위험"),

                SmombieSession(date="2026-06-11", startTime="09:12:15", duration=12000, warningCount=1, riskLevel="주의"),
                SmombieSession(date="2026-06-11", startTime="14:25:01", duration=28000, warningCount=2, riskLevel="위험"),
                SmombieSession(date="2026-06-11", startTime="20:01:48", duration=35000, warningCount=3, riskLevel="매우 위험")
            )

            dummySessions.forEach {
                dao.insertSession(it)
            }
        }.start()
    }
}