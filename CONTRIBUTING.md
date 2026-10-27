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
