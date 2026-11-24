package de.marween.tv

import java.io.BufferedReader

/** Liest M3U/M3U8-Listen zeilenweise (auch sehr große Listen). */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v", "webm", "mpg", "mpeg")

    fun parse(reader: BufferedReader, playlistId: String): List<Channel> {
        val result = ArrayList<Channel>()
        var name: String? = null
        var logo = ""
        var group = ""
        var extGrp = ""
        var ua = ""
        var ref = ""

        reader.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"] ?: ""
                    group = (attrs["group-title"] ?: "").replace(";", " · ")
                    attrs["http-user-agent"]?.let { ua = it }
                    attrs["http-referrer"]?.let { ref = it }
                    name = extractName(line).ifBlank { attrs["tvg-name"] ?: "Unbenannt" }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGrp = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    when {
                        opt.startsWith("http-user-agent=", true) -> ua = opt.substringAfter('=')
                        opt.startsWith("http-referrer=", true) || opt.startsWith("http-referer=", true) ->
                            ref = opt.substringAfter('=')
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    if (name != null || line.contains("://")) {
                        val ext = line.substringBefore('?').substringAfterLast('.', "").lowercase()
                        val kind = if (ext in videoExt || line.contains("/movie/") || line.contains("/series/")) Kind.MOVIE else Kind.LIVE
                        result.add(
                            Channel(
                                name = name ?: line.substringAfterLast('/'),
                                url = line,
                                logo = logo,
                                group = group.ifBlank { extGrp }.ifBlank { "Ohne Gruppe" },
                                kind = kind,
                                userAgent = ua,
                                referrer = ref,
                                playlistId = playlistId,
                            )
                        )
                    }
                    name = null; logo = ""; group = ""; extGrp = ""; ua = ""; ref = ""
                }
            }
        }
        return result
    }

    /** Der Name steht nach dem ersten Komma außerhalb von Anführungszeichen. */
    private fun extractName(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return line.substring(i + 1).trim()
        }
        return ""
    }
}
