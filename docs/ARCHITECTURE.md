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

### Archivtransport ab 0.1.5

Der Gerätetest mit 0.1.4 erreichte Archivkanal 1 und brach dort ab. Der bisherige periodische Versand von `F9 41` und `03 00` vermischte Empfangsfreigaben und Sitzungspflege. Der Mitschnitt enthält eine wiederholte Erneuerung von Register `0x0093` mit 10.000 ms; 0.1.4 führte diese nur bei der Initialisierung aus. `ArchiveLink` trennt nun beide Aufgaben: alle drei Sekunden denselben Keepalive senden, DATA-Schreibpakete nur nach einer `F9`-Freigabe des Geräts zulassen und nach jeweils 65 empfangenen DATA-Notifications 65 Empfangsplätze freigeben. Der Initialwert bleibt 128. BULK-Notifications verbrauchen dieses DATA-Fenster nicht. Warteschleifen für Antworten und Schleifen über bereits vorhandene Bereiche pflegen die Sitzung ebenfalls. Die Paketfenster folgen der im privaten Mitschnitt beobachteten Sequenz und sind durch ein unabhängiges simuliertes Gegenüber mit begrenztem Fenster und Sitzungsfrist geprüft; der Hardwaretest steht weiterhin aus.

Solarleistung, Batteriespannung und Strom werden vor Zusatzkanälen gelesen. Jede blockweise Übernahme invalidiert Statistik und Datenrevision. Die Oberfläche lädt bei geänderter Revision die aktuelle Verlaufsauswahl neu, auch wenn die Aufzeichnung pausiert oder nur Felder einer bestehenden Zeile ergänzt wurden. Android-Ablehnung, GATT-Callbackstatus und Verbindungsende werden getrennt diagnostiziert; Zähler beschreiben übernommene Werte und vollständige Blöcke, ohne Messinhalte oder Gerätekennungen zu protokollieren.

Der vollständige Archivabruf beider Geräte und die Anzeige der historischen Werte wurden anschließend vom Anwender mit 0.1.5 erfolgreich an der realen Anlage bestätigt.

## Automatische Verlaufsskalen ab 0.1.6

`scaleMode` ergänzt die bestehende Kachelkonfiguration mit `fixed` (Standard und bisherige Konfigurationen) oder `auto`. `chartRange` verwendet Minima und Maxima der ursprünglichen Daten im Kachelzeitraum, einschließlich Spitzen innerhalb gebündelter Kurvenpunkte und der exakten SQL-Zusammenfassung. Der angezeigte Durchschnitt bestimmt die Skala nicht. Für Leistungs-/Strom- und Mengenkacheln bleibt der Nullpunkt sichtbar; andere Messwerte nutzen ihre tatsächliche Spanne mit zehn Prozent Rand, bei konstanten Reihen eine endliche Mindestspanne. Der gültige Ladezustand bleibt innerhalb 0–100 %. Grenzen werden nach der Berechnung in die gewählte Einheit umgerechnet; Achsenbeschriftungen berücksichtigen kleine Einheitenwerte.

Die festen Grenzen bleiben unabhängig von der dynamischen Skala gespeichert. Leere Reihen erzeugen keine Messungen und weiterhin den Hinweis auf fehlende Werte. Zeitfenster, Auswertung, Anordnung und Bluetooth-Import bleiben unverändert.


## Unabhängige Livezahl und Übersichtsblöcke ab 0.1.7

`valueMode` wählt für Verlaufskacheln `history` (Migration bestehender Kacheln) oder `live`. Der Kurvenzeitraum und die historische Auswertung bleiben separat gespeichert. Livezahlen verwenden denselben frischen Empfang wie Zahlenkacheln: ohne Empfang, nach 45 Sekunden oder bei Pause keine Ersatzwerte aus der Datenbank. Null bleibt ein gültiger Messwert. Eine fehlgeschlagene Historienabfrage versteckt einen vorhandenen Livewert nicht.

`blockOrder` speichert Kacheln, Wetter, kleinen Verlauf und Aufzeichnungsschalter. Die Normalisierung ergänzt fehlende Blöcke und entfernt unbekannte oder doppelte Einträge. Umordnen ändert keine Kachelpositionen. `miniRange` wird separat als geprüfte App-Einstellung gespeichert (`sunrise`, `today`, `last6h`). Die Wetterabfrage ergänzt ausschließlich `sunrise`; die 14-Tage-Anzeige bleibt bei Symbol und Sonnenstunden. Lokale ISO-Zeiten werden mit der IANA-Zeitzone des Wetterorts in Zeitpunkte übersetzt. Veraltete, fehlende, ungültige oder noch bevorstehende Sonnenaufgänge ergeben den ausdrücklich beschrifteten Tageszeitraum. Alte Wetter-Caches werden einmal neu geladen.

