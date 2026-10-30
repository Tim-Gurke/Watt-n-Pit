# Planung und umgesetzte Änderungen

## Mit 1.6.11 umgesetzt: zwei Hinweiszeilen, lokale Darstellungsgröße und mobile Verbrauchsschätzung

- Kachelhinweise auf höchstens zwei Zeilen begrenzen; Auslassungspunkte gehören in die zweite Zeile. Nachgeladene Meldungen dürfen die Kachelgeometrie nicht verdrängen. Vollständige Erklärung in der Großansicht.
- Darstellungsgröße **50–150 %**, Standard **100 %**, separat je Handy. Kachelanordnungen erhalten, verschobene/skalierte Berührungspunkte korrekt verarbeiten und Schriften/Diagramme gemeinsam skalieren.
- Bei zu kleinem Wettermodell und bei Ladebegrenzung die mobile Erzeugung mit Messbilanz und unabhängig zuvor beobachtetem Grundbedarf plausibilisieren. Bekannte Roller-Ladeanteile zeitlich passend berücksichtigen. Keine zusätzliche mobile Korrektur im Verbrauchslernen.
- Brauchbare positive Teile eines teilweise negativen Rechenabschnitts erhalten. Fehlender Grundbedarf, unplausible Eingänge und auffällige Spannungen bleiben explizite Lücken; Teilabdeckung verständlich kennzeichnen. Mobiles Sonnenpotenzial nicht unbesehen bis Sonnenuntergang als tatsächlichen Ertrag ausweisen.
- Modellwetter und native Aufstellungswechsel für spätere Prüfungen mit exportieren. Version-4-Datenbank samt bestehender Wetterwerte erhalten.

Offene Hardware-/Betriebspunkte: Spannungsauffälligkeit des mobilen Ladezweigs unabhängig gegenprüfen; Download-/Browserhänger des G5 erst nach Gegenprobe zuordnen. Unterstützung mehrerer separater Solarregler ist ein eigener möglicher Ausbau und nicht Bestandteil dieses Updates.

## Mit 1.6.10 umgesetzt: Geräteverbrauch und mobiles Solar getrennt darstellen

Wünsche vom 30. Oktober 2026: **Geräteverbrauch** soll den gesamten Bedarf der Geräte benennen. Die mobile Erzeugung soll eine eigene Kachel bekommen, statt nur unauffällig den bisherigen berechneten Verbrauch zu senken. Die bisherige Arbeitsbezeichnung **Restbedarf** ist damit abgelöst.

- Eine separate, frei hinzufügbare, verschiebbare und skalierbare Kachel **Solarleistung mobil** in W vorsehen; **geschätzt** dauerhaft als gut sichtbaren Zusatz zeigen. Sie nutzt das Modell der tatsächlich aktiven mobilen Aufstellung einschließlich PWM-Verlustansatz und ist keine Bluetooth-Liveablesung des PWM-Reglers.
- Leistung und Ertrag sprachlich und rechnerisch trennen: Für eine aufsummierte Ansicht in Wh lautet die Bezeichnung **Solarertrag mobil**, ebenfalls **geschätzt**. Nur nachvollziehbar abgedeckte Aufstellungs- und Modellzeiträume verwenden; fehlende Grundlagen nicht als gemessene Null ausgeben.
- Die Verbrauchskachel als **Geräteverbrauch** mit dem gesamten Bedarf einschließlich Rollerladung und Ladeverlusten auslegen. Dazu die zeitlich passende mobile Schätzung einmal zur bisherigen Rechnung ergänzen: gemessener fester Solarertrag + geschätzter mobiler Solarertrag − gemessene Akkubilanz. Den Gesamtwert wegen des geschätzten mobilen Anteils entsprechend kennzeichnen.
- Feste gemessene Solarerzeugung, mobile geschätzte Erzeugung und Geräteverbrauch verständlich unterscheiden. Die tatsächliche Akkubilanz bleibt am Shunt gemessen. Ein gegebenenfalls angebotener Gesamtertrag muss seinen geschätzten Anteil erkennen lassen. Keine doppelte mobile Korrektur in Anzeige, Prognose oder Verbrauchslernen; die bereits im Gesamtverbrauch enthaltene Rollerladung nicht zusätzlich aufschlagen.
- Momentane Leistung in W und aufsummierte Tagesenergie in Wh klar unterscheiden. Auch bei positiver Akkubilanz steigt der gesamte Geräteverbrauch weiter, solange die Geräte Energie nutzen. Eine sinkende alte Verbrauchskurve bezeichnete den Rest nach ungemessener Einspeisung und darf unter der neuen Bezeichnung nicht als rückgängig gemachter Geräteverbrauch erklärt werden.
- Ursprüngliche Messwerte erhalten, Schätzanteile und fehlende Grundlagen nachvollziehbar kennzeichnen. Vorhandene individuelle Kacheltitel und beide Gerätelayouts erhalten; neue Kacheln über die vorhandene Auswahl anbieten. Schriftgrößen weiterhin höchstens 20.

