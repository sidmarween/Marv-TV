package de.marween.tv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

data class Programme(val start: Long, val end: Long, val title: String, val desc: String)

/** TV-Programm im XMLTV-Format (auch .gz). Hält nur die Sender, die gebraucht werden. */
object Epg {
    /** Deutsches TV-Programm (epgshare01) */
    const val DEFAULT_DE = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz"

    private val byId = ConcurrentHashMap<String, List<Programme>>()
    private val idByName = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, String>()
    /** Je Quelle: für welche Sender schon gesucht wurde */
    private val requested = ConcurrentHashMap<String, MutableSet<String>>()

    private fun keyOf(ch: Channel) = normName(ch.name) + "|" + normId(ch.epgId)

    fun needsLoad(url: String, channels: List<Channel>): Boolean {
        val set = requested[url] ?: return true
        return channels.any { keyOf(it) !in set }
    }

    fun clear(cacheDir: File) {
        byId.clear(); idByName.clear(); icons.clear(); requested.clear()
        cacheDir.listFiles()?.filter { it.name.startsWith("epg_") }?.forEach { it.delete() }
    }

    fun normId(s: String) = s.substringBefore('@').trim().lowercase(Locale.ROOT)

    private val bracket = Regex("\\[.*?]|\\(.*?\\)")
    private val prefix = Regex("^\\s*(de|ger|deu|at|ch)\\s*[:|\\-]\\s*")
    private val noise = Regex("\\b(hd|fhd|uhd|4k|sd|hevc|h265|raw|backup|live|tv)\\b")

    fun normName(s: String): String {
        var n = s.lowercase(Locale.GERMAN)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        n = prefix.replace(n, "")
        n = bracket.replace(n, " ")
        n = noise.replace(n, " ")
        return n.filter { it.isLetterOrDigit() }
    }

    private fun idFor(ch: Channel): String? {
        if (ch.epgId.isNotBlank()) {
            val id = normId(ch.epgId)
            if (byId.containsKey(id)) return id
        }
        return idByName[normName(ch.name)]
    }

    fun programmes(ch: Channel): List<Programme> = idFor(ch)?.let { byId[it] } ?: emptyList()

    fun nowNext(ch: Channel, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val list = programmes(ch)
        if (list.isEmpty()) return null to null
        val i = list.indexOfFirst { now < it.end }
        if (i < 0) return null to null
        val cur = list[i].takeIf { it.start <= now }
        val next = if (cur != null) list.getOrNull(i + 1) else list[i]
        return cur to next
    }

    fun icon(ch: Channel): String? = idFor(ch)?.let { icons[it] }

    /**
     * Lädt eine XMLTV-Quelle (mit Zwischenspeicher, 12 Stunden gültig) und übernimmt
     * nur Sender, deren ID oder Name in der Liste vorkommt.
     */
    fun load(url: String, cacheDir: File, channels: List<Channel>, force: Boolean = false) {
        val file = File(cacheDir, "epg_" + Integer.toHexString(url.hashCode()) + ".xml")
        val fresh = file.exists() && file.length() > 0 &&
            System.currentTimeMillis() - file.lastModified() < 12L * 60 * 60 * 1000
        if (force || !fresh) {
            val tmp = File(cacheDir, file.name + ".tmp")
            try {
                Http.openStream(url).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                file.delete()
                tmp.renameTo(file)
            } catch (e: Exception) {
                tmp.delete()
                if (!file.exists()) throw e
            }
        }
        val wantedIds = channels.mapNotNullTo(HashSet()) { c -> c.epgId.takeIf { it.isNotBlank() }?.let { normId(it) } }
        val wantedNames = channels.mapTo(HashSet()) { normName(it.name) }
        file.inputStream().use { parse(it, wantedIds, wantedNames) }
        requested.getOrPut(url) { java.util.Collections.synchronizedSet(HashSet<String>()) }
            .addAll(channels.map { keyOf(it) })
    }

    private fun openMaybeGzip(raw: InputStream): InputStream {
        val b = BufferedInputStream(raw, 64 * 1024)
        b.mark(2)
        val first = b.read()
        val second = b.read()
        b.reset()
        return if (first == 0x1f && second == 0x8b) BufferedInputStream(GZIPInputStream(b, 64 * 1024), 64 * 1024) else b
    }

    private fun parse(raw: InputStream, wantedIds: Set<String>, wantedNames: Set<String>) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(openMaybeGzip(raw), null)

