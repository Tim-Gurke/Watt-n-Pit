# Watt’n’Pit bedienen

Für Version **1.6.4** · [APK herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/releases/download/v1.6.4/Watt-n-Pit-v1.6.4.apk) · [Zur Projektübersicht](../README.md)

Watt’n’Pit zeigt Solarleistung, Batteriezustand und gespeicherte Verläufe direkt auf deinem Android-Telefon. Die folgenden Screenshots stammen aus der App-Oberfläche mit **frei erfundenen Beispieldaten**. Wetterort und Gerätekennungen sind Beispiele; es sind keine privaten Verbindungsdaten enthalten. Eine sichtbare Demo liest keine Bluetooth-Geräte aus.

**Watt’n’Pit ist eine Energieanzeige und ein Cockpit. Die App ersetzt VictronConnect nicht.** Sie zeigt Messwerte, Verläufe und Energieauswertungen. Einstellungen an Solarregler und SmartShunt müssen weiterhin über die Victron-Software **VictronConnect** vorgenommen werden. Die Einstellungen in Watt’n’Pit betreffen nur die App und ändern keine Geräteparameter.

Auch Firmware-Updates deiner Victron-Geräte führst du weiterhin über die offiziellen Victron-Apps durch, bei SmartShunt und SmartSolar über VictronConnect. Watt’n’Pit kann keine Geräteupdates durchführen.


## Mehrere Handys: Anlagenverbund ab 1.6.0

Alle beteiligten Handys auf 1.6.1 aktualisieren und im selben WLAN beziehungsweise Hotspot verbinden.

1. Am Gerät bei der Anlage **Einstellungen → Anlagenverbund → Als Hauptgerät erstellen** wählen und einen Namen vergeben, z. B. „Mein Wohnwagen“. Die Bluetooth-Aufzeichnung auf diesem Gerät wie gewohnt starten.
2. **QR-Code anzeigen** wählen. Die Darstellung enthält den Zugang zum eigenen Anlagenverbund.
3. Am anderen Handy **Einstellungen → Anlagenverbund → QR-Code scannen** wählen. Die Kameraberechtigung beim ersten Scan erlauben und die Kamera auf den Code des Hauptgeräts richten. Die App prüft die Verbindung und aktiviert nach erfolgreicher Antwort die Anzeige. Ein Abbruch erhält die bisherige Texteingabe.
4. Die Übersicht nennt die Anlage und den Empfangsstatus. Anlagenangaben und Aufzeichnung am Hauptgerät ändern; Kacheln, Farben und Anordnung bleiben je Handy einstellbar.

Alternativ am Hauptgerät **Verbindungscode kopieren** wählen, auf der Anzeige das gesamte Codefeld ersetzen und **Als Anzeige beitreten** antippen. Bei einer Meldung über einen unvollständigen oder veränderten Code frisch kopieren oder scannen. **QR-Code aktualisieren** verwendet die aktuelle Adresse des Hauptgeräts.

Beispiel: Das S20 stellt den Hotspot bereit, das G5 bleibt als Hauptgerät am Wohnwagen, das S10 zeigt dessen Daten. Das S20 darf ebenfalls beitreten. Auch das Hotspot-Handy selbst kann stattdessen Hauptgerät sein.

Das Hauptgerät benötigt WLAN-Empfang, Bluetooth-Aufzeichnung und den laufenden Anlagenverbund. Die Anzeige ruft bei geöffneter App den gemeinsamen Stand und die benötigten Kurven ab. Ohne Verbindung erscheint „Hauptgerät nicht erreichbar“ mit dem letzten Empfangszeitpunkt; dieser Stand ist kein Liveempfang. Neue Verlaufsabfragen benötigen die Verbindung. Unabhängige alte Aufzeichnungen werden erhalten und beim Verlassen wieder verwendet, jedoch nicht automatisch zusammengeführt. Nach einer geänderten Hotspot-Adresse wird das Hauptgerät im lokalen Netz gesucht. Falls die Suche blockiert ist, einen frischen Code vom Hauptgerät übernehmen.

Der Code ist der lesende Zugang zu dieser Anlage. Der Austausch bleibt verschlüsselt im lokalen IPv4-Netz; Geräteschlüssel und PINs bleiben am Hauptgerät. **Verbund beenden** am Hauptgerät macht den Zugang ungültig. Die Solarkreise unter „Deine Solaranlage“ bleiben davon unabhängig.

### Rollerladung als Sondernutzung

Im Rollerbereich **Ladung als Sondernutzung starten** wählen, wenn das Ladegerät den Roller aus dem Wohnwagen versorgt. Der Zeitraum wird als **Sondernutzung · Rollerladung** markiert. Nach dem Laden die Aufzeichnung beenden. Die betreffende Zeit lernt keinen allgemeinen Tagesbedarf; geeignete Messzeiten davor und danach bleiben nutzbar. Die tatsächliche Ladeenergie bleibt in Tagesverbrauch und Shuntbilanz enthalten. BMS-Energie und geschätzter Wohnwagenanteil stehen im Ladejournal. Der verbleibende geplante Ladebedarf fließt gesondert in die Akkuprognose ein.

Die Markierung ist manuell; eine unzugeordnete Shuntlast lässt sich nicht sicher als Rollerladung erkennen. Empfangslücken werden nicht als gemessene Ladeenergie ergänzt.

## 1. Installieren und Geräte einrichten

1. Die APK herunterladen und auf **Android 8 oder neuer** installieren. Beim Update die bestehende App behalten und die neue APK darüber installieren.
2. In **VictronConnect** den SmartSolar bzw. SmartShunt öffnen. Unter **Einstellungen → ⋮ → Produktinfo** die **Sofortanzeige über Bluetooth** aktivieren.
3. Bei **Sofortanzeige-Details → Anzeigen** die Geräteadresse und den **32-stelligen Verschlüsselungsschlüssel** übernehmen. Jeder Schlüssel gehört zu genau einem Gerät.
4. In Watt’n’Pit **Geräte → Suche starten** wählen. Das gefundene Gerät einrichten oder über **+** manuell hinzufügen. Gerätetyp, Name, Adresse und Schlüssel eintragen und speichern.
5. Die aktive Verbindung in VictronConnect trennen und in Watt’n’Pit die Aufzeichnung starten. In Reichweite der Geräte bleiben.

Beim ersten Öffnen erscheint der Hinweis **Dein Energie-Cockpit**. Mit **Verstanden** bestätigen. Bei bestehenden Installationen erscheint der seit 1.2.5 enthaltene Hinweis nur, solange er noch nicht bestätigt wurde; anschließend bleibt er bei unverändertem Text bestätigt. Ein Schließen ohne Bestätigung zählt nicht. Die Bestätigung wird nur auf diesem Gerät gespeichert und nicht mit Einstellungen übertragen. Der Hinweis bleibt auch unter **Geräte** und **Einstellungen** nachlesbar.

![Einmaliger Hinweis zur Rolle als Cockpit und zu VictronConnect](images/cockpit-notice.png)

Die sechsstellige **Bluetooth-PIN** wird für die Android-Kopplung verwendet. Sie ist ein anderer Code als der 32-stellige Schlüssel der Sofortanzeige. Eine bereits vorhandene Android-Kopplung benötigt keine erneute PIN-Abfrage.

Die Berechtigung für **Geräte in der Nähe** erlauben. Auf Android 8–11 sind für die Gerätesuche außerdem Standortberechtigung und eingeschalteter Standortdienst nötig. Watt’n’Pit zeichnet keinen Standort auf.

## 2. Gerätearchive automatisch ergänzen

Auf der Übersicht stehen **Play/Pause** und **Daten nachladen** in derselben Zeile. Der kleine Playknopf steuert die laufende Aufzeichnung. **Daten nachladen** gleicht die Archive beider eingerichteten Geräte ab und ergänzt die fehlenden Messgrößen und Zeitabschnitte. Dieser ausdrücklich gestartete Abruf ist auch während der automatischen Wartezeit nach einem Abbruch möglich, sobald der vorige Lauf beendet ist. Unter **Geräte → Gerätearchiv ergänzen** lässt sich weiterhin ein einzelnes Gerät abrufen.