### Praxisvergleich: festes Solar und PWM-Modul

Mitteilung vom 30. Oktober 2026: Die feste Anlage liefert aktuell **155 W**; am PWM-Regler des mobilen Moduls stehen **4,5 A**. Falls dies der Batterie-Ladestrom ist, ergeben sich mit der zuvor fotografierten Batteriespannung von **13,35 V** ungefähr **60,1 W mobil** und damit ungefähr **215 W insgesamt**. Spannung und Strom wurden nicht gleichzeitig dokumentiert; die Zuordnung der Stromanzeige ist nicht bestätigt. Als bedingten Plausibilitätsvergleich speichern, nicht als exakte Kalibrierung oder festen Reglerwirkungsgrad übernehmen.

### Praxisbeobachtung: Roller-Ladeleistung bei angezeigten 95 %

Foto vom 30. Oktober 2026, Anzeige 09:48: **95 % BMS-Ladezustand, 53,87 V, 1,61 A und 86,7 W BMS-Ladeleistung**. Der geschätzte Wohnwagenanteil beträgt **126,7 W**, gegenüber zuvor genannten 146–148 W. Das passt zu einer gegen Ladeende sinkenden Leistung. Einen festen Beginn exakt bei 95 % belegt diese Momentaufnahme nicht. Die bestehende Live-Schätzung des Wohnwagenanteils verwendet bereits die aktuelle BMS-Leistung; diese Beobachtung ist ein Vergleichspunkt für spätere Beurteilungen des Ladeverlaufs.

Status: in 1.6.10 umgesetzt; siehe [Versionshinweise](releases/1.6.10.md). Mobil aus und Anlagen ohne mobiles Modul werden automatisch berücksichtigt. Die bisherige Bilanz heißt nun Akkubilanz.

## Mit 1.5.0 umgesetzt

Roller-BMS und Ladeplanung, lokale ScootPit-Kopplung, verschiebbarer und skalierbarer Rollerblock, erhaltene Scrollposition, standardmäßiger Hintergrundabruf, Herunterziehen zum Nachladen und vollständiger Dashboard-Reset. Siehe [1.5.0](releases/1.5.0.md). Die frühere Wahlpflicht für jeden Abruf aus 1.2.6 ist damit abgelöst.

Weiter offen: automatische Auswertung abgeschlossener ScootPit-Fahrten, vermessener Rückspeiseweg und vollständige Verlustkurve.

## Vorgemerkt: 100-%-Referenz des SmartShunts

Wunsch vom 26. Oktober 2026: Die 100-%-Referenz bei tatsächlich erreichter Vollladung prüfen und die automatische Synchronisierung des SmartShunts passend abstimmen. Dazu die aktuell verwendeten Einstellungen von Solarregler und SmartShunt gemeinsam mit einer vollständigen Ladung auswerten. Die Anzeige soll eine nachgewiesene Vollladung zuverlässig übernehmen.

Status: als nächster eigener Prüfpunkt vorgemerkt.

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
