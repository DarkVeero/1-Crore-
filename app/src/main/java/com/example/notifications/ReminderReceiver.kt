package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(ReminderScheduler.EXTRA_SLOT, 1)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.evaluateAndSendReminder(
                    context = context,
                    slotOverride = slot,
                    allowCatchUp = false
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