![Play/Pause und Daten nachladen sind getrennte Knöpfe](images/reload-controls.png)

**Synchronisation abbrechen** beendet den Abruf erst nach einem zweiten bewussten Schritt: **Abbruch bestätigen**. **Weiter synchronisieren** schließt die Rückfrage und lässt den Abruf weiterlaufen. Ein versehentlicher Tipp auf die neu eingeblendete Warteanzeige beendet damit nicht unmittelbar die Synchronisation.

Ab 1.3.9 berücksichtigt der Abruf die gerundeten Gerätezeiten und die unterschiedlich alten Archivstufen. Gröbere Intervalle werden entsprechend dem Gerätearchiv verwendet; tatsächlich gelieferte Messungen behalten ihre Zeiten und Auflösungen. Eine normale Antwort am Ende einer Stufe führt nicht mehr zum wiederholten Neuplanen desselben Kanals. Beide Bluetooth-Datenkanäle erhalten die nötigen Empfangsfreigaben. Die Dauer des letzten Geräteabrufs steht in der kopierbaren Diagnose.

Beim Öffnen und nach längerem Aufenthalt im Hintergrund prüft Watt’n’Pit zuerst den lokalen Bestand anhand der gespeicherten Archivaufteilung. Nur bei tatsächlichem Bedarf wird die Archivverbindung aufgebaut und der Nachladeplan mit frischen Geräte-Metadaten bestimmt. Nach erfolgreichem Abgleich startet während derselben geöffneten Sitzung kein neuer automatischer Lauf; zusätzliche reine Archivmessgrößen werden beim nächsten Vordergrundabgleich gesammelt nachgeholt. Nach einem Fehler gilt je Gerät eine Wartefrist von fünf Minuten. Ein automatischer Wiederholungsversuch während der geöffneten Sitzung benötigt zusätzlich eine zwischenzeitliche Empfangsunterbrechung und frische gültige Livewerte desselben Geräts. Automatische und manuelle Abrufe haben kein festes Gesamtzeitlimit: Eine echte Lücke von drei Tagen oder drei Wochen kann in einem verbundenen Lauf ergänzt werden, soweit das Gerät sie noch vorhält. Einzelne unbeantwortete Bluetooth-Aufträge werden weiterhin beendet. **Abbrechen** pausiert automatische Abrufe 30 Minuten; manuelles Nachladen bleibt verfügbar.

![Geräteseite mit beispielhaftem Importstatus und neutralen Gerätekennungen](images/devices-archive.png)

*Beispiel einer eingerichteten Anlage: Importstatus, vorhandene Android-Kopplung und manueller Archivabruf stehen auf der Geräteseite.*

- **Nur verwertbare Lücken nachladen:** Ein vollständiger Abrufplan je Gerät steht vor der ersten Messwertabfrage fest. Vorhandene einzelne Livepunkte reichen nicht aus, um eine Lücke als abgedeckt zu betrachten; erforderliche Archiv-Randpunkte bleiben nachladbar. Neu eintreffende Livewerte verkleinern ihn vor den folgenden Datenpaketen weiter. Jede App-Installation prüft ihre eigene lokale Datenbank vor dem Bluetooth-Abruf. Der Abgleich erfolgt getrennt nach Victron-Gerät, Messgröße und Auflösung. Vorhandene Live- und Archivwerte werden bereits vor der Übertragung übersprungen, auch wenn nur ein Teil eines früheren Datenpakets fehlt. Neue Werte und weiter zurückliegende Lücken werden gezielt angefordert. Ein anderes Handy kann deshalb einen anderen Nachladebedarf haben. Bereits bestätigte Messbereiche werden nicht pauschal erneut geladen. Veränderliche Antworten ohne Messwert können bei einem späteren Abgleich erneut geprüft werden.
- Der Live-Scan wird beim Archivabruf nicht mehr abgeschaltet. Wenn ein Gerät weiter Bluetooth-Livewerte sendet, werden sie aufgezeichnet und auch während des Imports angezeigt. Das gerade per Archivverbindung belegte Victron-Gerät kann seine Sofortanzeige zeitweise selbst aussetzen.
- Zeitreferenzen werden je Gerät lokal zwischengespeichert. Bekannte historische Referenzen werden beim nächsten Abruf übersprungen. Bereiche ohne sichere Zeitzuordnung werden bereits vor der Messwertübertragung zurückgestellt und nicht bei jedem Öffnen wieder vollständig gelesen. Neue passende Referenzen machen sie später wieder nachladbar.
- **Geschlossene Oberfläche bedeutet keine Aufzeichnungslücke:** Der gestartete Hintergrunddienst speichert empfangene Livewerte im gewählten Speicherintervall weiter. Zusammenhängende Zehn-Sekunden-Aufzeichnung wird beispielsweise auch gegenüber einem sekündlichen Gerätearchiv als vorhanden erkannt. Echte Empfangsausfälle bleiben offen. Ältere Aufzeichnungen ohne gespeichertes Intervall behalten ihre bisherige Zeitgrenze von 90 Sekunden; für neue Aufzeichnungen gilt das tatsächlich gespeicherte Intervall. Abgleich, Kurve und Energieberechnung verwenden dafür dieselbe Grundlage.
- Fehlende Zeiträume lassen sich nur in der vom Gerät vorgehaltenen Auflösung ergänzen. Das Livepaket enthält nicht jede Archivmessgröße: Beispielsweise kann die PV-Eingangsspannung zusätzlich fehlen. Neue Metadaten und Zeitanker sowie aktuelle Tages- und Shunt-Gesamtwerte werden weiterhin geprüft. Der Fortschritt bezieht sich auf die vorab ermittelten fehlenden Trendwerte und nennt bereits vorhandene, nicht geladene Werte.
- Abgeschlossene Solar-Tage werden nach einem Abruf als vergangener Tag behalten. Der aktuelle Tag wird weiter aktualisiert und nach dem Tageswechsel einmal vollständig übernommen. Beim Update werden ältere Tageswerte einmalig bestätigt; bestehende Messdaten und Einstellungen bleiben erhalten.
- Während des Imports erscheint auf jeder Seite **Synchronisation läuft**. Zunächst ist die übrige Oberfläche gedimmt und gesperrt. Über **Im Hintergrund synchronisieren** gibst du die Bedienung für diesen Durchlauf frei. Der Abruf läuft weiter; die Auswahl gilt auch für das nächste Gerät in derselben Warteschlange. Beim nächsten neuen Durchlauf erscheint die Warteanzeige wieder. Es gibt dafür keine dauerhafte Einstellung. Gerät und Arbeitsschritt werden genannt; bei bekanntem Umfang erscheint der tatsächliche Fortschritt für das aktuelle Gerät, ansonsten eine Aktivitätsanzeige. **Synchronisation abbrechen** bleibt erreichbar. Bereits abgeschlossene Datenblöcke bleiben nach einem Abbruch gespeichert. Der nächste Abruf erkennt diese Werte und ergänzt die verbleibenden Lücken. Bei einem späteren fälligen Abgleich werden verbleibende Lücken ergänzt; ein manueller Abruf ist jederzeit unter **Geräte → Gerätearchiv ergänzen** möglich, sobald der laufende Import beendet ist. Nach Erfolg, Abbruch oder Fehler wird die Bedienung automatisch wieder freigegeben. Offene Eingaben bleiben erhalten. Nach dem Wechsel in den Hintergrund zeigt die kompakte Leiste oben **Synchronisation läuft · Werte vorläufig**. Antippen öffnet Gerät, Fortschritt und Abbrechen erneut; **Weiter im Hintergrund** oder Android-Zurück schließt diese Fortschrittsansicht wieder. Beim erneuten Öffnen der App während desselben Abrufs bleibt die Wahl erhalten.
- Beim Update bleiben sämtliche Messwerte erhalten. Frühere Archiv-Abschlussmarker werden einmal neu geprüft, da bereits angeforderte Werte früher wegen naher Livepunkte verworfen werden konnten. Tatsächlich vorhandene zusammenhängende Daten werden weiter übersprungen. Archivwerte verschiedener Auflösungen am selben Zeitpunkt bleiben nun separat erhalten.
- Archivwerte und selbst aufgezeichnete Werte können unterschiedlich fein sein. Die Standardansicht führt die Quellen zusammen; feinere vorhandene Messwerte haben Vorrang.
- Leere Archivfelder werden nicht als gemessene Nullwerte ausgegeben. Lücken können deshalb sichtbar bleiben.

