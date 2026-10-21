# Ausarbeitung: gezielter Archivabgleich

Entwicklungsstand auf Basis von 1.3.5 (`195a0b46f874f7e834d30f4526737df67908f6b2`). Keine APK und kein Release. Der [negative Nachweis für 1.3.5](archive-delta-1.3.5.md) bleibt unverändert erhalten.

## Änderungen

- Die vorhandene Tabelle `archive_coverage` bleibt der dauerhafte Fortschritt pro Gerät, Kanalregister und Auflösung. Erfolgreich gelieferte Antworten einschließlich NA werden sofort bestätigt. Die pauschale Wiederholung der letzten zwei Intervalle entfällt. Ein bestätigter Fehlwert erzeugt weiterhin keinen Messwert und keine Null.
- `HistoryDb.archiveBlock` speichert Messwerte, daraus abgeleitete Leistung und Bestätigungen in derselben SQLite-Transaktion. Ein Fehler rollt den gesamten Block zurück. Erst nach dem Commit wird die Warteschlange weitergeschaltet. Die Abschlussprüfung liest ausschließlich den gespeicherten Stand; eine zusätzliche flüchtige Bestätigungsliste ist nicht mehr nötig.
- Die gelesene Archivaufteilung samt Zeitzuordnung wird pro Gerät zwischengespeichert. `Repository.archiveNeedsData` prüft damit vor einer Verbindung denselben lokalen Fehlbereichsplan. Zeitablauf allein erteilt keine Startfreigabe. Fehlen Metadaten oder Daten, folgt der echte Geräteabgleich mit frischen Metadaten. Der Cache ist keine Bestätigung von Messwerten.
- Neue Zeitbereiche werden zunächst der laufenden Live-Aufzeichnung überlassen: 1,5 Speicherintervalle, bei 60 Sekunden also 90 Sekunden. Dieser kurze Aufschub markiert nichts als vorhanden. Größere Empfangslücken bleiben offen und werden nachgeladen. Im Vordergrund wird der Bedarf regelmäßig auf einem Arbeitsthread geprüft; der Zeitabstand begrenzt lokale Prüfungen und Versuche. Bei geschlossener Activity startet dieser Timer keine Archive.
- Jeder Lauf behält sein einmal bestimmtes Ende. Auch eine notwendige Neuplanung nach geänderten Geräteantworten zieht dieses Ende nicht nach vorne. Nach erfolgreichem Abschluss bleibt neuer Bedarf dem nächsten Lauf vorbehalten.

Die Datenbankversion und alle vorhandenen Daten bleiben erhalten. Die Energieformeln und Kurvendarstellung wurden nicht verändert.

## Ausgeführter Nachweis

| Fall | 1.3.5 | Ausarbeitung |
|---|---:|---:|
| Erneuter Abruf derselben bestätigten NA-Punkte nach fünf Minuten | 6 Punkte | 0 Punkte |
| Erneut geladene Punkte nach Abbruch und frischem Prozess | 3 Punkte | 0 Punkte |
| Messwertabfragen bei vollständig vorhandener Live-Aufzeichnung | 0 | 0 |
| Startfreigabe trotz vollständig vorhandener Aufzeichnung | erlaubt | verweigert |
| Offene Punkte nach vollständigem Drei-Wochen-Abruf | 0 | 0 |

Der Transportnachweis verwendet dieselben Datenfälle, vier Auflösungen und denselben unabhängigen Geräteersatz wie der negative Nachweis. Jede Phase öffnet SQLite in einem neuen JVM-Prozess. Erfasst werden tatsächlich erzeugte EC5B-Abfragen und erneut empfangene identische Kanal-/Auflösungs-/Zeitpunkte. [Ergebnisse und Quellhashes](archive-delta-fix.json).

Zusätzlich wurden die tatsächlichen Methoden von `HistoryDb` und `Repository` durch einen kleinen JVM-Adapter für Android-Speicherzugriffe gegen echte SQLite ausgeführt. **16 Prüfungen bestanden**: unter anderem ein absichtlich scheiternder Checkpoint, vollständiges Rollback samt Erhalt der vorherigen Bestätigung, Wiederaufnahme in einem neuen Prozess, fortlaufende Live-Aufzeichnung ohne Startfreigabe, ältere Lücken trotz frischer Livewerte, drei Tage und drei Wochen ohne Daten sowie unabhängige Geräte. [Ergebnisse und Quellhashes](archive-storage-fix.json), [Protokoll](archive-storage-fix.log).

Alle **172 Java-Kerntests** bestanden. [Protokoll](archive-core-fix.log). Die vollständigen Java-Appquellen wurden außerdem separat gegen Android API 35 auf Typfehler geprüft. Es wurde kein Gradle-Lauf, keine APK-Paketierung und keine Signierung ausgeführt.

## Wiederholen

Python 3 und JDK 17; Abhängigkeiten aus Maven Central: `junit:junit:4.13.2`, `org.hamcrest:hamcrest-core:1.3`, `org.xerial:sqlite-jdbc:3.46.1.0`, `org.slf4j:slf4j-api:1.7.36` und für den Speicheradapter `org.robolectric:android-all:15-robolectric-12468137`. Die Dateien liegen hier beispielhaft unter `/tmp/wattpit-deps`; der Android-JAR wird dabei `android-api35.jar` genannt.

```sh
python tools/check-core.py --dependencies /tmp/wattpit-deps --output /tmp/wattpit-core
python tools/prove-archive-delta.py --output /tmp/wattpit-delta --sqlite-jar /tmp/wattpit-deps/sqlite-jdbc-3.46.1.0.jar --slf4j-jar /tmp/wattpit-deps/slf4j-api-1.7.36.jar
python tools/prove-archive-storage.py --output /tmp/wattpit-storage --android-jar /tmp/wattpit-deps/android-api35.jar --sqlite-jar /tmp/wattpit-deps/sqlite-jdbc-3.46.1.0.jar --slf4j-jar /tmp/wattpit-deps/slf4j-api-1.7.36.jar
```

## Grenze der Aussage

Nachgewiesen sind Planung, gespeicherter Fortschritt, Transaktionsverhalten und lokale Startauswahl. Android-Servicelebenszyklus, Funkverbindungen und die Firmware der tatsächlichen Geräte werden hier nicht ausgeführt. Der Patch behandelt eine gelieferte Antwort unter ihrer Zeit und Auflösung als bestätigt. Ob eine Firmware bereits gelieferte jüngste Archivpunkte später unter derselben Identität verändert, wird durch den deterministischen Geräteersatz nicht geprüft. Die Ergebnisse sind deshalb kein abgeschlossener Hardware- oder Auslieferungsnachweis.
