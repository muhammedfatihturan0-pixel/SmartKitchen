package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class SmartKitchenApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // Firebase initialization
            Firebase.initialize(this)

            // Debug / Release ayrımı ile App Check başlatma
            val appCheckProviderFactory = if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }

            Firebase.appCheck.installAppCheckProviderFactory(appCheckProviderFactory)
            Log.d("SmartKitchenApplication", "Firebase App Check başarıyla başlatıldı (DEBUG: ${BuildConfig.DEBUG})")
        } catch (e: Exception) {
            Log.e("SmartKitchenApplication", "Firebase App Check başlatılırken hata oluştu: ${e.message}", e)
        }
    }
}