![Warteanzeige mit aktuellem Gerät, Fortschritt und Abbrechen](images/synchronisation.png)

![Weiter bedienbares Cockpit mit kompakter Fortschrittsanzeige](images/synchronisation-hintergrund.png)

Währenddessen bleibt der letzte verfügbare Datenstand sichtbar. Neue Zeiträume und Vergleichswerte werden ohne Bedienungssperre im Hintergrund geladen; bis zur Antwort erscheint **Daten werden im Hintergrund geladen …**. Die Werte sind während des Imports vorläufig und werden nach dessen Ende gemeinsam aktualisiert.

Falls nichts ankommt: Bluetooth und Reichweite prüfen, VictronConnect vom Gerät trennen und den Import erneut versuchen. **Liveempfang** bestätigt den Empfang der Sofortanzeige; der aktive Archivabruf ist ein eigener Vorgang. Über **Verbindungsdetails → Diagnose kopieren** lässt sich ein Bericht ohne Geräteadressen, Schlüssel oder Messwerte kopieren.

## 3. Den Startbildschirm gestalten

| Dunkles Design | Helles Design |
|---|---|
| ![Neue Hochkant-Vorlage mit zehn Kacheln im dunklen Design](images/portrait-1.3.12-dark.png) | ![Dieselbe Hochkant-Vorlage im hellen Design](images/portrait-1.3.12-light.png) |

*Die neue Standardvorlage umfasst zehn Kacheln; Werte, Farben, Größen und Positionen sind frei einstellbar.*

Die neue Hochkant-Vorlage ist dreispaltig: links Ladezustand, automatische Restlade-/Restlaufzeit, Batteriespannung und die mit „Aktuell“ beschriftete Batteriebilanz. In der Mitte stehen Solarleistung und Verbrauch; rechts Solarertrag und Bilanz. Darunter liegen die flache Wetterkachel und die Ladephase. Der Verlauf ist standardmäßig 465 Pixel hoch. Vorhandene eigene Anordnungen werden beim Update erhalten. Neue Installationen und **Einstellungen → Standardübersicht wiederherstellen** erhalten die neue Vorlage.

Die breite Ansicht speichert ihre Anordnung weiterhin separat. Die bisherigen acht Kacheln behalten in der breiten Standardvorlage ihre Positionen; Ladephase und automatische Akkuzeit werden darunter ergänzt. Im Querformat wechseln **+ Kachel**, **Anordnen**, Empfangszustand und Ansichtsart zusammen mit „Energie im Blick“ in den kompakten Appkopf. Ab 760 Pixel Breite stehen sie gemeinsam in einer Zeile.

![Breite Ansicht mit Werkzeugen und Empfangszustand oben im kompakten Kopf](images/overview-landscape.png)

Die Restlaufzeit kann über **+ Kachel → Akku → Restlaufzeit** ergänzt werden; Stunden sind weiterhin die voreingestellte Einheit.

**+ Kachel → Akku → Restladezeit / Restlaufzeit** ergänzt eine automatisch wechselnde Kachel. Bereits vorhandene Restladezeit-Kacheln bekommen diese Funktion direkt durch das Update. Ihre Kennung, Position, Größe und Einstellungen bleiben erhalten. Eine eigene Beschriftung bleibt bestehen; ohne eigene Beschriftung wechselt der Titel mit dem Trend.

- **Ladetrend:** „Restladezeit“ zeigt die geschätzte Zeit bis 100 %. Die Rechnung verwendet die fehlenden Ah und den mittleren Netto-Ladestrom. Unter **Einstellungen** die tatsächliche Batteriekapazität in Ah eintragen. Bei 70 % von 100 Ah und konstant 10 A in den Akku sind es ungefähr 3 Stunden. Verbraucher und alle am Shunt erfassten Ladequellen sind enthalten.
- **Entladetrend:** „Restlaufzeit“ übernimmt den vorhandenen Wert des SmartShunt. Fehlt dieser, erscheint **—**; es wird keine Ersatzlaufzeit aus der Kapazität berechnet.
- **Nahezu ausgeglichene Bilanz:** „Akkuzeit“ zeigt **—** mit dem Hinweis **„Zeit derzeit nicht bestimmbar“**.

Für die Richtung werden bis zu eine Minute Netto-Batteriestrom zeitgewichtet gemittelt. Ein Richtungswechsel braucht anschließend 30 Sekunden Bestätigung mit frischen Messwerten. Kurze Gegenbewegungen wechseln die Beschriftung daher nicht sofort. Während der Prüfung steht **—** und **„Trendwechsel wird geprüft“**. Beim ersten frischen Empfang wird die beobachtete Richtung direkt gewählt. Nach Empfangspausen und Gerätewechseln beginnt die Mittelung neu. Reine Wiederholungen desselben alten Messpakets bestätigen keinen Wechsel.

Die Ladezeit verwendet einen Mittelwert über bis zu fünf Minuten der aktuellen Ladephase. Direkt nach dem Start steht **„Mittelung läuft an“**. Das **≈** kennzeichnet die Näherung; wechselnde Sonne, Verbraucher und der sinkende Ladestrom gegen Ladeende können die tatsächliche Zeit verändern. Bei 100 % während einer Ladephase stehen 0 und **„100 % laut SmartShunt“**. Fehlende oder mehr als 45 Sekunden alte Livewerte ergeben **—**. Die Zeitmittelung läuft auch auf den anderen Vordergrundseiten weiter.

Stunden sind voreingestellt; Minuten, Tage und Sekunden sowie Zahl und Rundinstrument sind wählbar. Die Großansicht verwendet dieselbe Automatik und zeigt die Erklärung. Historische Zeiträume und Vergleichskurven sind für diese Live-Kachel ausgeblendet. Die separate Kachel **Restlaufzeit** bleibt weiterhin verfügbar.

| Beim Laden | Beim Entladen |
|---|---|
| ![Automatische Akkuzeit als Restladezeit bis 100 Prozent](images/battery-time-charge.png) | ![Dieselbe Kachel als Restlaufzeit vom SmartShunt](images/battery-time-runtime.png) |

Solarleistung und Solarertrag zeigen im Standard die Zeit **seit Sonnenaufgang**. Nach Sonnenuntergang endet ihre Zeitachse dort; der erreichte Wh-Ertrag bleibt sichtbar. Verwertbare Erzeugung davor oder danach erweitert das Fenster und wird mitgezählt. Die Aufzeichnung läuft weiter. Ohne gültigen Sonnenuntergang reicht das Solarfenster mit erkennbarem Hinweis bis jetzt. Verbrauch und Bilanz beziehen sich auf **heute ab Mitternacht** und enthalten damit auch die Nacht. Die vier Verlaufskacheln passen ihre Skalen automatisch an die vorhandenen Werte an. Die breite Ansicht besitzt eine eigene gespeicherte Anordnung.

**+ Kachel** öffnet die Auswahl. Neue Kacheln erscheinen am Ende des Rasters. Eine Messkachel antippen und **Kachel-Einstellungen** wählen, um Beschriftung, Einheit, Darstellung und Zeitraum dauerhaft zu ändern. **Großansicht öffnen** zeigt dieselbe Messgröße zum Untersuchen in groß. Als Darstellung stehen je nach Messgröße Zahl, Rundinstrument oder Verlauf zur Wahl.

Unter **Anordnen** die Kacheln ziehen und an der unteren rechten Ecke vergrößern. Das Raster für Position und Größe ist in beiden Formaten doppelt so fein wie zuvor. Überlappungen sind erlaubt. Andere Kacheln behalten ihre Position. **Fertig** beendet die Anordnung. Schmale und breite Ansichten speichern ihre Positionen und Größen getrennt. Auch die Höhe von **„So läuft deine Energie.“** ist verstellbar: Unter **Anordnen** den ↕-Griff unten rechts nach oben oder unten ziehen, anschließend **Fertig**. Die Höhe bleibt je Ansicht separat gespeichert und ist in der Einstellungssicherung enthalten. Mit Tastatur ändern ↑/↓ die Höhe in 20-Pixel-Schritten.

