# Marv TV

IPTV-Player für Android-Handy, Tablet, Android TV und Fire TV – komplett auf Deutsch.

## Funktionen

- **Eigene Listen:** M3U-Link, M3U-Datei vom Gerät oder Xtream Codes (Server, Benutzername, Passwort)
- Xtream-Links (`get.php?username=…&password=…`) werden automatisch als Xtream-Zugang erkannt
- **Xtream:** Live-TV, Filme und Serien (mit Staffeln/Folgen), Kontostatus, „Jetzt/Danach“-Programm im Player
- **Entdecken:** Suche in über 10.000 frei verfügbaren öffentlichen Sendern (iptv-org, Free-TV), fertige Listen nach Land, Sprache und Kategorie
- Kategorien, Suche in jeder Liste, Favoriten, „Zuletzt gesehen“
- Player (ExoPlayer/Media3): HLS, DASH, MPEG-TS, MP4/MKV; Senderwechsel mit ▲/▼ oder Kanal-Tasten der Fernbedienung
- Erwachseneninhalte standardmäßig ausgeblendet
- Fernbedienung: gut sichtbarer Fokus-Rahmen, Seitenleiste auf großen Bildschirmen

## APK bauen

### Variante A – GitHub (ohne eigenen PC-Aufwand)
1. Neues Repository anlegen und diesen Ordner hochladen.
2. Unter **Actions** startet „APK bauen“ automatisch.
3. Nach ca. 5 Minuten unter dem Lauf bei **Artifacts** die Datei `MarvTV-apk` herunterladen und entpacken.

### Variante B – Android Studio
Ordner öffnen → warten bis Gradle fertig ist → **Build › Build APK(s)**.

## Installation auf Fire TV / Android TV
APK z. B. mit der App „Downloader“ oder per USB-Stick installieren
(„Apps aus unbekannten Quellen“ erlauben).

© marween
