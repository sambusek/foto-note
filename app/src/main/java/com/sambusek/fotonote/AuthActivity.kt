package com.sambusek.fotonote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Riceve dal browser la risposta dell'accesso Microsoft (com.sambusek.fotonote://auth?code=…),
 * completa il collegamento dell'account e torna alle Impostazioni.
 */
class AuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data ?: return finish()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { OneDrive.completeSignIn(applicationContext, data) } }
            r.onSuccess {
                Backup.reschedule(applicationContext)
                Backup.changed(applicationContext)
                Toast.makeText(this@AuthActivity, "Account collegato: ${OneDrive.account}", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@AuthActivity, "Collegamento non riuscito: ${it.message}", Toast.LENGTH_LONG).show()
            }
            startActivity(
                Intent(this@AuthActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }
}
