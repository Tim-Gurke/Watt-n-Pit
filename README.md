# Watt’n’Pit

**Dein Energie-Cockpit.** Solar, Akku und Batteriebilanz auf einen Blick – direkt über Bluetooth, mit lokaler Aufzeichnung und frei gestaltbarer Übersicht.

**Watt’n’Pit ist eine Energieanzeige und ein Cockpit. Die App ersetzt VictronConnect nicht.** Sie zeigt Messwerte, Verläufe und Energieauswertungen. Einstellungen an Solarregler und SmartShunt müssen weiterhin über die Victron-Software **VictronConnect** vorgenommen werden. Die Einstellungen in Watt’n’Pit betreffen nur die App und ändern keine Geräteparameter.

**[Android-APK 1.3.3 herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v1.3.3/Watt-n-Pit-v1.3.3.apk)** · [Release](https://github.com/Tim-Gurke/Watt-n-Pit/releases/latest) · **[Anleitung mit Screenshots](docs/ANLEITUNG.md)** · [CSV-Import](docs/CSV-IMPORT.md)

Android 8 oder neuer · Kein Konto · Keine Werbung · Unabhängiges Projekt, kein offizielles Victron-Produkt.

## So sieht es aus

Die Screenshots zeigen die seit **1.2.6** unveränderte Bedienoberfläche mit frei erfundenen Beispieldaten. Wetterort und Gerätekennungen sind neutral; private Messungen und Verbindungscodes werden nicht veröffentlicht.

| Dunkles Design | Helles Design |
|---|---|
| ![Watt’n’Pit: Übersicht mit Solar-, Akku- und Wetterkacheln, dunkel](docs/images/overview-dark.png) | ![Dieselbe Übersicht im hellen Design](docs/images/overview-light.png) |

*Die Standardübersicht: Ladezustand und aktuelle Solarleistung oben, „Bilanz“ und Solarertrag darunter. Anschließend stehen Batteriespannung und aktuelle Batteriebilanz links neben dem berechneten Verbrauch. Die flache Wetterkachel schließt den Kachelblock ab.*

Solarleistung und Solarertrag beginnen standardmäßig **seit Sonnenaufgang**; Verbrauch und Bilanz zählen **ab Mitternacht**, einschließlich des nächtlichen Verbrauchs. Alle vier Verlaufskacheln verwenden automatische Skalen. Neue Installationen und „Standardübersicht wiederherstellen“ erhalten diese Anordnung; beim Update bleiben gespeicherte eigene Übersichten erhalten.

Im Hochformat bleibt die bewährte Anordnung erhalten. Im Querformat stehen Appname, Überschrift, Zustand, **+ Kachel**, Ansichtsart und **Anordnen** gemeinsam im flachen Kopf. Das Raster ist in beiden Formaten doppelt so fein. Die Restlaufzeit bleibt unter **+ Kachel → Akku → Restlaufzeit** verfügbar.

![Querformat mit gemeinsamem, flachem Kopf und unabhängig angeordneten Kacheln](docs/images/overview-landscape.png)

## Das kann 1.3.3

- **Direkter Empfang:** SmartSolar und SmartShunt liefern Livewerte über die Bluetooth-Sofortanzeige. Messdaten und Geräteschlüssel bleiben auf dem Telefon.
- **Synchronisation sichtbar:** Eine Warteanzeige nennt Gerät, Arbeitsschritt und bekannten Fortschritt. **Im Hintergrund synchronisieren** gibt die Bedienung für den aktuellen Durchlauf frei; eine kleine Statusanzeige bleibt erreichbar. **Synchronisation abbrechen** bleibt verfügbar. Die Auswahl gilt für die gesamte Gerätewarteschlange und wird anschließend verworfen. Bereits gespeicherte Daten bleiben erhalten.
- **Einmaliger Cockpit-Hinweis:** Den seit 1.2.5 enthaltenen Hinweis einmal mit **Verstanden** bestätigen; eine vorhandene Bestätigung bleibt erhalten. Watt’n’Pit ersetzt VictronConnect nicht; Geräteeinstellungen bleiben dort.
- **Bedarf vor dem Abruf bestimmen:** Nach kurzen Geräte-Metadaten wird der lokale Bestand vollständig je Gerät, Messgröße und Auflösung geprüft. Der Abrufplan enthält nur fehlende Bereiche. Auch eine geschlossene Oberfläche kann weiter Livewerte aufzeichnen; diese zählen beim Abgleich mit. Zusammenhängende Live-Aufzeichnung im gewählten Speicherintervall erzeugt keine künstlichen Lücken gegenüber sekündlichen Archiven.
- **Echte Lücken vollständig ergänzen:** Drei Tage oder drei Wochen ohne Empfang werden in der verfügbaren Geräteauflösung ergänzt. Es gibt kein festes Gesamtzeitlimit; einzelne unbeantwortete Bluetooth-Aufträge laufen weiterhin in eine Frist. Bestätigte Blöcke bleiben bei Abbruch gespeichert. Der nächste Abgleich ermittelt den restlichen Bedarf neu. Automatische Versuche liegen mindestens fünf Minuten auseinander; Abbrechen pausiert sie 30 Minuten. Zusätzliche Archivmessgrößen, heutige Solar-Tageswerte und Shunt-Gesamtwerte können weiterhin Daten benötigen, auch wenn die normalen Livegrößen vollständig sind.
- **Eigene Übersicht:** Kacheln hinzufügen, ziehen, vergrößern und überlappen. Zahlen, Rundinstrumente oder Verlauf wählen; Einheiten, Farben, Schrift und Zeiträume anpassen. Schmale und breite Ansichten haben eigene Anordnungen.
- **Großansicht:** Kachel antippen und **Großansicht öffnen** wählen. Zeitraum, Einheiten und Skala untersuchen, zoomen und weitere Messgrößen zum Vergleich einblenden. Ein Messpunkt wird durch Punkt und senkrechte Linie markiert. Die Auswahl lässt sich mit dem Finger ziehen; Uhrzeit und Werte folgen. **Auswahl aufheben** gibt das Verschieben des Zeitraums frei. Änderungen in dieser Ansicht sind vorübergehend; **Kachel-Einstellungen** speichert die gewünschte Kacheldarstellung.
- **Solarfenster:** Solarleistung und Solarertrag enden nach Sonnenuntergang regulär dort. Gemessene Erzeugung vor Sonnenaufgang oder nach Sonnenuntergang erweitert das Fenster und zählt mit. Der Wh-Ertrag bleibt sichtbar; Verbrauch und Bilanz zählen den ganzen Tag.
- **Erkennbare Kurven:** Optionale automatische Skalen folgen den bisherigen Messwerten. Die große Livezahl bleibt vom Kurvenzeitraum unabhängig; „Seit Sonnenaufgang“ ist je Kachel wählbar.
- **Drei Energiewerte:** Solarertrag, berechneter Verbrauch und die Differenz **Ertrag − Verbrauch** in Wh/kWh. Direkt genutzter Solarstrom ist im berechneten Verbrauch enthalten. Plus bei der Bilanz bedeutet Überschuss, Minus Defizit.
- **Passende Messabschnitte:** Nach Sonnenaufgang verwenden alle drei Werte nur gemeinsam beobachtete Regler- und Shuntabschnitte. Der nächtliche Verbrauch bleibt über den Shunt enthalten. Messlücken und zeitliche Abdeckung bleiben sichtbar.
- **Bestehende Übersicht übernehmen:** Die Batterieentnahme-Kachel wird beim Update zu „Verbrauch“, die bisherige Akkubilanz zu „Bilanz“. Position, Größe, Farben und Einheiten bleiben erhalten. Die reine Batterieentnahme und die vom Solarregler unabhängige Akkubilanz stehen weiterhin als Zusatzkacheln bereit.
- **Kompaktes Wetter:** 14 Tage mit je einem Symbol und erwarteten Sonnenstunden, seitlich wischbar. Die Wetterkachel lässt sich zwischen den Messkacheln anordnen. Bei aktiviertem Wetter wird die Vorhersage beim Öffnen und bei der Rückkehr aus dem Hintergrund automatisch aktualisiert; gespeicherte Werte bleiben bis dahin sichtbar.
- **Passend zum Setup:** Solarkreise mit Modulanzahl, Nennleistung, Montage, Regler und Messwertquelle hinterlegen. Sechs Farbdesigns, jeweils hell und dunkel.
- **Sichern und übertragen:** Einstellungen samt Anordnungen und Wetterort als JSON exportieren und mit Vorschau importieren. Messdaten getrennt als CSV sichern oder einlesen.

![Verlauf von Solarleistung und Batteriebilanz mit Quellenwahl, Zeitzoom und Werteskala](docs/images/history-dark.png)

*Heute, gestern, Woche, Monat oder freien Zeitraum wählen. Quellen filtern, Kurven zoomen, Achsen anpassen und einzelne Messwerte antippen.*

## In fünf Schritten starten

Beim ersten Öffnen den Cockpit-Hinweis lesen und **Verstanden** wählen. Er erscheint auch bei bestehenden Installationen einmal nach diesem Update. Die Bestätigung bleibt nur auf diesem Gerät und wird nicht mit Einstellungen exportiert.

1. Die [APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v1.3.3/Watt-n-Pit-v1.3.3.apk) und installieren. Für Updates die neue APK über die bestehende Installation installieren.
2. In **VictronConnect → Einstellungen → ⋮ → Produktinfo** die **Sofortanzeige über Bluetooth** des jeweiligen Geräts einschalten.
3. Unter **Sofortanzeige-Details → Anzeigen** die Geräteadresse und den **32-stelligen Verschlüsselungsschlüssel** übernehmen.
4. In Watt’n’Pit unter **Geräte → Suche starten** das Gerät einrichten oder mit **+** manuell hinzufügen. Jeder Geräteschlüssel gehört zu genau einem Gerät.
5. Die aktive Verbindung in VictronConnect trennen, die Aufzeichnung starten und in Reichweite bleiben. Gerätearchive werden automatisch ergänzt; Fortschritt und manueller Abruf stehen unter **Geräte**.

Die sechsstellige Bluetooth-PIN für die Android-Kopplung ist **ein anderer Code** als der Schlüssel der Sofortanzeige. Bestehende Kopplungen brauchen keine neue PIN-Abfrage. Auf Android 8–11 benötigt die BLE-Suche Standortberechtigung und eingeschalteten Standortdienst; Watt’n’Pit zeichnet keinen Standort auf.

**[Die vollständige Anleitung](docs/ANLEITUNG.md)** erklärt Einrichtung, Import, Anordnung, Sonnenaufgang, Skalen, Wetter, Solaranlage und Einstellungssicherung mit Screenshots.

## Was die Werte bedeuten

**Batteriebilanz und Batteriestrom:** positiv bedeutet Laden, negativ Entladen. Ladezustand und Restlaufzeit stammen vom SmartShunt. Fehlende oder veraltete Livewerte erscheinen als „—“.

**Solarertrag:** Aus erfasster Leistung des Solarreglers berechnet, im Standard seit Sonnenaufgang am Wetterort. Ein ungemessener zusätzlicher Regler ist darin nicht enthalten. Ohne gültigen Sonnenaufgang erscheint ausdrücklich „heute (Ersatz)“.

**Verbrauch:** Rechnerisch **Solarertrag − Akkubilanz**. Dazu gehören direkt genutzter Solarstrom und Energie aus dem Akku. Im Standard zählt er ab Mitternacht; außerhalb des Solarfensters wird der beobachtete Shunt-Verlauf verwendet. Tatsächlich erfasste Erzeugung vor Sonnenaufgang oder nach Sonnenuntergang erweitert das Solarfenster. Darin müssen Regler und Shunt für denselben Abschnitt vorliegen. Der Wert ist eine Näherung aus den Messstellen einschließlich der Verluste dazwischen.

**Bilanz:** **Solarertrag − Verbrauch**. Beispiel: **100 Wh Ertrag − 150 Wh Verbrauch = −50 Wh Defizit**. Die Bilanz entspricht im reinen Solarbetrieb dem erfassten Nettofluss am Akku; Ladeverluste werden nicht herausgerechnet. Sie beschreibt keine genaue Änderung des chemischen Akkuinhalts.

![Solarertrag, Verbrauch und Bilanz im berechneten Energieverlauf](docs/images/energy-history.png)

**Zusätzlicher PWM-Regler oder andere ungemessene Einspeisung:** Der fehlende Ertrag senkt den berechneten Verbrauch um denselben Betrag. Beispiel: 20 Wh gemessener Solarertrag + 30 Wh ungemessener PWM-Ertrag und 100 Wh tatsächlicher Verbrauch erscheinen als **20 Wh Ertrag, 70 Wh Verbrauch und −50 Wh Bilanz**. Im reinen Solarbetrieb bleibt die Differenz erhalten. Bei starker zusätzlicher Einspeisung kann der berechnete Verbrauch negativ werden; die App zeigt dann „Zusätzliche Einspeisung“. Alle Batteriezweige müssen korrekt über den Shunt erfasst werden.

Messlücken werden ausgelassen, nicht mit Tageswerten aufgefüllt. Die Prozentangabe nennt die erfasste Zeit des jeweiligen Fensters. Ohne verwertbare Messintervalle erscheint „—“, gemessene Null bleibt 0 Wh. Vor Sonnenaufgang steht der Solarertrag ohne erfasste frühere Erzeugung bei 0 Wh. Nach Sonnenuntergang endet die Solarzeitachse am Sonnenuntergang oder an der letzten verwertbaren späten Erzeugung; der Tagesertrag bleibt stehen.

![Die Bilanz als frei platzierbare Kachel mit Zahlenwert und Verlauf](docs/images/energy-tile.png)

Verbrauch und Bilanz verwenden einen gemeinsamen Zeitraum. Bei „Heute“ darf der Solarertrag seit Sonnenaufgang zählen; bei anderen Zeiträumen wechseln die drei Energiewerte gemeinsam. Einstellungen einer Großansicht verändern die Übersicht weiterhin nicht. Die Zusatzkacheln **Batterieentnahme berechnet** und **Akkubilanz** lassen sich unabhängig einstellen.

## Lokal und mit optionalem Wetter

Aufzeichnung, Gerätearchive und CSV-Daten werden lokal zusammengeführt. Geräteschlüssel werden mit dem Android Keystore verschlüsselt gespeichert. Die Einstellungssicherung enthält **keine Geräteadressen, PINs, Schlüssel oder Messdaten**; ihr Wetterort ist Bestandteil der Konfiguration.

Nur die optionale Ortssuche und Wettervorschau nutzen Internet: Suchtext bzw. Ortskoordinaten gehen per HTTPS an Open-Meteo. Wetterdaten: Open-Meteo, CC BY 4.0; Ortsdaten: GeoNames. Es werden keine Energiedaten oder Geräteschlüssel übertragen.

## Kompatibilität und Stand

SmartSolar 75/15 mit Bluetooth und SmartShunt 300 A wurden an realer Hardware erprobt. Der vollständige Archivimport und die Verlaufsanzeige sind seit 0.1.5 bestätigt; Übersicht und kompakte Wetterkachel wurden bis 0.1.9 ebenfalls am Android-Gerät bestätigt. Die Anordnungen und die Großansicht werden zusätzlich automatisiert geprüft. Der Wetterabruf beim Öffnen ist seit 1.2.2 enthalten. Weitere Victron-Geräte sind **nicht pauschal als kompatibel bestätigt**. In dieser Version lässt sich je ein SmartSolar und ein SmartShunt einrichten.

Es bleiben immer die **drei neuesten Releases mit APK-Downloads** verfügbar.

Die App ändert keine Ladeparameter und löscht keine Gerätetrends. Temperatur und Zusatzspannung benötigen einen entsprechend eingerichteten Geräteeingang. Eine lang laufende Aufzeichnung kann durch Android-Energiespareinstellungen begrenzt werden.

Automatisierte Prüfung: **137 JVM-Tests, 59 Node-Tests**, Android-Lint und Android-Build sowie Browserprüfungen für Bedienung, Speicherung, Importabgleich, Sonnenaufgang, Skalen, Energiekurven und Farbdesigns. Die Energieberechnung prüft zusätzlich direkten Solarverbrauch, Laden/Entladen, nicht gemessenen PWM-Ertrag, Nachtverbrauch trotz Sonnenaufgangsfenster, gemeinsame Messabdeckung, Messlücken und Quellenüberlappung. Browserprüfungen decken die Umstellung bestehender Kacheln, Zeitraumwahl, Einheiten und Einstellungsexport/-import ab. Die bisherigen Browserprüfungen testen zusätzlich asynchrone Abfragen während des Imports, die Wahl pro Abruf, Wiederöffnen, Gerätewechsel sowie die verschiebbare Kurvenmarkierung, sichtbare Warteanzeige, Abbruch und Erhalt offener Eingaben. Die neuen Synchronisationstests prüfen außerdem ältere Lücken, teilweise vorhandene Pakete, Livewerte, Auflösungen, unabhängige App-Installationen und Geräte, Wiederaufnahme und Neustart-Zeitanker. Die tatsächliche Dauer des Bluetooth-Abrufs und die Android-Dateiauswahl sind zusätzlich am Telefon zu bestätigen. [Versionshinweise](docs/releases/1.3.3.md) · [Technische Grundlage](docs/ARCHITECTURE.md)

## Entwicklung

Java 17 und Android SDK 35:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
node --test tools/dashboard-core.test.cjs tools/equipment-core.test.cjs tools/settings-backup.test.cjs tools/tile-detail.test.cjs
```

Die Debug-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Öffentliche APKs werden mit einem dauerhaften privaten Schlüssel signiert, damit spätere Updates kompatibel bleiben. Die App verwendet keine npm-Abhängigkeiten.

Dokumentationsbilder lassen sich mit Playwright über `node tools/docs-screenshots.cjs` reproduzieren. Dabei werden ausschließlich erfundene Beispieldaten verwendet. Weitere Browserprüfungen stehen unter `tools/`.

MIT-Lizenz.
