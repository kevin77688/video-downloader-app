package io.github.kevin77688.uvd.extractor

/**
 * Site-agnostic URL helpers. Share texts often wrap the link in prose and CJK punctuation,
 * e.g. "标题（作者） https://b23.tv/abc", so URLs are cut at the first non-URL character.
 */
object LinkFinder {
    private const val URL_CHARS = """[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]"""
    private val withScheme = Regex("""https?://$URL_CHARS+""", RegexOption.IGNORE_CASE)
    private val withoutScheme = Regex("""(?<![\w@.])(?:[a-z0-9-]+\.)+[a-z]{2,}/$URL_CHARS*""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?'\"*"

    /** Every URL in [text], in order. Falls back to scheme-less links ("youtu.be/x") when there are none. */
    fun urls(text: CharSequence?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val found = withScheme.findAll(text).map { clean(it.value) }.toList()
        if (found.isNotEmpty()) return found
        return withoutScheme.findAll(text).map { "https://" + clean(it.value) }.toList()
    }

    /** Lower-case host without "www." / "m." / "mobile.", or null if [url] has none. */
    fun host(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (host.isEmpty()) return null
        return host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
    }

    /** True when [url]'s host is [domain] or one of its subdomains. */
    fun hostIs(url: String, vararg domain: String): Boolean {
        val h = host(url) ?: return false
        return domain.any { h == it || h.endsWith(".$it") }
    }

    /** Path of [url] starting with "/" (no query or fragment); "/" when there is none. */
    fun path(url: String): String =
        "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')

    /** Value of query parameter [name] in [url], or null. */
    fun queryParam(url: String, name: String): String? =
        url.substringAfter('?', "").substringBefore('#').split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }

    private fun clean(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            url = when {
                last in TRAILING -> url.dropLast(1)
                // Drop a closing bracket only when it isn't part of the URL itself.
                last == ')' && url.count { it == '(' } < url.count { it == ')' } -> url.dropLast(1)
                last == ']' && url.count { it == '[' } < url.count { it == ']' } -> url.dropLast(1)
                else -> return url
            }
        }
        return url
    }
}
