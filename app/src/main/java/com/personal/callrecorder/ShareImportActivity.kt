package com.personal.callrecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.personal.callrecorder.imports.RecordingImporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible activity that receives a shared audio recording (ACTION_SEND) — e.g.
 * from Google Phone → Share → PersonalCallRecorder — and imports it.
 *
 * The incoming URI is a temporary read grant from the sharer's own FileProvider;
 * we only read it via ContentResolver. No private storage, no special access.
 */
@AndroidEntryPoint
class ShareImportActivity : ComponentActivity() {

    @Inject lateinit var importer: RecordingImporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent?.data
            else -> intent?.data
        }

        if (uri == null) {
            Toast.makeText(this, "No recording to import", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val summary = importer.importFromUri(uri)
            Toast.makeText(this@ShareImportActivity, summary.message(), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
