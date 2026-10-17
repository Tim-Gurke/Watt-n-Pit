# Watt’n’Pit

**Dein Energie-Cockpit.** Eine lokale Android-App für SmartSolar und SmartShunt: Solar, Akku und Batteriebilanz auf einen Blick, mit übersichtlichem Verlauf.

[APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/latest) · [Einrichtung](#einrichtung) · [CSV-Import](docs/CSV-IMPORT.md) · [Technische Grundlage](docs/ARCHITECTURE.md)

## Version 0.1.8

- SmartSolar 75/15 und SmartShunt 300 A gleichzeitig über Bluetooth-Sofortanzeige.
- Automatischer Gerätearchiv-Import beim App-Start, je Gerät nacheinander; Fortschritt und Abbruch unter Geräte. Android-Kopplung wird vor dem GATT-Aufbau geprüft; vorhandene Kopplungen benötigen keine neue PIN-Abfrage.
- Solaranlage unter Einstellungen frei anpassen: mehrere Solarkreise mit Modulanzahl, Wp pro Modul, Montage, Regler und Messwertquelle. Ohne Einrichtung werden keine persönlichen Anlagenangaben angenommen.
- Archivtransport mit regelmäßiger Sitzungserneuerung und Paketfreigaben; gespeicherte Blöcke werden während des Imports im Verlauf sichtbar.
- Kopierbare lokale Archivdiagnose mit Verbindungsschritten und Fehlerstatus, ohne Adressen, Schlüssel oder Messwerte.
- Abgleich pro Gerät, Messgröße und Zeitpunkt mit Aufzeichnungen, CSV und früheren Archivimporten. Feinere Daten haben Vorrang; fehlende Kanäle werden ergänzt.
- Sechs fertige Farbdesigns: Klassisch, Blau, Grün, Türkis, Orange und Neutral, jeweils hell und dunkel.
- Optionale 14-Tage-Wetterzeile: pro Tag ein Symbol und Sonnenstunden; manuelle Ortswahl ohne GPS.
- Standardübersicht mit sieben Kacheln: Ladezustand links, Solarleistung rechts, darunter Batterieentnahme und Solarertrag, unten Spannung, Restlaufzeit und Batteriebilanz. Bestehende Anordnungen bleiben beim Update erhalten; 26 Mess- und Zusatzwerte stehen optional bereit.
- Optionale automatische Skala je Verlaufskachel: bisherige Messwerte im gewählten Zeitraum bestimmen die Grenzen. Kleine Solarleistungen, Spannungsänderungen und negative Batteriebilanzen bleiben erkennbar.
- Zeitraum „Seit Sonnenaufgang“ je historischer Kachel: Leistungsdarstellung und berechnete Energiewerte verwenden den Sonnenaufgang am gewählten Wetterort. Fehlende oder noch bevorstehende Zeiten werden mit „heute (Ersatz)“ kenntlich gemacht.
- Berechnete Batterieentnahme und Solarertrag lassen sich über „Vorzeichen umkehren (+/−)“ auch negativ anzeigen, einschließlich Kurve und Skala.
- Einstellungen → Einstellungen sichern: Kacheln mit beiden Anordnungen, Farbdesign, Zeiträume, Akku-/Speicherparameter, Wetterort und Solaranlage als JSON exportieren und mit Vorschau wieder importieren. Messdaten, Geräteadressen, PINs und Schlüssel werden nicht übertragen.
- Livewert und Kurvenzeitraum getrennt wählen: Kachel → Darstellung Verlauf → Große Zahl „Aktuell (Live)“, Kurvenzeitraum beispielsweise „Heute“. Ohne frischen Empfang bleibt die Kurve gespeichert, die Livezahl zeigt —.
- Berechneter Solarertrag und Batterieentnahme in Wh/kWh: als eigene Kacheln und unter Verlauf → Energie berechnet. Die Anzeige nennt die erfasste Zeit; größere Messlücken werden ausgelassen. Batterieentnahme ist kein Gesamtverbrauch einschließlich direkt genutztem Solarstrom.
- Der kleine Übersichtsverlauf ist wählbar: seit Sonnenaufgang am Wetterort, heute oder letzte sechs Stunden. Ohne brauchbare Sonnenaufgangszeit wird „Heute“ mit Hinweis verwendet.
- Unter „Anordnen“ die Reihenfolge der vier Blöcke Kacheln, Wetter, Verlauf und Start/Pause ändern; die Anordnung bleibt gespeichert.
- Lesbare Zahlen im hellen Design: Farbberechnung für kurzes Weiß korrigiert, Kontrast auch für eigene Hintergründe und transparente Kacheln geprüft.
- Kacheln hinzufügen, antippen und anpassen, frei verschieben, skalieren und überlappen; getrennte Anordnungen für schmale und breite Ansichten.
- Zahlenwert, Rundinstrument oder kleiner Verlauf mit individuell gespeicherten Einheiten, Farben, Skalen, Schriftgrößen, Hintergrund und Deckkraft.
- Restlaufzeit wahlweise in Stunden, Tagen, Minuten oder Sekunden; W/kW, Wh/kWh und weitere passende Einheiten.
- Je Kachel: aktuell, letzte X Minuten/Stunden/Tage, heute, seit Zurücksetzen oder ein freier Zeitraum; letzter Wert, Durchschnitt, Minimum oder Maximum aus vorhandenen Messdaten.
- Entnommene Kapazität ist als Zusatzwert verfügbar und gehört nicht zur Standardübersicht.
- Solarleistung, Tagesertrag und Ladephase des SmartSolar.
- Aufzeichnung auf dem Telefon, auch bei geschlossener Oberfläche; pausierbar über die Benachrichtigung.
- Verlauf für heute, gestern, Woche, Monat und freie Zeiträume.
- Zoomen und Verschieben der Zeitachse, manuelle oder automatische Werteskala.
- CSV-Import mit überprüfbarer Spaltenzuordnung; vollständiger CSV-Export.
- Eigene Aufzeichnungen haben in der Standardansicht Vorrang vor überlappenden Importwerten.
- Dunkles und helles Farbschema, Hoch- und Querformat.
- Deutlich gekennzeichnete Demo ohne Speicherung erfundener Messwerte.

Kein Konto, keine Werbung. Energiedaten und Geräteschlüssel bleiben auf dem Telefon. Nur die optionale Wetterfunktion nutzt Internet: Suchtext bzw. Ortskoordinaten gehen per HTTPS an Open-Meteo. Wetterdaten: Open-Meteo (CC BY 4.0), Orte: GeoNames. Geräteschlüssel werden mit dem Android Keystore verschlüsselt gespeichert; Messdaten bleiben lokal.

## Ansichten

Die folgenden Ansichten zeigen **erfundene Beispieldaten im Demo-Modus**, keine Messung an einer realen Anlage.

| Dunkle Übersicht | Helle Übersicht |
|---|---|
| ![Watt’n’Pit im dunklen Farbschema, Demo](docs/images/overview-dark.png) | ![Watt’n’Pit im hellen Farbschema, Demo](docs/images/overview-light.png) |

| Verlauf, dunkel | Verlauf, hell |
|---|---|
| ![Leistungskurven im dunklen Farbschema, Demo](docs/images/history-dark.png) | ![Leistungskurven im hellen Farbschema, Demo](docs/images/history-light.png) |

## Einrichtung

1. [Aktuelle APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/latest) und auf **Android 8 oder neuer** installieren.
2. Beide Geräte in VictronConnect öffnen, unter **Einstellungen → ⋮ → Produktinfo** die **Sofortanzeige über Bluetooth** einschalten.
3. Unter **Sofortanzeige-Details → Anzeigen** jeweils MAC-Adresse und **32-stelligen Verschlüsselungsschlüssel** übernehmen.
4. In Watt’n’Pit **Geräte → Suche starten** wählen und das gefundene Gerät einrichten, oder es mit **+** manuell hinzufügen.
5. Schlüssel einfügen und Aufzeichnung starten. Die aktive Geräteverbindung in VictronConnect verlassen, damit die Sofortanzeige empfangen wird.

Der Schlüssel ist je Gerät verschieden und **nicht die sechsstellige Bluetooth-PIN**. Die Android-Berechtigung für Geräte in der Nähe ist erforderlich. Auf Android 8–11 benötigt die BLE-Suche außerdem die Standortberechtigung und den aktivierten Standortdienst; Watt’n’Pit zeichnet keinen Standort auf.

## Übersicht gestalten

- **+ Kachel**: Messwert auswählen. Die neue Kachel erscheint am Ende der Übersicht und wird in den sichtbaren Bereich gescrollt.
- **Kachel antippen**: Messwert, Beschriftung, Einheit, Darstellung und Zeitraum ändern. Weitere Optionen stehen unter Farben & Schrift, Skala sowie Größe & Ebene.
- **Anordnen**: Kacheln ziehen und an der unteren rechten Ecke vergrößern. Überlappungen sind erlaubt; andere Kacheln behalten ihre Position. **Fertig** beendet das Anordnen.
- Schmale und breite Ansichten haben eigene Positionen und Größen. Die Einstellungen werden auf diesem Telefon gespeichert und bleiben beim App-Update erhalten.
- Unter **Einstellungen → Standardübersicht wiederherstellen** lässt sich die schlanke Ausgangsansicht zurückholen.
- **Einstellungen → Deine Solaranlage → Bearbeiten**: Solarkreise hinzufügen oder entfernen. Nennleistungen werden addiert; sie ersetzen keine gemessenen Solarwerte und ändern keine Reglerparameter.

Temperatur und Zusatzspannung benötigen den entsprechend eingerichteten Shunt-Eingang. Die Energie im Akku ist eine rechnerische Näherung aus Ladezustand, Kapazität und momentaner Spannung. Fehlende Werte erscheinen als „—“. Zeitfenster zeigen ausschließlich gespeicherte Messwerte; beim Solar-Tagesertrag werden Zählerstände ausgewertet und keine Tageserträge addiert.

## Solar und Batteriebilanz

Die Solaransicht zeigt die am SmartSolar angeschlossenen Module. Ein mobiles Modul an einem separaten PWM-Regler ist in der **Batteriebilanz des SmartShunts** enthalten, wenn alle Ladegeräte/Verbraucher auf der Anlagenseite des Shunts angeschlossen sind. Ohne eigenen Messwert lässt sich seine einzelne Leistung nicht bestimmen.

Positiver Batteriestrom bedeutet Laden, negativer Batteriestrom Entladen. Restlaufzeit und Ladezustand stammen vom SmartShunt; Watt’n’Pit kalibriert den Shunt nicht und ändert keine Einstellungen des Ladegeräts. Temperatur oder Zusatzspannung erscheinen nur, wenn der entsprechende Shunt-Eingang dafür eingerichtet ist und Werte liefert.

## Stand und offene Aufgaben

Der Archivimport beider Geräte und die Anzeige im Verlauf wurden mit 0.1.5 an einer realen SmartSolar-/SmartShunt-Anlage erfolgreich bestätigt. Der Java-Decoder wurde zusätzlich mit einer privaten Bluetooth-Aufzeichnung gegen die unabhängige Offline-Auswertung abgeglichen (28.079 Einträge einschließlich fehlender Werte). Protokoll-, SQL-Abgleich- und CSV-Tests, Android-Lint und APK-Build sind automatisiert geprüft. 0.1.8 ergänzt Sonnenaufgangszeiträume je Kachel, negative Energieanzeigen, die neue Standardanordnung und eine portable Einstellungssicherung. Die Prüfung umfasst 59 JVM-Tests, 34 Node-Tests sowie Browserprüfungen für Zeitgrenzen, Anordnung, Importvorschau und -abbruch, Fehlerfälle, Geheimnisausschluss und Kontrast. Die Android-Dateiauswahl und Wiederherstellung sind zusätzlich am Gerät zu bestätigen.

Beim App-Start bzw. nach mehr als 30 Sekunden im Hintergrund beginnt ein kurzer Importlauf für die eingerichteten Geräte. Bluetooth muss eingeschaltet und VictronConnect getrennt sein. Android kann beim ersten Mal die sechsstellige Geräte-PIN anfordern. Die 32-stelligen Sofortanzeige-Schlüssel und diese PIN sind verschieden. Bereits gelesene Bereiche werden gespeichert; erneutes Starten ergänzt Lücken und erzeugt keine doppelten Archivpunkte. Abbrüche behalten alle schon abgeschlossenen Blöcke. Außerhalb der Reichweite endet der Lauf mit einem Hinweis; ein neuer Versuch erfolgt beim nächsten App-Start oder über „Gerätearchiv ergänzen“.

Die Archivkanäle und Speicherauflösungen werden vom Gerät abgefragt. Bisher sind SmartSolar und SmartShunt am Mitschnitt geprüft. Weitere Victron-Geräte sind damit **nicht pauschal bestätigt**. Nicht unterstützte Kanäle werden übersprungen, fehlende Werte bleiben leer. Ohne sicheren Zeitanker werden keine geratenen historischen Zeitstempel gespeichert. Die App löscht keine Gerätetrends und ändert keine Ladeeinstellungen.

In dieser Version lässt sich je ein SmartSolar und ein SmartShunt einrichten. Eine lang laufende Aufzeichnung kann unter aggressiven Android-Energiespareinstellungen eingeschränkt werden; bei Bedarf die Hintergrundnutzung für Watt’n’Pit erlauben.

## Entwicklung

Voraussetzungen: Java 17 und Android SDK 35.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Die öffentlich bereitgestellte Release-APK wird getrennt mit einem dauerhaften privaten Signaturschlüssel signiert. Die Signatur bleibt für spätere Updates erhalten.

Die Einheiten- und Konfigurationstests laufen mit `node --test tools/dashboard-core.test.cjs tools/equipment-core.test.cjs tools/settings-backup.test.cjs`. Zusätzliche Browserprüfungen: `node tools/dashboard-scale-smoke.cjs`, `node tools/dashboard-live-smoke.cjs`, `node tools/dashboard-contrast-smoke.cjs` und `node tools/dashboard-sunrise-settings-smoke.cjs`. Weitere UI-Prüfungen: `node tools/extras-smoke.cjs` und `node tools/equipment-smoke.cjs` (Playwright).

Für die Oberflächenprüfung: `node tools/ui-smoke.cjs` (Playwright erforderlich; startet einen lokalen Asset-Server selbst). Die App selbst nutzt keine npm-Abhängigkeiten.

MIT-Lizenz. Unabhängiges Projekt, kein offizielles Victron-Produkt.

## Tagesarchiv
Victron SolarHistory.csv wird getrennt von Livewerten gespeichert und im Verlauf als Tageswerttabelle angezeigt. Der Bluetooth-Import speichert zusätzlich bis zu 31 Solar-Tagesdatensätze und Shunt-Gesamtwerte getrennt von den Zeitreihen. Tagesdaten werden nach dem aktuellen Telefondatum zugeordnet; historische Zeitreihen verwenden die gespeicherten Gerätezeitanker. Die App lädt keine privaten Beispielmessungen vor.
