package com.cognify.ui.clock

import android.app.TimePickerDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.cognify.databinding.FragmentClockBinding
import com.cognify.data.local.AppDatabase
import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.RepeatInterval
import com.cognify.notification.ReminderScheduler
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class ClockFragment : Fragment() {

    private var _binding: FragmentClockBinding? = null
    private val binding get() = _binding!!

    private val db by lazy {
        AppDatabase.getDatabase(requireContext().applicationContext)
    }
    private val dao by lazy { db.reminderDao() }

    private lateinit var alarmAdapter: AlarmAdapter
    
    private var customUriSelectionCallback: ((android.net.Uri?) -> Unit)? = null
    private val documentPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Ignore
            }
            customUriSelectionCallback?.invoke(uri)
        } else {
            customUriSelectionCallback?.invoke(null)
        }
    }

    // Timer state
    private var timerMs: Long = 300_000L // 5 mins default
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false

    // Stopwatch state
    private var stopwatchStartTime: Long = 0L
    private var stopwatchElapsedTime: Long = 0L
    private var isStopwatchRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val stopwatchRunnable = object : Runnable {
        override fun run() {
            if (isStopwatchRunning) {
                stopwatchElapsedTime = System.currentTimeMillis() - stopwatchStartTime
                updateStopwatchDisplay()
                handler.postDelayed(this, 30)
            }
        }
    }

    private val lapList = mutableListOf<String>()
    private lateinit var lapAdapter: LapAdapter

    private var activeAlarmsList: List<ReminderEntity> = emptyList()
    private val nextAlarmRunnable = object : Runnable {
        override fun run() {
            if (::alarmAdapter.isInitialized) {
                alarmAdapter.notifyItemRangeChanged(0, alarmAdapter.itemCount, "TICK")
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        setupTabs()
        setupAlarmSection()
        setupTimerSection()
        setupStopwatchSection()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutAlarm.visibility = View.VISIBLE
                        binding.layoutTimer.visibility = View.GONE
                        binding.layoutStopwatch.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutAlarm.visibility = View.GONE
                        binding.layoutTimer.visibility = View.VISIBLE
                        binding.layoutStopwatch.visibility = View.GONE
                    }
                    2 -> {
                        binding.layoutAlarm.visibility = View.GONE
                        binding.layoutTimer.visibility = View.GONE
                        binding.layoutStopwatch.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupAlarmSection() {
        alarmAdapter = AlarmAdapter(
            onCheckChanged = { reminder, isChecked ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val status = if (isChecked) ReminderStatus.SCHEDULED else ReminderStatus.CANCELLED
                    val updated = reminder.copy(status = status, updatedAt = System.currentTimeMillis())
                    dao.updateReminder(updated)
                    if (isChecked) {
                        ReminderScheduler.scheduleReminder(requireContext(), updated)
                    } else {
                        ReminderScheduler.cancelReminder(requireContext(), updated.id, updated.notificationId)
                    }
                }
            },
            onDelete = { reminder ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    dao.deleteReminderById(reminder.id, userId)
                    ReminderScheduler.cancelReminder(requireContext(), reminder.id, reminder.notificationId)
                }
            },
            onClick = { reminder ->
                val cal = Calendar.getInstance().apply { timeInMillis = reminder.dateTime }
                showAlarmDialog(reminder, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
        )

        binding.rvAlarms.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alarmAdapter
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            dao.getByType(ReminderType.GENERAL.name, userId).collectLatest { alarms ->
                activeAlarmsList = alarms.filter { it.status == ReminderStatus.SCHEDULED }
                withContext(Dispatchers.Main) {
                    alarmAdapter.submitList(alarms)
                }
            }
        }

        binding.btnSetAlarm.setOnClickListener {
            if (!com.cognify.notification.NotificationHelper.hasAlarmPermissions(requireContext())) {
                com.cognify.notification.NotificationHelper.requestAlarmPermissions(requireContext())
                return@setOnClickListener
            }
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                showAlarmDialog(null, selectedHour, selectedMinute)
            }, hour, minute, false).show()
        }
    }

    private fun showAlarmDialog(existingReminder: ReminderEntity?, initialHour: Int, initialMinute: Int) {
        var currentHour = initialHour
        var currentMinute = initialMinute
        var selectedCustomUri: String? = existingReminder?.customSoundUri

        val view = LayoutInflater.from(requireContext()).inflate(com.cognify.R.layout.dialog_alarm_options, null)
        val tvEditTime = view.findViewById<android.widget.TextView>(com.cognify.R.id.tvEditTime)
        val etLabel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.cognify.R.id.etAlarmLabel)
        val spinnerRepeat = view.findViewById<android.widget.Spinner>(com.cognify.R.id.spinnerRepeat)
        val spinnerSound = view.findViewById<android.widget.Spinner>(com.cognify.R.id.spinnerSound)
        val spinnerSnooze = view.findViewById<android.widget.Spinner>(com.cognify.R.id.spinnerSnooze)
        val cbVibrate = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(com.cognify.R.id.cbVibrate)
        val cbSkipHolidays = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(com.cognify.R.id.cbSkipHolidays)

        val repeatOptions = arrayOf(getString(com.cognify.R.string.ui_repeat_once), getString(com.cognify.R.string.ui_repeat_every_day), getString(com.cognify.R.string.ui_repeat_mwf))
        val repeatAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, repeatOptions)
        spinnerRepeat.adapter = repeatAdapter

        val soundIds = arrayOf("alarm_default", "alarm_classic", "alarm_gentle", "alarm_beep", "alarm_digital", "custom", "silent")
        val soundNames = arrayOf(getString(com.cognify.R.string.ui_alarm_default), getString(com.cognify.R.string.ui_alarm_classic), getString(com.cognify.R.string.ui_alarm_gentle), getString(com.cognify.R.string.ui_alarm_beep), getString(com.cognify.R.string.ui_alarm_digital), getString(com.cognify.R.string.ui_cat_custom), getString(com.cognify.R.string.ui_alarm_silent))
        val soundAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, soundNames)
        spinnerSound.adapter = soundAdapter

        spinnerSound.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            var initialized = false
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                if (!initialized) {
                    initialized = true
                    return
                }
                if (soundIds[position] == "custom") {
                    customUriSelectionCallback = { uri ->
                        if (uri != null) {
                            selectedCustomUri = uri.toString()
                        }
                    }
                    documentPickerLauncher.launch(arrayOf("audio/*"))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val snoozeValues = intArrayOf(5, 10, 15, 20, 30)
        val snoozeNames = snoozeValues.map { resources.getQuantityString(com.cognify.R.plurals.ui_minutes, it, it) }.toTypedArray()
        val snoozeAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, snoozeNames)
        spinnerSnooze.adapter = snoozeAdapter

        // Default values
        spinnerSnooze.setSelection(1) // 10 minutes default

        val is24Hour = android.text.format.DateFormat.is24HourFormat(requireContext())
        val updateTimeText = {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, currentHour)
                set(Calendar.MINUTE, currentMinute)
            }
            val format = if (is24Hour) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) else java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            tvEditTime.text = format.format(cal.time)
        }
        updateTimeText()

        tvEditTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                currentHour = selectedHour
                currentMinute = selectedMinute
                updateTimeText()
            }, currentHour, currentMinute, is24Hour).show()
        }

        if (existingReminder != null) {
            etLabel.setText(existingReminder.title)
            cbVibrate.isChecked = existingReminder.isVibrationEnabled
            cbSkipHolidays.isChecked = existingReminder.skipHolidays
            val soundIdx = soundIds.indexOf(existingReminder.soundId)
            if (soundIdx >= 0) spinnerSound.setSelection(soundIdx)
            val snoozeIdx = snoozeValues.indexOf(existingReminder.snoozeDurationMinutes)
            if (snoozeIdx >= 0) spinnerSnooze.setSelection(snoozeIdx)
            val repeatIdx = when (existingReminder.repeatInterval) {
                RepeatInterval.DAILY -> 1
                else -> {
                    if (existingReminder.selectedWeekdays == "2,4,6") 2 else 0
                }
            }
            spinnerRepeat.setSelection(repeatIdx)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (existingReminder == null) getString(com.cognify.R.string.ui_new_alarm) else getString(com.cognify.R.string.ui_edit_alarm))
            .setView(view)
            .setPositiveButton(getString(com.cognify.R.string.ui_save)) { _, _ ->
                val alarmTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, currentHour)
                    set(Calendar.MINUTE, currentMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                val label = etLabel.text.toString().trim()
                val repeatMode = repeatOptions[spinnerRepeat.selectedItemPosition]
                val selectedSoundId = soundIds[spinnerSound.selectedItemPosition]
                val selectedSnooze = snoozeValues[spinnerSnooze.selectedItemPosition]
                val vibrate = cbVibrate.isChecked
                val skipHolidays = cbSkipHolidays.isChecked

                val repeatInterval = if (spinnerRepeat.selectedItemPosition == 1) RepeatInterval.DAILY else RepeatInterval.NONE
                val selectedWeekdays = if (spinnerRepeat.selectedItemPosition == 2) "2,4,6" else ""

                val newAlarm = ReminderEntity(
                    id = existingReminder?.id ?: java.util.UUID.randomUUID().toString(),
                    title = label,
                    description = "",
                    dateTime = alarmTime.timeInMillis,
                    type = ReminderType.GENERAL,
                    status = ReminderStatus.SCHEDULED,
                    priority = com.cognify.data.model.ReminderPriority.MEDIUM,
                    repeatInterval = repeatInterval,
                    notificationId = existingReminder?.notificationId ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                    linkedItemId = null,
                    linkedItemType = com.cognify.data.model.LinkedItemType.NONE,
                    aiSuggested = false,
                    lastReminderAnalysis = null,
                    createdAt = existingReminder?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = com.cognify.data.model.SyncStatus.PENDING,
                    lastSyncedAt = null,
                    isEnabled = true,
                    selectedWeekdays = selectedWeekdays,
                    soundId = selectedSoundId,
                    customSoundUri = if (selectedSoundId == "custom") selectedCustomUri else null,
                    isVibrationEnabled = vibrate,
                    snoozeDurationMinutes = selectedSnooze,
                    skipHolidays = skipHolidays,
                    userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                )

                lifecycleScope.launch(Dispatchers.IO) {
                    if (existingReminder != null) {
                        dao.updateReminder(newAlarm)
                        ReminderScheduler.cancelReminder(requireContext(), existingReminder.id, existingReminder.notificationId)
                    } else {
                        dao.insertReminder(newAlarm)
                    }
                    ReminderScheduler.scheduleReminder(requireContext(), newAlarm)
                }
            }
            .setNegativeButton(getString(com.cognify.R.string.ui_cancel), null)
            .show()
    }

    private fun setupTimerSection() {
        loadTimerState()

        binding.btnTimerPlus1m.setOnClickListener {
            timerMs += 60_000L
            updateTimerDisplay()
        }
        binding.btnTimerPlus5m.setOnClickListener {
            timerMs += 300_000L
            updateTimerDisplay()
        }
        binding.btnTimerPlus10m.setOnClickListener {
            timerMs += 600_000L
            updateTimerDisplay()
        }

        binding.btnSetCustomTimer.setOnClickListener {
            val h = binding.etCustomHours.text.toString().toLongOrNull() ?: 0L
            val m = binding.etCustomMinutes.text.toString().toLongOrNull() ?: 0L
            val s = binding.etCustomSeconds.text.toString().toLongOrNull() ?: 0L
            
            val totalMs = (h * 3600000L) + (m * 60000L) + (s * 1000L)
            if (totalMs > 0) {
                timerMs = totalMs
                updateTimerDisplay()
                binding.etCustomHours.text?.clear()
                binding.etCustomMinutes.text?.clear()
                binding.etCustomSeconds.text?.clear()
            }
        }

        binding.btnTimerStart.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.btnTimerReset.setOnClickListener {
            resetTimer()
        }
    }

    private fun startTimer() {
        if (timerMs <= 0) return
        countDownTimer?.cancel()
        
        val endTime = System.currentTimeMillis() + timerMs
        
        countDownTimer = object : CountDownTimer(timerMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerMs = millisUntilFinished
                updateTimerDisplay()
                saveTimerState(endTime)
            }

            override fun onFinish() {
                isTimerRunning = false
                timerMs = 0
                binding.btnTimerStart.text = "Start"
                saveTimerState(0L)
            }
        }.start()
        isTimerRunning = true
        binding.btnTimerStart.text = "Pause"
        saveTimerState(endTime)
        scheduleTimerAlarm(endTime)
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        binding.btnTimerStart.text = "Start"
        saveTimerState(0L)
        cancelTimerAlarm()
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        timerMs = 300_000L
        updateTimerDisplay()
        binding.btnTimerStart.text = "Start"
        saveTimerState(0L)
        cancelTimerAlarm()
    }
    
    private fun saveTimerState(endTime: Long) {
        if (context == null) return
        val prefs = requireContext().getSharedPreferences("timer_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("timer_ms", timerMs)
            putBoolean("is_timer_running", isTimerRunning)
            putLong("timer_end_time", endTime)
        }.apply()
    }

    private fun loadTimerState() {
        val prefs = requireContext().getSharedPreferences("timer_prefs", android.content.Context.MODE_PRIVATE)
        isTimerRunning = prefs.getBoolean("is_timer_running", false)
        if (isTimerRunning) {
            val endTime = prefs.getLong("timer_end_time", 0L)
            timerMs = endTime - System.currentTimeMillis()
            if (timerMs <= 0) {
                timerMs = 0
                isTimerRunning = false
                updateTimerDisplay()
                binding.btnTimerStart.text = "Start"
            } else {
                startTimer()
            }
        } else {
            timerMs = prefs.getLong("timer_ms", 300_000L)
            updateTimerDisplay()
        }
    }
    
    private val TIMER_REMINDER_ID = "cognify_timer_id"
    private fun scheduleTimerAlarm(endTime: Long) {
        val timerReminder = ReminderEntity(
            id = TIMER_REMINDER_ID,
            title = "Timer",
            description = "Timer Finished",
            dateTime = endTime,
            type = ReminderType.GENERAL,
            status = ReminderStatus.SCHEDULED,
            priority = com.cognify.data.model.ReminderPriority.HIGH,
            repeatInterval = RepeatInterval.NONE,
            notificationId = 999999,
            linkedItemId = null,
            linkedItemType = com.cognify.data.model.LinkedItemType.NONE,
            aiSuggested = false,
            lastReminderAnalysis = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = com.cognify.data.model.SyncStatus.PENDING,
            lastSyncedAt = null,
            userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
        lifecycleScope.launch(Dispatchers.IO) {
            dao.insertReminder(timerReminder)
            ReminderScheduler.scheduleReminder(requireContext(), timerReminder)
        }
    }
    
    private fun cancelTimerAlarm() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            dao.deleteReminderById(TIMER_REMINDER_ID, userId)
            ReminderScheduler.cancelReminder(requireContext(), TIMER_REMINDER_ID, 999999)
        }
    }

    private fun updateTimerDisplay() {
        val totalSecs = timerMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        binding.tvTimerDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    private fun setupStopwatchSection() {
        lapAdapter = LapAdapter()
        binding.rvLaps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = lapAdapter
        }

        binding.btnStopwatchStart.setOnClickListener {
            if (isStopwatchRunning) {
                pauseStopwatch()
            } else {
                startStopwatch()
            }
        }

        binding.btnStopwatchReset.setOnClickListener {
            resetStopwatch()
        }

        binding.btnStopwatchLap.setOnClickListener {
            if (isStopwatchRunning) {
                lapList.add(binding.tvStopwatchDisplay.text.toString())
                lapAdapter.submitList(lapList.toList())
                binding.rvLaps.scrollToPosition(lapList.size - 1)
            }
        }
    }

    private fun startStopwatch() {
        stopwatchStartTime = System.currentTimeMillis() - stopwatchElapsedTime
        isStopwatchRunning = true
        handler.post(stopwatchRunnable)
        binding.btnStopwatchStart.text = "Pause"
    }

    private fun pauseStopwatch() {
        isStopwatchRunning = false
        handler.removeCallbacks(stopwatchRunnable)
        binding.btnStopwatchStart.text = "Start"
    }

    private fun resetStopwatch() {
        isStopwatchRunning = false
        handler.removeCallbacks(stopwatchRunnable)
        stopwatchElapsedTime = 0L
        lapList.clear()
        lapAdapter.submitList(emptyList())
        updateStopwatchDisplay()
        binding.btnStopwatchStart.text = "Start"
    }

    private fun updateStopwatchDisplay() {
        val mins = (stopwatchElapsedTime / 60000) % 60
        val secs = (stopwatchElapsedTime / 1000) % 60
        val millis = (stopwatchElapsedTime % 1000) / 10
        binding.tvStopwatchDisplay.text = String.format(Locale.getDefault(), "%02d:%02d.%02d", mins, secs, millis)
    }



    override fun onResume() {
        super.onResume()
        handler.post(nextAlarmRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(nextAlarmRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        handler.removeCallbacks(stopwatchRunnable)
        handler.removeCallbacks(nextAlarmRunnable)
        _binding = null
    }
}
