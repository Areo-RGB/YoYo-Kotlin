package com.example.yoyo

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Durable storage for the encoded roster and session history. */
interface StateStore {
    suspend fun read(): String?
    suspend fun write(value: String)
}

class InMemoryStateStore(var value: String? = null) : StateStore {
    override suspend fun read(): String? = value
    override suspend fun write(value: String) {
        this.value = value
    }
}

class SharedPreferencesStateStore(context: Context) : StateStore {
    private val prefs = context.applicationContext.getSharedPreferences("yoyo", Context.MODE_PRIVATE)

    override suspend fun read(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY, null) }

    // Already off the main thread; commit so a failed write reaches the caller.
    @SuppressLint("ApplySharedPref")
    override suspend fun write(value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY, value).commit()
        Unit
    }

    private companion object {
        const val KEY = "yoyo-native-state-v1"
    }
}