## Berechnete Energie ab 0.1.7

`HistoryQuery.rawPower` liefert ursprüngliche Leistungspunkte statt gebündelter Kurvenmittelwerte. `EnergyIntegral` integriert linear zwischen benachbarten Messungen über die tatsächlich verstrichene Zeit. Sonnenleistung ergibt Solarertrag, nur der negative Anteil der Shunt-Leistung ergibt Batterieentnahme; ein Nulldurchgang wird aufgeteilt, Ladung verrechnet keine frühere Entladung. Messintervalle werden an Zeitraumgrenzen abgeschnitten. Ohne ein verwendbares Intervall bleibt die Energie null/fehlend, bei erfasster Nullleistung dagegen 0 Wh.

Datenströme nach Gerät, Quelle und Archivauflösung bilden nur eigene Intervalle. Bei zeitlicher Überlappung wird pro Abschnitt einmal gezählt: Aufzeichnung vor Archiv vor CSV, bei Archivdaten die feinere Auflösung. Die Anwendung unterstützt weiterhin ein eingerichtetes Gerät je Typ. Lücken über 1,5 Archivintervalle bzw. 90 Sekunden bei unbekannter Auflösung werden ausgelassen; weder Zeitraumränder noch fehlende Abschnitte werden hochgerechnet. Die Abdeckung wird als Anteil der erfassten Dauer am vergangenen Auswahlzeitraum angezeigt. Die Kurve zeigt kumulierte Energie seit Beginn der Auswahl, behält sichtbare Unterbrechungen und ist auf etwa 1.200 Endpunkte begrenzt; die Summe wird davor aus allen verwendbaren Rohintervallen berechnet.

Der Victron-Tageswert bleibt separat erhalten. Ein fein aufgelöster Rechenwert ist keine Zusage einer höheren Messgenauigkeit, insbesondere bei gröberen Gerätearchiven. Batterieentnahme bezeichnet Energie aus der Batterie; direkter Solarverbrauch ist damit nicht erfasst. Unbekannte zusätzliche Ladequellen werden nicht zu einem vermeintlich gemessenen Gesamtverbrauch verrechnet.

## Textkontrast ab 0.1.7

Die alte Anpassung verarbeitete `#fff` wie einen sechsstelligen Farbwert und erzeugte dadurch im hellen Design weiße Zahlen. `readableColor` expandiert Kurzfarben und berechnet den zusammengesetzten Hintergrund aus Kachelfarbe, Deckkraft und Seitenfarbe. Zahlen sowie kleine Beschriftungen erhalten eine lesbare Textfarbe unabhängig von der dekorativen Farbanpassung; Kurven und Instrumente behalten deren Option. Zielkontrast ist 7:1, bei mittleren eigenen Hintergründen die bestmögliche Schwarz-/Weißrichtung. Die Browserprüfung misst mindestens 4,5:1 für gerenderte Zahlen, Einheiten, Titel und Achsen in allen zwölf Designkombinationen sowie eigenen und transparenten Flächen.


## Kachelzeitraum und Einstellungssicherung ab 0.1.8

`period.mode=sunrise` ist je gespeicherter Messgröße wählbar. `windowFor` erhält den Wettercache aus dem aktuellen Snapshot und verwendet dieselbe Datum-/Zeitzonenauflösung wie der kleine Übersichtsverlauf. Das Abfragefenster beginnt am heutigen Sonnenaufgang des Wetterorts. Die bestehenden Energieabfragen integrieren dadurch nur dieses Fenster; vorhandene Daten werden nicht umgeschrieben. Fehlende, veraltete oder bevorstehende Sonnenaufgänge behalten die Auswahl, zeigen aber ausdrücklich „heute (Ersatz)“. Die Kacheleinstellungen erklären den Ersatz und nennen bei gültigem Sonnenaufgang die lokale Uhrzeit am Wetterort. Livezahl und Kurvenzeitraum bleiben unabhängig.

