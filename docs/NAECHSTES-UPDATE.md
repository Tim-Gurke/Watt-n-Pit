# Für das nächste Update vorgemerkt

## Solarfenster an Sonnenzeiten und tatsächliche Erzeugung anpassen

Wunsch vom 18. Oktober 2026: Solarwerte mit Tageslichtfenster sollen nach Sonnenuntergang nicht weiter eine immer längere Nullstrecke anzeigen. Tatsächlich gemessene Erzeugung vor Sonnenaufgang oder nach Sonnenuntergang zählt weiterhin mit.

- Die Zeitachse von Solarleistung und Solarertrag im Sonnenaufgangsfenster läuft tagsüber bis jetzt, nach Sonnenuntergang regulär nur bis zum Sonnenuntergang.
- Der erreichte Solarertrag in Wh/kWh bleibt sichtbar. Aufzeichnung und Speicherung laufen weiter.
- Verwertbare positive Solarmessungen vor Sonnenaufgang erweitern den Beginn des Fensters nach vorn. Entsprechende Messungen nach Sonnenuntergang verlängern das Ende. Die Sonnenzeiten sind die regulären Grenzen; tatsächlich beobachtete Erzeugung hat Vorrang.
- Das gilt auch für die Energieberechnung: Gemessenen Ertrag außerhalb der Sonnenzeiten in Solarertrag und berechnetem Verbrauch mitführen; Ertrag minus Verbrauch muss weiterhin die Bilanz ergeben. Die bisherige Nullannahme vor Sonnenaufgang dafür anpassen. Messlücken bleiben unbekannt; keine Energie über unbeobachtete Abschnitte hochrechnen.
- Verbrauch und Bilanz zählen weiter bis jetzt beziehungsweise zum Ende des gewählten Tages. Der Verbrauch nach Sonnenuntergang bleibt enthalten; Ertrag minus Verbrauch muss weiterhin die angezeigte Bilanz ergeben.
- Freie Zeiträume behalten ihre ausdrücklich gewählten Grenzen. Beide Anordnungen sowie die unabhängige Livezahl bleiben erhalten.
- Sonnenuntergang am eingestellten Wetterort zusätzlich übernehmen. Bei fehlender oder ungültiger Sonnenzeit den bisherigen Verlauf mit erkennbarem Ersatz verwenden.

## Rolle als Energie-Cockpit deutlich erklären

Wunsch vom 19. Oktober 2026: Watt’n’Pit ergänzt die Victron-Software als Energieanzeige; Geräteeinstellungen werden weiterhin über VictronConnect vorgenommen.

- Beim ersten Öffnen nach der Installation als einmalige Hinweisbox mit Schaltfläche **Verstanden** anzeigen. Die Bestätigung auf dem Gerät speichern; sie gilt erst nach dem bewussten Bestätigen.
- Bei bestehenden Installationen den Hinweis beim ersten Start nach dem Update ebenfalls einmal zeigen. Nach der Bestätigung bei unverändertem Hinweis nicht bei jedem Start oder jedem weiteren Update wiederholen. Der gespeicherte Bestätigungsstand gehört nicht in die übertragbare Einstellungssicherung.
- Diesen Hinweis gut sichtbar auf der Geräteseite und bei den App-Einstellungen aufnehmen, ohne die Übersicht mit einer zusätzlichen dauerhaften Kachel zu belegen.
- Geplanter Wortlaut: „Watt’n’Pit ist eine Energieanzeige und ein Cockpit. Die App ersetzt VictronConnect nicht. Einstellungen an Solarregler und SmartShunt werden weiterhin in VictronConnect vorgenommen. Die Einstellungen hier betreffen nur Watt’n’Pit.“
- Bei der Batteriekapazität und den Angaben zur Solaranlage ausdrücklich erklären, dass diese nur der Auswertung und Darstellung in der App dienen und nicht in die Geräte geschrieben werden.
- Der entsprechende Hinweis steht bereits am Anfang der Projektbeschreibung und Anleitung. Die Ergänzung der App-Oberfläche folgt mit dem nächsten Update.

## Synchronisation sichtbar machen und Bedienung erhalten

Beobachtung vom 19. Oktober 2026: Während der Synchronisation reagieren Schaltflächen nicht. Der zusätzliche Blitz in der Android-Statusleiste erklärt den Zustand nicht ausreichend.

- Auf allen App-Seiten, insbesondere der Übersicht, eine gut sichtbare Anzeige direkt unter dem Kopf vorsehen: **Synchronisation läuft – Gerätedaten werden ergänzt**. Die Anzeige gehört nicht als zusätzliche Kachel in die gespeicherte Anordnung.
- Aktuelles Gerät (SmartSolar/SmartShunt) und Arbeitsschritt verständlich nennen. Bei bekanntem Umfang den tatsächlichen Fortschritt anzeigen; andernfalls eine laufende Aktivitätsanzeige ohne erfundene Prozentwerte.
- **Synchronisation abbrechen** dort direkt erreichbar und bedienbar anbieten. Bereits gespeicherte Daten bleiben erhalten. Eine Anzeige darf Navigation und Schaltflächen nicht verdecken oder absichtlich die ganze App sperren.
- Nach erfolgreichem Abschluss den laufenden Hinweis automatisch entfernen und den Abschluss kurz bestätigen. Abbruch und Fehler eindeutig als solche anzeigen. Hoch-/Querformat, Hell/Dunkel und offene Eingaben berücksichtigen.
- Die gemeldete vollständige Eingabeblockade gesondert untersuchen und beheben. Im aktuellen Code werden absichtlich nur erneute Archivabrufe gesperrt. Mögliche Engpässe sind die synchronen Aufrufe der nativen Snapshot-/Verlaufs-/Energieabfragen und das sofortige Neuberechnen der Übersicht nach importbedingten Datenrevisionen; die konkrete Ursache ist noch nicht nachgewiesen.
- Status zuerst sichtbar rendern, Datenbankarbeit und aufwendige Neuberechnungen so organisieren, dass Navigation, Scrollen und Abbrechen auch bei größeren Importen weiter reagieren. Neue Daten gesammelt statt für jeden Importblock vollständig neu zeichnen, ohne Messwerte oder Abschlussaktualisierungen zu verlieren.

Status: vorgemerkt; noch nicht in der veröffentlichten Version 1.2.4 umgesetzt.
