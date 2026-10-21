# Wiederholte Archivstarts im Vordergrund: 1.3.6 und Korrektur

Ausgangspunkt ist die veröffentlichte 1.3.6, Quellcommit `73976718b406dc893d1bfdd61e6e8a018e005aeb`.

## Nachgewiesene Ursache

`MainActivity.retryIncompleteArchive` ruft alle 15 Sekunden `startAutomaticImport()` ohne die Beschränkung auf unvollständige Abrufe auf. Das leitet `retriesOnly=false` an das Repository weiter. Damit werden auch erfolgreich abgeschlossene Geräte nach Ablauf der fünfminütigen Wartezeit wieder geprüft.

Die Live-Sofortanzeige enthält nicht alle Trendmessgrößen. Im untersuchten Fall ist Solarleistung kontinuierlich vorhanden, PV-Spannung aber nur im Gerätearchiv. Deren neue Punkte reichen aus, um nach jedem erfolgreichen Lauf nach fünf Minuten den nächsten vollständigen Verbindungsaufbau einschließlich Metadaten und Zusatzabfragen zu starten. Das ist ein anderer Fehler als das erneute Laden bereits bestätigter Punkte: Im neuen Gegenbeispiel werden **null identische Punkte doppelt übertragen**, trotzdem öffnen sich zwölf Archivverbindungen pro Stunde.

Zusätzlich wiederholt der Timer fehlgeschlagene GATT-Sitzungen nach fünf Minuten auch dann, wenn sich der passive Empfangszustand nicht geändert hat.

## Vergleich mit identischen Szenarien

| Szenario | Veröffentlichte 1.3.6 | Korrektur |
| --- | ---: | ---: |
| Eine Stunde offen, bereits erfolgreich importiert, laufende Solar-Livewerte und neue reine PV-Archivwerte | 12 neue Verbindungen, 84 Messwertanfragen | 0 wiederholte Verbindungen, 0 Anfragen |
| Identische bestätigte Punkte während dieses Szenarios erneut gelesen | 0 | 0 |
| Fehlgeschlagene GATT-Sitzung, anschließend 30 Minuten ohne Empfangsunterbrechung | 6 weitere Versuche | 0 |
| Erneutes Öffnen holt zwischenzeitlich aufgelaufene Archivwerte nach | ja | ja |
| Kürzere erneute Vordergrundöffnung mit neuem Bedarf nach erfolgreichem Import | alte Fünf-Minuten-Sperre | sofort nach lokaler Bedarfsprüfung |
| Ausfall und echte Rückkehr des Empfangs nach einem fehlgeschlagenen Versuch | nicht als Voraussetzung unterschieden | genau ein weiterer offener Abruf |
| Nach Abschluss dieser Wiederaufnahme weiter zehn Minuten geöffnet | erneut möglich | kein weiterer Start |
| Manuell abgebrochen, dann Vordergrund-/Empfangsereignis innerhalb der Pause | pausiert | pausiert |

Die positive Prüfung enthält 12 Assertions. Die bisherigen 172 Kerntests und 16 Speicher-/Startprüfungen bestehen weiterhin; darunter Drei-Tage-/Drei-Wochen-Lücken, Geräteisolierung, Transaktionsfehler und Wiederaufnahme.

## Umsetzung

- Der vorhandene Vordergrund-Timer prüft ausschließlich offene Abrufe (`retriesOnly=true`).
- Das Repository unterscheidet eine neue Empfangsphase von fortlaufenden Livepaketen. Ein automatischer Retry verlangt eine frische neue Empfangsphase nach dem letzten Versuch und weiterhin tatsächlichen Fehlbedarf.
- Der gültige Livewert läuft durch dieselbe Speicherfunktion wie zuvor. Der zusätzliche Empfangsmarker ändert keine Messwerte und bestätigt keine Archivlücke.
- Erfolgreiche Abrufe oder Prüfungen ohne Fehlbedarf erzeugen keine fünfminütige Sperre für den nächsten echten Vordergrundbedarf.
- Zusätzliche reine Archivkanäle werden innerhalb einer ununterbrochen geöffneten erfolgreichen Sitzung gesammelt zurückgestellt. Sie werden beim nächsten Vordergrundabgleich ergänzt; dies ist bewusst kein permanenter Fünf-Minuten-Import mehr. Die Speicherfrist des Gerätearchivs bleibt die äußere Grenze für längere Zeiträume ohne erneuten Vordergrundabgleich.
- Die Anzeige der Appversion wird über `BuildConfig.VERSION_NAME` versorgt.

## Reproduzieren

Mit JDK 17, Python 3 und den in `tools/prove-archive-storage.py` verwendeten Abhängigkeiten `android-api35.jar`, `sqlite-jdbc-3.46.1.0.jar`, `slf4j-api-1.7.36.jar`:

```sh
git archive v1.3.6 | tar -x -C /tmp/wattpit-baseline
python3 tools/prove-archive-foreground.py --dependencies /tmp/wattpit-deps --source-root /tmp/wattpit-baseline --output /tmp/wattpit-foreground-before --mode baseline
python3 tools/prove-archive-foreground.py --dependencies /tmp/wattpit-deps --output /tmp/wattpit-foreground-after --mode fixed
```

Das Zielverzeichnis für den Baseline-Export vorher anlegen. Die beiden Programme bleiben auf dem aktuellen Korrekturzweig; `--source-root` wählt nur die Produktionsquellen aus. [Negativer Nachweis](archive-foreground-before.json), [positiver Nachweis](archive-foreground-after.json), [Protokoll vorher](archive-foreground-before.log), [Protokoll nachher](archive-foreground-after.log).

## Aussagegrenze

Die originalen Methoden der Vordergrundauslösung werden in einen kleinen Android-UI-/Executor-Adapter übernommen, das Repository mit einer kontrollierten Uhr ausgeführt und die Original-Speicherung durch den vorhandenen JDBC-Adapter betrieben. Die Prüfberichte enthalten die Quellhashes. Nur der Zeitgeber wird in der generierten Repository-Testkopie ersetzt; die Produktionsquelle wird dafür nicht verändert.

Android-Servicelebenszyklus, Funkstrecke und Firmwareantworten des konkreten Nutzergeräts sind nicht Bestandteil dieses Tests. Nachgewiesen sind der verbliebene Neustartpfad und seine Unterdrückung im gezeigten Ablauf. Damit ist weder die Dauer eines einzelnen realen Imports gemessen noch jede Ursache des gemeldeten Synchronisationsverhaltens ausgeschlossen.