![Anordnungsmodus mit verschiebbaren Kacheln und Reihenfolge der Übersichtsblöcke](images/arrange.png)

*Die Reihenfolge von „Kacheln mit Wetter“, „Verlauf“ und „Start / Pause“ lässt sich über die Pfeile ändern. Innerhalb des Kachelblocks wird direkt gezogen.*

**Einstellungen → Standardübersicht wiederherstellen** stellt die neue Standardansicht einschließlich der Kachelzeiträume für schmale und breite Bildschirme her. Beim normalen App-Update bleiben vorhandene eigene Übersichten erhalten. Messdaten und eingerichtete Geräte werden durch das Wiederherstellen der Standardübersicht nicht verändert.

Bei **„So läuft deine Energie.“** stehen **Seit Sonnenaufgang, Heute, Gestern, Woche, Monat, Letzte 6 Stunden und Letzte 24 Stunden** zur Wahl. Woche umfasst die letzten sieben Kalendertage einschließlich heute, Monat die letzten 30. Gestern zeigt den vollständigen vorherigen Tag; Zeitumstellungen werden berücksichtigt. Die Auswahl bleibt nach einem Neustart erhalten. Es werden vorhandene lokale Messwerte gelesen; fehlende Messzeiten bleiben Lücken.

## 4. Aktuell, Zeitraum und Skala wählen

Bei einer Verlaufskachel sind **die große Zahl und die Kurve getrennt einstellbar**:

| Einstellung | Bedeutung |
|---|---|
| Große Zahl: **Aktuell (Live)** | Frischer Empfang des Geräts; ohne frischen Wert erscheint „—“. |
| Große Zahl: **Letzter gespeicherter Wert** | Letzter gespeicherter Wert im gewählten Zeitraum. |
| Durchschnitt / Minimum / Maximum | Auswertung vorhandener Werte im gewählten Zeitraum. |
| Kurvenzeitraum: **Seit Sonnenaufgang** | Regulär Sonnenaufgang bis jetzt, nach Sonnenuntergang nur bis Sonnenuntergang; gemessene frühere oder spätere Erzeugung erweitert das Fenster. |
| Skala: **Automatisch nach bisherigen Werten** | Grenzen folgen den Messwerten des gewählten Zeitraums. |

![Kacheleinstellungen mit Sonnenaufgang als Kurvenzeitraum und aktueller Livezahl](images/tile-sunrise.png)

*Beispiel Solarleistung: Livezahl anzeigen und die gespeicherte Kurve seit Sonnenaufgang betrachten. Die Skala kann sich automatisch anpassen.*

**Kachel antippen → Kachel-Einstellungen → Wert & Zeitraum → Seit Sonnenaufgang** funktioniert auch bei berechnetem Solarertrag und Batterieentnahme. Deren Energiesummen beziehen sich dann auf dieses Fenster. Ohne verwendbare Sonnenaufgangszeit oder vor dem heutigen Sonnenaufgang steht ausdrücklich **heute (Ersatz)**; die Auswahl bleibt gespeichert.

Unter **Skala** zwischen festen Grenzen und automatischer Skalierung wählen. Bei kleinen Solarleistungen nutzt die Kurve dadurch die verfügbare Höhe. Spannungs- und Temperaturkurven passen auch die Untergrenze an. **Farben & Schrift** bietet eigene Farben, Schriftgröße, Nachkommastellen, Hintergrund und Deckkraft.

### Großansicht und Vergleichswerte

**Kachel antippen → Großansicht öffnen** vergrößert die Messgröße. Oben den Zeitraum wählen: Heute, Gestern, Woche, Monat, Seit Sonnenaufgang, Seit Zurücksetzen, Letzte … oder Von / bis. **Ansicht einstellen** öffnet Darstellung, Einheit, große Zahl, automatische oder feste Skalen, Nachkommastellen und Farbe.

![Großansicht der Solarleistung mit Batteriebilanz und Batteriespannung als Vergleich](images/tile-detail.png)

Unter **Vergleichswerte** weitere Messgrößen einschalten. Gleiche Einheiten werden gemeinsam dargestellt; unterschiedliche Einheiten erhalten eigene Werteskalen bei derselben Zeitachse. **+ / −** zoomt die Zeitachse. Einen Messpunkt antippen oder kurz gedrückt halten: Ein farbiger **Punkt** auf der Kurve und eine **senkrechte Linie** zeigen die Auswahl. Die Auswahl mit einem Finger ziehen; Markierung, Uhrzeit und Messwerte folgen. Bei Vergleichskurven erscheinen Punkte nur an tatsächlich vorhandenen nahen Messwerten; in Messlücken wird kein Wert erfunden. **Auswahl aufheben** beendet die Auswahl. Danach verschiebt ein Finger wieder den Zeitraum; zwei Finger zoomen auch während einer Auswahl. **Gesamter Zeitraum** stellt das vollständige gewählte Zeitfenster wieder her. Berechnete Energiesummen bleiben auf den gewählten Zeitraum bezogen.

Diese Änderungen gelten nur in der geöffneten Großansicht. **×** oder Android-Zurück schließt sie; die gespeicherte Kachel und ihre Anordnung bleiben erhalten. Für bleibende Änderungen **Kachel-Einstellungen** verwenden. Im Anordnungsmodus öffnet Antippen direkt den Kacheleditor.

## 5. Solarertrag, Verbrauch und Bilanz verstehen

| Kachel | Bedeutung | Standardzeitraum |
|---|---|---|
| **Solarertrag** | Aus der gemessenen Solarleistung berechnete Energie | Seit Sonnenaufgang |
| **Verbrauch** | Solarertrag minus Akkubilanz; direkt genutzter Solarstrom ist enthalten | Heute ab Mitternacht |
| **Bilanz** | Solarertrag minus berechneter Verbrauch | Heute ab Mitternacht |

Beispiel: **100 Wh Solarertrag − 150 Wh Verbrauch = −50 Wh Bilanz**. Plus bedeutet Überschuss, Minus Defizit. Wh und kWh sind wählbar. Die Rechnung verwendet die tatsächlichen Zeitabstände der erfassten Leistungspunkte und ist eine Näherung aus Regler und Shunt einschließlich der Verluste zwischen den Messstellen.

Der nächtliche Verbrauch bleibt enthalten: Außerhalb des Solarfensters zählt der beobachtete Shunt-Verlauf mit Solar null. Tatsächlich erfasste frühe oder späte Erzeugung erweitert das Fenster und wird in Ertrag und Verbrauch mitgeführt. Innerhalb dieses Fensters werden nur Zeitabschnitte verwendet, in denen Solarregler **und** Shunt verwertbare Daten haben. Es werden keine unbekannten Tageslücken mit null aufgefüllt. Die Prozentangabe nennt die erfasste Zeit des jeweiligen Fensters; fehlende Messintervalle erscheinen als **—**, erfasste Null als **0,00**. Ohne gültige Sonnenaufgangszeit wird ausdrücklich **heute (Ersatz)** verwendet. Vor dem bekannten Sonnenaufgang steht der Ertrag ohne verwertbare frühere Erzeugung bei 0 Wh. Verbrauch und Bilanz zählen auch nach Sonnenuntergang weiter; die Differenz bleibt Ertrag minus Verbrauch.

![Solarertrag, Verbrauch und gestrichelte Bilanz im berechneten Energieverlauf](images/energy-history.png)

Unter **Verlauf → Energie berechnet** erscheinen diese drei Kurven für die dortige Zeit- und Quellenwahl. **Kurve „Bilanz“ anzeigen** blendet die dritte Kurve ein oder aus. Antippen des Diagramms zeigt die Werte am ausgewählten Zeitpunkt. Überlappende Aufzeichnungen, Archive und CSV-Werte werden einmal gezählt; eigene Aufzeichnungen und feinere Archive haben Vorrang. Die Bilanz beschreibt den Energiefluss und keine genaue Änderung der chemisch gespeicherten Akkuenergie; Ladeverluste werden nicht korrigiert.

### Zusätzlicher PWM-Regler und andere Einspeisungen

