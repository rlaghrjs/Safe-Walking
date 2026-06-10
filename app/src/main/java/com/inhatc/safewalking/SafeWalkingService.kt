package com.inhatc.safewalking

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.sqrt
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeWalkingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    // 센서 및 상태 값들
    private var accX = 0f; private var accY = 0f; private var accZ = 0f
    private val accelMagnitudes = ArrayDeque<Float>(20)
    private var isWalking = false; private var isScreenOn = false; private var isSmombie = false; private var isLookingAtPhone = false
    private var pitch = 0.0

    private var lastWarningTime = 0L
    private val warningCooldown = 5000L
    private var smombieStartTime = 0L
    private var smombieDuration = 0L
    private var riskLevel = "안전"
    private var smombieCount = 0
    private var lastSmombieState = false
    private var lastLookingAtPhoneTime = 0L
    private val lookingGracePeriod = 1000L

    private val CHANNEL_ID = "SafeWalkingChannel"
    private val NOTIFICATION_ID = 1

    // UI 과부하 방지 주기 (0.2초)
    private var lastUiUpdateTime = 0L
    private val uiUpdateInterval = 200L

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private var isOverlayShowing = false

    private var stepSensor: Sensor? = null
    private var lastStepTime = 0L // 마지막으로 발걸음이 감지된 시간
    private val stepTimeout = 2000L // 3초 동안 걸음이 없으면 정지 상태로 간주

    private var hasCountedThisSession = false

    private var sessionWarningCount = 0 // 이번 스몸비 세션(한 번 연속해서 폰 보고 걸을 때) 동안 발생한 경고 수

    private var walkingStreakCount = 0   // 연속 걸음 유효 횟수 (오작동 방지 버퍼)
    private val REQUIRED_STREAK = 2      // 최소 2번 이상 발걸음 신호가 이어져야 실제 보행으로 인정

    // [공식 변수] 스몸비 유효 각도 범위 (논문 표준 데이터 반영)
    private val MIN_LOOKING_PITCH = 15.0
    private val MAX_LOOKING_PITCH = 60.0

    // [고도화 핵심] MainActivity와 통신하기 위한 실시간 데이터 전송 채널 (LiveData)
    companion object {
        data class UIState(
            val accX: Float, val accY: Float, val accZ: Float,
            val pitch: Double, val isWalking: Boolean, val isScreenOn: Boolean,
            val isLookingAtPhone: Boolean, val isSmombie: Boolean,
            val riskLevel: String, val smombieDuration: Long, val smombieCount: Int
        )
        val liveUiState = MutableLiveData<UIState>()
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // [추정 고도화] 걸음 감지 센서 가져오기
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        // 두 센서 모두 리스너 등록
        accelSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        stepSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) // 걸음은 조금 더 여유 있게 수집
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // 초기 상주 알림은 조용하게 시작
        val notification = buildNotification("안전 보행 감지 중", "주변을 살피며 걸어주세요.", false)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        // 1. 걸음 감지 센서 신호가 오면 마지막 걸음 시간을 현재 시간으로 갱신
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                lastStepTime = System.currentTimeMillis()
            }
        }

        // 2. 가속도 센서 연산
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accX = event.values[0]; accY = event.values[1]; accZ = event.values[2]
            val magnitude = sqrt(accX * accX + accY * accY + accZ * accZ)

            detectWalking(magnitude)
            detectPhoneAngle()
            detectSmombie()

            // UI 전송
            val now = System.currentTimeMillis()
            if (now - lastUiUpdateTime > uiUpdateInterval) {
                sendDataToUI()
                lastUiUpdateTime = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun detectWalking(magnitude: Float) {
        accelMagnitudes.addLast(magnitude)
        if (accelMagnitudes.size > 40) accelMagnitudes.removeFirst()
        if (accelMagnitudes.size < 40) return

        val now = System.currentTimeMillis()

        val mean = accelMagnitudes.average().toFloat()
        val variance = accelMagnitudes
            .map { (it - mean) * (it - mean) }
            .average()
            .toFloat()

        val std = kotlin.math.sqrt(variance)

        val isStepDetected = (now - lastStepTime) < stepTimeout

        // 너무 큰 흔들림은 "손으로 폰 흔들기" 가능성이 높음
        val isTooMuchShake = std > 5.0f

        // 최종 보행 판단
        isWalking = isStepDetected && !isTooMuchShake
    }

    private fun detectPhoneAngle() {
        pitch = Math.toDegrees(atan2(accY.toDouble(), accZ.toDouble()))
        val now = System.currentTimeMillis()
        val rawLooking = pitch in 15.0..65.0

        if (rawLooking) {
            lastLookingAtPhoneTime = now
            isLookingAtPhone = true
        } else {
            isLookingAtPhone = now - lastLookingAtPhoneTime < lookingGracePeriod
        }
    }

    private fun detectSmombie() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        val currentSmombieState = isWalking && isScreenOn && isLookingAtPhone
        val now = System.currentTimeMillis()

        if (currentSmombieState) {
            if (!isSmombie) {
                smombieStartTime = now
                hasCountedThisSession = false
                sessionWarningCount = 0 // 새 세션이 시작되면 세션 경고 횟수 리셋
            }
            smombieDuration = now - smombieStartTime
            isSmombie = true

            val oldRisk = riskLevel
            riskLevel = when {
                smombieDuration < 5000 -> "일반 사용"
                smombieDuration < 15000 -> "주의"
                smombieDuration < 30000 -> "위험"
                else -> "매우 위험"
            }

            if (smombieDuration >= 5000 && !hasCountedThisSession) {
                smombieCount++
                sessionWarningCount = 1 // 이번 세션에서 경고가 1회 충족됨을 기록
                hasCountedThisSession = true
            }

            if (oldRisk != riskLevel) {
                updateNotification("🚨 스몸비 위험 감지: $riskLevel", "지금 즉시 고개를 들고 전방을 확인하세요!", true)
            } else {
                updateNotification("🚨 스몸비 위험 감지: $riskLevel", "지금 즉시 고개를 들고 전방을 확인하세요!", false)
            }

            if (riskLevel == "위험" || riskLevel == "매우 위험") {
                showOverlay()
                triggerWarning()
            } else {
                removeOverlay()
            }
        } else {
            // [수정] 스몸비 상태였다가 방금 안전해진 순간! (세션 종료)
            if (isSmombie) {
                updateNotification("안전 보행 감지 중", "주변을 살피며 걸어주세요.", false)

                // ⭐️ 안전 구역으로 돌아온 순간, 이번 세션의 누적 시간과 경고 횟수를 DB에 영구 저장합니다.
                saveSmombieDataToDb(smombieDuration, sessionWarningCount)
            }
            removeOverlay()
            isSmombie = false; smombieStartTime = 0L; smombieDuration = 0L; riskLevel = "안전"
            hasCountedThisSession = false
        }
        lastSmombieState = isSmombie
    }

    private fun triggerWarning() {
        val now = System.currentTimeMillis()
        if (now - lastWarningTime < warningCooldown) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(500)
        }
        lastWarningTime = now
    }

    // UI 객체로 묶어서 전송
    private fun sendDataToUI() {
        val state = UIState(
            accX, accY, accZ, pitch, isWalking, isScreenOn,
            isLookingAtPhone, isSmombie, riskLevel, smombieDuration, smombieCount
        )
        liveUiState.postValue(state) // Main Thread 혹은 Worker Thread 안전하게 값 전달
    }

    private fun buildNotification(title: String, content: String, isHighImportance: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // [핵심 고도화] 위험 단계가 변경되어 팝업을 띄워야 할 때
        if (isHighImportance) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH) // 헤드업 알림 필수 조건 1

            // 헤드업 알림 필수 조건 2: 소리 또는 진동을 알림 객체에 직접 박아넣어야 합니다.
            // 시스템 기본 알림 소리 설정
            val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(defaultSoundUri)

            // 시스템 기본 진동 패턴 설정 (0초 대기, 0.5초 진동)
            builder.setVibrate(longArrayOf(0, 500))
        } else {
            // 평소 일반 보행이나 정지 상태일 때는 조용히 상주만 하도록 설정
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
        }

        return builder.build()
    }

    private fun updateNotification(title: String, content: String, isHighImportance: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content, isHighImportance))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // [수정] 채널의 중요도를 IMPORTANCE_DEFAULT에서 IMPORTANCE_HIGH(높음)로 변경합니다.
            // 이 설정이 되어야 안드로이드가 화면 상단에 팝업(헤드업)을 밀어 올립니다.
            val channel = NotificationChannel(
                CHANNEL_ID, "Safe Walking Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "스몸비 위험 경고를 위한 알림 채널입니다."
                enableVibration(true) // 채널 자체에 진동 허용
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        // 권한이 최종적으로 있는지 다시 한번 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 코드로 동적 레이아웃 생성 (XML 없이 깔끔하게 빨간색 반투명 가림막 만들기)
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater

        val textView = TextView(this).apply {
            text = "🚨\n전방 주시 요망!\n\n걸음을 멈추거나\n고개를 들어주세요."

            // [수정] 코틀린 정석 문법으로 변경
            setTextColor(android.graphics.Color.WHITE)
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#CCF44336")) // 80% 반투명 빨간색
        }

        overlayView = textView

        // 최신 안드로이드 버전에 맞는 오버레이 윈도우 파라미터 설정
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            // 터치 이벤트를 이 뷰가 먹어서 뒤의 화면(유튜브 등)을 조작하지 못하게 방어함
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        // 핸들러를 이용해 메인(UI) 스레드에서 화면에 레이어 배치
        Handler(Looper.getMainLooper()).post {
            try {
                windowManager?.addView(overlayView, params)
                isOverlayShowing = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeOverlay() {
        if (!isOverlayShowing || overlayView == null) return

        Handler(Looper.getMainLooper()).post {
            try {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // SafeWalkingService.kt 내부의 saveSmombieDataToDb 함수 교체
    private fun saveSmombieDataToDb(sessionDuration: Long, sessionWarningCount: Int) {
        if (sessionDuration <= 0 && sessionWarningCount <= 0) return

        Thread {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.smombieDao()
            val todayStr = getTodayDateString()

            // ⭐️ 현재 시각에서 지속 시간을 빼서 '스몸비가 시작된 시각'을 포맷팅합니다.
            val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val startTimeStr = timeSdf.format(Date(System.currentTimeMillis() - sessionDuration))

            // 개별 세션 데이터 생성
            val newSession = SmombieSession(
                date = todayStr,
                startTime = startTimeStr,
                duration = sessionDuration,
                warningCount = sessionWarningCount,
                riskLevel = riskLevel // 세션 종료 시점의 위험 등급
            )

            // 데이터베이스에 독립된 레코드로 삽입
            dao.insertSession(newSession)
        }.start()
    }
}