        val now = System.currentTimeMillis()
        val from = now - 3L * 60 * 60 * 1000
        val until = now + 48L * 60 * 60 * 1000

        val channelNames = HashMap<String, MutableList<String>>()
        val channelIcons = HashMap<String, String>()
        var matched: Set<String>? = null
        val progs = HashMap<String, MutableList<Programme>>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val id = normId(parser.getAttributeValue(null, "id") ?: "")
                        val names = channelNames.getOrPut(id) { ArrayList() }
                        val depth = parser.depth
                        while (true) {
                            val e = parser.next()
                            if (e == XmlPullParser.END_DOCUMENT) break
                            if (e == XmlPullParser.END_TAG && parser.depth == depth) break
                            if (e == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "display-name" -> names.add(readText(parser))
                                    "icon" -> parser.getAttributeValue(null, "src")?.let { if (it.isNotBlank()) channelIcons[id] = it }
                                }
                            }
                        }
                    }
                    "programme" -> {
                        val set = matched ?: channelNames.filter { (id, names) ->
                            id in wantedIds || names.any { normName(it) in wantedNames }
                        }.keys.also { matched = it }
                        val id = normId(parser.getAttributeValue(null, "channel") ?: "")
                        if (id !in set) {
                            skip(parser)
                        } else {
                            val start = parseTime(parser.getAttributeValue(null, "start"))
                            val end = parseTime(parser.getAttributeValue(null, "stop"))
                            var title = ""
                            var desc = ""
                            val depth = parser.depth
                            while (true) {
                                val e = parser.next()
                                if (e == XmlPullParser.END_DOCUMENT) break
                                if (e == XmlPullParser.END_TAG && parser.depth == depth) break
                                if (e == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "title" -> if (title.isEmpty()) title = readText(parser)
                                        "desc" -> if (desc.isEmpty()) desc = readText(parser)
                                    }
                                }
                            }
                            if (start > 0 && end > start && end > from && start < until) {
                                progs.getOrPut(id) { ArrayList() }.add(Programme(start, end, title.trim(), desc.trim()))
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Ergebnisse übernehmen
        val set = matched ?: emptySet()
        for (id in set) {
            progs[id]?.let { list -> byId[id] = list.sortedBy { it.start }.distinctBy { it.start } }
            channelIcons[id]?.let { icons[id] = it }
            channelNames[id]?.forEach { n -> idByName.putIfAbsent(normName(n), id) }
            idByName.putIfAbsent(normName(id.substringBeforeLast('.')), id)
        }
    }

    private fun readText(parser: XmlPullParser): String = try {
        parser.nextText() ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun parseTime(value: String?): Long {
        val s = value?.trim() ?: return 0
        if (s.length < 14) return 0
        return try {
            val base = s.substring(0, 14)
            val tz = s.substring(14).trim()
            if (tz.isNotEmpty()) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse("$base $tz")?.time ?: 0
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(base)?.time ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
}

/** Senderlogos aus der offenen Datenbank von iptv-org (7 Tage zwischengespeichert). */
object Logos {
    private const val URL = "https://iptv-org.github.io/api/logos.json"
    @Volatile private var map: Map<String, String>? = null

    fun get(cacheDir: File): Map<String, String> {
        map?.let { return it }
        val file = File(cacheDir, "logos.json")
        val fresh = file.exists() && file.length() > 0 &&
            System.currentTimeMillis() - file.lastModified() < 7L * 24 * 60 * 60 * 1000
        if (!fresh) {
            val tmp = File(cacheDir, "logos.tmp")
            try {
                Http.openStream(URL).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                file.delete()
                tmp.renameTo(file)
            } catch (e: Exception) {
                tmp.delete()
                if (!file.exists()) throw e
            }
        }
        val arr = org.json.JSONArray(file.readText())
        val result = HashMap<String, String>()
        val score = HashMap<String, Int>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val format = o.optString("format").uppercase(Locale.ROOT)
            if (format == "SVG") continue
            val id = o.optString("channel").lowercase(Locale.ROOT)
            val url = o.optString("url")
            if (id.isBlank() || url.isBlank()) continue
            // Hauptlogo bevorzugen: ohne Feed, aktuell verwendet
            var s = 0
            if (o.isNull("feed")) s += 2
            if (o.optBoolean("in_use", true)) s += 1
            if ((score[id] ?: -1) < s) {
                score[id] = s
                result[id] = url
            }
        }
        map = result
        return result
    }

    fun clear(cacheDir: File) {
        map = null
        File(cacheDir, "logos.json").delete()
    }
}