Ein Regler ohne eigenen Ertragsmesswert entlastet den Akku, fehlt aber im angezeigten Solarertrag. Dadurch fällt auch der **berechnete Verbrauch um diesen Ertrag zu niedrig aus**.

Beispiel: Der SmartSolar liefert **20 Wh**, ein ungemessener PWM-Regler **30 Wh**, die Verbraucher nutzen tatsächlich **100 Wh**. Die App zeigt **20 Wh Solarertrag, 70 Wh Verbrauch und −50 Wh Bilanz**. Im reinen Solarbetrieb bleibt die Differenz richtig, obwohl Ertrag und Verbrauch unvollständig sind. Das setzt voraus, dass die Batteriezweige korrekt über den Shunt erfasst werden.

Ungemessener Landstrom oder andere Ladegeräte beeinflussen den Rechenwert ebenfalls. Bei starker zusätzlicher Einspeisung kann er negativ werden. Die Verbrauchskachel meldet dann **Zusätzliche Einspeisung**; der Wert wird nicht auf null begrenzt. Der Hinweis steht auch in den Kacheleinstellungen, in der Großansicht und unter **Verlauf → Energie berechnet → Berechnung und zusätzliche Einspeisung**.

### Die drei Kacheln einstellen

Die vorhandene Batterieentnahme-Kachel wird beim Update zu **Verbrauch**, die bisherige Akkubilanz zu **Bilanz**. Position, Größe in beiden Ansichten, Farben, Einheiten und eigene Beschriftungen bleiben erhalten. Unveränderte alte Standardbeschriftungen werden aktualisiert. Der Tagesstandard ist Solarertrag ab Sonnenaufgang und Verbrauch/Bilanz ab Mitternacht.

![Bilanz in Wh mit Verlauf und automatischer Skala](images/energy-tile.png)

Unter **+ Kachel → Energie** sind alle drei Werte einzeln ergänzbar. **Kachel antippen → Kachel-Einstellungen** bietet Zahlenwert, Rundinstrument oder Verlauf sowie Wh/kWh, Farben und Skala. Verbrauch und Bilanz verwenden einen gemeinsamen Zeitraum. Bei **Heute** kann Solar seit Sonnenaufgang zählen; bei **Letzte …**, **Seit Zurücksetzen** und freien Zeiträumen wechseln die drei Werte gemeinsam. Eine dauerhaft geänderte Sonnenaufgangsauswahl an der Solarkachel lässt Verbrauch und Bilanz auf Heute stehen. Vorzeichen der drei Hauptwerte bleiben ihrer Bedeutung entsprechend; in der Großansicht gelten Änderungen weiterhin nur vorübergehend.

Die bisherigen Werte **Batterieentnahme berechnet** und **Akkubilanz** bleiben unter **+ Kachel → Energie** als unabhängige Zusatzwerte verfügbar. Batterieentnahme summiert nur Energie aus dem Akku und erscheint standardmäßig negativ; die Akkubilanz summiert Laden minus Entladen allein aus dem Shunt. Deren Vorzeichen lassen sich weiterhin umkehren. Der grobe Tageszähler des SmartSolar bleibt unter **Solarertrag heute** auswählbar.

Alle Einstellungen einschließlich beider Anordnungen und des gemeinsamen Vergleichszeitraums sind in der JSON-Einstellungssicherung enthalten.

## 6. Den Verlauf untersuchen

Unter **Verlauf** den Zeitraum und die Messgröße wählen. **Alle Daten**, **Aufgezeichnet**, **Gerätearchiv** und **CSV-Import** filtern die Datenquelle.

![Leistungsverlauf mit Quellenwahl und Bedienelementen für die Achsen](images/history-dark.png)

*Solarleistung und Batteriebilanz gemeinsam betrachten. Unter „Werteskala“ lassen sich die Grenzen von Hand setzen.*

Mit **+ / −** die Zeitachse zoomen. Mit einem Finger die Zeitachse verschieben, mit zwei Fingern zoomen und durch Antippen einzelne Werte ansehen. Lange Zeiträume bündeln Messwerte; die Schattierung zeigt die ursprünglichen Minima und Maxima. **Zurücksetzen** stellt die Achsen zurück.

Die kleine Verlaufskarte auf der Übersicht bietet **Seit Sonnenaufgang**, **Heute**, **Gestern**, **Woche**, **Monat**, **Letzte 6 Stunden** und **Letzte 24 Stunden**. Diese Auswahl ist unabhängig vom großen Verlauf und den einzelnen Kacheln.

Im **Solar-Tagesarchiv** unter dem Verlauf steht neben dem importierten Ertrag der **berechnete Verbrauch in Wh**. Er gilt für den lokalen Kalendertag einschließlich der Nacht und verwendet die vorhandenen Solar- und Shunt-Messungen. Die Prozentangabe zeigt die zeitliche Abdeckung; Lücken werden nicht hochgerechnet. „—“ bedeutet, dass keine verwertbaren Messabschnitte vorhanden sind, und ist von einem gemessenen Nullverbrauch zu unterscheiden. Der heutige Wert ist vorläufig. Der Regler-Tagesertrag und der berechnete Verbrauch können wegen unterschiedlicher Datenquellen und Abdeckung nicht unmittelbar zu einer Bilanz subtrahiert werden. Die weiteren Tabellenspalten bleiben seitlich erreichbar.

![Solar-Tagesarchiv mit berechnetem Verbrauch und erfasster Zeit](images/solar-daily-consumption.png)

## 7. Solarvorschau und Solaranlage einstellen

**Einstellungen → Sonne voraus. → Aufstellung ändern** führt durch Standort, Himmelsrichtung und geschätzten Schatten. Die Ortssuche funktioniert ohne GPS-Berechtigung. Alternativ übernimmt **GPS-Standort übernehmen** nach deiner Zustimmung einen aktuellen Standort. Das ist ein einzelner Abruf; es gibt keine laufende Standortverfolgung. Ohne Standort bleibt die Vorschau ausgeschaltet.

### Solarkreise einmalig beschreiben

Unter **Deine Solaranlage → Bearbeiten** hinterlegst du je Kreis Modulanzahl, Wp pro Modul, Regler, Messwertquelle und **Montagewinkel**: 0° bedeutet waagerecht, 90° senkrecht. Der Winkel bleibt Teil der Anlage und muss am neuen Standplatz nicht erneut eingegeben werden. Ein bisher unbekannter Winkel bleibt unbekannt. Feste Kreise lassen sich standardmäßig einbeziehen; mobile Kreise werden erst vor Ort zugeschaltet.

### Aufstellung am Standplatz

Der Standort gilt für alle Kreise. Pro Kreis wählst du **eine von 16 Himmelsrichtungen** und Schatten für den ganzen Tag oder getrennt für morgens, mittags und abends. Die Richtung beschreibt die Vorderseite des Moduls, nicht die Fahrzeugfront. **Neuer Standplatz** setzt Richtung und Schatten zurück und schaltet mobile Kreise aus. Die gleiche Rücksetzung erfolgt bei Auswahl eines anderen Orts. Feste Montagewinkel bleiben erhalten. Alles wird mit **Aufstellung speichern** übernommen.

**Mit Handy bestimmen** öffnet den Kompassassistenten. Das Handy waagerecht halten. **Draußen** frontal vor der Modulvorderseite stehen und die Handyoberkante auf das Modul richten: Die App nimmt die Gegenrichtung als Modulrichtung. **Drinnen** die Handyoberkante zur Montagewand richten, an deren Außenseite das Modul sitzt. Die Übernahme wird erst bei ruhigen und ausreichend guten Sensorwerten freigegeben. Metall und Magnete können auch ruhige Werte verfälschen; eine Kontrollmessung außerhalb des Fahrzeugs ist sinnvoll. Ohne geeigneten Sensor bleibt die manuelle Auswahl verfügbar. Beim Schließen oder Verlassen der App werden die Sensoren gestoppt.

### Solar statt vollständigem Wetterbericht

