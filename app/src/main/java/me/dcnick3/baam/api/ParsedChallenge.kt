package me.dcnick3.baam.api

import android.util.Log
import io.ktor.http.Url

private const val TAG = "ParsedChallenge"

data class ParsedChallenge(
    val url: Url,
    val code: String,
    val challenge: String,
)

fun parseChallenge(s: String): ParsedChallenge? {
    val url = try {
        Url(s)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse url for challenge `$s`", e)
        return null
    }
    val parts = url.fragment.split('-')
    if (parts.count() != 2) {
        Log.e(TAG, "Challenge `$s` does not have 2 parts separated by `-`")
        return null
    }
    // TODO: validate either code or challenge? They have allowed chars, you know
    val code = parts[0]
    val challenge = parts[1]

    return ParsedChallenge(url, code, challenge)
}