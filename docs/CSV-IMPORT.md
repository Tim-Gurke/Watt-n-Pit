# Verlauf importieren und sichern

In Watt’n’Pit unter **Verlauf → CSV importieren** eine exportierte Datei auswählen. Watt’n’Pit erkennt Semikolon, Komma und Tabulator sowie übliche Datum-/Zeit- und Messwertspalten. UTF-8, UTF-16 mit BOM und Windows-1252 werden gelesen. Zellen mit Anführungszeichen und mehrzeilige Textzellen sind möglich. Pro Datei maximal 25 MB und 500.000 Zeilen.

Vor der Übernahme wird eine Zuordnung angezeigt. Mindestens ein Zeitstempel oder eine Datumsspalte sowie ein Messwert müssen zugeordnet sein. Datum und Uhrzeit können getrennte Spalten sein. Gerätetyp SmartShunt/SmartSolar wählen. Eine native Watt’n’Pit-Datei behält die Gerätetypen und Datenquellen jeder Zeile.

| Messgröße | Einheit in Watt’n’Pit |
|---|---|
| Batteriespannung | V |
| Batteriestrom | A |
| Batterieleistung | W, positiv Laden / negativ Entladen |
| Solarleistung | W |
| Ladezustand | % |
| Tagesertrag | Wh |
| Restlaufzeit | Minuten |
| Entnommene Kapazität | Ah |
| Temperatur | °C |

Spaltenköpfe mit `kW`, `kWh`, `mA`, `mV` werden passend umgerechnet. Restlaufzeit mit `h`/`s` im Spaltenkopf wird in Minuten umgerechnet. Eine fehlende Messgröße bleibt leer. Batteriespannung × Batteriestrom liefert Batterieleistung, sofern sie nicht als eigene Spalte vorhanden ist.

Zeitstempel können ISO-8601 mit Zeitzone, Unix-Zeit in Sekunden/Millisekunden oder lokale Angaben wie `14.10.2026 11:00:00` und `2026-10-14 11:00:00` sein. Zeitstempel ohne Zeitzone verwenden die lokale Telefonzeit. Ungültige Zeilen werden gezählt; sie ändern keine vorhandenen Werte. Doppelte Datensätze werden übersprungen.

`Alle Daten` bevorzugt eigene Aufzeichnungen in zeitlich überlappenden Bereichen. `CSV-Import` zeigt ausschließlich CSV-Werte; `Gerätearchiv` zeigt den Bluetooth-Import. CSV ist eine zusätzliche Quelle; ein Import ersetzt keine eigene Aufzeichnung.

**Verlauf exportieren** sichert sämtliche Rohdaten mit Datum, Gerät, Gerätetyp und Quelle. Geräteschlüssel werden nicht exportiert. Eine exportierte Watt’n’Pit-CSV kann wieder eingelesen werden.

Ein VictronConnect-CSV kann je nach App-Version unterschiedlich aussehen. Daher ist die Zuordnung sichtbar und anpassbar. Binäre Victron-Einstellungsdateien sind keine Messwert-CSV. Gerätearchive werden in 0.1.9 zusätzlich direkt über Bluetooth ergänzt; dafür unter Geräte den Archivabruf verwenden. [Anleitung](ANLEITUNG.md#2-gerätearchive-automatisch-ergänzen).
