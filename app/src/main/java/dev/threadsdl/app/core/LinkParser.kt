package dev.threadsdl.app.core

/** Finds and normalizes Threads post links inside arbitrary text (share text, clipboard, user input). */
object LinkParser {
    private val linkRegex = Regex(
        """https?://(?:www\.)?threads\.(?:com|net)/(?:@[\w.]+/post/[\w-]+|share/[\w-]+|t/[\w-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val postRegex = Regex(
        """threads\.(?:com|net)/@([\w.]+)/post/([\w-]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** First Threads link in [text], without query string, or null. */
    fun find(text: CharSequence?): String? = text?.let { linkRegex.find(it)?.value }

    /** (username, post code) when [url] is a canonical post URL; null for share/short links. */
    fun parsePost(url: String): Pair<String, String>? =
        postRegex.find(url)?.let { it.groupValues[1] to it.groupValues[2] }
}
