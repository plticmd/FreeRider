package com.tangem.operations.pins

import com.tangem.common.CardIdFormatter
import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.Instruction
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.apdu.StatusWord
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.core.TangemSdkError
import com.tangem.common.doOnFailure
import com.tangem.common.doOnSuccess
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.tlv.TlvBuilder
import com.tangem.common.tlv.TlvDecoder
import com.tangem.common.tlv.TlvTag
import com.tangem.operations.Command

class SetUserCodeCommand private constructor() : Command<SuccessResponse>() {

    internal var shouldRestrictDefaultCodes = true

    internal var isPasscodeRequire = true
    private var codes = mutableMapOf<UserCodeType, UserCodeAction>()

    override fun requiresPasscode(): Boolean = isPasscodeRequire

    override fun prepare(session: CardSession, callback: CompletionCallback<Unit>) {
        requestIfNeeded(UserCodeType.AccessCode, session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    requestIfNeeded(UserCodeType.Passcode, session) { result ->
                        when (result) {
                            is CompletionResult.Success -> callback(CompletionResult.Success(Unit))
                            is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                        }
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }
    }

    override fun run(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        if (codes.values.contains(UserCodeAction.Request)) { // If prepare not called e.g. chaining
            val error = getPauseError(session.environment)
            if (error == null) {
                session.pause()
            } else {
                session.pause()
            }

            prepare(session) { result ->
                when (result) {
                    is CompletionResult.Success -> session.resume { runCommand(session, callback) }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } else {
            runCommand(session, callback)
        }
    }

    private fun runCommand(session: CardSession, callback: CompletionCallback<SuccessResponse>) {
        // Restrict default codes except reset command
        if (shouldRestrictDefaultCodes) {
            if (!isCodeAllowed(UserCodeType.AccessCode)) {
                callback(CompletionResult.Failure(TangemSdkError.AccessCodeCannotBeChanged()))
                return
            }
            if (!isCodeAllowed(UserCodeType.Passcode)) {
                callback(CompletionResult.Failure(TangemSdkError.PasscodeCannotBeChanged()))
                return
            }
        }

        super.run(session) { result ->
            result
                .doOnSuccess { response ->
                    val accessCodeValue = codes[UserCodeType.AccessCode]?.value
                    if (accessCodeValue != null) {
                        session.environment.accessCode = UserCode(UserCodeType.AccessCode, accessCodeValue)
                    }

                    val passcodeValue = codes[UserCodeType.Passcode]?.value
                    if (passcodeValue != null) {
                        session.environment.passcode = UserCode(UserCodeType.Passcode, passcodeValue)
                    }

                    callback(CompletionResult.Success(response))
                }
                .doOnFailure { error -> callback(CompletionResult.Failure(error)) }
        }
    }

    private fun isCodeAllowed(type: UserCodeType): Boolean {
        return !codes[type]?.value.contentEquals(type.defaultValue.calculateSha256())
    }

    override fun serialize(environment: SessionEnvironment): CommandApdu {
        val accessCodeValue = codes[UserCodeType.AccessCode]?.value ?: environment.accessCode.value
        val passcodeValue = codes[UserCodeType.Passcode]?.value ?: environment.passcode.value
        if (accessCodeValue == null || passcodeValue == null) {
            throw TangemSdkError.SerializeCommandError()
        }

        val tlvBuilder = TlvBuilder()
        tlvBuilder.append(TlvTag.Pin, environment.accessCode.value)
        tlvBuilder.append(TlvTag.Pin2, environment.passcode.value)
        tlvBuilder.append(TlvTag.CardId, environment.card?.cardId)
        tlvBuilder.append(TlvTag.NewPin, accessCodeValue)
        tlvBuilder.append(TlvTag.NewPin2, passcodeValue)
        tlvBuilder.append(TlvTag.Cvc, environment.cvc)

        val firmwareVersion = environment.card?.firmwareVersion
        if (firmwareVersion != null && firmwareVersion >= FirmwareVersion.BackupAvailable) {
            val hash = (accessCodeValue + passcodeValue).calculateSha256()
            tlvBuilder.append(TlvTag.CodeHash, hash)
        }

        return CommandApdu(Instruction.SetPin, tlvBuilder.serialize())
    }

    override fun deserialize(environment: SessionEnvironment, apdu: ResponseApdu): SuccessResponse {
        val tlvData = apdu.getTlvData() ?: throw TangemSdkError.DeserializeApduFailed()

        val decoder = TlvDecoder(tlvData)
        return SuccessResponse(decoder.decode(TlvTag.CardId))
    }

    private fun getPauseError(environment: SessionEnvironment): TangemSdkError? {
        val filtered = codes.filter { it.value == UserCodeAction.Request }
        val type = filtered.keys.firstOrNull() ?: return null

        return TangemSdkError.from(type, environment)
    }

    private fun requestIfNeeded(type: UserCodeType, session: CardSession, callback: CompletionCallback<Unit>) {
        if (codes[type] != UserCodeAction.Request) {
            callback(CompletionResult.Success(Unit))
            return
        }

        val formattedCid = session.cardId?.let {
            CardIdFormatter(session.environment.config.cardIdDisplayFormat).getFormattedCardId(it)
        }

        session.viewDelegate.requestUserCodeChange(type, formattedCid) { result ->
            when (result) {
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                is CompletionResult.Success -> {
                    codes[type] = UserCodeAction.StringValue(result.data)
                    callback(CompletionResult.Success(Unit))
                }
            }
        }
    }

    companion object {
        fun changeAccessCode(accessCode: String?): SetUserCodeCommand {
            return SetUserCodeCommand().apply {
                codes[UserCodeType.AccessCode] = accessCode?.let { UserCodeAction.StringValue(it) }
                    ?: UserCodeAction.Request
                codes[UserCodeType.Passcode] = UserCodeAction.NotChange
            }
        }

        fun changePasscode(passcode: String?): SetUserCodeCommand {
            return SetUserCodeCommand().apply {
                codes[UserCodeType.AccessCode] = UserCodeAction.NotChange
                codes[UserCodeType.Passcode] = passcode?.let { UserCodeAction.StringValue(it) }
                    ?: UserCodeAction.Request
            }
        }

        fun change(accessCode: String?, passcode: String?): SetUserCodeCommand {
            return change(accessCode?.calculateSha256(), passcode?.calculateSha256())
        }

        fun change(accessCode: ByteArray?, passcode: ByteArray?): SetUserCodeCommand {
            return SetUserCodeCommand().apply {
                codes[UserCodeType.AccessCode] = accessCode?.let { UserCodeAction.Value(it) }
                    ?: UserCodeAction.Request
                codes[UserCodeType.Passcode] = passcode?.let { UserCodeAction.Value(it) }
                    ?: UserCodeAction.Request
            }
        }

        fun resetAccessCodeCommand(): SetUserCodeCommand {
            return changeAccessCode(UserCodeType.AccessCode.defaultValue).apply {
                shouldRestrictDefaultCodes = false
            }
        }

        fun resetPasscodeCommand(): SetUserCodeCommand {
            return changePasscode(UserCodeType.Passcode.defaultValue).apply {
                shouldRestrictDefaultCodes = false
            }
        }

        fun resetUserCodes(): SetUserCodeCommand {
            return change(UserCodeType.AccessCode.defaultValue, UserCodeType.Passcode.defaultValue).apply {
                shouldRestrictDefaultCodes = false
            }
        }
    }

    sealed class UserCodeAction(val value: ByteArray?) {
        object Request : UserCodeAction(null)
        class StringValue(value: String) : UserCodeAction(value.calculateSha256())
        class Value(data: ByteArray) : UserCodeAction(data)
        object NotChange : UserCodeAction(null)
    }
}

enum class SetPinStatus {
    PinsNotChanged,
    Pin1Changed,
    Pin2Changed,
    Pin3Changed,
    Pins12Changed,
    Pins13Changed,
    Pins23Changed,
    Pins123Changed,
    ;

    companion object {
        fun fromStatusWord(statusWord: StatusWord): SetPinStatus? {
            return when (statusWord) {
                StatusWord.ProcessCompleted -> PinsNotChanged
                StatusWord.Pin1Changed -> Pin1Changed
                StatusWord.Pin2Changed -> Pin2Changed
                StatusWord.Pins12Changed -> Pins12Changed
                StatusWord.Pin3Changed -> Pin3Changed
                StatusWord.Pins13Changed -> Pins13Changed
                StatusWord.Pins23Changed -> Pins23Changed
                StatusWord.Pins123Changed -> Pins123Changed
                else -> null
            }
        }
    }
}