Die vorhandene Wetterkachel behält Position und Größe und zeigt **14 Tage Solarvorschau**. Das Symbol beschreibt den Sonnenanteil der Tageslichtstunden, statt den ungünstigsten Wetterzustand des Tages. Sonnenstunden und ein Balken aus erwarteter Strahlung helfen auch ohne vollständige Anlagenangaben. Bei bekanntem Winkel, Richtung und aktiven Kreisen kommt ein geschätzter Wh-Wert hinzu; in der Tagesansicht steht die Spanne. In sehr flachen Kacheln bleibt der Wh-Wert in der Tagesansicht zugänglich.

Ab **1.4.2** sind Symbole und Balken größer. Der Balken **Sonne** zeigt die Einstrahlung relativ zum besten angezeigten Vorschautag. Der zusätzliche Balken **Akku** zeigt den geschätzten Ladezustand bei **Sonnenuntergang am gewählten Wetterort**, auf einer festen Skala von **0 bis 100 %**. In der Tagesansicht stehen außerdem der erwartete Stand bei **Sonnenaufgang am Folgetag**, die genauen Ortszeiten und ein Schätzbereich. So lässt sich die Reserve für die Nacht und nach der Nacht unterscheiden. Die größeren Tagesfelder sind weiterhin seitlich wischbar; die gespeicherte Kachelgröße wird nicht verändert.

Ab **1.4.3** passen sich die Tagesfelder an die tatsächliche Kachelbreite an. Auf üblichen Handybreiten werden drei vollständige Tage gezeigt, statt den dritten Prozentwert abzuschneiden; schmalere Kacheln zeigen entsprechend weniger ganze Tage. Die restlichen Tage bleiben seitlich wischbar. Unter der Vorschau steht bei einer fehlenden Akkuprognose der konkrete Grund, etwa **Akku: Verbrauch wird ermittelt** oder **Akku: aktueller Shuntwert fehlt**. Antippen öffnet die ausführliche Erklärung.

Die neue Startvorlage verwendet eine um eine Rasterzeile höhere Vorschau und die Ladephase direkt darunter. Neue Installationen und **Standardübersicht wiederherstellen** erhalten die neue Anordnung samt Darstellungswerten in beiden Ausrichtungen. Gespeicherte eigene Anordnungen werden beim Update nicht überschrieben. Beim Übersichtsverlauf neuer Installationen ist **Heute** voreingestellt; bestehende Zeitraumwahlen bleiben erhalten.

Die Akkuprognose benötigt einen aktuellen SmartShunt-Ladezustand samt Batteriespannung, eine eingestellte Kapazität und einen automatisch ermittelten oder freiwillig vorgegebenen Verbrauch. Ohne geeignete Verbrauchsdaten bleibt der Akkubalken zunächst **—**; eine Eingabe ist nicht erforderlich. Veraltete Livewerte, fehlende Ertragsdaten oder fehlende Sonnenzeiten werden ebenfalls erklärt. Liegt der heutige Sonnenuntergang bereits zurück, wird dafür keine neue Prognose angezeigt; der nächste Sonnenaufgang bleibt berechenbar. Die Solarvorschau funktioniert auch bei unbekannter Akkureserve.

Die Reserve wird ab jetzt stundenweise fortgeschrieben: Solar versorgt zunächst den laufenden Verbrauch; die verbleibende Energie lädt bis höchstens 100 %. Weitere mögliche Erzeugung wird als Überschuss verworfen und steht nachts nicht als gespeicherte Reserve zur Verfügung. Die Umrechnung in Akkuprozent verwendet Kapazität und aktuelle Batteriespannung. Mobile Kreise zählen nur bei gemeldeter Aufstellung. Das ist eine Planungsschätzung mit unsicherem Ertrag und Verbrauch; Ladeende, Batterieeigenschaften und eine geänderte Nutzung können abweichen. Der Schätzbereich ist keine garantierte Grenze. Könnte die Reserve zwischendurch aufgebraucht sein, bleibt dieser Hinweis auch nach einer späteren rechnerischen Aufladung sichtbar.

Einen Tag antippen: **morgens bis 11 Uhr**, **mittags 11–16 Uhr**, **abends ab 16 Uhr**, feste Erzeugung, mobile Entlastung und Vergleich zum üblichen Verbrauch. Die Tageswerte gelten für dieselbe Aufstellung über den ganzen Tag; sie sind kein gemessener Ertrag. Weiter entfernte Tage sind grobe Tendenzen. Fehlende Anlagendaten oder Vorhersagewerte ergeben **—**, keinen erfundenen Nullertrag. Eine bewusst ausgeschaltete Anlage hat dagegen 0 Wh erwarteten Beitrag.

**Mobil an/aus** meldet ein aufgestelltes bzw. abgebautes Modul. Bei mehreren mobilen Kreisen öffnet sich die gemeinsame Aufstellung. Ein ungemessener PWM-Kreis zählt in der Planung als geschätzte Verbrauchsentlastung. Sein tatsächlicher Beitrag ist bereits im gemessenen Shunt-Nettofluss enthalten; die bestehende Verbrauchsformel wird deshalb nicht zusätzlich korrigiert.

Ab 1.4.1 bleiben die Wettertage auch über die Zeitumstellung eindeutig. Die Tagesabschnitte berücksichtigen 23- und 25-Stunden-Tage. Fehlt eine Grundlage für die Wh-Schätzung, nennt die Tagesansicht konkret den fehlenden Montagewinkel, die fehlende Himmelsrichtung oder unvollständige Wetterdaten. Bei einem Wetterdatenproblem bietet sie **Vorschau aktualisieren** an. Eine Solarvorhersage benötigt keine vorherige Lernphase.

Die Vorschau wird bei aktivierter Funktion beim Öffnen und Zurückkehren aktualisiert; **↻** lädt von Hand. Während des Abrufs und ohne Internet bleibt die gespeicherte Vorschau sichtbar. **Wetter ausschalten** beendet die Wetterabrufe. Die Kachel liegt weiter im gemeinsamen Raster; sie lässt sich über **Anordnen** verschieben, vergrößern, entfernen und über **+ Kachel → Wetter** wieder hinzufügen.

### Automatisches Lernen ohne Vorabeingaben

**Verbrauch für die Planung → Automatisch lernen** ist der Standard. Geeignete Messabschnitte bilden die Grundlage; ein vollständiger Tag ist nicht erforderlich. Bei „Mobil an“ sind Nachtdaten weiterhin direkt nutzbar. In geeigneten, nicht erkennbar begrenzten Tagesabschnitten ergänzt eine schwächer gewichtete Schätzung die ungemessene mobile Erzeugung anhand der gemeldeten Aufstellung und des Wetters. Fehlende Tageszeiten werden vorläufig hochgerechnet; der Schätzbereich bleibt dann breiter. Die App berücksichtigt Jahreszeit und Tagesabschnitte mit fließenden Übergängen. Neuere Daten und passende Jahreszeiten haben mehr Gewicht. Noch unbekannte Jahreszeiten bleiben ausdrücklich vorläufig. Ohne geeignete Daten steht **Wird ermittelt**; die Solarvorschau funktioniert trotzdem. Freiwillig kannst du eigene Wh pro Tag für Frühling, Sommer, Herbst und Winter hinterlegen. Leere Felder verlangen keine Eingabe.

Originale Vorhersagen werden mit Zeitpunkt und Aufstellung lokal gespeichert und mit späteren geeigneten Messungen verglichen. Erst ab drei vergleichbaren Tagen wird je Tagesabschnitt vorsichtig korrigiert, bis zu zehn Tage schrittweise stärker. Kreise an derselben Messquelle werden gemeinsam gelernt. Die Ertragskorrektur verwendet die tatsächlich gemessene Solarquelle und bleibt auch bei einem ungemessenen mobilen Modul möglich. Messlücken, unklare oder begrenzte Ladephasen scheiden für die Ertragskorrektur aus. Beim Verbrauch bleiben vollständig gemessene Abschnitte auch während einer Begrenzung nutzbar; ungemessener mobiler Ertrag wird dort nicht hinzugeschätzt. Eine volle Batterie ist kein Beweis für geringen möglichen Solarertrag. Unbekannte Schattenlagen erweitern die Schätzspanne. Wetterfehler und nicht gemeldete Veränderungen können weiterhin zu Abweichungen führen.

### Aktive Ladeeinstellungen und Schonladeprofil

