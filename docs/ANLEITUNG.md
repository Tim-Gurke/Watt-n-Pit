# Watt’n’Pit bedienen

Für Version **0.1.9** · [APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v0.1.9/Watt-n-Pit-v0.1.9.apk) · [Zur Projektübersicht](../README.md)

Watt’n’Pit zeigt Solarleistung, Batteriezustand und gespeicherte Verläufe direkt auf deinem Android-Telefon. Die folgenden Screenshots stammen aus der App-Oberfläche mit **frei erfundenen Beispieldaten**. Wetterort und Gerätekennungen sind Beispiele; es sind keine privaten Verbindungsdaten enthalten. Eine sichtbare Demo liest keine Bluetooth-Geräte aus.

## 1. Installieren und Geräte einrichten

1. Die APK herunterladen und auf **Android 8 oder neuer** installieren. Beim Update die bestehende App behalten und die neue APK darüber installieren.
2. In **VictronConnect** den SmartSolar bzw. SmartShunt öffnen. Unter **Einstellungen → ⋮ → Produktinfo** die **Sofortanzeige über Bluetooth** aktivieren.
3. Bei **Sofortanzeige-Details → Anzeigen** die Geräteadresse und den **32-stelligen Verschlüsselungsschlüssel** übernehmen. Jeder Schlüssel gehört zu genau einem Gerät.
4. In Watt’n’Pit **Geräte → Suche starten** wählen. Das gefundene Gerät einrichten oder über **+** manuell hinzufügen. Gerätetyp, Name, Adresse und Schlüssel eintragen und speichern.
5. Die aktive Verbindung in VictronConnect trennen und in Watt’n’Pit die Aufzeichnung starten. In Reichweite der Geräte bleiben.

Die sechsstellige **Bluetooth-PIN** wird für die Android-Kopplung verwendet. Sie ist ein anderer Code als der 32-stellige Schlüssel der Sofortanzeige. Eine bereits vorhandene Android-Kopplung benötigt keine erneute PIN-Abfrage.

Die Berechtigung für **Geräte in der Nähe** erlauben. Auf Android 8–11 sind für die Gerätesuche außerdem Standortberechtigung und eingeschalteter Standortdienst nötig. Watt’n’Pit zeichnet keinen Standort auf.

## 2. Gerätearchive automatisch ergänzen

Beim App-Start ergänzt Watt’n’Pit die Archive der eingerichteten Geräte nacheinander. Nach mehr als 30 Sekunden im Hintergrund beginnt beim Zurückkehren ebenfalls ein kurzer Importlauf. Unter **Geräte → Gerätearchiv ergänzen** lässt sich der Abruf von Hand starten.

![Geräteseite mit beispielhaftem Importstatus und neutralen Gerätekennungen](images/devices-archive.png)

*Beispiel einer eingerichteten Anlage: Importstatus, vorhandene Android-Kopplung und manueller Archivabruf stehen auf der Geräteseite.*

- Vorhandene Daten werden abgeglichen. Ein erneuter Import ergänzt fehlende Werte, statt denselben Archivpunkt mehrfach abzulegen.
- Fortschritt und **Import abbrechen** stehen oben auf der Geräteseite. Bereits abgeschlossene Datenblöcke bleiben nach einem Abbruch gespeichert und sind im Verlauf verfügbar.
- Archivwerte und selbst aufgezeichnete Werte können unterschiedlich fein sein. Die Standardansicht führt die Quellen zusammen; feinere vorhandene Messwerte haben Vorrang.
- Leere Archivfelder werden nicht als gemessene Nullwerte ausgegeben. Lücken können deshalb sichtbar bleiben.

Falls nichts ankommt: Bluetooth und Reichweite prüfen, VictronConnect vom Gerät trennen und den Import erneut versuchen. **Liveempfang** bestätigt den Empfang der Sofortanzeige; der aktive Archivabruf ist ein eigener Vorgang. Über **Verbindungsdetails → Diagnose kopieren** lässt sich ein Bericht ohne Geräteadressen, Schlüssel oder Messwerte kopieren.

## 3. Den Startbildschirm gestalten

| Dunkles Design | Helles Design |
|---|---|
| ![Übersicht mit Messkacheln, negativer Batterieentnahme und Wetterkachel, dunkel](images/overview-dark.png) | ![Dieselbe Übersicht im hellen Design](images/overview-light.png) |

*Sieben Messkacheln und die kompakte Wetterkachel bilden den Standard. Werte, Farben, Größe und Position lassen sich anpassen.*

