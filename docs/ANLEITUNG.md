# Watt’n’Pit bedienen

Für Version **1.2.2** · [APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v1.2.2/Watt-n-Pit-v1.2.2.apk) · [Zur Projektübersicht](../README.md)

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

Auf schmalen Bildschirmen stehen der schmale Ladezustand und die breitere Solarleistung oben. Darunter folgen „Ertrag − Entnahme“ und berechneter Solarertrag nebeneinander. Batteriespannung und die mit „Aktuell“ beschriftete Batteriebilanz stehen links übereinander; rechts liegt die größere negative Batterieentnahme. Das Wetter liegt flach über die gesamte Breite. Anschließend folgen Verlauf und Start/Pause. Im Hochformat bleibt diese Anordnung erhalten. Im Querformat wechseln **+ Kachel**, **Anordnen**, Empfangszustand und Ansichtsart zusammen mit „Energie im Blick“ in den kompakten Appkopf. Ab 760 Pixel Breite stehen sie gemeinsam in einer Zeile.

![Breite Ansicht mit Werkzeugen und Empfangszustand oben im kompakten Kopf](images/overview-landscape.png)

Die Restlaufzeit kann über **+ Kachel → Akku → Restlaufzeit** ergänzt werden; Stunden sind weiterhin die voreingestellte Einheit.

Solarleistung und berechneter Solarertrag zeigen im Standard die Zeit **seit Sonnenaufgang**. „Ertrag − Entnahme“ und Batterieentnahme beziehen sich auf **heute ab Mitternacht**. Die vier Verlaufskacheln passen ihre Skalen automatisch an die vorhandenen Werte an. Die breite Ansicht besitzt eine eigene gespeicherte Anordnung.

**+ Kachel** öffnet die Auswahl. Neue Kacheln erscheinen am Ende des Rasters. Eine Messkachel antippen und **Kachel-Einstellungen** wählen, um Beschriftung, Einheit, Darstellung und Zeitraum dauerhaft zu ändern. **Großansicht öffnen** zeigt dieselbe Messgröße zum Untersuchen in groß. Als Darstellung stehen je nach Messgröße Zahl, Rundinstrument oder Verlauf zur Wahl.

Unter **Anordnen** die Kacheln ziehen und an der unteren rechten Ecke vergrößern. Das Raster für Position und Größe ist in beiden Formaten doppelt so fein wie zuvor. Überlappungen sind erlaubt. Andere Kacheln behalten ihre Position. **Fertig** beendet die Anordnung. Schmale und breite Ansichten speichern ihre Positionen und Größen getrennt.

![Anordnungsmodus mit verschiebbaren Kacheln und Reihenfolge der Übersichtsblöcke](images/arrange.png)

*Die Reihenfolge von „Kacheln mit Wetter“, „Verlauf“ und „Start / Pause“ lässt sich über die Pfeile ändern. Innerhalb des Kachelblocks wird direkt gezogen.*

**Einstellungen → Standardübersicht wiederherstellen** stellt die neue Standardansicht einschließlich der Kachelzeiträume für schmale und breite Bildschirme her. Beim normalen App-Update bleiben vorhandene eigene Übersichten erhalten. Messdaten und eingerichtete Geräte werden durch das Wiederherstellen der Standardübersicht nicht verändert.

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

**Kachel antippen → Kachel-Einstellungen → Wert & Zeitraum → Seit Sonnenaufgang** funktioniert auch bei berechnetem Solarertrag und Batterieentnahme. Deren Energiesummen beziehen sich dann auf dieses Fenster. Ohne verwendbare Sonnenaufgangszeit oder vor dem heutigen Sonnenaufgang steht ausdrücklich **heute (Ersatz)**; die Auswahl bleibt gespeichert.

Unter **Skala** zwischen festen Grenzen und automatischer Skalierung wählen. Bei kleinen Solarleistungen nutzt die Kurve dadurch die verfügbare Höhe. Spannungs- und Temperaturkurven passen auch die Untergrenze an. **Farben & Schrift** bietet eigene Farben, Schriftgröße, Nachkommastellen, Hintergrund und Deckkraft.

### Großansicht und Vergleichswerte

**Kachel antippen → Großansicht öffnen** vergrößert die Messgröße. Oben den Zeitraum wählen: Heute, Gestern, Woche, Monat, Seit Sonnenaufgang, Seit Zurücksetzen, Letzte … oder Von / bis. **Ansicht einstellen** öffnet Darstellung, Einheit, große Zahl, automatische oder feste Skalen, Nachkommastellen und Farbe.

![Großansicht der Solarleistung mit Batteriebilanz und Batteriespannung als Vergleich](images/tile-detail.png)

Unter **Vergleichswerte** weitere Messgrößen einschalten. Gleiche Einheiten werden gemeinsam dargestellt; unterschiedliche Einheiten erhalten eigene Werteskalen bei derselben Zeitachse. **+ / −** zoomt die Zeitachse. Mit einem Finger verschieben, mit zwei Fingern zoomen. **Gesamter Zeitraum** stellt das vollständige gewählte Zeitfenster wieder her. Berechnete Energiesummen bleiben auf den gewählten Zeitraum bezogen.