Neue Installationen und das bewusste Wiederherstellen der Standardübersicht verwenden die sieben Kacheln Ladezustand, Solarleistung, Batterieentnahme, Solarertrag, Spannung, Restlaufzeit und Batteriebilanz. Die beiden oberen Spalten sind gegeneinander versetzt, die untere Reihe besteht aus drei Kacheln. Gespeicherte Übersichten werden weiterhin normalisiert und nicht automatisch durch diesen Standard ersetzt.

`reverseSign` ist für die beiden berechneten Energiekacheln optional. Die Darstellung spiegelt die Energiesumme, Kurvenpunkte und feste Skalen um null; die automatische Skala berücksichtigt die gespiegelten Extrema. Feste Grenzen und Messdaten bleiben in ihrer ursprünglichen Form gespeichert, damit Einheitenwechsel und erneutes Umschalten keine doppelte Umkehr erzeugen. Null bleibt null, fehlende Werte bleiben leer. Die Einstellung ist unabhängig von `invert` für die Reihenfolge der Rundinstrument-Farben und wird im Konfigurationsexport erhalten.

Die portable JSON-Datei hat Format `wattnpit-settings`, Version 1, maximal 256.000 Bytes. `settings-backup.js` baut sie aus explizit ausgewählten Einstellungen, der normalisierten Dashboard-Konfiguration, Solargruppen und dem optionalen Wetterort. Unbekannte Felder werden entfernt, unbekannte Messgrößen oder Zeiträume und beschädigte Pflichtdaten abgelehnt. Geräte, Verbindungsschlüssel, PINs, Messdaten und Wettercache gehören nicht zum Format. Die Dateivorschau zeigt Umfang und Wetterort; vor „Übernehmen“ wird nichts geändert.

Android verwendet die System-Dateiauswahl für Erstellen und Öffnen. `ConfigurationBackup` prüft den nativen Übergang erneut und wählt erlaubte Felder aus. Nach vollständiger Prüfung schreibt `Repository.importConfiguration` alle portablen Einstellungen mit einem einzigen SharedPreferences-Commit. Die Geräte-, Schlüssel- und Aufzeichnungseinträge sowie die Datenbank werden nicht angefasst. Bei geändertem Wetterort wird nur der Wettercache verworfen und die freiwillig konfigurierte Vorschau neu geladen. Der Exportinhalt wird für Activity-Neuanlagen zwischengespeichert; Importvorschauen warten gegebenenfalls auf die fertig geladene Oberfläche.

## Wetterkachel ab 0.1.9

`weather` ist ein Widget im Kachelkatalog und verwendet dieselben Positionen, Größen und Speicherwege wie Messkacheln. Es führt keine numerische Verlaufabfrage aus. Die Darstellung bleibt bei 14 Tagen mit Symbol und Sonnenstunden; die Tagesreihe scrollt innerhalb der Kachel. Das bestehende Wetter-DOM wird bei Raster-Neuanlagen versetzt statt neu erzeugt, damit die Aktualisierungsbedienung und der Scrollzustand erhalten bleiben. Lange Ortsnamen werden entwertet und auf eine Zeile begrenzt.

`blockOrder` enthält nun `tiles`, `history` und `record`. Eine alte Reihenfolge mit `weather` ergänzt genau eine Wetterkachel am Ende beider Rasterprofile; vorhandene Kacheln werden nicht verschoben. Neue Konfigurationen ohne Wetterkachel werden nicht automatisch ergänzt, sodass bewusstes Entfernen dauerhaft bleibt. Die native Sicherungsprüfung akzeptiert alte Vierer- und neue Dreier-Reihenfolgen; die Oberfläche normalisiert beides vor der Übernahme. Export und Import behalten die Widget-Anordnung. Ohne Wetterort wird die Kachel außerhalb des Bearbeitungsmodus verborgen und bei der Rasterhöhe ausgelassen.

Ab 0.1.9 setzt die einmalige Migration `dischargeSignVersion=1` bestehende Batterieentnahme-Kacheln auf die negative Anzeige. Neue Batterieentnahme-Kacheln beginnen ebenfalls negativ; spätere ausdrückliche Vorzeichenänderungen bleiben erhalten. Die Sicherung übernimmt den Migrationsstand. Im berechneten Energieverlauf werden Batterieentnahme-Punkte und Extremwerte beim Darstellen nach negativ gespiegelt; Datenbank und Integration behalten ihre unveränderten Beträge.