Öffne einen Vorschautag und **Aktive Ladeeinstellungen und Begrenzung**. Dort stehen die zuletzt ausgelesenen Ladegrenzen des SmartSolar sowie Kapazität und Vollerkennung des SmartShunt, jeweils mit Lesezeitpunkt. Nicht unterstützte oder unbeantwortete Werte bleiben unbekannt. Watt’n’Pit liest diese Werte und verändert keine Geräteparameter.

Die Werte werden bei einem Geräteabruf ergänzt. Wenn noch kein Stand vorliegt oder er sechs Stunden alt ist, darf die bestehende Automatik einen kurzen erneuten Leseabruf starten; bestätigte Archivwerte werden deshalb nicht nochmals übertragen. Abbruchpausen gelten weiterhin. **Daten nachladen** fordert den Abruf von Hand an und erkennt auch einen zwischenzeitlichen Profilwechsel.

Ein Schonladeprofil kann die mögliche Erzeugung bereits bei einer niedrigeren Ladespannung begrenzen. Die App betrachtet deshalb Reglerzustand, verfügbare Spannungs-/Stromgrenzen und Messwerte gemeinsam. Eine 100-%-Anzeige allein ist kein Beweis. Die nicht geernteten Wh sind nicht direkt messbar; auch der tatsächliche Beitrag eines PWM-Reglers ohne Bluetooth bleibt geschätzt. Die Akkureserve ist weiterhin eine Energienäherung, kein präzises Ladeendmodell für ein beliebiges Spannungsprofil.

## 8. Einstellungen und Messdaten sichern

![Einstellungssicherung mit Export und Import als JSON-Datei](images/settings-backup.png)

*Einstellungen → Einstellungen sichern: die persönliche Gestaltung und Anlagenkonfiguration als Datei sichern.*

**Einstellungen exportieren** erstellt eine JSON-Datei mit Kacheln, beiden Anordnungen, Farben, Zeiträumen, Akku-/Speicherparametern, Solaranlage, Aufstellung, freiwilligen saisonalen Verbrauchswerten und Wetterort. **Einstellungen importieren** zeigt vor der Übernahme eine geprüfte Vorschau. Ältere Sicherungen mit separatem Wetterblock werden übernommen.

Die Einstellungssicherung enthält **keine Geräteadressen, PINs, Verbindungsschlüssel oder Messdaten**. Auf dem Zieltelefon eingerichtete Geräte und vorhandene Messdaten bleiben erhalten. Auf einem neuen Telefon die Geräte separat einrichten. Der enthaltene Standort gehört zu deiner Konfiguration; eine Sicherung deshalb bewusst weitergeben. Mobile Kreise sind nach dem Import aus. Lerndaten werden nicht exportiert.

Messdaten werden getrennt über **Verlauf → Verlauf exportieren** als CSV gesichert und über **CSV importieren** wieder eingelesen. Näheres zur Spaltenzuordnung: [CSV-Import und Datenexport](CSV-IMPORT.md).

## 9. Lokal, mit optionalem Wetterabruf

Watt’n’Pit benötigt kein Konto und überträgt keine Energiedaten oder Geräteschlüssel an einen Cloud-Dienst. Schlüssel liegen mit dem Android Keystore geschützt auf dem Telefon. Die optionale Wetterfunktion benötigt Internet: Suchtext bzw. gewählte Ortskoordinaten, Montagewinkel und Ausrichtung gehen an Open-Meteo. Wetterdaten: Open-Meteo, CC BY 4.0; Ortsdaten: GeoNames.

Ladezustand und Restlaufzeit stammen vom SmartShunt. Temperatur und Zusatzspannung benötigen einen entsprechend eingerichteten Eingang. Weitere Victron-Geräte sind nicht pauschal als kompatibel bestätigt. [Technische Grundlage und Grenzen](ARCHITECTURE.md).

## 10. Mehrere Handys

Mit dem optionalen **Anlagenverbund** stellt ein Hauptgerät Messwerte, Verläufe, Wetter und Lernstand verschlüsselt im selben WLAN bereit. Anzeigen brauchen keine eigenen Sofortanzeigeschlüssel. Kacheln und Farben bleiben pro Handy einstellbar. Unter **Zusatzspeicher** lassen sich Start und Ende der Sondernutzung nach Bestätigung am Hauptgerät fernbedienen. Ab 1.6.3 kannst du auch die gemeinsamen App-, Anlagen- und Geräteeinstellungen von einer Anzeige ändern; das Hauptgerät bestätigt die Änderung.

Ohne Anlagenverbund arbeiten die Handys mit eigenen Geräten und Daten. Einstellungen lassen sich dann per JSON, Messdaten per CSV übertragen; importierte CSV-Daten bilden keine verlässlichen neuen Lernbeispiele.