Diese Änderungen gelten nur in der geöffneten Großansicht. **×** oder Android-Zurück schließt sie; die gespeicherte Kachel und ihre Anordnung bleiben erhalten. Für bleibende Änderungen **Kachel-Einstellungen** verwenden. Im Anordnungsmodus öffnet Antippen direkt den Kacheleditor.

## 5. Ertrag und Batterieentnahme verstehen

**Solarertrag berechnet** summiert die gespeicherte Solarleistung über die erfasste Zeit. **Batterieentnahme berechnet** summiert die über den Shunt erfasste Entladeleistung. Beide Werte werden in Wh oder kWh dargestellt. Größere Messlücken werden ausgelassen; die Anzeige nennt den erfassten Anteil des Zeitraums.

Batterieentnahme erscheint standardmäßig **negativ**. Eine Kurve unter null zeigt die wachsende Entnahme aus dem Akku. In der Energiekachel lässt sich unter **Wert & Zeitraum → Vorzeichen umkehren (+/−)** weiterhin die gewünschte Darstellung wählen. Die gespeicherten Messwerte werden dadurch nicht verändert.

![Berechneter Solarertrag oberhalb und Batterieentnahme unterhalb von null](images/energy-history.png)

*Verlauf → Energie berechnet: positiver Solarertrag, negative Batterieentnahme und die blaue gestrichelte Kurve „Ertrag − Entnahme“. Die Prozentangaben beziehen sich auf die erfasste Zeit.*

**Ertrag − Entnahme** ist der Solarertrag abzüglich der Batterieentnahme im selben ausgewählten Zeitraum. Zum Beispiel ergeben 40 Wh Ertrag und 70 Wh Entnahme den Vergleichswert **−30 Wh**. Unter **Kurve „Ertrag − Entnahme“ anzeigen** lässt sich die dritte Kurve ein- und ausblenden. Der Zahlenwert steht über dem Diagramm; Antippen des Diagramms zeigt die drei Werte am gewählten Zeitpunkt mit zwei Nachkommastellen.

Der Vergleich beschreibt weder den Gesamtverbrauch noch die Änderung der gespeicherten Akkuenergie. Die bisherige **Batteriebilanz in W** zeigt weiterhin die aktuelle Lade- bzw. Entladeleistung am Shunt. Fehlende Ertrags- oder Entnahmewerte werden nicht als null eingesetzt. Zwischen bekannten Kurvenpunkten werden die Zeitpunkte angeglichen; Aufzeichnungslücken bleiben unterbrochen.

### Den Vergleich als Kachel auf die Übersicht legen

Unter **+ Kachel → Energie → Ertrag − Entnahme** hinzufügen. Die neue Kachel erscheint am Ende der Übersicht; unter **Anordnen** lässt sie sich an die gewünschte Stelle ziehen. Vorhandene Kacheln behalten ihre Position.

![Die Vergleichskachel mit berechnetem Wert, Kurve und automatischer Skala](images/energy-tile.png)

Unter **Kachel antippen → Kachel-Einstellungen** die Darstellung **Zahlenwert** oder **Verlauf**, **Wh** oder **kWh** sowie den Zeitraum wählen. Möglich sind beispielsweise **Heute**, **Seit Sonnenaufgang**, **Letzte …** und ein freier Zeitraum. Beide Ausgangswerte werden für dasselbe Fenster berechnet. Im Verlauf ist die automatische Skala voreingestellt; feste Grenzen und Vorzeichenumkehr sind ebenfalls möglich.

Fehlt einer der beiden Werte, zeigt die Kachel **—** und den Hinweis, dass Ertrag und Entnahme benötigt werden. Eine gemessene Null wird als **0,00** angezeigt. Größe, Position, Darstellung, Einheit und Zeitraum sind Bestandteil der Einstellungssicherung. Die zeitliche Erfassung wird im ergänzenden Hinweis getrennt für Solar und Batterie geführt.

**Batterieentnahme ist kein Gesamtverbrauch.** Solarstrom, der direkt einen Verbraucher versorgt, muss nicht aus dem Akku entnommen werden. Ein weiterer Regler kann die Batteriebilanz beeinflussen, ohne seinen eigenen Solarertrag separat zu melden. Der grobe Tageszähler des SmartSolar bleibt als **Solarertrag heute** zusätzlich auswählbar.

## 6. Den Verlauf untersuchen

Unter **Verlauf** den Zeitraum und die Messgröße wählen. **Alle Daten**, **Aufgezeichnet**, **Gerätearchiv** und **CSV-Import** filtern die Datenquelle.

![Leistungsverlauf mit Quellenwahl und Bedienelementen für die Achsen](images/history-dark.png)

*Solarleistung und Batteriebilanz gemeinsam betrachten. Unter „Werteskala“ lassen sich die Grenzen von Hand setzen.*

