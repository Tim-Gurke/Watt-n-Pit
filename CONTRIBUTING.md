# Hinweise für Änderungen

Neue Kacheln der Übersicht gehören in das gemeinsame Dashboard-Raster. Sie müssen unter **Anordnen** verschiebbar und skalierbar sein und ihre Position getrennt für schmale und breite Ansicht speichern. Keine neuen feststehenden Kacheln oberhalb oder unterhalb des Rasters ergänzen. Bereits gespeicherte Positionen bei Erweiterungen erhalten; neue Kacheln einmalig an freier Stelle ergänzen.

Die Verbundkachel verwendet denselben Katalog und dieselben Gesten wie die Mess- und Wetterkacheln. Umfangreiche Bereiche wie **Zusatzspeicher** erhalten einen eigenen Menüpunkt. Die genaue Standardanordnung der Kacheln wird separat mit dem Nutzer abgestimmt.

Aktionen über den Anlagenverbund brauchen eine Bestätigung vom Hauptgerät. Vor dieser Antwort darf die Anzeige keinen erfolgreichen Zustandswechsel behaupten. Neue Netzwerkaktionen auf ihren benötigten Umfang begrenzen; wiederholte oder verspätete Anfragen dürfen keine zweite oder inzwischen ungültige Änderung auslösen.

## Freigegebener Umfang für 1.6.3

Der Nutzer hat Umsetzung, Build und Veröffentlichung freigegeben.

- Schriftgröße überall maximal **20**, einschließlich gespeicherter und importierter Layouts; kleinere Werte erhalten.
- Die G5-Datei `Watt-n-Pit-Einstellungen (2).json` und der Screenshot `Screenshot_20261027-184807364.jpg` sind die freigegebene Layoutvorlage. Nur deren Dashboard ist in `dashboard-preset.json` übernommen; private Anlagen-, Standort- und Geräteeinstellungen gehören nicht in die Vorlage.
- Beim Update die G5-Vorlage auf jedem Handy einmalig installieren, einschließlich Hauptgerät und Anzeigen. Damit ersetzt sie ausdrücklich die bisherigen lokalen Layouts; spätere eigene Änderungen bleiben erhalten. Beide Ansichten sind unabhängig gespeichert.
- Gemeinsame App- und Anlageneinstellungen auch von Anzeigen bedienen: insbesondere Solaranlage, Aufstellung, Wetter, Zusatzspeicher, Geräte und Aufzeichnung. Erst die Antwort des Hauptgeräts bestätigt die Änderung. Wiederholte und überholte Befehle abfangen; Layout und Erscheinungsbild bleiben lokal einstellbar.
- Beim Zusatzspeicher die **aktuell enthaltene Energie in Wh** anzeigen: Näherung aus BMS-Restkapazität und Nennspannung, ersatzweise aus Ladezustand und Nennenergie. Fehlende aktuelle BMS-Werte bleiben unbekannt.

## Nachladen und Tagesarchiv

Bereits erfolgreich geladene historische Werte und Kurven bleiben beim Nachladen derselben Ansicht sichtbar. Frische Daten ersetzen sie erst nach der Antwort; währenddessen „Letzter Stand“ anzeigen. Fehler als Aktualisierungsfehler kennzeichnen, nicht als fehlende Messdaten. Bei einem Wechsel von Anlage, Messwert, Quelle oder Zeitraum keine fremden Werte übernehmen. Echte neue Leerantworten müssen alte Kurven weiterhin entfernen können.

Kalenderabfragen für das Solar-Tagesarchiv enthalten das Monatsende, auch wenn es noch in der Zukunft liegt. Hauptgerät und Anzeige müssen dafür dieselbe begrenzte Tagesberechnung verwenden. Reale Nullwerte und unbekannte Werte unterscheiden.

## Freigegebener Umfang für 1.6.5

Der Nutzer hat Umsetzung, Build und Veröffentlichung freigegeben. Die Diagnose der Version 1.6.4 zeigte einen nur aus einem teilweise geschätzten Mittag hochgerechneten Tagesbedarf.

