package com.flivoro.tile8auncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion

/**
 * Process-level shell signals that must be known before an Activity resume is
 * composed. ACTION_USER_PRESENT marks the next Start entrance as the longer
 * Windows 8.1 session/sign-in motion. The marker expires automatically, so
 * ordinary Home/Back returns keep the compact, stable entrance.
 */
class Tile8Application : Application() {
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                StartEntranceMotion.markSignInWindow()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // A fresh launcher process is equivalent to the initial/session reveal.
        StartEntranceMotion.markSignInWindow()

        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            // USER_PRESENT is sent by Android itself. On recent Android versions
            // a NOT_EXPORTED dynamic receiver may miss privileged system senders.
            // This receiver performs no privileged action and consumes no payload;
            // it only switches the next cosmetic Start entrance profile.
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
