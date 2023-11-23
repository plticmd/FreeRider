package com.tangem.common.core

import com.tangem.Log
import com.tangem.Message
import com.tangem.SessionViewDelegate
import com.tangem.ViewDelegateMessage
import com.tangem.WrongValueType
import com.tangem.common.CardIdFormatter
import com.tangem.common.CompletionResult
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.Apdu
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.EncryptionMode
import com.tangem.common.doOnFailure
import com.tangem.common.doOnResult
import com.tangem.common.doOnSuccess
import com.tangem.common.extensions.VoidCallback
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.json.JSONRPCConverter
import com.tangem.common.json.JSONRPCLinker
import com.tangem.common.nfc.CardReader
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.usersCode.UserCodeRepository
import com.tangem.crypto.EncryptionHelper
import com.tangem.crypto.pbkdf2Hash
import com.tangem.operations.OpenSessionCommand
import com.tangem.operations.PreflightReadMode
import com.tangem.operations.PreflightReadTask
import com.tangem.operations.read.ReadCommand
import com.tangem.operations.resetcode.ResetCodesController
import com.tangem.operations.resetcode.ResetPinService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Allows interaction with Tangem cards. Should be opened before sending commands.
 *
 * @property environment
 * @property reader  is an interface that is responsible for NFC connection and
 * transfer of data to and from the Tangem Card.
 * @property viewDelegate is an  interface that allows interaction with users and shows relevant UI.
 * @property cardId ID, Unique Tangem card ID number. If not null, the SDK will check that you the card
 * with which you tapped a phone has this [cardId] and SDK will return
 * the [TangemSdkError.WrongCardNumber] otherwise.
 * @property initialMessage A custom description that will be shown at the beginning of the NFC session.
 * If null, a default header and text body will be used.
 */