**+ Kachel** öffnet die Auswahl. Neue Kacheln erscheinen am Ende des Rasters. Eine Messkachel antippen, um Beschriftung, Einheit, Darstellung und Zeitraum zu ändern. Als Darstellung stehen je nach Messgröße Zahl, Rundinstrument oder Verlauf zur Wahl.

Unter **Anordnen** die Kacheln ziehen und an der unteren rechten Ecke vergrößern. Überlappungen sind erlaubt. Andere Kacheln behalten ihre Position. **Fertig** beendet die Anordnung. Schmale und breite Ansichten speichern ihre Positionen und Größen getrennt.

![Anordnungsmodus mit verschiebbaren Kacheln und Reihenfolge der Übersichtsblöcke](images/arrange.png)

*Die Reihenfolge von „Kacheln mit Wetter“, „Verlauf“ und „Start / Pause“ lässt sich über die Pfeile ändern. Innerhalb des Kachelblocks wird direkt gezogen.*

**Einstellungen → Standardübersicht wiederherstellen** setzt die Kachelanordnung zurück. Eigene Messdaten und eingerichtete Geräte bleiben dabei erhalten.

## 4. Aktuell, Zeitraum und Skala wählen

Bei einer Verlaufskachel sind **die große Zahl und die Kurve getrennt einstellbar**:

| Einstellung | Bedeutung |
|---|---|
| Große Zahl: **Aktuell (Live)** | Frischer Empfang des Geräts; ohne frischen Wert erscheint „—“. |
| Große Zahl: **Letzter gespeicherter Wert** | Letzter gespeicherter Wert im gewählten Zeitraum. |
| Durchschnitt / Minimum / Maximum | Auswertung vorhandener Werte im gewählten Zeitraum. |
| Kurvenzeitraum: **Seit Sonnenaufgang** | Beginn am heutigen Sonnenaufgang des eingestellten Wetterorts. |
| Skala: **Automatisch nach bisherigen Werten** | Grenzen folgen den Messwerten des gewählten Zeitraums. |

![Kacheleinstellungen mit Sonnenaufgang als Kurvenzeitraum und aktueller Livezahl](images/tile-sunrise.png)

*Beispiel Solarleistung: Livezahl anzeigen und die gespeicherte Kurve seit Sonnenaufgang betrachten. Die Skala kann sich automatisch anpassen.*

**Kachel antippen → Wert & Zeitraum → Seit Sonnenaufgang** funktioniert auch bei berechnetem Solarertrag und Batterieentnahme. Deren Energiesummen beziehen sich dann auf dieses Fenster. Ohne verwendbare Sonnenaufgangszeit oder vor dem heutigen Sonnenaufgang steht ausdrücklich **heute (Ersatz)**; die Auswahl bleibt gespeichert.

Unter **Skala** zwischen festen Grenzen und automatischer Skalierung wählen. Bei kleinen Solarleistungen nutzt die Kurve dadurch die verfügbare Höhe. Spannungs- und Temperaturkurven passen auch die Untergrenze an. **Farben & Schrift** bietet eigene Farben, Schriftgröße, Nachkommastellen, Hintergrund und Deckkraft.

## 5. Ertrag und Batterieentnahme verstehen

**Solarertrag berechnet** summiert die gespeicherte Solarleistung über die erfasste Zeit. **Batterieentnahme berechnet** summiert die über den Shunt erfasste Entladeleistung. Beide Werte werden in Wh oder kWh dargestellt. Größere Messlücken werden ausgelassen; die Anzeige nennt den erfassten Anteil des Zeitraums.

Batterieentnahme erscheint standardmäßig **negativ**. Eine Kurve unter null zeigt die wachsende Entnahme aus dem Akku. In der Energiekachel lässt sich unter **Wert & Zeitraum → Vorzeichen umkehren (+/−)** weiterhin die gewünschte Darstellung wählen. Die gespeicherten Messwerte werden dadurch nicht verändert.

![Berechneter Solarertrag oberhalb und Batterieentnahme unterhalb von null](images/energy-history.png)

*Verlauf → Energie berechnet: positiver Solarertrag und negative Batterieentnahme. Die Prozentangaben beziehen sich auf die erfasste Zeit.*

**Batterieentnahme ist kein Gesamtverbrauch.** Solarstrom, der direkt einen Verbraucher versorgt, muss nicht aus dem Akku entnommen werden. Ein weiterer Regler kann die Batteriebilanz beeinflussen, ohne seinen eigenen Solarertrag separat zu melden. Der grobe Tageszähler des SmartSolar bleibt als **Solarertrag heute** zusätzlich auswählbar.

## 6. Den Verlauf untersuchen

Unter **Verlauf** den Zeitraum und die Messgröße wählen. **Alle Daten**, **Aufgezeichnet**, **Gerätearchiv** und **CSV-Import** filtern die Datenquelle.

