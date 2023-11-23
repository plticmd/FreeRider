package com.tangem.common.tlv

import com.tangem.Log
import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.SigningMethod
import com.tangem.common.card.UserSettingsMask
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.crypto.hdWallet.DerivationNode.Companion.serialize
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.attestation.AttestCardKeyCommand
import com.tangem.operations.files.FileDataMode
import com.tangem.operations.issuerAndUserData.IssuerExtraDataMode
import com.tangem.operations.personalization.entities.ProductMask
import com.tangem.operations.read.ReadMode
import com.tangem.operations.resetcode.AuthorizeMode
import java.util.Calendar
import java.util.Date

/**
 * Encodes information that is to be written on the card from parsed classes into [ByteArray]
 * (according to the provided [TlvTag] and corresponding [TlvValueType])
 * and then forms [Tlv] with the encoded values.
 */
class TlvEncoder {
    /**
     * @param tag [TlvTag] which defines the type of [Tlv] that will be created.
     * @param value information that is to be encoded into [Tlv].
     */
    inline fun <reified T> encode(tag: TlvTag, value: T?): Tlv {
        if (value != null) {
            return Tlv(tag, encodeValue(tag, value)).apply { sendToLog(value) }
        } else {
            val error = TangemSdkError.EncodingFailed("Encoding error. Value for tag $tag is null")
            Log.error { error.customMessage }
            throw error
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    inline fun <reified T> encodeValue(tag: TlvTag, value: T): ByteArray {
        return when (tag.valueType()) {
            TlvValueType.HexString -> {
                typeCheck<T, String>(tag)
                (value as String).hexToBytes()
            }
            TlvValueType.HexStringToHash -> {
                typeCheck<T, String>(tag)
                (value as String).calculateSha256()
            }
            TlvValueType.Utf8String -> {
                typeCheck<T, String>(tag)
                (value as String).toByteArray()
            }
            TlvValueType.Uint8 -> {
                typeCheck<T, Int>(tag)
                (value as Int).toByteArray(1)
            }
            TlvValueType.Uint16 -> {
                typeCheck<T, Int>(tag)
                (value as Int).toByteArray(2)
            }
            TlvValueType.Uint32 -> {
                typeCheck<T, Int>(tag)
                (value as Int).toByteArray()
            }
            TlvValueType.BoolValue -> {
                typeCheck<T, Boolean>(tag)
                val booleanValue = value as Boolean
                if (booleanValue) byteArrayOf(1) else byteArrayOf(0)
            }
            TlvValueType.ByteArray -> {
                typeCheck<T, ByteArray>(tag)
                value as ByteArray
            }
            TlvValueType.EllipticCurve -> {
                typeCheck<T, EllipticCurve>(tag)
                (value as EllipticCurve).curve.toByteArray()
            }
            TlvValueType.DateTime -> {
                typeCheck<T, Date>(tag)
                val calendar = Calendar.getInstance().apply { time = value as Date }
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                return year.toByteArray(2) + month.toByte() + day.toByte()
            }
            TlvValueType.ProductMask -> {
                typeCheck<T, ProductMask>(tag)
                byteArrayOf((value as ProductMask).rawValue.toByte())
            }
            TlvValueType.SettingsMask -> {
                try {
                    typeCheck<T, Card.SettingsMask>(tag)
                    val rawValue = (value as Card.SettingsMask).rawValue
                    rawValue.toByteArray(determineByteArraySize(rawValue))
                } catch (ex: TangemSdkError.EncodingFailedTypeMismatch) {
                    Log.warning { "Type of mask is not CardSettingsMask. Trying to check CardWalletSettingsMask" }
                    typeCheck<T, CardWallet.SettingsMask>(tag)
                    val rawValue = (value as CardWallet.SettingsMask).rawValue
                    rawValue.toByteArray(size = 4)
                }
            }
            TlvValueType.UserSettingsMask -> {
                typeCheck<T, UserSettingsMask>(tag)
                (value as UserSettingsMask).rawValue.toByteArray(size = 4)
            }
            TlvValueType.Status -> {
                try {
                    typeCheck<T, Card.Status>(tag)
                    (value as Card.Status).code.toByteArray()
                } catch (ex: Exception) {
                    Log.warning { "Status is not Card.Status type. Trying to check CardWallet.Status" }
                    typeCheck<T, CardWallet.Status>(tag)
                    (value as CardWallet.Status).code.toByteArray()
                }
            }
            TlvValueType.BackupStatus -> {
                typeCheck<T, Card.BackupRawStatus>(tag)
                (value as Card.BackupRawStatus).code.toByteArray()
            }
            TlvValueType.SigningMethod -> {
                typeCheck<T, SigningMethod>(tag)
                byteArrayOf((value as SigningMethod).rawValue.toByte())
            }
            TlvValueType.InteractionMode -> {
                when (T::class) {
                    IssuerExtraDataMode::class -> byteArrayOf((value as IssuerExtraDataMode).code)
                    ReadMode::class -> byteArrayOf((value as ReadMode).rawValue.toByte())
                    AuthorizeMode::class -> byteArrayOf((value as AuthorizeMode).rawValue.toByte())
                    FileDataMode::class -> byteArrayOf((value as FileDataMode).rawValue.toByte())
                    AttestCardKeyCommand.RawMode::class -> byteArrayOf((value as AttestCardKeyCommand.RawMode).value)
                    else -> {
                        val error = getEncodingError<T>(tag)
                        Log.error { error.customMessage }
                        throw error
                    }
                }
            }
            TlvValueType.DerivationPath -> {
                typeCheck<T, DerivationPath>(tag)
                return (value as DerivationPath).nodes.map { it.serialize() }.reduce { acc, bytes -> acc + bytes }
            }
        }
    }

    @Suppress("MagicNumber")
    fun determineByteArraySize(value: Int): Int {
        val mask = 0xFFFF0000.toInt()
        return if (value and mask != 0) 4 else 2
    }

    inline fun <reified T, reified ExpectedT> typeCheck(tag: TlvTag) {
        if (T::class != ExpectedT::class) {
            val error = getEncodingError<T>(tag)
            Log.error { error.customMessage }
            throw error
        }
    }

    inline fun <reified T> getEncodingError(tag: TlvTag): TangemSdkError {
        return TangemSdkError.EncodingFailedTypeMismatch(
            "Encoder: Mapping error. Type for tag: $tag must be ${tag.valueType()}. It is ${T::class}",
        )
    }
}