- Die Aufstellung vom **20.–26. Oktober 2026** war laut ausdrücklicher nachträglicher Bestätigung unverändert. Diese Bestätigung ausschließlich der passenden lokal gespeicherten Anlage zuordnen, als eigenen begrenzten Zeitraum dokumentieren und deren vorhandene Messdaten erneut auswerten. Andere Installationen und bereits dokumentierte Aufstellungen nicht überschreiben. Keine Geräteadressen, Koordinaten oder private Anlagenkonfiguration im öffentlichen Quelltext ablegen.
- Historisch ungemessene mobile Beiträge bleiben Schätzungen. Fehlende frühere Ladephasen oder Reglereinstellungen nicht als erwiesene unbeschränkte Erzeugung behandeln. Rekonstruierte Archivbeiträge schwächer gewichten und mit größerer Unsicherheit anzeigen; die Solar-Ertragskalibrierung damit nicht trainieren.
- Ein einzelner nur geschätzter Tagesabschnitt darf keinen scheinbar belastbaren üblichen Tagesbedarf festlegen. Für solche Abschnitte mehrere Tage und ausreichend wirksame Messzeit verlangen. Fehlende belastbare Werte als offen ausweisen; Messwerte nicht auf einen gewünschten Zielverbrauch zurechtrechnen. Ausschlussgründe und Beiträge je Tagesabschnitt sichtbar machen.
- Bei veränderten Gewohnheiten ein ausreichend belegtes aktuelles Profil der letzten zwei Wochen stärker gewichten. Einzelne Sondertage abfangen; saisonale Erfahrungen getrennt erhalten. Lerndaten außerhalb des 400-Tage-Fensters nicht mehr verwenden, ohne den Messverlauf zu löschen.
- Die G5-Layoutübernahme war **einmalig**. Ab jetzt bestehende Layouts jedes Geräts immer erhalten, auch ohne den früheren Migrationsmarker oder beim Überspringen älterer Releases. Das umfasst Anordnung, Größen und Gestaltung beider Ansichten. Neue Kacheln nur an freier Stelle ergänzen; kein automatischer Layoutwechsel beim Update.

## Roller-BMS: Geräteaufteilung

Auf der Zentrale **G5** läuft nur Watt’n’Pit; **ScootPit läuft auf dem S20**. Die vorhandene lokale BMS-Übergabe zwischen beiden Apps auf demselben Telefon deckt das nicht ab. Eine automatische Übergabe G5 → Fahrhandy S20 → G5 fehlt noch und gehört nicht zur Anzeigekorrektur in 1.6.4. Die Solaraufzeichnung soll bei einer späteren BMS-Übergabe weiterlaufen.

## Freigegebener Umfang für 1.6.6 und ScootPit 1.6.14

Der Nutzer hat die zusammengefasste Umsetzung, Builds und Veröffentlichung **beider Apps** am 28.10.2026 freigegeben. Geräteaufteilung bleibt G5 als Zentrale, S20 als Fahrhandy/Hotspot. Keine zusätzliche Übergabetaste: der vorhandene ausdrückliche Einschaltwunsch in ScootPit fordert automatisch die BMS-Verbindung über das lokal gekoppelte Watt’n’Pit an. Solaraufzeichnung dabei unverändert weiterführen.

- Lastausgang ausschließlich durch ausdrücklichen Nutzerbefehl in ScootPit schalten; die Übergabe selbst liest nur. Wartende Einschaltwünsche verfallen und werden nie nach Neustart oder Abbruch nachgeholt.
- Fahrtpausen und Sperren unterwegs behalten die Zuständigkeit des Fahrhandys. Für reguläre Rückgabe stabile Erreichbarkeit, beendete Fahrt und frische Bestätigung der Sperre verlangen. Bereits gesperrt genügt. Vor bestätigter Freigabe keine konkurrierende neue Verbindung starten; Erfolg erst durch frische Daten bestätigen.
- Ausfallende Reservierungen begrenzen, verspätete/duplizierte Aktionen und frühere Dienstprozesse abweisen; Besitzer, Sperrstatus und Verlauf verständlich zeigen. Eine fehlende Zentrale darf das direkte Schalten unterwegs nicht blockieren.
- Roller-Ladeanteile nur bei passender endlicher BMS-Abdeckung und eingefrorenem Verlustprofil vom Verbrauchslernen abziehen. Rohwerte und tatsächliche Gesamtsummen erhalten. Geschätzte Anteile schwächer gewichten, Empfangslücken niemals ergänzen.
- Feste Messung nur vorsichtig als mobile Modellreferenz verwenden. Standortbezogene Korrekturen von begrenzter allgemeiner Anlagenerfahrung trennen; Schatten und aktuelle Reglerbegrenzung nicht blind übertragen. Mobile Energie bleibt eine Schätzung. Verlustfaktoren pro Kreis zulassen.
- Gerätelayouts erhalten, neue Kacheln verschiebbar, Standardschrift höchstens 20. Tatsächliche Hardware-/Bluetooth-Tests nicht aus Softwareprüfungen behaupten.
