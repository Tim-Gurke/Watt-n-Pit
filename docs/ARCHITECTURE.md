# Technische Grundlage

WattPit ist eine lokale Android-App ohne Internetberechtigung. Eine native Java-Schicht liest Bluetooth, verwaltet Schlüssel im Android Keystore und speichert Messwerte in SQLite. Die vollständig mitgelieferte Oberfläche läuft in einer WebView; sie lädt keine externen Bibliotheken, Schriften oder Ressourcen. Fremde Navigation und Dateizugriff außerhalb der App-Assets sind gesperrt.

## Geräte und Livewerte

Der BLE-Empfang liest Manufacturer Data mit Victrons Hersteller-ID `0x02e1` und Product Advertisement `0x10`. Unterstützte Datensatztypen sind Solar Charger `0x01` und Battery Monitor `0x02`. Die Datensätze werden mit AES-128-CTR entschlüsselt. Header-Prüfung, NA-Sentinelwerte, vorzeichenbehaftete und nicht byte-ausgerichtete Felder sind berücksichtigt. Gerätetyp und Schlüssel werden nicht geraten.

Primärquelle: [Victrons Extra manufacturer Data, 14.12.2022](https://communityarchive.victronenergy.com/storage/attachments/extra-manufacturer-data-2022-12-14.pdf). [Offizielle Ankündigung und Schlüsseleinrichtung](https://communityarchive.victronenergy.com/questions/187303/victron-bluetooth-advertising-protocol.html).

In 0.1.0 wird ein SmartSolar und ein SmartShunt eingerichtet. Das Dashboard zeigt nur empfangene Livewerte; nach 45 Sekunden ohne nutzbare Daten bzw. nach dem Pausieren werden Werte als fehlend dargestellt. Gespeicherte Messwerte bleiben im Verlauf. Der Solarstrom des Reglers und der Batteriestrom des Shunts sind unterschiedliche Messgrößen. Verbraucherleistung oder mobile PV-Leistung werden nicht aus ihrer Differenz behauptet.

## Aufzeichnung und Vorrang

Live-Datensätze tragen `origin=live`. Generische CSV-Dateien tragen `origin=csv`; native WattPit-Backups behalten ihre ursprüngliche Datenquelle. Die Datenbank ignoriert doppelte Kombinationen aus Zeitstempel, Gerät und Quelle.

Die Standardansicht `Alle Daten` bevorzugt eigene Aufzeichnungen **pro Messgröße und Gerätetyp**. Ein importierter Punkt wird in der Standardansicht unterdrückt, wenn ein eigener gültiger Punkt derselben Messgröße innerhalb von 45 Sekunden existiert. Die Importdatei bleibt vollständig gespeichert, explizit auswählbar und exportierbar. Fehlt etwa SOC in einer eigenen Aufzeichnung, wird ein gültiger importierter SOC nicht von einem vorhandenen Spannungswert verdrängt.

Lange Fenster werden in bis zu etwa 600 Zeitabschnitte je Quelle/Gerät gebündelt. Die Linie zeigt den Mittelwert; Minimum/Maximum bleiben als Schattierung sichtbar. Nach dem Zoomen werden die Daten mit höherer Zeitauflösung neu abgefragt. Empfangslücken werden nicht durch pauschale Nullwerte ersetzt.

## Gespeicherte Trends: Grenze von 0.1.0

Instant Readout enthält keine archivierten Zeitreihen. VictronConnect kann gespeicherte Trends aus kompatiblen Geräten lesen. Der dokumentierte Drittanbieter-GATT-Dienst des SmartShunts ist ebenfalls ein Livewertdienst und kein dokumentierter SmartSolar-Trend-Download.

- [Gespeicherte Trends im VictronConnect-Handbuch](https://www.victronenergy.de/media/pg/VictronConnect_app/de/stored-trends---instant-readout.html)
- [Offizieller Drittanbieter-GATT-Dienst](https://communityarchive.victronenergy.com/questions/93919/victron-bluetooth-ble-protocol-publication.html)

Ein direkter automatischer Archivabruf ist deshalb kein funktionierendes Feature dieser Version. Der nächste Entwicklungsschritt braucht eine dokumentierte oder anhand echter Kommunikation verifizierte Abrufsequenz samt Zeitstempeln, Datenlayout und Firmware-Kompatibilität. Ohne diese Grundlage werden keine vermuteten GATT-Schreibbefehle an Ladegeräte gesendet.

Ziel des späteren Backfill-Ablaufs: Bei Empfang Datenabdeckung prüfen, nur fehlende Bereiche anfordern, eigene Werte unverändert priorisieren, Gerätedaten mit eigener Quellenkennung einfügen, geprüften Fortschritt je Gerät speichern und nach Verbindungsabbruch fortsetzen. Fehlende Daten der Vergangenheit sind vom fehlenden aktuellen Bluetooth-Empfang zu unterscheiden.

## Bauen und signieren

Java 17, Android SDK 35, Gradle 8.11.1, Android Gradle Plugin 8.9.2. `./gradlew testDebugUnitTest lintDebug assembleDebug` prüft und baut die Entwicklungsversion.

Für eine dauerhaft aktualisierbare Release-APK werden `WATTPIT_KEYSTORE` und `WATTPIT_SIGNING_PASSWORD` außerhalb des Repositorys gesetzt. Der Keystore-Alias lautet `wattpit`. Der private Signaturschlüssel gehört niemals ins Repository. `./gradlew assembleRelease` verwendet ihn, wenn er gesetzt ist.

Die Veröffentlichung verwendet einen temporären `publish/<Version>`-Branch mit einem zusätzlichen Commit, der ausschließlich `.release/`-Dateien enthält. Nach erfolgreichen Tests prüft GitHub Actions die vorbereitete signierte APK, erstellt Prüfsumme und Release und setzt den Release-Tag auf den **vorherigen Quellcode-Commit**. Der Branch kann anschließend gelöscht werden; auf `main` liegen keine APK-Dateien oder Signaturschlüssel. CI-Debug-Artefakte dienen ausschließlich der Entwicklung.