Die Sofortanzeige empfängt ausgesendete Livewerte ohne feste Bluetooth-Verbindung. Der Gerätearchivabruf benötigt eine Verbindung: Laut Victron kann nur ein Handy zugleich mit demselben Gerät verbunden sein. Archive deshalb nacheinander ergänzen; eine offene VictronConnect-Verbindung kann den Abruf ebenfalls belegen. [Victron-Verbindungshinweise](https://www.victronenergy.com/media/pg/VictronConnect_app/en/troubleshooting.html).

## Ergänzungen in 1.4.6

Unter **Einstellungen → Akku & Aufzeichnung → Betriebsbenachrichtigung einstellen** öffnet sich der vorhandene Android-Benachrichtigungskanal **Watt’n’Pit-Aufzeichnung**. Seine Anzeige lässt sich dort ausblenden, ohne die Aufzeichnung zu pausieren. **Gerätearchiv importieren** ist ein getrennter Kanal. Android kann einen laufenden Vordergrunddienst zusätzlich in seiner Systemübersicht anzeigen. Pausieren und Fortsetzen bleiben in Watt’n’Pit verfügbar.

Die Wh-Spanne eines Vorschautags ist das geschätzte **Gesamtpotenzial aller aktiven Kreise**, einschließlich der gemeldeten mobilen Aufstellung. Feste Kreise und mobile Entlastung werden darunter getrennt ausgewiesen. Die Solarertrag-Kachel misst nur die Solarregler; ungemessene mobile Entlastung ist darin nicht enthalten. Die Formel für gemessenen Verbrauch und Bilanz sowie die Prognoseberechnung bleiben unverändert. Ladebegrenzung kann den abgerufenen Ertrag verringern; die Schätzspanne ist keine garantierte Untergrenze.

Nach dem heutigen Sonnenuntergang zeigt die Akkuvorschau den zeitlich nächsten gespeicherten SmartShunt-Messwert innerhalb von 15 Minuten vor oder nach Sonnenuntergang. Messzeit und Quelle stehen in der Tagesansicht. Es werden weder Zwischenwerte erfunden noch heutige Livewerte rückwirkend eingesetzt. Fehlt ein passender Originalwert, bleibt die Anzeige unbekannt und erklärt den Grund. Die Reserve beim nächsten Sonnenaufgang wird weiterhin ab dem aktuellen Ladezustand prognostiziert.

© 2026 Tim Weißbach und Mitwirkende · Watt’n’Pit


## Roller und Ladeplanung ab 1.5.0

1. **Zusatzspeicher → Roller einrichten**: tatsächliche Akkudaten und BMS-Adresse eintragen oder ein gekoppeltes Gerät wählen. Watt’n’Pit-Aufzeichnung starten.
2. Für den Ladeweg Wohnwagen → Wechselrichter → Netzladegerät → Roller im Rollerbereich **Ladung als Sondernutzung starten** wählen und nach dem Laden beenden. Die App schaltet keine Hardware. Wh im Roller sind aus erfassten BMS-Abschnitten berechnet; der Wohnwagenanteil ist mit dem vorläufigen Verlustprofil geschätzt. Fehlende Messzeiten bleiben offen.
3. Ladeziel und optional Strecke, Wh/km, Fahrpuffer und Abfahrts-/Rückkehrzeit hinterlegen. Mit **Ladung ab jetzt in der Akkuprognose einplanen** den noch benötigten Ladebedarf berücksichtigen. Verbrauch und Verluste sind vorläufige Planungswerte, keine Ladegarantie.
4. Auf demselben Telefon kann ScootPit ab der BMS-Schnittstelle in **1.6.13-test.1** seine Grunddaten lokal liefern. Beide Apps aktualisieren und dasselbe BMS verwenden. Bei Übernahme erscheint **BMS über ScootPit · lokale Datenübernahme**. Mit älteren ScootPit-Versionen bzw. Overkill ist diese Übergabe nicht möglich. Kein Austausch zwischen unterschiedlichen Telefonen.
5. Ab 1.6.2 steht der Roller unter **Zusatzspeicher** in einem eigenen Menüpunkt. Die Seite scrollt mit ihrem gesamten Inhalt.

## Hintergrundabruf und Herunterziehen ab 1.5.0

Neue Archivabrufe laufen standardmäßig im Hintergrund. Die Statusanzeige oben zeigt Fortschritt und vorläufige Daten; Antippen öffnet Details und die Abbruchmöglichkeit. Nach Abschluss werden Daten und Kurven aktualisiert. Auf der Übersicht kann am oberen Seitenrand auch durch deutliches Herunterziehen und Loslassen nachgeladen werden. Der Button bleibt erhalten. Beim Kachel-Anordnen, in Dialogen, über Diagrammen oder während eines Abrufs löst die Geste keinen weiteren Import aus.

## Vollständige Standardübersicht

**Einstellungen → Ansicht → Standardübersicht wiederherstellen** ersetzt nach Bestätigung sämtliche Kacheln einschließlich Größen, Positionen, Kachelfarben, Reihenfolge der Blöcke und Höhe des Verlaufs. Beide Ansichten werden zurückgesetzt. Messdaten, Geräte und Rollerprofil werden nicht gelöscht. Eigene Layouts vorher über **Einstellungen exportieren** sichern. Die Kachelanordnung und Blockreihenfolge sind darin enthalten; BMS-Adresse und Ladejournal gehören zum lokalen Diagnoseexport.


## Zusatzspeicher und Verbundbedienung ab 1.6.2

Die Navigation lautet **Übersicht · Zusatzspeicher · Verlauf · Geräte · Einstellungen**. Rollerwerte und Ladejournal stehen unter **Zusatzspeicher**. Das Profil bleibt auch über **Einstellungen → Roller einrichten** erreichbar.

Für die automatische Zuordnung im Rollerprofil **Laden am Wohnwagen automatisch zuordnen** einmal einschalten. Positiver BMS-Ladestrom bestätigt den Ladestart; eine Minute ohne nennenswerten Ladestrom bestätigt das Ende. Die Aufzeichnung und BMS-Verbindung müssen am Hauptgerät laufen. Das BMS erkennt die Ladequelle nicht: Vor dem Laden an einer externen Steckdose die Automatik ausschalten. Manuelles Beenden pausiert sie bis zum bestätigten Ladeende. Empfangslücken bleiben ohne erfundene Ladeenergie; nur erfasste automatische Ladeabschnitte werden aus dem Lernen des normalen Tagesbedarfs herausgenommen.

Die Sondernutzung lässt sich unter **Zusatzspeicher** auch am Anzeigegerät starten und beenden. Der Button wartet auf die Antwort des Hauptgeräts; danach erscheint **Bestätigt: Sondernutzung läuft/beendet**. Bei einem Fehler bleibt eine verständliche Meldung sichtbar. Für diese Funktion alle beteiligten Handys auf 1.6.2 aktualisieren. Die App schaltet das physische Ladegerät nicht.

Nach erfolgreichem Beitritt zum Verbund erscheint **Verbindung hergestellt** mit Anlagenname und Empfangsstand. **Zur Übersicht** führt zur Verbundkachel. Diese ist unter **Anordnen** frei zwischen den Hauptkacheln verschiebbar und skalierbar; vorhandene Positionen werden beim Update erhalten. Entfernte Verbundkacheln lassen sich über **+ Kachel → Anlagenverbund** wieder hinzufügen. Ab 1.6.3 wird die freigegebene G5-Anordnung beim Update einmalig auf alle Handys übernommen.

Beim QR-Scan alle Ränder des Codes im Rahmen halten und etwas Abstand zum Bildschirm lassen. Nach fehlender Kameraberechtigung oder Zeitablauf erklärt die App den nächsten Schritt. Ein über die Kamera-App kopierter Textcode bleibt als Alternative verwendbar.


## Gemeinsam bedienen und G5-Layout ab 1.6.3

**Alle beteiligten Handys aktualisieren**, zuerst das Hauptgerät. Am Anzeigehandy lassen sich jetzt Solaranlage, mobile Aufstellung, Standort/Wetter, saisonaler Bedarf, Zusatzspeicherprofil, Geräte, Batteriekapazität und Abtastintervall ändern. Aufzeichnung sowie Gerätearchivabrufe lassen sich starten, beenden bzw. abbrechen und im Hintergrund weiterführen. GPS und Kompass verwenden die Sensoren des gerade bedienten Handys; gespeichert werden die bestätigten Angaben am Hauptgerät.

Während einer Änderung erscheint **Bestätigung vom Hauptgerät wird erwartet …**, anschließend **Vom Hauptgerät bestätigt** oder eine Fehlermeldung. Bei zwischenzeitlich geänderten Einstellungen die Ansicht schließen und neu öffnen. Ohne erreichbares Hauptgerät wird keine erfolgreiche Änderung behauptet. Der Verbindungscode erlaubt jetzt auch die Bedienung der gemeinsamen App-Einstellungen. Geräteparameter und Firmware ändern sich weiterhin ausschließlich über die jeweilige Hersteller-App. CSV-Verlaufsdateien und Diagnosepakete werden am Hauptgerät verwaltet.

Das freigegebene **G5-Layout** ersetzt beim ersten Start nach dem Update einmalig die bisherigen Kachelanordnungen auf jedem Handy, in schmaler und breiter Ansicht. Weitere Starts erhalten danach deine eigenen Änderungen. **Standardübersicht wiederherstellen** verwendet ebenfalls diese Vorlage. Schriftgrößen sind auf **höchstens 20** begrenzt, auch beim Laden älterer Einstellungsdateien. Kleinere Werte bleiben erhalten. Neue Kacheln bleiben im gemeinsamen Raster verschiebbar und skalierbar.

Unter **Zusatzspeicher → Enthaltene Energie** steht der aktuelle Speicherinhalt in Wh als Näherung: BMS-Restkapazität in Ah × eingestellte Nennspannung, ersatzweise Ladezustand × hinterlegte Nennenergie. Ohne aktuelle BMS-Daten bleibt der Wert unbekannt. **Geladen** zeigt weiterhin nur die in einer Ladeaufzeichnung gemessenen Abschnitte. Der Speicherinhalt ist keine Zusage für tatsächlich zum Wohnwagen rückspeisbare Energie.


## Tagesarchiv und Nachladen ab 1.6.4

Im **Verlauf → Solar-Tagesarchiv** können Anzeigen die Verbrauchswerte auch für den laufenden Kalendermonat vom Hauptgerät abfragen. Die Berechnung endet am aktuellen Zeitpunkt; zukünftige Tage erhalten keine erfundenen Werte. Ertrag, Spitzenleistung und Spannungsgrenzen stammen weiterhin aus dem Tagesarchiv des Hauptgeräts.

Bei einer Aktualisierung bleiben die bereits geladenen Kurven und historischen Werte derselben Ansicht sichtbar. **Letzter Stand · neue Daten werden geladen …** kennzeichnet den laufenden Abruf. Erst die neue Antwort ersetzt die Daten. Bei einem Aktualisierungsfehler bleibt der letzte erfolgreiche Stand erkennbar; die App versucht den Abruf erneut. Eine erfolgreiche Antwort ohne Messdaten entfernt alte Werte. Andere Anlagen, Messgrößen, Quellen und ausgewählte Zeiträume übernehmen keine Daten aus der vorherigen Ansicht.

Für beide Korrekturen **Hauptgerät und Anzeigen auf 1.6.4 aktualisieren**. Die G5-Vorlage wird dabei nach erfolgtem 1.6.3-Update nicht erneut über eigene Layoutänderungen geschrieben.
