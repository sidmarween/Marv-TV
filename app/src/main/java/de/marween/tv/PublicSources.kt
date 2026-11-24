package de.marween.tv

/** Frei zugängliche, öffentliche Senderlisten (iptv-org, Free-TV). */
data class PublicSource(val title: String, val subtitle: String, val url: String) {
    fun asPlaylist() = Playlist(
        id = "public:$url",
        name = title,
        type = PlaylistType.M3U,
        url = url,
        isPublic = true,
    )
}

object PublicSources {
    private const val ORG = "https://iptv-org.github.io/iptv"

    /** Durchsuchbarer Gesamtindex aller öffentlichen Sender */
    const val SEARCH_INDEX = "$ORG/index.m3u"

    val featured = listOf(
        PublicSource("Deutschsprachige Sender", "Alle frei verfügbaren Sender in deutscher Sprache", "$ORG/languages/deu.m3u"),
        PublicSource("Deutschland", "Frei verfügbare Sender aus Deutschland", "$ORG/countries/de.m3u"),
        PublicSource("Österreich", "Frei verfügbare Sender aus Österreich", "$ORG/countries/at.m3u"),
        PublicSource("Schweiz", "Frei verfügbare Sender aus der Schweiz", "$ORG/countries/ch.m3u"),
    )

    val categories = listOf(
        cat("Nachrichten", "news"),
        cat("Sport", "sports"),
        cat("Kinder", "kids"),
        cat("Musik", "music"),
        cat("Filme", "movies"),
        cat("Serien", "series"),
        cat("Dokumentationen", "documentary"),
        cat("Unterhaltung", "entertainment"),
        cat("Comedy", "comedy"),
        cat("Kultur", "culture"),
        cat("Bildung", "education"),
        cat("Wissenschaft", "science"),
        cat("Kochen", "cooking"),
        cat("Reisen", "travel"),
        cat("Klassiker", "classic"),
        cat("Wetter", "weather"),
    )

    val countries = listOf(
        country("Luxemburg", "lu"),
        country("Liechtenstein", "li"),
        country("Niederlande", "nl"),
        country("Belgien", "be"),
        country("Frankreich", "fr"),
        country("Italien", "it"),
        country("Spanien", "es"),
        country("Großbritannien", "uk"),
        country("Polen", "pl"),
        country("Türkei", "tr"),
        country("Griechenland", "gr"),
        country("USA", "us"),
    )

    val collections = listOf(
        PublicSource("Alle öffentlichen Sender weltweit", "iptv-org – über 10.000 frei verfügbare Sender", SEARCH_INDEX),
        PublicSource("Free-TV", "Kuratierte Liste frei empfangbarer Sender", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"),
    )

    val all: List<PublicSource> get() = featured + categories + countries + collections

    private fun cat(name: String, slug: String) =
        PublicSource(name, "Öffentliche Sender: $name (weltweit)", "$ORG/categories/$slug.m3u")

    private fun country(name: String, code: String) =
        PublicSource(name, "Frei verfügbare Sender aus $name", "$ORG/countries/$code.m3u")
}
