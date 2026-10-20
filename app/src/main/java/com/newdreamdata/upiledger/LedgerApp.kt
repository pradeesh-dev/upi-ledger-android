package com.newdreamdata.upiledger

import android.app.Application
import com.newdreamdata.upiledger.data.AppDatabase
import com.newdreamdata.upiledger.data.Repository
import com.newdreamdata.upiledger.util.Notifications
import com.newdreamdata.upiledger.util.Settings

class LedgerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: Repository by lazy { Repository(database) }
    val settings: Settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }
}
