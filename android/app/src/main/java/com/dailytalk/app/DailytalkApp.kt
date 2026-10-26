package com.dailytalk.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class DailytalkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase is configured from build-time values (no google-services.json is needed or committed).
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }
        Notifier.createChannels(this)
    }
}
