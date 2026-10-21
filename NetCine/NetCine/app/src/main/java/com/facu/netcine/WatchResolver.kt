package com.facu.netcine

import android.util.Base64
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

/** Un video listo para reproducir. [key] identifica el video para recordar por dónde ibas. */
data class Playable(val url: String, val title: String, val startSec: Long, val key: String)

data class Resolved(val items: List<Playable>, val startIndex: Int)

/**
 * La web del proveedor, al tocar "Reproducir", navega a una página `?watch=...#N`.
 * Esa página trae en su JavaScript una lista (`var movie = [...]` o `var serie = [...]`)
 * con las URLs de los MP4 codificadas en base64. Acá descargamos esa página con las
 * mismas cookies que el catálogo, sacamos esa lista y armamos qué reproducir.
 */
object WatchResolver {

    fun resolve(watchUrl: String, userAgent: String): Resolved {
        val hash = watchUrl.substringAfter('#', "")
        val pageUrl = watchUrl.substringBefore('#')
        val html = fetch(pageUrl, userAgent)

        val pageTitle = Regex("""var title = '((?:[^'\\]|\\.)*)'""")
            .find(html)?.groupValues?.get(1)?.replace("\\'", "'")?.trim().orEmpty()
        val lang = Regex("""var lenguaje = '(\w*)'""").find(html)?.groupValues?.get(1) ?: "spa"

        val movie = extractJson(html, "movie")
        if (movie is JSONArray && movie.length() > 0) return resolveMovie(movie, hash, pageTitle)

        val serie = extractJson(html, "serie")
        if (serie is JSONArray && serie.length() > 0) return resolveSerie(serie, hash, pageTitle, lang)

        throw IllegalStateException("La página no tiene videos")
    }

    private fun resolveMovie(arr: JSONArray, hash: String, title: String): Resolved {
        val versions = (0 until arr.length()).map { arr.getJSONObject(it) }
        val chosen = versions.firstOrNull { it.optString("number") == hash } ?: versions.first()
        val url = decode(chosen.optString("stream")) ?: throw IllegalStateException("Sin URL")
        val name = chosen.optString("name")
        val fullTitle = if (versions.size > 1 && name.isNotBlank()) "$title ($name)" else title
        return Resolved(listOf(Playable(url, fullTitle, 0, url)), 0)
    }

    private fun resolveSerie(arr: JSONArray, hash: String, title: String, lang: String): Resolved {
        // Preferimos el idioma que eligió la web; MP4 antes que HLS porque anda en cualquier Fire Stick.
        val other = if (lang.startsWith("sub")) "spa" else "sub"
        val pref = if (lang.startsWith("sub")) "sub" else "spa"
        val order = listOf("mp4_$pref", "mp4_$other", "hls_$pref", "hls_$other")

        val episodes = (0 until arr.length()).map { arr.getJSONObject(it) }
            .sortedBy { it.optInt("number") }

        val items = mutableListOf<Playable>()
        var startIndex = 0
        for (ep in episodes) {
            val url = order.firstNotNullOfOrNull { decode(ep.optString(it)) } ?: continue
            val n = ep.optInt("number")
            val epTitle = if (title.isNotBlank() && !title.equals("Watch", true)) "$title · Episodio $n" else "Episodio $n"
            if (n.toString() == hash) startIndex = items.size
            items += Playable(url, epTitle, ep.optLong("seek", 0), ep.optString("id", url))
        }
        if (items.isEmpty()) throw IllegalStateException("Sin episodios")
        return Resolved(items, startIndex)
    }

    private fun decode(b64: String?): String? {
        if (b64.isNullOrBlank() || b64 == "null") return null
        return try {
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim().takeIf { it.startsWith("http") }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Lee el valor JSON de `var <name> = ...;` respetando corchetes y comillas. */
    private fun extractJson(html: String, name: String): Any? {
        val marker = "var $name = "
        var i = html.indexOf(marker)
        while (i >= 0) {
            val start = i + marker.length
            val c = html.getOrNull(start)
            if (c == '[' || c == '{') {
                val end = matchingEnd(html, start)
                if (end > start) {
                    return try {
                        JSONTokener(html.substring(start, end + 1)).nextValue()
                            .takeIf { it is JSONArray || it is JSONObject }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            i = html.indexOf(marker, start)
        }
        return null
    }

    private fun matchingEnd(s: String, start: Int): Int {
        var depth = 0
        var inStr = false
        var esc = false
        for (k in start until s.length) {
            val ch = s[k]
            if (inStr) {
                when {
                    esc -> esc = false
                    ch == '\\' -> esc = true
                    ch == '"' -> inStr = false
                }
                continue
            }
            when (ch) {
                '"' -> inStr = true
                '[', '{' -> depth++
                ']', '}' -> { depth--; if (depth == 0) return k }
            }
        }
        return -1
    }

    private fun fetch(url: String, userAgent: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
