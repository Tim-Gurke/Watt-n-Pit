# Hinweise für Änderungen

Neue Kacheln der Übersicht gehören in das gemeinsame Dashboard-Raster. Sie müssen unter **Anordnen** verschiebbar und skalierbar sein und ihre Position getrennt für schmale und breite Ansicht speichern. Keine neuen feststehenden Kacheln oberhalb oder unterhalb des Rasters ergänzen. Bereits gespeicherte Positionen bei Erweiterungen erhalten; neue Kacheln einmalig an freier Stelle ergänzen.

Die Verbundkachel verwendet denselben Katalog und dieselben Gesten wie die Mess- und Wetterkacheln. Umfangreiche Bereiche wie **Zusatzspeicher** erhalten einen eigenen Menüpunkt. Die genaue Standardanordnung der Kacheln wird separat mit dem Nutzer abgestimmt.

Aktionen über den Anlagenverbund brauchen eine Bestätigung vom Hauptgerät. Vor dieser Antwort darf die Anzeige keinen erfolgreichen Zustandswechsel behaupten. Neue Netzwerkaktionen auf ihren benötigten Umfang begrenzen; wiederholte oder verspätete Anfragen dürfen keine zweite oder inzwischen ungültige Änderung auslösen.

## Vormerkungen für ein späteres Update

Derzeit nur Wünsche festhalten. Umsetzung, Build und Veröffentlichung warten auf einen späteren Auftrag des Nutzers.

- Schriftgröße überall maximal **20** voreinstellen. Mit dem späteren Update auch größere Schrift in bereits gespeicherten Layouts einmalig auf 20 reduzieren; kleinere Werte und die Anordnung erhalten.
- Die vom Hauptgerät **G5** bereitgestellte Datei `Watt-n-Pit-Einstellungen (2).json` und der Screenshot `Screenshot_20261027-184807364.jpg` dienen als Layoutreferenz für das spätere Update. Die Datei enthält 11 Kacheln, davon 6 mit Schriftgröße 32. Diese Werte später auf höchstens 20 setzen und die gespeicherten Anordnungen übernehmen. Die Anhänge bleiben bis dahin Referenzmaterial; derzeit nur sammeln.
- Mit dem späteren Update das bereitgestellte **G5-Layout auf allen Handys** automatisch einmalig übernehmen, einschließlich Hauptgerät und Anzeigegeräten. Dabei die Schriftgröße überall auf höchstens **20** setzen. Diese ausdrücklich gewünschte Übernahme ersetzt einmalig die bisherigen lokalen Layouts; ein manueller Import auf jedem Handy soll entfallen. Die Übernahme betrifft das Layout und seine Schriftgrößen.
- Das Anzeigehandy soll alle gemeinsamen App- und Anlageneinstellungen steuern können, insbesondere Solaranlage, mobile Aufstellung und Wetter. Das Hauptgerät übernimmt und bestätigt die Änderungen.
- Beim Zusatzspeicher die **aktuell enthaltene Energie in Wh** anzeigen; diese Anzeige beschreibt den gegenwärtigen Speicherinhalt.
