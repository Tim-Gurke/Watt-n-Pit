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

## Roller-BMS: Geräteaufteilung (offen)

Auf der Zentrale **G5** läuft nur Watt’n’Pit; **ScootPit läuft auf dem S20**. Die vorhandene lokale BMS-Übergabe zwischen beiden Apps auf demselben Telefon deckt das nicht ab. Eine automatische Übergabe G5 → Fahrhandy S20 → G5 fehlt noch und gehört nicht zur Anzeigekorrektur in 1.6.4. Die Solaraufzeichnung soll bei einer späteren BMS-Übergabe weiterlaufen.
