package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class SmartKitchenApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            val initializedApp = if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                Firebase.initialize(this)
            }

            if (initializedApp != null) {
                val appCheckProviderFactory = if (BuildConfig.DEBUG) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
                Firebase.appCheck.installAppCheckProviderFactory(appCheckProviderFactory)
                Log.d("SmartKitchenApplication", "Firebase App Check başlatıldı (DEBUG: ${BuildConfig.DEBUG})")
            } else {
                Log.i("SmartKitchenApplication", "Firebase yapılandırması bulunamadı; doğrudan Gemini REST API kullanılacak.")
            }
        } catch (e: Exception) {
            Log.i("SmartKitchenApplication", "Firebase opsiyonel başlatma atlandı: ${e.message}")
        }
    }
}
