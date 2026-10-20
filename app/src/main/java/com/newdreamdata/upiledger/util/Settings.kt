package com.newdreamdata.upiledger.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class Settings(private val context: Context) {
    private val SMS_AUTO = booleanPreferencesKey("sms_auto_capture")
    private val ONBOARDED = booleanPreferencesKey("onboarded")

    val smsAutoCaptureFlow: Flow<Boolean> = context.dataStore.data.map { it[SMS_AUTO] ?: true }
    suspend fun smsAutoCapture(): Boolean = smsAutoCaptureFlow.first()
    suspend fun setSmsAutoCapture(v: Boolean) { context.dataStore.edit { it[SMS_AUTO] = v } }

    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDED] ?: false }
    suspend fun setOnboarded() { context.dataStore.edit { it[ONBOARDED] = true } }
}
