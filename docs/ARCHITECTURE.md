# Technische Grundlage

Watt’n’Pit ist eine Android-App mit lokaler Energie-Datenhaltung. Nur die optionale Wetterfunktion nutzt Internet. Eine native Java-Schicht liest Bluetooth, verwaltet Schlüssel im Android Keystore und speichert Messwerte in SQLite. Die vollständig mitgelieferte Oberfläche läuft in einer WebView; sie lädt keine externen Bibliotheken, Schriften oder Ressourcen. Fremde Navigation und Dateizugriff außerhalb der App-Assets sind gesperrt.

## Geräte und Livewerte

Der BLE-Empfang liest Manufacturer Data mit Victrons Hersteller-ID `0x02e1` und Product Advertisement `0x10`. Unterstützte Datensatztypen sind Solar Charger `0x01` und Battery Monitor `0x02`. Die Datensätze werden mit AES-128-CTR entschlüsselt. Header-Prüfung, NA-Sentinelwerte, vorzeichenbehaftete und nicht byte-ausgerichtete Felder sind berücksichtigt. Gerätetyp und Schlüssel werden nicht geraten.

Primärquelle: [Victrons Extra manufacturer Data, 14.12.2022](https://communityarchive.victronenergy.com/storage/attachments/extra-manufacturer-data-2022-12-14.pdf). [Offizielle Ankündigung und Schlüsseleinrichtung](https://communityarchive.victronenergy.com/questions/187303/victron-bluetooth-advertising-protocol.html).

Es wird ein SmartSolar und ein SmartShunt eingerichtet. Das Dashboard zeigt nur empfangene Livewerte; nach 45 Sekunden ohne nutzbare Daten bzw. nach dem Pausieren werden Werte als fehlend dargestellt. Gespeicherte Messwerte bleiben im Verlauf. Der Solarstrom des Reglers und der Batteriestrom des Shunts sind unterschiedliche Messgrößen. Verbraucherleistung oder mobile PV-Leistung werden nicht aus ihrer Differenz behauptet.

## Aufzeichnung und Vorrang

Live-Datensätze tragen `origin=live`, Gerätearchive `origin=archive`, generische CSV-Daten `origin=csv`. Native Backups behalten ihre Quelle und die Archivauflösung. Die Datenbank ignoriert doppelte Kombinationen aus Zeitstempel, Gerät, Quelle und Auflösung. Bei Archivblöcken werden ergänzende Felder derselben Auflösung zusammengeführt; verschiedene Auflösungen bleiben erhalten.

`ArchiveMerge` verwirft nur identische bereits gespeicherte Archivwerte desselben Geräts, Felds, Zeitpunkts und derselben Auflösung. Ein für einen Anschluss benötigter Wert wird nicht wegen eines nahen Live- oder CSV-Punkts entfernt. Fehlende Kanäle bleiben ergänzbar; gleiche Zahlen zu unterschiedlichen Zeitpunkten bleiben eigenständige Messpunkte.

`Alle Daten` bevorzugt Aufzeichnungen pro Messgröße und Gerät sowie feinere Archivpunkte in überlappenden Bereichen; Gerätearchive haben Vorrang vor CSV. Die Rohquellen sind getrennt auswählbar und exportierbar. `period` bewahrt die Archivauflösung in Sekunden und steuert die Darstellung von Datenlücken.

Lange Fenster werden in bis zu etwa 600 Zeitabschnitte je Quelle/Gerät gebündelt. Die Linie zeigt den Mittelwert; Minimum/Maximum bleiben als Schattierung sichtbar. Nach dem Zoomen werden die Daten mit höherer Zeitauflösung neu abgefragt. Empfangslücken werden nicht durch pauschale Nullwerte ersetzt.

## Gespeicherte Trends ab 0.1.3

`ArchiveService` ist ein abbrechbarer Foreground-Service mit vorab ermitteltem Messwertbedarf. Er verbindet eingerichtete Geräte seriell über Android GATT und die UUID-Familie `306b0001…0004`. Android verwaltet die Bluetooth-PIN/Kopplung; ab 0.1.4 wird eine fehlende Kopplung vor dem GATT-Verbindungsaufbau angefordert. Bereits bestehende Kopplungen lösen keine zusätzliche PIN-Abfrage aus; der Sofortanzeige-Schlüssel ist dafür kein Ersatz. Operationen sind serialisiert, begrenzt und abbrechbar. Eine aktive VictronConnect-Verbindung muss vorher getrennt werden. Nach einem Verbindungsabbruch bleiben bestätigte Blöcke erhalten; erneuter App-Start oder manueller Abruf setzt den Abgleich fort.

`ArchiveCodec` liest definite CBOR-Registerantworten samt fragmentierten Notifications. Statusantworten werden nicht als Daten interpretiert. EC5D meldet die Kanalregister, EC4A–EC59 Auflösungen und Zeitreferenzen; EC5B liefert Trendblöcke mit Kanal, Gerätezeit, Anzahl und Intervall, neueste Werte zuerst. Bekannte Feldtypen definieren Breite, Vorzeichen, Skalierung und Fehlwert. Unbekannte Kanäle werden übersprungen. Zusatzeingänge unbekannter Betriebsart bleiben Rohwerte. PV-Eingangsspannung ist getrennt von der batteriebezogenen Reglerspannung.

Die Zeitzuordnung verwendet bestätigte gespeicherte EC5F/EC63-Anker innerhalb der EC64-Neustartgrenzen. Vorgeschlagene, aber nicht bestätigte Uhrzeiten werden nicht übernommen. Ohne sicheren Anker wird der betroffene Wert nicht importiert. EC5C (Archiv löschen), Gerätezeit- und Ladekonfigurationsschreibzugriffe sind nicht freigegeben. Schreibzugriffe betreffen nur die Transportinitialisierung, Session-Keepalive und die allowlist-geprüften Abfragen EC5B, EC63 und EC64.

SQLite-Version 3 ergänzt Auflösung, PV-Spannung, Zusatz-Rohwert, Geräte-Tagesdaten, Shunt-Gesamtwerte und `archive_coverage`. Vollständig gelesene Bereiche werden je Gerät, Kanalregister und Auflösung zusammengeführt. Erst nach bestätigter Speicherung eines Blocks wird seine Abdeckung gespeichert. Neue bzw. noch veränderliche Randwerte werden erneut geprüft; bereits abgedeckte ältere Bereiche können ohne erneuten Download übersprungen werden. Import und Aufzeichnung bleiben eigenständige Quellen. Keine privaten Messdaten oder Kopplungsdaten liegen im Repository oder APK.

Die Implementierung ist an einer privaten Aufzeichnung von klassischem SmartSolar und SmartShunt gegengeprüft. Aktives GATT, Pairing und die automatische Importfolge sind noch am Gerät zu testen. Andere Modellfamilien sind nicht allein wegen der Marke kompatibel.

- [Victron: gespeicherte Trends](https://www.victronenergy.de/media/pg/VictronConnect_app/de/stored-trends---instant-readout.html)
- [Offizielles MPPT-Registerprotokoll für Tagesdaten](https://www.victronenergy.com/upload/documents/BlueSolar-HEX-protocol.pdf)
- [CBOR RFC 8949](https://www.rfc-editor.org/rfc/rfc8949)

## Wetter und Farbdesigns

`WeatherClient` ist der einzige Netzwerkpfad. Nur feste HTTPS-Hosts von Open-Meteo sind erlaubt, Redirects sind gesperrt, Antworten und Wartezeiten begrenzt. Ein gewählter Ort aktiviert die Funktion; sie ist standardmäßig aus. Suchtext wird an die Geocoding-API gesendet, Koordinaten an die Forecast-API. Seit 1.4.0 werden tägliche Sonnenzeiten, Sonnenscheindauer und Strahlung sowie stündliche Strahlung und geneigte Einstrahlung abgefragt; die Anzeige umfasst 14 Tage. Sekunden werden in Sonnenstunden umgerechnet; fehlende Daten bleiben leer. Seit 1.2.2 wird bei aktiviertem Wetter in `MainActivity.onStart()` eine Aktualisierung angefordert, auch beim Zurückkehren aus dem Hintergrund. Laufende Abrufe werden zusammengefasst; bei Netzfehlern bleibt der Cache sichtbar. Ortswechsel und Ausschalten verwerfen den alten Ortscache; veraltete Antworten überschreiben keine neue Auswahl. Die WebView bleibt vollständig lokal und darf selbst keine Netzverbindungen öffnen.

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

`blockOrder` speichert Kacheln, Wetter, kleinen Verlauf und Aufzeichnungsschalter. Die Normalisierung ergänzt fehlende Blöcke und entfernt unbekannte oder doppelte Einträge. Umordnen ändert keine Kachelpositionen. `miniRange` wird separat als geprüfte App-Einstellung gespeichert (`sunrise`, `today`, `last6h`). Die Wetterabfrage ergänzt `sunrise` und ab 1.2.5 `sunset`; seit 1.4.0 ergänzt die 14-Tage-Anzeige Solarplanung. Lokale ISO-Zeiten werden mit der IANA-Zeitzone des Wetterorts in Zeitpunkte übersetzt. Veraltete, fehlende, ungültige oder noch bevorstehende Sonnenaufgänge ergeben den ausdrücklich beschrifteten Tageszeitraum. Alte Wetter-Caches werden einmal neu geladen.

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


## Solarertrag, Verbrauch und Bilanz ab 1.2.4

`EnergyIntegral.flow` löst zunächst für jede Messgröße zeitliche Quellenüberlappungen nach der bestehenden Priorität auf. Danach werden die ausgewählten linearen Leistungsintervalle geschnitten. Auf gemeinsamen Abschnitten gilt `P_Verbrauch = P_Solar − P_Shunt` und `P_Bilanz = P_Shunt`. Die drei Energiekurven entstehen aus denselben Integrationsintervallen, nicht durch Subtraktion unabhängig erfasster Gesamtsummen oder bereits reduzierter Diagramme. Negative Residuen bleiben sichtbar; unbekannte Ladequellen werden nicht auf null gekappt.

Der Tagesstandard der Übersicht verwendet `solarFrom` am Sonnenaufgang, Verbrauch und Bilanz dagegen ab lokalem Mitternacht. Ab 1.2.5 begrenzen `solarFrom` und `solarTo` regulär Sonnenaufgang und Sonnenuntergang. Positive, nach Quellenpriorität aufgelöste Leistungsintervalle erweitern beide Grenzen. Außerhalb dieses Fensters erhalten beobachtete Shuntintervalle den Nachtverbrauch bei Solar null; innerhalb werden beidseitig vorhandene Intervalle benötigt. Der Solarverlauf umfasst das so erweiterte Sonnenfenster; seine Abdeckungsangabe bezieht sich auf dieses Fenster. Fehlende Tagesdaten werden nicht mit der nächtlichen Nullannahme überbrückt. Ein bekannter noch bevorstehender Sonnenaufgang ohne erfasste frühe Erzeugung ergibt null Ertrag und weiterhin beobachteten Nachtverbrauch. Fehlt die Wetterzeit, bleibt der bisher ausdrücklich angezeigte Tagesersatz ohne künstliche Tageswerte erhalten.

Die Bridge `energyHistoryWindow(from,to,source,solarFrom,solarTo)` gibt `solar`, `consumption` und `balance` zurück. `HistoryDb.energyFlow` serialisiert Summen, tatsächliche Extrema, Lücken und Abdeckung. Das Dashboard fragt die drei Reihen pro Fenster gemeinsam ab und verwendet denselben Antwortstand. Die Großansicht und die Verlaufsseite vergleichen auf ihrem selbst gewählten Fenster; Änderungen der Großansicht bleiben temporär.

`energyFlowVersion=1` migriert bestehende Batterieentnahme-Kacheln einmal zu `consumptionEnergy` und `energyBalance` zur angezeigten Bilanz. Kachel-IDs, beide Anordnungen, Farben, Einheiten und eigene Titel bleiben erhalten; unveränderte Standardtitel werden ersetzt. Verbrauch wird nicht negativ gespiegelt. Der Tagesstandard wird bei dieser Migration hergestellt. `energyPeriod` synchronisiert Verbrauch und Bilanz sowie die drei Werte für andere Vergleichszeiträume; bei Heute darf die Solarkachel `sunrise` verwenden. Native und JavaScript-Sicherungsprüfung erhalten beide Felder. `dischargeEnergy` und `batteryEnergy` bleiben unabhängig auswählbare Zusatzwerte.

Ungemessene zusätzliche Einspeisung (PWM, Ladegerät) fehlt im Ertrag und wird dadurch vom rechnerischen Verbrauch abgezogen. Die Oberflächenhinweise benennen diese Einschränkung. Im reinen Solarbetrieb bleibt der Saldo bei fehlendem PWM-Ertrag gleich, sofern alle Batteriezweige über den Shunt erfasst werden. Umwandlungs-/Leitungsverluste zwischen den Messstellen gehören zum rechnerischen Verbrauch, Ladeverluste werden nicht separat korrigiert.

Nach erfolgreicher Veröffentlichung löscht der Workflow alle öffentlichen Releases außer den drei neuesten samt ihren Downloads. Tags und Quellhistorie bleiben erhalten.


## Bedienzustände und Kurvenauswahl ab 1.2.5

`syncState()` liefert ohne Datenbankzugriff oder Repository-Monitor einen unveränderlichen Archivstatus und das volatile Warteschlangenmerkmal `archiveBusy`. `WattLifecycle` zeigt die Warteanzeige vor normalen Abfragen und überspringt Snapshots, Statistik, Energieintegrationen und Diagrammaufbau während des gesamten Importlaufs. Auch zwischen zwei Geräten bleibt die Anzeige stehen. Die native Bridge verwirft teure Abfragen während des Imports zusätzlich. Fortschritt und Abbruch verwenden ausschließlich den leichten Statuspfad. Nach Ende erfolgt eine gemeinsame Invalidierung; Fokus, Überlaufzustand, geöffnete Dialoge und Eingaben bleiben erhalten. Abgeschlossene Archivblöcke und die bestehende automatische/manuelle Wiederaufnahme bleiben unverändert.

Der Cockpit-Hinweis ist mit `cockpitNotice-v1` in privaten Android-Einstellungen bestätigt. Nur der ausdrückliche Bridge-Aufruf nach **Verstanden** setzt das Merkmal; Export und Import verwenden weiterhin eine Positivliste ohne diesen Zustand. Der automatische Archivstart wartet auf die Bestätigung. Der dauerhafte Hinweis steht auf Geräte- und Einstellungsseite.

Die Großansicht hält den ausgewählten Messzeitpunkt nur temporär. Punktmarker werden aus realen nahen Messpunkten der jeweiligen Reihe und ihrer Einheit gezeichnet; die gemeinsame senkrechte Linie bezeichnet den ausgewählten Zeitpunkt. Ziehen während der Auswahl aktualisiert Auswahl und Werte ohne Zeitverschiebung. Gedrückthalten beginnt ebenfalls eine Auswahl. Zweifingerzoom und das Aufheben der Auswahl erhalten die bisherige Zeitnavigation. Darstellung, Farbe, Rotation und Aktualisierung zeichnen die Auswahl neu; beim Schließen wird nichts in der Kachelkonfiguration gespeichert.


## Hintergrundauswahl je Archivlauf ab 1.2.6

`ArchiveRun` hält eine unveränderliche, flüchtige Zustandssicht mit Laufnummer, Startzeit, Aktivität und Hintergrundauswahl. Die Wahl wird nur für die aktuell aktive Laufnummer angenommen. Gerätewechsel innerhalb der Warteschlange behalten den Zustand; Erfolg, Abbruch und Fehler verwerfen ihn. Activity-Neuanlage liest denselben Repository-Zustand. Einstellungen und Sicherungen enthalten die Wahl nicht.

Die Bridge liefert während des Imports einen datenbankfreien Snapshot aus dem letzten vollständigen Stand samt aktuellen App-Einstellungen und Wetter. Datenrevisionen lösen während eines Laufs keine fortlaufenden Gesamtintegrationen aus. `WattReadBridge` erhält vorhandene Kurven und nutzt für neue Zeiträume `requestHistory` auf einem eigenen Lesethread. Es bleibt jeweils ein Leseauftrag aktiv; Cache und Warteschlange sind begrenzt. Antworten aus einem früheren Lauf werden durch die Generation verworfen. Fehlende Antworten erscheinen als Ladezustand, niemals als erfundene Null. Nach Ende werden die Daten zusammen neu geladen.

Die kompakte Statusleiste reserviert ihre tatsächliche Höhe auch für offene Dialoge und Großansichten, damit sie keine Bedienelemente verdeckt. Die ausführliche Fortschrittsansicht bleibt über die Leiste erreichbar; Abbrechen funktioniert unabhängig vom Lesethread. Ohne ausdrückliche Hintergrundwahl bleibt die bisherige Warteansicht aktiv.


## Vollständiger Abrufplan und Live-Abdeckung ab 1.3.3

Vor der ersten EC5B-Messwertabfrage werden alle unterstützten Kanalauflösungen und Zeitreferenzen gelesen sowie sämtliche lokalen Live-, Archiv- und zuordenbaren CSV-Werte pro Gerät und Feld geprüft. `ArchiveSyncPlan.plan` erstellt unveränderliche zusammenhängende Fehlbereiche mit separaten Zählern für vorhandene und zeitlich nicht zuordenbare Punkte. Erst nach Abschluss dieses Schritts werden die vorgesehenen Tages-/Gesamtwerte und Trendbereiche übertragen. Während der Übertragung neu gespeicherte Livewerte werden vor jedem Paket nachgeladen und aus dem Bedarf entfernt. Teilantworten arbeiten nur ihren bestätigten Teil ab. Abweichende Gerätezeit/Auflösung, leere oder nicht unterstützte Antworten erzeugen keine erfundene Abdeckung.

Sekündliche Archivstufen dürfen die absichtlich größeren Live-Speicherabstände nicht als Ausfälle behandeln. Benachbarte nicht leere Livepunkte eines Feldes bilden einen zusammenhängenden Abschnitt, wenn ihr Abstand höchstens 1,5 Speicherintervalle beträgt. Die tatsächlich gewählte Kadenz wird für neue Livewerte als `period` gespeichert; ältere `period=0`-Aufzeichnungen verwenden den damaligen Standard von zehn Sekunden. Zweifelhafte ältere langsamere Aufzeichnungen werden konservativ nachgeprüft. Ein fehlendes Feld oder ein größerer Empfangsabstand bildet keine solche Brücke. Bei Kadenzwechseln gilt die kleinere Nachbarschaft. Für die Energieberechnung bleibt die Livequelle auch am Übergang von alten zu neuen Intervallmetadaten zusammenhängend; Archivauflösungen bleiben getrennte Ströme.

Die Gesamtlaufzeitbudgets aus 1.3.2 entfallen. Ein verbundener Lauf arbeitet den tatsächlichen Plan vollständig ab, unabhängig von der Offline-Dauer. Einzeloperationen, Kopplung und Antwortwartezeiten bleiben begrenzt, fehlender Transportfortschritt beendet den Lauf. `ArchiveSchedule` regelt ausschließlich automatische Wiederholungen und die Abbruchpause. Der passive Hintergrundscan bleibt aktiv. Geschlossene Activity und fehlender BLE-Empfang sind unabhängige Zustände.

Vollständig gelesene Zeitreferenzlisten werden pro Gerät mit ihrem Abschlusszustand gespeichert. Folgeabrufe lesen neue Seiten bis zum bekannten Teil und springen nicht erneut an den ältesten Rand. Ein Gerätezeitrücklauf verwirft den Cache einschließlich Abschlusszustand. Ein unterbrochener Erstaufbau wird nicht als vollständig behandelt. Unbekannte Zeitbereiche werden weiterhin vor dem Messwerttransfer zurückgestellt.

Die Tests prüfen fortlaufende Hintergrundaufzeichnung über drei Wochen ohne Trendtransfer, echte drei-/einundzwanzigtägige Offline-Bereiche ohne Laufzeitabschnitt, fehlende Livepunkte und Felder, ein-/zehn-/sechzigsekündliche Speicherung, neue Livewerte nach Planung, ältere getrennte Lücken, Teilantworten und veränderte Geräteantworten. BLE-Dauer und Android-Hintergrundverhalten erfordern zusätzlich den Gerätetest.


## Einheitliche Datenabdeckung ab 1.3.4

`SampleTimeline` berechnet die tatsächlich unterstützten Zeitabschnitte. Innerhalb einer Quelle bestimmen gespeicherte Kadenz und Auflösung die zulässigen Nachbarabstände. Altaufzeichnungen ohne Intervall behalten die vorherige 90-Sekunden-Grenze. Nur benachbarte, tatsächlich gemessene Endpunkte desselben Geräts dürfen einen Quellen-/Auflösungswechsel verbinden. Solche Verbindungen schließen nur ungedeckte Übergänge und ersetzen keine vorhandenen Quellintervalle. Livewerte und feinere Archive behalten ihren Vorrang; echte Lücken und unbeobachtete Ränder werden nicht extrapoliert.

Der Archivabgleich prüft die Abdeckung der jeweiligen Archivzelle statt eines großzügigen Abstands um einzelne Livepunkte. Wo nötig, werden Randpunkte für den Anschluss nachgeladen. Ein schon in derselben Auflösung gelesener Archivpunkt bleibt bekannt. Feinere bestätigte Daten sind ohne die längere Änderungsfrist der gröberen Zielauflösung verwendbar. Unverfügbare Gerätesamples bleiben als abgefragt markiert, ohne Messwerte oder Nullwerte zu erfinden.

SQLite-Version 5 erhält alle bisherigen Zeilen durch eine transaktionale Migration mit ausdrücklich benannten Spalten. Die Identität ist nun `(ts,device,origin,period)`, damit unterschiedliche Auflösungen desselben Zeitpunkts nicht mehr zusammenfallen. Nach einer geplanten Übertragung wird ein Wert wegen eines nahen Live-/CSV-Punkts nicht erneut verworfen. Frühere `archive_coverage`-Abschlussmarker werden einmal neu aufgebaut: Sie konnten Antworten als vollständig markieren, obwohl deren Werte beim Speichern verworfen worden waren. Die vorhandenen Rohmesswerte bleiben erhalten und vermeiden weiterhin erneute Übertragungen tatsächlich gedeckter Bereiche.

Die Kurven werden aus derselben Zeitachse wie die Energie aufgebaut. Explizite `gap`-Merkmale bestimmen Übersicht, Großansicht und Verlauf; eigene Grenzen oder Quellenwechsel im JavaScript ändern diese Abdeckung nicht mehr. Die Reduktion langer Kurven behält echte Segmentenden und -anfänge, statt eine kurze Lücke auf einen ganzen Anzeigebereich auszudehnen. Ergänzende Spannungs-/Stromkanäle desselben Geräts liefern Leistung auf ihren gemeinsam gedeckten Abschnitten, auch bei unterschiedlichen Auflösungen. Unabhängige Geräte und unbeobachtete Abschnitte werden dafür nicht miteinander verbunden.

`SyncPipelineTest` führt Nachladeplan, tatsächliche Protokollblöcke, lokale Speicherung, erneuten Plan, Energiefluss und Kurven durch. Fälle sind unter anderem der gemeldete 20-Sekunden-Liveabstand, Quellen-/Auflösungswechsel, drei Tage/drei Wochen ohne Empfang, drei Wochen Hintergrunddaten ohne Abruf, Wiederaufnahme, Gerätebindung und zufällige Empfangsausfälle. SQLite-Tests prüfen Migration, Messwerterhalt und parallele Auflösungen. `SyncCoverageFixture` erzeugt Daten mit den tatsächlichen Java-Klassen; der Browser testet damit die gerenderten Linien und Abdeckungsanzeigen auf allen drei Ansichten. Die Prüfungen laufen verpflichtend im Android-Workflow.


## Importsteuerung und Abschlussprüfung ab 1.3.5

`ArchiveTransferPlan` ist die vom Android-Service tatsächlich verwendete Warteschlange. Leere und abweichende Antworten verbrauchen keine geplanten Punkte. Gültige zurückgegebene Messwerte werden unter ihren tatsächlichen Zeiten und Auflösungen gespeichert. Bei einer veränderten Archivaufteilung liest der Service nur die Metadaten des betroffenen Kanals neu, gleicht diesen mit SQLite ab und ersetzt dessen verbleibenden Plan; die anderen Kanäle bleiben in der Warteschlange. Teilantworten bestätigen nur ihre tatsächliche Anzahl. Drei aufeinanderfolgende erfolglose Neuplanungen beenden den Lauf ausdrücklich als unvollständig statt mit einem scheinbaren Erfolg.

Nach Abarbeitung wird jeder Kanal nochmals aus SQLite eingelesen und gegen die verfügbaren Archivstufen geprüft. Dabei gelten die gerade gespeicherten Randwerte für diesen Lauf als gelesen. Frische NA-Antworten werden nur im aktuellen Lauf bestätigt und später erneut auf veränderliche Werte geprüft. Ein bei der Kontrolle fehlender Bereich wird wieder eingereiht. Fehler erzeugen einen offenen Wiederholungsbedarf; im Vordergrund wird dieser nach der bestehenden fünfminütigen Pause erneut gestartet. Erfolgreiche Geräte werden durch diese Wiederholung nicht zusätzlich abgefragt. Ein ausdrücklicher Abbruch behält die 30-Minuten-Pause. Das Reparaturupdate prüft eingerichtete Geräte beim ersten Start sofort, unabhängig von der kurzen Wiederholungsfrist der alten Version.

`ArchiveTransferPlanTest` verwendet dieselbe Warteschlange, wirkliche Protokollblöcke und SQLite einschließlich der produktiven Tabellen- und Abgleichdefinitionen. Drei Kanäle mit vier Archivstufen (1, 30, 300, 1800 Sekunden) werden über 21 Tage bis zu Energiesummen, Abdeckung und Kurven geprüft. Weitere Fälle sind der Stufenwechsel während einer Abfrage, eine leere Antwort, Teilantworten, ein dauerhaft fehlerhafter Peer und eine absichtlich bei der Abschlusskontrolle fehlende Datenbankzeile. Das ersetzt keine Messung am Bluetooth-Gerät; es deckt die zuvor außerhalb der durchgängigen Tests liegende Importwarteschlange ab.

## Solarplanung ab 1.4.0

`solar-core.js` enthält die reine Berechnung, `solar-ui.js` die lokal mitgelieferte Oberfläche. `tilt` (0..90°, alte Gruppen null) und `defaultActive` ergänzen die bestehenden Kreisangaben. `solar-setup-v1` speichert Aktivierung, Azimut von Nord, drei Schattenstufen und freiwillige saisonale Wh-Werte. Mobile Kreise starten aus. Der Standort ist der gemeinsame bestehende Wetterort; neue Orte setzen die standortabhängige Richtung und Schattenannahmen zurück.

`WeatherClient` verwendet UNIX-Zeit und die IANA-Zeitzone. Abgerufen werden sieben vergangene und 15 künftige Kalendertage: Der zusätzliche Tag liefert die abschließende Stunde für 14 vollständige Vorschautage. GTI ist die mittlere Einstrahlung der vorangehenden Stunde in W/m². Ein deduplizierter Abruf pro aktivem bekannten Winkel/Azimut liefert die geneigte Einstrahlung; Provider-Azimut ist Nordrichtung minus 180° (Süd=0, Ost=-90). Umrechnung: Wp × GTI/1000 × 1 h × 0,8 × geschätzter Schattenfaktor. Standort, Winkel und Richtung gehen an Open-Meteo; Gerätekennungen, Anlagenleistung und Messwerte nicht. Fehlende Stunden verhindern einen vollständigen Wh-Wert; echte Null bleibt Null. DST-Tage haben 23/25 reale Stunden.

`SolarPlanning` hält Vorhersagen und Beobachtungen getrennt in `wattpit-planning.db`. Ein einzelner Worker verarbeitet HistoryDb-Intervalle, nie auf dem UI-/Bridge-Thread und nicht während eines laufenden Archivimports. Messgeräte-Identitäten werden nur als lokaler SHA256-Quellenbezug verwendet. Aufstellungsschlüssel enthalten Standort, aktive Gruppen, Winkel, Richtung, Schatten und Quellenbezug. Zeitgestempelte Kontextwechsel machen den betroffenen Abschnitt ungültig. Alte Standorte werden beim Wechsel nicht mit neuen Sonnenfenstern überschrieben. CSV-Werte sind keine Lernquelle.

Beobachtungen umfassen drei lokale Tagesabschnitte mit dem bestehenden `EnergyIntegral.flowIntervals` (Solar minus Netto-Shunt), Abdeckung, solarem Bulk-Anteil und Hinweisen auf zusätzliche Einspeisung. Vollständige Tage ohne bekannte ungemessene Einspeisung liefern den üblichen Verbrauch; kontinuierliche Saisongewichte, Aktualitätsgewichte und robuste Ausreißerbegrenzung vermeiden Saisonwechsel-Sprünge. Ohne vergleichbare Jahreszeit ist die Schätzung vorläufig. Freiwillige vier Saisonwerte werden gleitend interpoliert; ohne Einträge bleibt die automatische Ermittlung verfügbar.

Prognosekorrekturen vergleichen nur passende ursprüngliche Vorhersagen mit mindestens 12 h Vorlauf, vollständiger Abdeckung und Bulk-Solarzustand. Mindestens drei verschiedene Tage sind erforderlich; die Korrektur wächst vorsichtig bis zehn Tage und bleibt auf 0,5..1,5 begrenzt. Vorhersagen bleiben 45 Tage, Beobachtungen 400 Tage gespeichert. Ein Standort-/Aufstellungswechsel trennt die Solarerfahrung; passende frühere Aufstellungen können wiederverwendet werden. Die Daten werden nicht über ein Konto abgeglichen und nicht in der Einstellungssicherung exportiert. Mobile Aktivierungen werden beim Einstellungsimport ausgeschaltet.

`SiteSensors` verwendet Beschleunigungs- und Magnetfeldsensor nur im geöffneten Kompassassistenten. Displayrotation und mit bekanntem Standort magnetische Deklination werden berücksichtigt. Die Oberfläche verlangt ausreichend gute, ruhige Werte und eine ausdrückliche Übernahme; Stabilität ist kein Beweis gegen Fahrzeugstörungen. GPS/Netzwerkstandort wird nur nach einem eigenen Knopfdruck und Android-Zustimmung einmalig bis zu 20 s abgefragt, mit Alters-/Genauigkeitsprüfung und manueller Alternative. Schließen, Hintergrund und zerstörte Activity beenden die Sensoren; keine Hintergrund-Standortberechtigung.

Prüfung: 178 JVM-Tests, 99 Node-Tests, zusätzliche echte SQLite-Prüfungen des Planers und Browser-Smoke mit synthetischen Wetter-, GPS- und Kompassereignissen. Physische Sensorqualität und reale Forecast-Abweichungen werden dadurch nicht bestätigt. Primärquellen: [Open-Meteo](https://open-meteo.com/en/docs), [Android Sensorposition](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position).
