package io.github.kevin77688.uvd.extractor.newpipe

import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.Http
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Platform
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import java.io.IOException

/**
 * Glue between NewPipeExtractor (YouTube and other sites it supports) and this app:
 * HTTP goes through [Http] (HttpURLConnection, no OkHttp), and its exceptions become [UserException]s.
 */
object NewPipeBridge {
    /** Desktop UA NewPipe expects for its web requests (same family NewPipe itself uses). */
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    @Volatile private var initialized = false

    fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(HttpDownloader, Localization.DEFAULT)
                initialized = true
            }
        }
    }

    /** Runs a NewPipe call, translating its exceptions into readable, localized failures. */
    inline fun <T> call(platform: Platform, block: () -> T): T {
        ensureInit()
        try {
            return block()
        } catch (e: AgeRestrictedContentException) {
            throw UserException(R.string.err_age_restricted)
        } catch (e: PrivateContentException) {
            throw UserException(R.string.err_unavailable)
        } catch (e: GeographicRestrictionException) {
            throw UserException(R.string.err_unavailable)
        } catch (e: PaidContentException) {
            throw UserException(R.string.err_unavailable)
        } catch (e: ContentNotAvailableException) {
            throw UserException(R.string.err_unavailable)
        } catch (e: ReCaptchaException) {
            throw UserException(R.string.err_rate_limited, platform.displayName)
        } catch (e: ExtractionException) {
            throw UserException(R.string.err_extract_failed, platform.displayName)
        }
    }

    private object HttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val conn = Http.open(request.url(), userAgent = USER_AGENT)
            try {
                conn.requestMethod = request.httpMethod()
                request.headers().forEach { (name, values) ->
                    values.forEachIndexed { i, v ->
                        if (i == 0) conn.setRequestProperty(name, v) else conn.addRequestProperty(name, v)
                    }
                }
                request.dataToSend()?.let { body ->
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(body.size)
                    conn.outputStream.use { it.write(body) }
                }
                val code = conn.responseCode
                if (code == 429) throw ReCaptchaException("Rate limited (HTTP 429)", request.url())
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }
                val headers = conn.headerFields.filterKeys { it != null }
                return Response(code, conn.responseMessage, headers, body, conn.url.toString())
            } catch (e: IOException) {
                throw e
            } finally {
                conn.disconnect()
            }
        }
    }
}