![Leistungsverlauf mit Quellenwahl und Bedienelementen für die Achsen](images/history-dark.png)

*Solarleistung und Batteriebilanz gemeinsam betrachten. Unter „Werteskala“ lassen sich die Grenzen von Hand setzen.*

Mit **+ / −** die Zeitachse zoomen. Mit einem Finger die Zeitachse verschieben, mit zwei Fingern zoomen und durch Antippen einzelne Werte ansehen. Lange Zeiträume bündeln Messwerte; die Schattierung zeigt die ursprünglichen Minima und Maxima. **Zurücksetzen** stellt die Achsen zurück.

Die kleine Verlaufskarte auf der Übersicht bietet ebenfalls **Seit Sonnenaufgang**, **Heute** oder **Letzte 6 Stunden**. Diese Auswahl ist unabhängig vom großen Verlauf und den einzelnen Kacheln.

## 7. Wetter und Solaranlage einstellen

**Einstellungen → Sonne voraus.** öffnet die manuelle Ortssuche. GPS wird dafür nicht benötigt. Nach der Ortswahl zeigt die flache Wetterkachel **14 Tage mit je einem Symbol und den erwarteten Sonnenstunden**. Seitlich wischen, um die weiteren Tage zu sehen; **↻** aktualisiert die Vorschau.

Die Wetterkachel liegt im selben Raster wie die Messkacheln. **Anordnen** macht sie verschiebbar. Den Ortskopf antippen oder **···** wählen, um Beschriftung, Breite, Höhe und Deckkraft anzupassen. Nach dem Entfernen ist sie über **+ Kachel → Wetter** wieder verfügbar. Ohne Wetterort wird sie außerhalb des Anordnungsmodus ausgeblendet.

![Editor für beispielhafte Solarkreise mit Nennleistung und Messwertquelle](images/equipment-editor.png)

*Einstellungen → Deine Solaranlage → Bearbeiten: Modulgruppen beschreiben und ihre Messwertquelle zuordnen. Die gezeigten Angaben sind frei erfunden.*

Pro Solarkreis lassen sich Bezeichnung, Modulanzahl, Wp pro Modul, Montage und Regler eintragen. Als Messwertquelle stehen Bluetooth-Solarladeregler, Batteriebilanz des Shunts oder kein eigener Messwert zur Wahl. Die Nennleistung beschreibt deine Hardware; sie ersetzt keine Messung und ändert keine Geräteparameter. In 0.1.9 kann je ein SmartSolar und ein SmartShunt eingerichtet werden.

## 8. Einstellungen und Messdaten sichern

![Einstellungssicherung mit Export und Import als JSON-Datei](images/settings-backup.png)

*Einstellungen → Einstellungen sichern: die persönliche Gestaltung und Anlagenkonfiguration als Datei sichern.*

**Einstellungen exportieren** erstellt eine JSON-Datei mit Kacheln, beiden Anordnungen, Farben, Zeiträumen, Akku-/Speicherparametern, Solaranlage und Wetterort. **Einstellungen importieren** zeigt vor der Übernahme eine geprüfte Vorschau. Ältere Sicherungen mit separatem Wetterblock werden übernommen.

Die Einstellungssicherung enthält **keine Geräteadressen, PINs, Verbindungsschlüssel oder Messdaten**. Auf dem Zieltelefon eingerichtete Geräte und vorhandene Messdaten bleiben erhalten. Auf einem neuen Telefon die Geräte separat einrichten. Der enthaltene Wetterort gehört zu deiner Konfiguration; eine Sicherung deshalb bewusst weitergeben.

Messdaten werden getrennt über **Verlauf → Verlauf exportieren** als CSV gesichert und über **CSV importieren** wieder eingelesen. Näheres zur Spaltenzuordnung: [CSV-Import und Datenexport](CSV-IMPORT.md).

## 9. Lokal, mit optionalem Wetterabruf

Watt’n’Pit benötigt kein Konto und überträgt keine Energiedaten oder Geräteschlüssel an einen Cloud-Dienst. Schlüssel liegen mit dem Android Keystore geschützt auf dem Telefon. Die optionale Wetterfunktion benötigt Internet: Suchtext bzw. gewählte Ortskoordinaten gehen an Open-Meteo. Wetterdaten: Open-Meteo, CC BY 4.0; Ortsdaten: GeoNames.

Ladezustand und Restlaufzeit stammen vom SmartShunt. Temperatur und Zusatzspannung benötigen einen entsprechend eingerichteten Eingang. Weitere Victron-Geräte sind nicht pauschal als kompatibel bestätigt. [Technische Grundlage und Grenzen](ARCHITECTURE.md).
