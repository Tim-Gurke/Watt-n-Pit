# Planung und umgesetzte Änderungen

## Mit 1.2.6 umgesetzt: Synchronisation auf Wunsch im Hintergrund

Wunsch vom 19. Oktober 2026: Der laufende Geräteabruf soll wahlweise im Hintergrund weiterlaufen, während die App wieder bedienbar wird. Die Entscheidung gilt ausschließlich für den aktuellen Synchronisationslauf, einschließlich der nacheinander abgefragten Geräte.

- In der Warteanzeige zusätzlich zu **Synchronisation abbrechen** die Schaltfläche **Im Hintergrund synchronisieren** anbieten. Wer nichts auswählt, bleibt bei der bisherigen Warteanzeige.
- Nach dieser Auswahl die normale Bedienung wieder freigeben und den Abruf weiterlaufen lassen. Einen kompakten, jederzeit erreichbaren Status mit Gerät, Arbeitsschritt und echtem Fortschritt zeigen; darüber Fortschrittsanzeige und Abbrechen weiterhin erreichbar halten.
- Der bestehende Android-Dienst mit seiner Benachrichtigung führt den Abruf weiter. Das Ausblenden der Warteanzeige beendet den Import nicht.
- Die Wahl an den aktiven Lauf binden und bei dessen Erfolg, Abbruch oder Fehler verwerfen. Wird derselbe laufende Abruf erneut geöffnet, die bereits getroffene Wahl beibehalten. Beim nächsten neuen Lauf wieder die normale Warteanzeige zeigen. Keine dauerhafte Hintergrundeinstellung und kein Export dieser Auswahl.
- Während des Imports den angezeigten Datenstand klar kennzeichnen. Vorhandene Werte und Kurven erhalten, keine gesperrten Abfragen als leere oder gemessene Nullwerte darstellen. Nach Abschluss die ergänzten Daten gemeinsam aktualisieren; offene Eingaben und beide Anordnungen erhalten.
- Die aktuellen Bridge-Sperren und teuren synchronen Datenbank-/Energieabfragen dafür gezielt anpassen. Nur die Warteanzeige auszublenden genügt nicht. Bedienung, Fortschritt und Abbrechen müssen auch bei großen Archiven reagieren.
- Hoch-/Querformat, Hell/Dunkel, laufenden Gerätewechsel, App-Wechsel, Abbruch, Fehler und den nächsten neuen Lauf prüfen.

Status: mit 1.2.6 umgesetzt. Siehe [Versionshinweise](releases/1.2.6.md) und [Anleitung](ANLEITUNG.md).

## Mit 1.2.5 umgesetzt

### Solarfenster an Sonnenzeiten und tatsächliche Erzeugung anpassen

Wunsch vom 18. Oktober 2026: Solarwerte mit Tageslichtfenster sollen nach Sonnenuntergang nicht weiter eine immer längere Nullstrecke anzeigen. Tatsächlich gemessene Erzeugung vor Sonnenaufgang oder nach Sonnenuntergang zählt weiterhin mit.

- Die Zeitachse von Solarleistung und Solarertrag im Sonnenaufgangsfenster läuft tagsüber bis jetzt, nach Sonnenuntergang regulär nur bis zum Sonnenuntergang.
- Der erreichte Solarertrag in Wh/kWh bleibt sichtbar. Aufzeichnung und Speicherung laufen weiter.
- Verwertbare positive Solarmessungen vor Sonnenaufgang erweitern den Beginn des Fensters nach vorn. Entsprechende Messungen nach Sonnenuntergang verlängern das Ende. Die Sonnenzeiten sind die regulären Grenzen; tatsächlich beobachtete Erzeugung hat Vorrang.
- Das gilt auch für die Energieberechnung: Gemessenen Ertrag außerhalb der Sonnenzeiten in Solarertrag und berechnetem Verbrauch mitführen; Ertrag minus Verbrauch muss weiterhin die Bilanz ergeben. Die bisherige Nullannahme vor Sonnenaufgang dafür anpassen. Messlücken bleiben unbekannt; keine Energie über unbeobachtete Abschnitte hochrechnen.
- Verbrauch und Bilanz zählen weiter bis jetzt beziehungsweise zum Ende des gewählten Tages. Der Verbrauch nach Sonnenuntergang bleibt enthalten; Ertrag minus Verbrauch muss weiterhin die angezeigte Bilanz ergeben.
- Freie Zeiträume behalten ihre ausdrücklich gewählten Grenzen. Beide Anordnungen sowie die unabhängige Livezahl bleiben erhalten.
- Sonnenuntergang am eingestellten Wetterort zusätzlich übernehmen. Bei fehlender oder ungültiger Sonnenzeit den bisherigen Verlauf mit erkennbarem Ersatz verwenden.

### Rolle als Energie-Cockpit deutlich erklären

