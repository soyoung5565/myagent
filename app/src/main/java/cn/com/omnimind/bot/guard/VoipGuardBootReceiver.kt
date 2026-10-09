package cn.com.omnimind.bot.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class VoipGuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val act = intent.action
        Log.i("[VoipGuardBoot]", "onReceive $act -> starting VoipGuardService")
        if (act == Intent.ACTION_BOOT_COMPLETED || act == Intent.ACTION_MY_PACKAGE_REPLACED || act == Intent.ACTION_PACKAGE_REPLACED) {
            VoipGuardService.start(context)
        }
    }
}
