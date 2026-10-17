# Technische Grundlage

Watt’n’Pit ist eine Android-App mit lokaler Energie-Datenhaltung. Nur die optionale Wetterfunktion nutzt Internet. Eine native Java-Schicht liest Bluetooth, verwaltet Schlüssel im Android Keystore und speichert Messwerte in SQLite. Die vollständig mitgelieferte Oberfläche läuft in einer WebView; sie lädt keine externen Bibliotheken, Schriften oder Ressourcen. Fremde Navigation und Dateizugriff außerhalb der App-Assets sind gesperrt.

## Geräte und Livewerte

Der BLE-Empfang liest Manufacturer Data mit Victrons Hersteller-ID `0x02e1` und Product Advertisement `0x10`. Unterstützte Datensatztypen sind Solar Charger `0x01` und Battery Monitor `0x02`. Die Datensätze werden mit AES-128-CTR entschlüsselt. Header-Prüfung, NA-Sentinelwerte, vorzeichenbehaftete und nicht byte-ausgerichtete Felder sind berücksichtigt. Gerätetyp und Schlüssel werden nicht geraten.

Primärquelle: [Victrons Extra manufacturer Data, 14.12.2022](https://communityarchive.victronenergy.com/storage/attachments/extra-manufacturer-data-2022-12-14.pdf). [Offizielle Ankündigung und Schlüsseleinrichtung](https://communityarchive.victronenergy.com/questions/187303/victron-bluetooth-advertising-protocol.html).

Es wird ein SmartSolar und ein SmartShunt eingerichtet. Das Dashboard zeigt nur empfangene Livewerte; nach 45 Sekunden ohne nutzbare Daten bzw. nach dem Pausieren werden Werte als fehlend dargestellt. Gespeicherte Messwerte bleiben im Verlauf. Der Solarstrom des Reglers und der Batteriestrom des Shunts sind unterschiedliche Messgrößen. Verbraucherleistung oder mobile PV-Leistung werden nicht aus ihrer Differenz behauptet.

## Aufzeichnung und Vorrang

Live-Datensätze tragen `origin=live`, Gerätearchive `origin=archive`, generische CSV-Daten `origin=csv`. Native Backups behalten ihre Quelle und die Archivauflösung. Die Datenbank ignoriert doppelte Kombinationen aus Zeitstempel, Gerät und Quelle. Bei Archivblöcken werden ergänzende Felder zusammengeführt; feinere vorhandene Werte werden nicht durch gröbere ersetzt.

Vor dem Speichern gleicht `ArchiveMerge` jede Messgröße mit dem gesamten lokalen Bestand ab. Livewerte desselben Geräts innerhalb maximal 45 Sekunden (bei kurzen Archivintervallen entsprechend enger) haben Vorrang. CSV ohne bekannte Auflösung gilt nur bei praktisch gleichem Zeitpunkt und Zahlenwert als bereits vorhanden. Generische CSV-Gerätekennungen werden dem eingerichteten Gerätetyp zugeordnet; es ist weiterhin nur ein Gerät je Typ konfigurierbar. Fehlende Kanäle bleiben ergänzbar. Identische Zahlen an verschiedenen Zeitpunkten bleiben eigenständige Messpunkte.

`Alle Daten` bevorzugt Aufzeichnungen pro Messgröße und Gerät sowie feinere Archivpunkte in überlappenden Bereichen; Gerätearchive haben Vorrang vor CSV. Die Rohquellen sind getrennt auswählbar und exportierbar. `period` bewahrt die Archivauflösung in Sekunden und steuert die Darstellung von Datenlücken.

Lange Fenster werden in bis zu etwa 600 Zeitabschnitte je Quelle/Gerät gebündelt. Die Linie zeigt den Mittelwert; Minimum/Maximum bleiben als Schattierung sichtbar. Nach dem Zoomen werden die Daten mit höherer Zeitauflösung neu abgefragt. Empfangslücken werden nicht durch pauschale Nullwerte ersetzt.

## Gespeicherte Trends ab 0.1.3

`ArchiveService` ist ein zeitlich begrenzter Foreground-Service. Er verbindet eingerichtete Geräte seriell über Android GATT und die UUID-Familie `306b0001…0004`. Android verwaltet die Bluetooth-PIN/Kopplung; ab 0.1.4 wird eine fehlende Kopplung vor dem GATT-Verbindungsaufbau angefordert. Bereits bestehende Kopplungen lösen keine zusätzliche PIN-Abfrage aus; der Sofortanzeige-Schlüssel ist dafür kein Ersatz. Operationen sind serialisiert, begrenzt und abbrechbar. Eine aktive VictronConnect-Verbindung muss vorher getrennt werden. Nach einem Verbindungsabbruch bleiben bestätigte Blöcke erhalten; erneuter App-Start oder manueller Abruf setzt den Abgleich fort.

`ArchiveCodec` liest definite CBOR-Registerantworten samt fragmentierten Notifications. Statusantworten werden nicht als Daten interpretiert. EC5D meldet die Kanalregister, EC4A–EC59 Auflösungen und Zeitreferenzen; EC5B liefert Trendblöcke mit Kanal, Gerätezeit, Anzahl und Intervall, neueste Werte zuerst. Bekannte Feldtypen definieren Breite, Vorzeichen, Skalierung und Fehlwert. Unbekannte Kanäle werden übersprungen. Zusatzeingänge unbekannter Betriebsart bleiben Rohwerte. PV-Eingangsspannung ist getrennt von der batteriebezogenen Reglerspannung.

Die Zeitzuordnung verwendet bestätigte gespeicherte EC5F/EC63-Anker innerhalb der EC64-Neustartgrenzen. Vorgeschlagene, aber nicht bestätigte Uhrzeiten werden nicht übernommen. Ohne sicheren Anker wird der betroffene Wert nicht importiert. EC5C (Archiv löschen), Gerätezeit- und Ladekonfigurationsschreibzugriffe sind nicht freigegeben. Schreibzugriffe betreffen nur die Transportinitialisierung, Session-Keepalive und die allowlist-geprüften Abfragen EC5B, EC63 und EC64.

SQLite-Version 3 ergänzt Auflösung, PV-Spannung, Zusatz-Rohwert, Geräte-Tagesdaten, Shunt-Gesamtwerte und `archive_coverage`. Vollständig gelesene Bereiche werden je Gerät, Kanalregister und Auflösung zusammengeführt. Erst nach bestätigter Speicherung eines Blocks wird seine Abdeckung gespeichert. Neue bzw. noch veränderliche Randwerte werden erneut geprüft; bereits abgedeckte ältere Bereiche können ohne erneuten Download übersprungen werden. Import und Aufzeichnung bleiben eigenständige Quellen. Keine privaten Messdaten oder Kopplungsdaten liegen im Repository oder APK.

Die Implementierung ist an einer privaten Aufzeichnung von klassischem SmartSolar und SmartShunt gegengeprüft. Aktives GATT, Pairing und die automatische Importfolge sind noch am Gerät zu testen. Andere Modellfamilien sind nicht allein wegen der Marke kompatibel.

- [Victron: gespeicherte Trends](https://www.victronenergy.de/media/pg/VictronConnect_app/de/stored-trends---instant-readout.html)
- [Offizielles MPPT-Registerprotokoll für Tagesdaten](https://www.victronenergy.com/upload/documents/BlueSolar-HEX-protocol.pdf)
- [CBOR RFC 8949](https://www.rfc-editor.org/rfc/rfc8949)

## Wetter und Farbdesigns

`WeatherClient` ist der einzige Netzwerkpfad. Nur feste HTTPS-Hosts von Open-Meteo sind erlaubt, Redirects sind gesperrt, Antworten und Wartezeiten begrenzt. Ein gewählter Ort aktiviert die Funktion; sie ist standardmäßig aus. Suchtext wird an die Geocoding-API gesendet, Koordinaten an die Forecast-API. Abgerufen werden ausschließlich täglicher Wettercode und Sonnenscheindauer für 14 Tage (`timezone=auto`). Sekunden werden in Sonnenstunden umgerechnet; fehlende Daten bleiben leer. Der Cache wird bei App-Start nach sechs Stunden erneuert und bei Netzfehlern weiter angezeigt. Ortswechsel und Ausschalten verwerfen den alten Ortscache; veraltete Antworten überschreiben keine neue Auswahl. Die WebView bleibt vollständig lokal und darf selbst keine Netzverbindungen öffnen.

`extras.css` definiert sechs vollständige Paletten in hell und dunkel. Automatische Kachelfarben folgen der Palette und werden auf ausreichenden Textkontrast angepasst. Gespeicherte individuelle Farben und Positionen bleiben bestehen.

## Bauen und signieren

Java 17, Android SDK 35, Gradle 8.11.1, Android Gradle Plugin 8.9.2. `./gradlew testDebugUnitTest lintDebug assembleDebug` prüft und baut die Entwicklungsversion.

Für eine dauerhaft aktualisierbare Release-APK werden `WATTPIT_KEYSTORE` und `WATTPIT_SIGNING_PASSWORD` außerhalb des Repositorys gesetzt. Der Keystore-Alias lautet `wattpit`. Der private Signaturschlüssel gehört niemals ins Repository. `./gradlew assembleRelease` verwendet ihn, wenn er gesetzt ist.

Die Veröffentlichung verwendet einen temporären `publish/<Version>`-Branch mit einem zusätzlichen Commit, der ausschließlich `.release/`-Dateien enthält. Nach erfolgreichen Tests prüft GitHub Actions die vorbereitete signierte APK, erstellt Prüfsumme und Release und setzt den Release-Tag auf den **vorherigen Quellcode-Commit**. Der Branch kann anschließend gelöscht werden; auf `main` liegen keine APK-Dateien oder Signaturschlüssel. CI-Debug-Artefakte dienen ausschließlich der Entwicklung.


## Variable Übersicht ab 0.1.2

`dashboard-core.js` enthält den Werte-Katalog, Einheitenumrechnung, Prüfung gespeicherter Konfigurationen und Zeitfenster. `dashboard.js` rendert die Kacheln und bearbeitet jede Kachel unabhängig. Positionen und Größen werden für schmale und breite Ansichten getrennt gespeichert; Überlappungen verändern keine Nachbarkacheln. Die native Bridge speichert die versionierte Konfiguration unter `dashboard-v1` in den bestehenden privaten App-Einstellungen. App-Kennung, Datenbank und Signatur bleiben beim Namenswechsel unverändert.

Verlaufskacheln verwenden die vorhandenen SQLite-Messwerte. `HistoryQuery.summary` berechnet Durchschnitt, Minimum, Maximum und Anzahl direkt aus den ausgewählten Originalpunkten; `latest` liefert den tatsächlich zuletzt gespeicherten Wert. Die Kurve wird separat in bis zu etwa 600 Abschnitte gebündelt. Zeitfensterabfragen verwenden dieselbe Quellenpriorität wie die große Verlaufsansicht. Fehlende Werte bleiben null, Empfangslücken bleiben in der Kurve sichtbar. Ladezustand × Kapazität liefert rechnerische Ah; zusätzlich mit momentaner Spannung multipliziert ergibt sich eine gekennzeichnete Wh-Näherung. Tageserträge sind Zählerstände und werden nicht über mehrere Tage summiert.

Die Dashboard-Konfiguration bleibt mit 0.1.2 kompatibel; zusätzliche Messgrößen verändern die gespeicherte Anordnung nicht.

## Verbindungskorrektur und Solaranlage ab 0.1.4

Im ersten Gerätetest scheiterte 0.1.3 beim aktiven Archivaufbau. Beim Vergleich mit dem Mitschnitt fehlten die Sitzungskontexte 1 und 3. `ArchiveSession` bildet jetzt die vollständige Initialisierung nach: Protokollbegrüßung, Kontext 0, Session-Keepalive, Kontext 1 und Archivkontext 3. Auf Begrüßung und Kontextbestätigungen wird gewartet; Ablehnungen und Fristüberschreitungen stoppen den Lauf mit Angabe des Schritts. `ArchiveCodec.Stream` gibt diese Bestätigungen vollständig an den Sitzungsablauf weiter, statt sie nur zu überspringen.

Der Client handelt MTU 517 aus; die Antwort des Geräts entscheidet über die nutzbare Größe. Der Mitschnitt zeigt MTU 77. Ablehnung oder fehlende MTU-Antwort fallen auf den BLE-Standard zurück. GATT-Rückrufe laufen auf dem expliziten Main-Handler, damit sie nicht vor dem Speichern der Verbindungsreferenz eintreffen. Operationsantworten werden nach Typ zugeordnet. Der passive Scan pausiert während des Archivimports und wird danach mit unveränderter Aufzeichnungsabsicht fortgesetzt.

Lokale Diagnosen enthalten nur vordefinierte Schrittbezeichnungen und numerische Statuswerte. Rohpakete, Geräteadressen, Namen, Schlüssel und Messwerte werden nicht protokolliert. Eine zusätzliche Redaktion entfernt Adress-, Schlüssel- und sechsstellige Code-Muster vor dem Kopieren. Die letzten Berichte für Regler und Shunt bleiben getrennt auf dem Telefon. Ein erfolgreiches Hardware-Ende ist damit noch nicht nachgewiesen; der neue Ablauf muss erneut an der Anlage getestet werden.

`equipment-v1` speichert eine geprüfte Liste von bis zu 32 Solarkreisen in privaten App-Einstellungen. Pro Kreis: Bezeichnung, Modulanzahl, Nennleistung je Modul, Montageart, Reglerbezeichnung und Messwertquelle (`solar`, `shunt`, `none`). Die Standardliste ist leer. Nennleistung und Modulanzahl werden nur als erklärte Hardware summiert. Keine Angabe schreibt Reglerparameter oder erzeugt historische Messungen. Die Hinweise der Übersicht entstehen aus der gewählten Zuordnung; fest eingebaute Anlagenbehauptungen entfallen. Die Oberfläche entwertet HTML aus Freitexten und bewahrt offene Eingaben während laufender Statusaktualisierung.