Wunsch vom 19. Oktober 2026: Watt’n’Pit ergänzt die Victron-Software als Energieanzeige; Geräteeinstellungen werden weiterhin über VictronConnect vorgenommen.

- Beim ersten Öffnen nach der Installation als einmalige Hinweisbox mit Schaltfläche **Verstanden** anzeigen. Die Bestätigung auf dem Gerät speichern; sie gilt erst nach dem bewussten Bestätigen.
- Bei bestehenden Installationen den Hinweis beim ersten Start nach dem Update ebenfalls einmal zeigen. Nach der Bestätigung bei unverändertem Hinweis nicht bei jedem Start oder jedem weiteren Update wiederholen. Der gespeicherte Bestätigungsstand gehört nicht in die übertragbare Einstellungssicherung.
- Diesen Hinweis gut sichtbar auf der Geräteseite und bei den App-Einstellungen aufnehmen, ohne die Übersicht mit einer zusätzlichen dauerhaften Kachel zu belegen.
- Geplanter Wortlaut: „Watt’n’Pit ist eine Energieanzeige und ein Cockpit. Die App ersetzt VictronConnect nicht. Einstellungen an Solarregler und SmartShunt werden weiterhin in VictronConnect vorgenommen. Die Einstellungen hier betreffen nur Watt’n’Pit.“
- Bei der Batteriekapazität und den Angaben zur Solaranlage ausdrücklich erklären, dass diese nur der Auswertung und Darstellung in der App dienen und nicht in die Geräte geschrieben werden.
- Der entsprechende Hinweis steht bereits am Anfang der Projektbeschreibung und Anleitung. Die App-Oberfläche enthält den Hinweis ab 1.2.5.

### Synchronisation deutlich anzeigen; normale Bedienung darf pausieren

Beobachtung vom 19. Oktober 2026: Während der Synchronisation reagieren Schaltflächen nicht. Der zusätzliche Blitz in der Android-Statusleiste erklärt den Zustand nicht ausreichend. Klarstellung des Anwenders: Eine vorübergehend gesperrte normale Bedienung ist in Ordnung; entscheidend ist eine verständliche Kennzeichnung des laufenden Abgleichs.

- Auf allen App-Seiten, insbesondere der Übersicht, eine gut sichtbare Warteanzeige vorsehen: **Synchronisation läuft – Gerätedaten werden ergänzt. Bitte warten.** Die Anzeige gehört nicht als zusätzliche Kachel in die gespeicherte Anordnung. Bei einer Bedienungssperre die übrige Oberfläche sichtbar dimmen beziehungsweise deaktivieren.
- Aktuelles Gerät (SmartSolar/SmartShunt) und Arbeitsschritt verständlich nennen. Bei bekanntem Umfang den tatsächlichen Fortschritt anzeigen; andernfalls eine laufende Aktivitätsanzeige ohne erfundene Prozentwerte.
- **Synchronisation abbrechen** als direkt erreichbare, weiterhin bedienbare Funktion anbieten, besonders bei längeren Importen. Bereits gespeicherte Daten bleiben erhalten. Normale Cockpit- und Navigationsbedienung darf währenddessen bewusst gesperrt sein.
- Nach Erfolg, Abbruch oder Fehler die normale Bedienung automatisch wieder freigeben. Den Abschluss kurz bestätigen beziehungsweise den Fehler verständlich nennen. Hoch-/Querformat, Hell/Dunkel und offene Eingaben berücksichtigen.
- Warteanzeige und Fortschritt müssen weiter aktualisiert werden. Eine absichtliche Bedienungssperre ist akzeptiert; eine vollständig eingefrorene Statusanzeige oder ein unwirksames Abbrechen bleiben zu untersuchen.
- Status zuerst sichtbar rendern und Datenbankarbeit sowie Neuberechnungen so organisieren, dass Warteanzeige und Abbrechen auch bei größeren Importen reagieren. Synchrone Snapshot-/Verlaufs-/Energieabfragen und Neuberechnungen nach jeder importbedingten Datenrevision als mögliche Engpässe prüfen; die konkrete Ursache ist noch nicht nachgewiesen. Eine generell uneingeschränkte Bedienbarkeit aller App-Seiten ist für diese Änderung nicht erforderlich.

Status: in 1.2.5 umgesetzt. Siehe [Versionshinweise](releases/1.2.5.md) und [Anleitung](ANLEITUNG.md).

### Verschiebbare Messpunktauswahl in der Großansicht

Wunsch vom 19. Oktober 2026: Ein ausgewählter Kurvenwert erhält einen Punkt und eine senkrechte Linie. Während der Auswahl lässt sich die Markierung mit dem Finger verschieben; Uhrzeit und Werte folgen. **Auswahl aufheben** gibt das Verschieben des Zeitraums wieder frei.