@Suppress("LargeClass")
class CardSession(
    cardId: String? = null,
    val viewDelegate: SessionViewDelegate,
    val environment: SessionEnvironment,
    val userCodeRepository: UserCodeRepository?,
    private val reader: CardReader,
    private val jsonRpcConverter: JSONRPCConverter,
    private val secureStorage: SecureStorage,
    private var initialMessage: ViewDelegateMessage? = null,
) {

    var cardId: String? = cardId
        private set
    var connectedTag: TagType? = null
        private set
    var state = CardSessionState.Inactive
        private set

    private var resetCodesController: ResetCodesController? = null

    val scope = CoroutineScope(Dispatchers.IO) + CoroutineExceptionHandler { _, throwable ->
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val exceptionAsString: String = sw.toString()
        Log.error { exceptionAsString }
        throw throwable
    }

    private var preflightReadMode: PreflightReadMode = PreflightReadMode.FullCardRead

    private var onTagConnectedAfterResume: VoidCallback? = null

    fun setInitialMessage(message: ViewDelegateMessage?) {
        initialMessage = message
    }

    fun setMessage(message: ViewDelegateMessage?) {
        viewDelegate.setMessage(message)
    }

    /**
     * This method starts a card session, performs preflight [ReadCommand],
     * invokes [CardSessionRunnable.run] and closes the session.
     * @param iconScanRes iconResource to replace on Scan Bottom Sheet
     * @param runnable [CardSessionRunnable] that will be performed in the session.
     * @param callback will be triggered with a [CompletionResult] of a session.
     */
    fun <T : CardSessionRunnable<R>, R> startWithRunnable(
        iconScanRes: Int? = null,
        runnable: T,
        callback: CompletionCallback<R>,
    ) {
        if (state != CardSessionState.Inactive) {
            val error = TangemSdkError.Busy()
            Log.error { "$error" }
            callback(CompletionResult.Failure(error))
            return
        }

        Log.session { "start card session with runnable" }
        prepareSession(runnable) { prepareResult ->
            when (prepareResult) {
                is CompletionResult.Success -> {
                    start(iconScanRes) { _, error ->
                        if (error != null) {
                            Log.error { "$error" }
                            callback(CompletionResult.Failure(error))
                            return@start
                        }
                        Log.session { "start runnable" }
                        runnable.run(this) { result ->
                            Log.session { "runnable completed" }
                            when (result) {
                                is CompletionResult.Success -> {
                                    stop()
                                }
                                is CompletionResult.Failure -> {
                                    stopWithError(result.error)
                                }
                            }
                            callback(result)
                        }
                    }
                }
                is CompletionResult.Failure -> {
                    Log.error { "${prepareResult.error}" }
                    stopWithError(prepareResult.error)
                    callback(CompletionResult.Failure(prepareResult.error))
                }
            }
        }
    }

    /**
     * Starts a card session and performs preflight [ReadCommand].
     * @param iconScanRes iconResource to replace on Scan Bottom Sheet
     * @param onSessionStarted: callback with the card session. Can contain [TangemSdkError] if something goes wrong.
     */
    fun start(iconScanRes: Int? = null, onSessionStarted: SessionStartedCallback) {
        Log.session { "start card session with delegate" }
        state = CardSessionState.Active
        viewDelegate.onSessionStarted(cardId, initialMessage, environment.config.howToIsEnabled, iconScanRes)

        reader.scope = scope
        reader.startSession()

        scope.launch {
            reader.tag.asFlow()
                .filterNotNull()
                .take(1)
                .collect { tagType ->
                    selectApplet()
                        .doOnSuccess {
                            if (tagType == TagType.Nfc && preflightReadMode != PreflightReadMode.None) {
                                preflightCheck(onSessionStarted)
                            } else {
                                onSessionStarted(this@CardSession, null)
                            }
                        }
                        .doOnFailure {
                            onSessionStarted(this@CardSession, it)
                        }
                }
        }

        scope.launch {
            reader.tag.asFlow()
                .drop(1)
                .collect {
                    if (it == null) {
                        viewDelegate.onTagLost()
                    } else {
                        viewDelegate.onTagConnected()
                        onTagConnectedAfterResume?.invoke()
                        onTagConnectedAfterResume = null
                    }
                }
        }

        scope.launch {
            reader.tag.asFlow()
                .onCompletion {
                    if (it is CancellationException && it.message == TangemSdkError.UserCancelled().customMessage) {
                        stopWithError(TangemSdkError.UserCancelled())
                        viewDelegate.dismiss()
                        onSessionStarted(this@CardSession, TangemSdkError.UserCancelled())
                    }
                }
                .collect {
                    if (it == null && connectedTag != null && state == CardSessionState.Active) {
                        environment.encryptionKey = null
                        connectedTag = null
                    } else if (it != null) {
                        connectedTag = it
                    }
                }
        }
    }

    fun run(jsonRequest: String, callback: (String) -> Unit) {
        val jsonrpcLinker = JSONRPCLinker(jsonRequest).apply { initRunnable(jsonRpcConverter) }
        if (jsonrpcLinker.hasError()) {
            callback(jsonrpcLinker.response.toJson())
            return
        }

        jsonrpcLinker.runnable!!.run(this) {
            jsonrpcLinker.linkResult(it)
            callback(jsonrpcLinker.response.toJson())
        }
    }

    private suspend fun shouldRequestBiometrics(): Boolean {
        return when {
            userCodeRepository == null -> false
            cardId != null && userCodeRepository.hasSavedUserCode(cardId!!) -> true
            else -> userCodeRepository.hasSavedUserCodes()
        }
    }

    private fun <T : CardSessionRunnable<*>> prepareSession(runnable: T, callback: CompletionCallback<Unit>) {
        Log.session { "prepare card session" }
        preflightReadMode = runnable.preflightReadMode()

        if (!runnable.allowsRequestAccessCodeFromRepository) {
            runnable.prepare(this, callback)
            return
        }

        val requestUserCode = { codeType: UserCodeType ->
            when (codeType) {
                UserCodeType.AccessCode -> {
                    environment.accessCode = UserCode(codeType, null)
                }
                UserCodeType.Passcode -> {
                    environment.passcode = UserCode(codeType, null)
                }
            }
            requestUserCodeIfNeeded(
                type = codeType,
                isFirstAttempt = true,
            ) { userCodeResult ->
                userCodeResult
                    .doOnSuccess { runnable.prepare(this, callback) }
                    .doOnFailure { error -> callback(CompletionResult.Failure(error)) }
            }
        }

        when (val policy = environment.config.userCodeRequestPolicy) {
            is UserCodeRequestPolicy.AlwaysWithBiometrics -> {
                scope.launch(Dispatchers.Main) {
                    if (shouldRequestBiometrics()) {
                        userCodeRepository?.unlock()
                            ?.doOnSuccess { runnable.prepare(this@CardSession, callback) }
                            ?.doOnFailure { e ->
                                Log.error {
                                    """
                                        User codes storage could not be unlocked
                                        |- Cause: $e
                                    """.trimIndent()
                                }
                                requestUserCode(policy.codeType)
                            }
                    } else {
                        requestUserCode(policy.codeType)
                    }
                }
            }
            is UserCodeRequestPolicy.Always -> {
                requestUserCode(policy.codeType)
            }
            is UserCodeRequestPolicy.Default -> {
                runnable.prepare(this, callback)
            }
        }
    }

    private fun preflightCheck(onSessionStarted: SessionStartedCallback) {
        Log.session { "start preflight check" }
        val preflightTask = PreflightReadTask(preflightReadMode, cardId)
        preflightTask.run(this) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    onSessionStarted(this, null)
                }
                is CompletionResult.Failure -> {
                    val wrongType = when (result.error) {
                        is TangemSdkError.WrongCardType -> WrongValueType.CardType
                        is TangemSdkError.WrongCardNumber -> WrongValueType.CardId(result.error.cardId)
                        else -> null
                    }
                    if (wrongType != null) {
                        Log.error { "${result.error}" }
                        viewDelegate.onWrongCard(wrongType)
                        scope.launch(scope.coroutineContext) {
                            delay(timeMillis = 3500)
                            if (connectedTag == null) {
                                onSessionStarted(this@CardSession, result.error)
                                stopWithError(result.error)
                            } else {
                                viewDelegate.onTagConnected()
                                preflightCheck(onSessionStarted)
                            }
                        }
                    } else {
                        onSessionStarted(this, result.error)
                        stopWithError(result.error)
                    }
                }
            }
        }
    }

    /**
     * Stops the current session with the text message.
     * @param message If null, the default message will be shown.
     */
    fun stop(message: Message? = null) {
        stopSessionIfActive()
        viewDelegate.onSessionStopped(message)
    }

    /**
     * Stops the current session on error.
     * @param error An error that will be shown.
     */
    fun stopWithError(error: TangemError) {
        stopSessionIfActive()
        if (error !is TangemSdkError.UserCancelled) {
            Log.error { "Finishing with error: ${error.code}" }
            viewDelegate.onError(error)
        } else {
            Log.debug { "User cancelled NFC session" }
        }
    }

    private fun stopSessionIfActive() {
        if (state == CardSessionState.Inactive) return

        Log.session { "stop session" }
        state = CardSessionState.Inactive
        preflightReadMode = PreflightReadMode.FullCardRead
        saveUserCodeIfNeeded()
        reader.stopSession()
        scope.cancel()
    }

    fun send(apdu: CommandApdu, callback: CompletionCallback<ResponseApdu>) {
        Log.session { "send CommandApdu" }
        val subscription = reader.tag.openSubscription()
        scope.launch {
            subscription.consumeAsFlow()
                .filterNotNull()
                .map { establishEncryptionIfNeeded() }
                .map { apdu.encrypt(environment.encryptionMode, environment.encryptionKey) }
                .map { encryptedApdu -> reader.transceiveApdu(encryptedApdu) }
                .map { responseApdu -> decrypt(responseApdu) }
                .catch {
                    if (it is TangemSdkError) {
                        Log.error { "$it" }
                        callback(CompletionResult.Failure(it))
                    }
                }
                .collect { result ->
                    when (result) {
                        is CompletionResult.Success -> {
                            subscription.cancel()
                            callback(result)
                        }
                        is CompletionResult.Failure -> {
                            when (result.error) {
                                is TangemSdkError.TagLost -> Log.session { "tag lost. Waiting for tag..." }
                                else -> {
                                    Log.error { "${result.error}" }
                                    subscription.cancel()
                                    callback(result)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun pause() {
        reader.pauseSession()
    }

    fun resume(onTagConnected: VoidCallback? = null) {
        onTagConnectedAfterResume = onTagConnected
        reader.resumeSession()
    }

    private suspend fun selectApplet(): CompletionResult<ByteArray?> {
        Log.session { "select the Wallet applet" }
        return reader.transceiveRaw(Apdu.build(Apdu.SELECT, Apdu.TANGEM_WALLET_AID))
    }

    private suspend fun establishEncryptionIfNeeded(): CompletionResult<Boolean> {
        Log.session { "establish encryption if needed" }
        if (environment.encryptionMode == EncryptionMode.None || environment.encryptionKey != null) {
            return CompletionResult.Success(true)
        }

        val encryptionHelper = EncryptionHelper.create(environment.encryptionMode)
            ?: return CompletionResult.Failure(
                TangemSdkError.CryptoUtilsError("Failed to establish encryption"),
            )

        val openSessionCommand = OpenSessionCommand(encryptionHelper.keyA)
        val apdu = openSessionCommand.serialize(environment)

        when (val response = reader.transceiveApdu(apdu)) {
            is CompletionResult.Success -> {
                val result = try {
                    openSessionCommand.deserialize(environment, response.data)
                } catch (error: TangemSdkError) {
                    return CompletionResult.Failure(error)
                }

                val uid = result.uid
                val protocolKey = environment.accessCode.value?.pbkdf2Hash(uid, iterations = 50)
                    ?: return CompletionResult.Failure(
                        TangemSdkError.CryptoUtilsError(
                            "Failed to establish encryption",
                        ),
                    )

                val secret = encryptionHelper.generateSecret(result.sessionKeyB)
                val sessionKey = (secret + protocolKey).calculateSha256()
                environment.encryptionKey = sessionKey

                return CompletionResult.Success(true)
            }
            is CompletionResult.Failure -> return CompletionResult.Failure(response.error)
        }
    }

    private fun decrypt(result: CompletionResult<ResponseApdu>): CompletionResult<ResponseApdu> {
        return when (result) {
            is CompletionResult.Success -> {
                try {
                    CompletionResult.Success(result.data.decrypt(environment.encryptionKey))
                } catch (error: TangemSdkError) {
                    CompletionResult.Failure(error)
                }
            }
            is CompletionResult.Failure -> result
        }
    }

    fun requestUserCodeIfNeeded(type: UserCodeType, isFirstAttempt: Boolean, callback: CompletionCallback<Unit>) {
        val userCode = when (type) {
            UserCodeType.AccessCode -> environment.accessCode.value
            UserCodeType.Passcode -> environment.passcode.value
        }
        if (userCode != null) {
            callback(CompletionResult.Success(Unit))
            return
        }
        Log.session { "request user code of type: $type" }

        val cardId = environment.card?.cardId ?: this.cardId
        val showForgotButton = environment.card?.backupStatus?.isActive ?: false
        val formattedCardId = cardId?.let {
            CardIdFormatter(environment.config.cardIdDisplayFormat).getFormattedCardId(it)
        }

        viewDelegate.requestUserCode(
            type = type,
            isFirstAttempt = isFirstAttempt,
            showForgotButton = showForgotButton,
            cardId = formattedCardId,
        ) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    updateEnvironment(type, result.data)
                    callback(CompletionResult.Success(Unit))
                }
                is CompletionResult.Failure -> {
                    if (result.error is TangemSdkError.UserForgotTheCode) {
                        viewDelegate.dismiss()
                        restoreUserCode(type, cardId) { restoreCodeResult ->
                            when (restoreCodeResult) {
                                is CompletionResult.Success -> {
                                    updateEnvironment(type, restoreCodeResult.data)
                                    resetCodesController = null
                                    callback(CompletionResult.Success(Unit))
                                }
                                is CompletionResult.Failure -> {
                                    callback(CompletionResult.Failure(restoreCodeResult.error))
                                }
                            }
                        }
                    } else {
                        callback(CompletionResult.Failure(result.error))
                    }
                }
            }
        }
    }

    fun updateUserCodeIfNeeded() {
        val card = environment.card ?: return
        val userCode = userCodeRepository?.get(card.cardId)
        when {
            userCode?.type == UserCodeType.AccessCode && card.isAccessCodeSet -> {
                environment.accessCode = userCode
            }
            userCode?.type == UserCodeType.Passcode && card.isPasscodeSet == true -> {
                environment.passcode = userCode
            }
        }
    }

    private fun saveUserCodeIfNeeded() {
        val saveCodeAndLock: suspend (String, UserCode) -> Unit = { cardId, code ->
            userCodeRepository?.save(cardId, code)
                ?.doOnResult {
                    userCodeRepository.lock()
                }
                ?.doOnFailure {
                    Log.error { "Access code saving failed: $it" }
                }
        }

        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            val card = environment.card
            when {
                card == null -> userCodeRepository?.lock()
                environment.accessCode.value != null && card.isAccessCodeSet -> {
                    saveCodeAndLock(card.cardId, environment.accessCode)
                }
                environment.passcode.value != null && card.isPasscodeSet == true -> {
                    saveCodeAndLock(card.cardId, environment.passcode)
                }
            }
        }
    }

    private fun updateEnvironment(type: UserCodeType, code: String) {
        val userCode = UserCode(type, code)
        environment.encryptionKey = null // we need to reset encryption key with new userCode
        when (type) {
            UserCodeType.AccessCode -> environment.accessCode = userCode
            UserCodeType.Passcode -> environment.passcode = userCode
        }
    }

    private fun restoreUserCode(type: UserCodeType, cardId: String?, callback: CompletionCallback<String>) {
        val sessionBuilder = SessionBuilder(
            viewDelegate = viewDelegate,
            secureStorage = secureStorage,
            userCodeRepository = userCodeRepository,
            reader = reader,
            jsonRpcConverter = jsonRpcConverter,
        )
        val config = environment.config.apply {
            userCodeRequestPolicy = UserCodeRequestPolicy.Default
        }
        val resetService = ResetPinService(
            sessionBuilder = sessionBuilder,
            stringsLocator = viewDelegate.resetCodesViewDelegate.stringsLocator,
            config = config,
        )
        viewDelegate.resetCodesViewDelegate.stopSessionCallback = {
            stopSessionIfActive()
            callback(CompletionResult.Failure(TangemSdkError.UserCancelled()))
        }
        resetCodesController = ResetCodesController(
            resetService = resetService,
            viewDelegate = viewDelegate.resetCodesViewDelegate,
        ).apply {
            cardIdDisplayFormat = environment.config.cardIdDisplayFormat
            start(codeType = type, cardId = cardId, callback = callback)
        }
    }

    enum class CardSessionState {
        Inactive,
        Active,
    }
}

typealias SessionStartedCallback = (session: CardSession, error: TangemError?) -> Unit

enum class TagType {
    Nfc,
    Slix,
}

class SessionBuilder(
    val viewDelegate: SessionViewDelegate,
    val secureStorage: SecureStorage,
    val userCodeRepository: UserCodeRepository?,
    val reader: CardReader,
    val jsonRpcConverter: JSONRPCConverter,
) {
    fun build(config: Config, cardId: String? = null, initialMessage: Message? = null): CardSession {
        return CardSession(
            cardId = cardId,
            viewDelegate = viewDelegate,
            environment = SessionEnvironment(config, secureStorage),
            userCodeRepository = userCodeRepository,
            reader = reader,
            jsonRpcConverter = jsonRpcConverter,
            secureStorage = secureStorage,
            initialMessage = initialMessage,
        )
    }
}
