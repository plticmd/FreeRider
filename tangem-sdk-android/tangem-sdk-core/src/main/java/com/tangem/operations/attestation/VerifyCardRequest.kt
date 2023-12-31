package com.tangem.operations.attestation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CardVerifyAndGetInfo {

    @JsonClass(generateAdapter = true)
    data class Request(var requests: List<Item>? = null) {

        @JsonClass(generateAdapter = true)
        data class Item(
            @Json(name = "CID") var cid: String = "",
            var publicKey: String = "",
        )
    }

    @JsonClass(generateAdapter = true)
    data class Response(var results: List<Item>? = null) {

        @JsonClass(generateAdapter = true)
        data class Item(
            var error: String? = null,
            @Json(name = "CID") var cid: String = "",
            var passed: Boolean = false,
            var batch: String = "",
            var artwork: ArtworkInfo? = null,
            var substitution: SubstitutionInfo? = null,
        ) {

            @JsonClass(generateAdapter = true)
            data class SubstitutionInfo(
                var data: String? = null,
                var signature: String? = null,
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class ArtworkInfo(
    var id: String = "",
    var hash: String = "",
    var date: String = "",
) {
    fun getUpdateDate(): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).parse(date)
        } catch (e: Exception) {
            null
        }
    }
}
