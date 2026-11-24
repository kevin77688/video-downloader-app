package io.github.kevin77688.uvd.core

import android.content.Context
import androidx.annotation.StringRes
import io.github.kevin77688.uvd.R
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** A failure whose message is meant for the user, given as a string resource (translatable). */
class UserException(@StringRes val messageRes: Int, vararg val args: Any) : Exception() {
    override val message: String get() = "UserException(${args.joinToString()})"
}

/** Readable, localized message for any failure that reaches the UI or a notification. */
fun Throwable.userMessage(context: Context): String = when (this) {
    is UserException -> context.getString(messageRes, *args)
    is HttpStatusException -> context.getString(R.string.err_http, code)
    is UnknownHostException -> context.getString(R.string.err_offline)
    is SocketTimeoutException -> context.getString(R.string.err_timeout)
    else -> message ?: javaClass.simpleName
}