Mit **+ / −** die Zeitachse zoomen. Mit einem Finger die Zeitachse verschieben, mit zwei Fingern zoomen und durch Antippen einzelne Werte ansehen. Lange Zeiträume bündeln Messwerte; die Schattierung zeigt die ursprünglichen Minima und Maxima. **Zurücksetzen** stellt die Achsen zurück.

Die kleine Verlaufskarte auf der Übersicht bietet ebenfalls **Seit Sonnenaufgang**, **Heute** oder **Letzte 6 Stunden**. Diese Auswahl ist unabhängig vom großen Verlauf und den einzelnen Kacheln.

## 7. Wetter und Solaranlage einstellen

**Einstellungen → Sonne voraus.** öffnet die manuelle Ortssuche. GPS wird dafür nicht benötigt. Nach der Ortswahl zeigt die flache Wetterkachel **14 Tage mit je einem Symbol und den erwarteten Sonnenstunden**. Seitlich wischen, um die weiteren Tage zu sehen; **↻** aktualisiert die Vorschau von Hand.

**Ab 1.2.2 wird die Vorhersage beim Öffnen der App und bei der Rückkehr aus dem Hintergrund automatisch frisch geladen**, sofern ein Wetterort eingestellt ist. Der Abruf läuft im Hintergrund. Währenddessen und ohne Internet bleibt die gespeicherte Vorschau sichtbar; bei einem Fehler erscheint ein entsprechender Hinweis. Ohne aktivierten Wetterort erfolgt kein Wetterabruf. **Wetter ausschalten** deaktiviert die Wetterfunktion wieder.

Die Wetterkachel liegt im selben Raster wie die Messkacheln. **Anordnen** macht sie verschiebbar. Den Ortskopf antippen oder **···** wählen, um Beschriftung, Breite, Höhe und Deckkraft anzupassen. Nach dem Entfernen ist sie über **+ Kachel → Wetter** wieder verfügbar. Ohne Wetterort wird sie außerhalb des Anordnungsmodus ausgeblendet.

![Editor für beispielhafte Solarkreise mit Nennleistung und Messwertquelle](images/equipment-editor.png)

*Einstellungen → Deine Solaranlage → Bearbeiten: Modulgruppen beschreiben und ihre Messwertquelle zuordnen. Die gezeigten Angaben sind frei erfunden.*

Pro Solarkreis lassen sich Bezeichnung, Modulanzahl, Wp pro Modul, Montage und Regler eintragen. Als Messwertquelle stehen Bluetooth-Solarladeregler, Batteriebilanz des Shunts oder kein eigener Messwert zur Wahl. Die Nennleistung beschreibt deine Hardware; sie ersetzt keine Messung und ändert keine Geräteparameter. In 1.2.2 kann je ein SmartSolar und ein SmartShunt eingerichtet werden.

## 8. Einstellungen und Messdaten sichern

![Einstellungssicherung mit Export und Import als JSON-Datei](images/settings-backup.png)

*Einstellungen → Einstellungen sichern: die persönliche Gestaltung und Anlagenkonfiguration als Datei sichern.*

**Einstellungen exportieren** erstellt eine JSON-Datei mit Kacheln, beiden Anordnungen, Farben, Zeiträumen, Akku-/Speicherparametern, Solaranlage und Wetterort. **Einstellungen importieren** zeigt vor der Übernahme eine geprüfte Vorschau. Ältere Sicherungen mit separatem Wetterblock werden übernommen.

Die Einstellungssicherung enthält **keine Geräteadressen, PINs, Verbindungsschlüssel oder Messdaten**. Auf dem Zieltelefon eingerichtete Geräte und vorhandene Messdaten bleiben erhalten. Auf einem neuen Telefon die Geräte separat einrichten. Der enthaltene Wetterort gehört zu deiner Konfiguration; eine Sicherung deshalb bewusst weitergeben.

Messdaten werden getrennt über **Verlauf → Verlauf exportieren** als CSV gesichert und über **CSV importieren** wieder eingelesen. Näheres zur Spaltenzuordnung: [CSV-Import und Datenexport](CSV-IMPORT.md).

## 9. Lokal, mit optionalem Wetterabruf

Watt’n’Pit benötigt kein Konto und überträgt keine Energiedaten oder Geräteschlüssel an einen Cloud-Dienst. Schlüssel liegen mit dem Android Keystore geschützt auf dem Telefon. Die optionale Wetterfunktion benötigt Internet: Suchtext bzw. gewählte Ortskoordinaten gehen an Open-Meteo. Wetterdaten: Open-Meteo, CC BY 4.0; Ortsdaten: GeoNames.

Ladezustand und Restlaufzeit stammen vom SmartShunt. Temperatur und Zusatzspannung benötigen einen entsprechend eingerichteten Eingang. Weitere Victron-Geräte sind nicht pauschal als kompatibel bestätigt. [Technische Grundlage und Grenzen](ARCHITECTURE.md).
