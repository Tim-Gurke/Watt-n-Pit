# Watt’n’Pit

**Dein Energie-Cockpit.** Solar, Akku und Batteriebilanz auf einen Blick – direkt über Bluetooth, mit lokaler Aufzeichnung und frei gestaltbarer Übersicht.

**[Android-APK 1.2.2 herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v1.2.2/Watt-n-Pit-v1.2.2.apk)** · [Release](https://github.com/Tim-Gurke/Watt-n-Pit/releases/latest) · **[Anleitung mit Screenshots](docs/ANLEITUNG.md)** · [CSV-Import](docs/CSV-IMPORT.md)

Android 8 oder neuer · Kein Konto · Keine Werbung · Unabhängiges Projekt, kein offizielles Victron-Produkt.

## So sieht es aus

Die Screenshots zeigen die App-Oberfläche von **1.2.2 mit frei erfundenen Beispieldaten**. Wetterort und Gerätekennungen sind neutral; private Messungen und Verbindungscodes werden nicht veröffentlicht.

| Dunkles Design | Helles Design |
|---|---|
| ![Watt’n’Pit 1.2.2: Übersicht mit Solar-, Akku- und Wetterkacheln, dunkel](docs/images/overview-dark.png) | ![Dieselbe Übersicht im hellen Design](docs/images/overview-light.png) |

*Die Standardübersicht: Ladezustand und aktuelle Solarleistung oben, „Ertrag − Entnahme“ und berechneter Solarertrag darunter. Anschließend stehen Batteriespannung und aktuelle Batteriebilanz links neben der negativen Batterieentnahme. Die flache Wetterkachel schließt den Kachelblock ab.*

Solarleistung und berechneter Solarertrag beginnen standardmäßig **seit Sonnenaufgang**; „Ertrag − Entnahme“ und Batterieentnahme zählen **ab Tagesbeginn**. Alle vier Verlaufskacheln verwenden automatische Skalen. Neue Installationen und „Standardübersicht wiederherstellen“ erhalten diese Anordnung; beim Update bleiben gespeicherte eigene Übersichten erhalten.

Im Hochformat bleibt die bewährte Anordnung erhalten. Im Querformat stehen Appname, Überschrift, Zustand, **+ Kachel**, Ansichtsart und **Anordnen** gemeinsam im flachen Kopf. Das Raster ist in beiden Formaten doppelt so fein. Die Restlaufzeit bleibt unter **+ Kachel → Akku → Restlaufzeit** verfügbar.

![Querformat mit gemeinsamem, flachem Kopf und unabhängig angeordneten Kacheln](docs/images/overview-landscape.png)

## Das kann 1.2.2

- **Direkter Empfang:** SmartSolar und SmartShunt liefern Livewerte über die Bluetooth-Sofortanzeige. Messdaten und Geräteschlüssel bleiben auf dem Telefon.
- **Archive automatisch ergänzen:** Beim App-Start liest Watt’n’Pit vorhandene Gerätearchive nacheinander ein. Vorhandene Werte werden abgeglichen, Lücken ergänzt und Archivpunkte nicht doppelt gespeichert.
- **Eigene Übersicht:** Kacheln hinzufügen, ziehen, vergrößern und überlappen. Zahlen, Rundinstrumente oder Verlauf wählen; Einheiten, Farben, Schrift und Zeiträume anpassen. Schmale und breite Ansichten haben eigene Anordnungen.
- **Großansicht:** Kachel antippen und **Großansicht öffnen** wählen. Zeitraum, Einheiten und Skala untersuchen, zoomen und weitere Messgrößen zum Vergleich einblenden. Änderungen in dieser Ansicht sind vorübergehend; **Kachel-Einstellungen** speichert die gewünschte Kacheldarstellung.
- **Erkennbare Kurven:** Optionale automatische Skalen folgen den bisherigen Messwerten. Die große Livezahl bleibt vom Kurvenzeitraum unabhängig; „Seit Sonnenaufgang“ ist je Kachel wählbar.
- **Berechnete Energie:** Solarertrag und Batterieentnahme aus erfassten Leistungspunkten, mit sichtbarer zeitlicher Abdeckung. Batterieentnahme erscheint direkt negativ, auch im Energieverlauf.
- **Ertrag und Entnahme vergleichen:** Unter „Verlauf → Energie berechnet“ ergänzt eine einblendbare dritte Kurve den Wert „Ertrag − Entnahme“ in Wh. Antippen zeigt die drei Werte am gewählten Zeitpunkt.
- **Vergleich direkt als Kachel:** Über „+ Kachel → Energie → Ertrag − Entnahme“ als Zahlenwert oder Verlauf auf die Übersicht legen. Wh/kWh, Zeitraum, Skala und Darstellung lassen sich anpassen; die bestehende Anordnung bleibt erhalten.
- **Kompaktes Wetter:** 14 Tage mit je einem Symbol und erwarteten Sonnenstunden, seitlich wischbar. Die Wetterkachel lässt sich zwischen den Messkacheln anordnen. Bei aktiviertem Wetter wird die Vorhersage beim Öffnen und bei der Rückkehr aus dem Hintergrund automatisch aktualisiert; gespeicherte Werte bleiben bis dahin sichtbar.
- **Passend zum Setup:** Solarkreise mit Modulanzahl, Nennleistung, Montage, Regler und Messwertquelle hinterlegen. Sechs Farbdesigns, jeweils hell und dunkel.
- **Sichern und übertragen:** Einstellungen samt Anordnungen und Wetterort als JSON exportieren und mit Vorschau importieren. Messdaten getrennt als CSV sichern oder einlesen.

![Verlauf von Solarleistung und Batteriebilanz mit Quellenwahl, Zeitzoom und Werteskala](docs/images/history-dark.png)

*Heute, gestern, Woche, Monat oder freien Zeitraum wählen. Quellen filtern, Kurven zoomen, Achsen anpassen und einzelne Messwerte antippen.*

## In fünf Schritten starten

1. Die [APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v1.2.2/Watt-n-Pit-v1.2.2.apk) und installieren. Für Updates die neue APK über die bestehende Installation installieren.
2. In **VictronConnect → Einstellungen → ⋮ → Produktinfo** die **Sofortanzeige über Bluetooth** des jeweiligen Geräts einschalten.
3. Unter **Sofortanzeige-Details → Anzeigen** die Geräteadresse und den **32-stelligen Verschlüsselungsschlüssel** übernehmen.
4. In Watt’n’Pit unter **Geräte → Suche starten** das Gerät einrichten oder mit **+** manuell hinzufügen. Jeder Geräteschlüssel gehört zu genau einem Gerät.
5. Die aktive Verbindung in VictronConnect trennen, die Aufzeichnung starten und in Reichweite bleiben. Gerätearchive werden automatisch ergänzt; Fortschritt und manueller Abruf stehen unter **Geräte**.

Die sechsstellige Bluetooth-PIN für die Android-Kopplung ist **ein anderer Code** als der Schlüssel der Sofortanzeige. Bestehende Kopplungen brauchen keine neue PIN-Abfrage. Auf Android 8–11 benötigt die BLE-Suche Standortberechtigung und eingeschalteten Standortdienst; Watt’n’Pit zeichnet keinen Standort auf.

**[Die vollständige Anleitung](docs/ANLEITUNG.md)** erklärt Einrichtung, Import, Anordnung, Sonnenaufgang, Skalen, Wetter, Solaranlage und Einstellungssicherung mit Screenshots.

## Was die Werte bedeuten

**Batteriebilanz und Batteriestrom:** positiv bedeutet Laden, negativ Entladen. Ladezustand und Restlaufzeit stammen vom SmartShunt. Fehlende oder veraltete Livewerte erscheinen als „—“.

**Solarertrag berechnet:** Watt’n’Pit integriert erfasste Solarleistung über den gewählten Zeitraum. Größere Lücken werden ausgelassen; die Prozentangabe nennt die erfasste Zeit. Der grobe Tageszähler des SmartSolar bleibt zusätzlich verfügbar.

**Batterieentnahme berechnet:** Energie, die über den Shunt aus dem Akku entnommen wurde. Das ist **kein Gesamtverbrauch**, weil direkt genutzter Solarstrom fehlen kann. Ein weiteres Modul an einem separaten Regler ist ohne eigenen Messwert nur in der Batteriebilanz erkennbar.

![Positiver Solarertrag und negative Batterieentnahme im berechneten Energieverlauf](docs/images/energy-history.png)

*Berechnete Werte entstehen ausschließlich aus vorhandenen Leistungspunkten. Unbeobachtete Zeiträume werden nicht hochgerechnet.*

**Ertrag − Entnahme:** Solarertrag abzüglich der aus dem Akku entnommenen Energie im selben ausgewählten Zeitraum. Die blaue gestrichelte Vergleichskurve lässt sich ein- und ausblenden. Sie beschreibt weder den Gesamtverbrauch noch die Änderung der gespeicherten Akkuenergie; dafür müssten auch Ladeenergie und weitere Quellen berücksichtigt werden. Fehlt eine Messreihe, erscheint kein erfundener Nullwert.

![Die zusätzliche Kachel Ertrag minus Entnahme mit Zahlenwert und Verlauf](docs/images/energy-tile.png)

*Dieselbe Berechnung steht auch als frei platzierbare Kachel zur Verfügung. Der gewählte Zeitraum gilt gemeinsam für beide Quellen; „Seit Sonnenaufgang“ ist ebenfalls möglich.*

## Lokal und mit optionalem Wetter

Aufzeichnung, Gerätearchive und CSV-Daten werden lokal zusammengeführt. Geräteschlüssel werden mit dem Android Keystore verschlüsselt gespeichert. Die Einstellungssicherung enthält **keine Geräteadressen, PINs, Schlüssel oder Messdaten**; ihr Wetterort ist Bestandteil der Konfiguration.

Nur die optionale Ortssuche und Wettervorschau nutzen Internet: Suchtext bzw. Ortskoordinaten gehen per HTTPS an Open-Meteo. Wetterdaten: Open-Meteo, CC BY 4.0; Ortsdaten: GeoNames. Es werden keine Energiedaten oder Geräteschlüssel übertragen.

## Kompatibilität und Stand

SmartSolar 75/15 mit Bluetooth und SmartShunt 300 A wurden an realer Hardware erprobt. Der vollständige Archivimport und die Verlaufsanzeige sind seit 0.1.5 bestätigt; Übersicht und kompakte Wetterkachel wurden bis 0.1.9 ebenfalls am Android-Gerät bestätigt. Die Anordnungen und die Großansicht werden zusätzlich automatisiert geprüft. Der Wetterabruf beim Öffnen ist in 1.2.2 ergänzt. Weitere Victron-Geräte sind **nicht pauschal als kompatibel bestätigt**. In dieser Version lässt sich je ein SmartSolar und ein SmartShunt einrichten.

Die App ändert keine Ladeparameter und löscht keine Gerätetrends. Temperatur und Zusatzspannung benötigen einen entsprechend eingerichteten Geräteeingang. Eine lang laufende Aufzeichnung kann durch Android-Energiespareinstellungen begrenzt werden.

Automatisierte Prüfung: **59 JVM-Tests, 50 Node-Tests**, Android-Lint und Android-Build sowie Browserprüfungen für Bedienung, Speicherung, Importabgleich, Sonnenaufgang, Skalen, Energiekurven und Farbdesigns. Die neue Vergleichskachel wurde zusätzlich mit Darstellung, Einheitenwechsel, fehlenden Werten und Einstellungsexport/-import geprüft. Die Android-Dateiauswahl für Einstellungssicherungen ist zusätzlich am Gerät zu bestätigen. [Versionshinweise](docs/releases/1.2.2.md) · [Technische Grundlage](docs/ARCHITECTURE.md)

## Entwicklung

Java 17 und Android SDK 35:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
node --test tools/dashboard-core.test.cjs tools/equipment-core.test.cjs tools/settings-backup.test.cjs tools/tile-detail.test.cjs
```

Die Debug-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Öffentliche APKs werden mit einem dauerhaften privaten Schlüssel signiert, damit spätere Updates kompatibel bleiben. Die App verwendet keine npm-Abhängigkeiten.

Dokumentationsbilder lassen sich mit Playwright über `node tools/docs-screenshots.cjs` reproduzieren. Dabei werden ausschließlich erfundene Beispieldaten verwendet. Weitere Browserprüfungen stehen unter `tools/`.

MIT-Lizenz.
