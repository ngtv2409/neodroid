package com.ngtv.neodroid

import android.app.Application
import com.ngtv.neodroid.security.KeystoreKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class NeodroidApplication : Application() {
    lateinit var keystoreKeyManager: KeystoreKeyManager
        private set

    override fun onCreate() {
        super.onCreate()
        setupBouncyCastle()

        keystoreKeyManager = KeystoreKeyManager.getInstance(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            keystoreKeyManager.initialize()
        }
    }

    private fun setupBouncyCastle() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
