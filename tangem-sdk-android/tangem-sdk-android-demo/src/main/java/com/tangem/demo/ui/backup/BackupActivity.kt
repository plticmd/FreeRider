package com.tangem.demo.ui.backup

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.demo.asFlow
import com.tangem.demo.postUi
import com.tangem.operations.backup.BackupService
import com.tangem.operations.backup.ResetBackupCommand
import com.tangem.sdk.extensions.init
import com.tangem.tangem_demo.R
import kotlinx.android.synthetic.main.activity_backup.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.coroutines.CoroutineContext

class BackupActivity : AppCompatActivity() {

    lateinit var backupService: BackupService
    lateinit var tangemSdk: TangemSdk

    private val mainCoroutineContext: CoroutineContext
        get() = Job() + Dispatchers.Main + initCoroutineExceptionHandler()
    val mainScope = CoroutineScope(mainCoroutineContext)

    private fun initCoroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val exceptionAsString: String = sw.toString()
            Log.e("Coroutine", exceptionAsString)
            throw throwable
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        tangemSdk = TangemSdk.init(this)
        backupService = BackupService.init(tangemSdk, this)
        backupService.discardSavedBackup()

        btn_add_backup_card.setOnClickListener {
            backupService.addBackupCard { result ->
                postUi {
                    when (result) {
                        is CompletionResult.Success -> {
                            tv_add_backup_card.text = "${backupService.addedBackupCardsCount} added."
                        }
                        is CompletionResult.Failure -> Unit
                    }
                }
            }
        }

        btn_add_origin_card.setOnClickListener {
            backupService.readPrimaryCard { result ->
                postUi {
                    when (result) {
                        is CompletionResult.Success -> {
                            tv_add_origin_card.text = "Good! You've scanned origin card."
                        }
                        is CompletionResult.Failure -> Unit
                    }
                }
            }
        }

        til_access_code.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && !et_access_code.text.isNullOrBlank()) {
                backupService.setAccessCode(et_access_code.text.toString())
            }
        }

        et_access_code.asFlow().debounce(timeoutMillis = 400)
            .filter { it.isNotBlank() }
            .onEach {
                backupService.setAccessCode(it)
            }
            .launchIn(mainScope)

        btn_start.setOnClickListener {
            if (backupService.currentState != BackupService.State.Preparing) {
                backupService.proceedBackup {
                }
            }
        }

        btn_reset.setOnClickListener {
            tangemSdk.startSessionWithRunnable(ResetBackupCommand()) {
            }
        }
    }
}
