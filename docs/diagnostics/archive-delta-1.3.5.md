# Nachweis zum Archivabgleich von 1.3.5

Ergebnis: **Die Anforderungen sind mit dem veröffentlichten Quellstand nicht erfüllt.**

Geprüfter Release-Quellstand: `195a0b46f874f7e834d30f4526737df67908f6b2`. Die produktiven Klassen wurden nicht geändert. Ihre SHA-256-Werte stehen im [maschinenlesbaren Ergebnis](archive-delta-1.3.5.json).

| Fall | Tatsächlich angeforderte Punkte | Erneut angeforderte, schon bestätigte Punkte |
|---|---:|---:|
| Zweiter Lauf mit vollständig gespeicherten älteren Werten | 0 | 0 |
| Zweiter Lauf nach aktuellem Import | 0 | 0 |
| Unverändertes Archiv mit bestätigten NA-Antworten, fünf Minuten nach Ende erneut geöffnet | 6 | 6 |
| Abbruch nach acht Blöcken, fünf Sekunden später in neuer JVM fortgesetzt | 6433 | 3 |
| Drei Wochen lückenlose lokale Liveaufzeichnung | 0 | 0 |

Die Kontrollfälle mit drei Wochen gültigen Archivdaten erreichen vollständige Energieabdeckung und bestehen den erneuten Abgleich. Neue Werte führen zu keinen wiederholten identischen Archivpunkten. Zusätzliche andere Auflösungen werden separat gezählt: Der Fall mit 60 neuen Sekunden fordert 180 Sekundenpunkte und sechs zusätzliche 30-Sekunden-Punkte an; diese sechs sind keine wiederholten identischen Archivpunkte.

Im Fall mit bestätigten NA-Antworten werden sechs identische Archivpunkte erneut angefordert ([Abrufprotokoll](unavailable_second_after_5min-1.3.5.tsv)). Beim Fortsetzen werden drei identische Punkte wiederholt ([Abrufprotokoll](resume_fresh-1.3.5.tsv)). Dazu gehören bestätigte NA-Antworten und ein bereits gespeicherter gültiger Messpunkt. Die Zwei-Intervall-Regel in `ArchiveSyncPlan.Availability.reading/known` und `completed` stuft aktuelle Daten weiterhin als nachzuladen ein bzw. bewahrt ihre Empfangsbestätigung nicht dauerhaft auf.

`ArchiveSchedule.automaticDue` erteilt nach fünf Minuten erneut eine Startfreigabe, auch wenn der geprüfte lokale Bestand vollständig ist. `Repository.automaticArchiveDevices` verwendet diese Freigabe ohne Prüfung des tatsächlichen Messwertbedarfs. Damit ist eine überflüssige Startfreigabe im Programmablauf nachgewiesen. **Eine reale Bluetooth-Verbindung wurde in diesem Test nicht ausgeführt oder gezählt.**

## Prüfumfang

Die unveränderten Produktionsklassen `ArchiveCodec`, `ArchiveSyncPlan`, `ArchiveTransferPlan`, `ArchiveMerge`, `ArchiveSchedule`, `HistorySchema`, `SampleTimeline` und `EnergyIntegral` werden direkt ausgeführt. Der unabhängige Geräteersatz liefert deterministische Binärblöcke; die ausgehenden EC5B-Befehle werden ebenfalls erzeugt. Zwei Geräte und drei Messkanäle sowie optional ein vierter Kanal mit NA-Antworten verwenden vier Archivstufen (1, 30, 300, 1800 Sekunden) über 21 Tage. SQLite speichert Messwerte und Bestätigungen mit dem produktiven Schema und den produktiven Abgleichabfragen. Ein zusätzliches reines Protokollierungstableau zählt identische bereits empfangene Punkte je Kanal, Auflösung und Zeitstempel, ohne die Planung zu beeinflussen.

Jede Phase startet eine neue JVM und lädt SQLite neu. Pro Antwort vergeht eine simulierte Sekunde; der folgende Lauf startet erst nach dem so berechneten Ende. Dadurch wird nicht irrtümlich angenommen, ein vollständiger Mehrwochenimport sei vor seiner ersten Antwort bereits abgeschlossen. Die Bestätigungszeit und der beim Planungsbeginn festgelegte Zeitpunkt bleiben wie im Android-Service getrennt. Android-GATT und die konkreten Rohdaten des Nutzers sind nicht Teil dieses Tests. Es wurde keine APK gebaut und kein Release erstellt.

## Wiederholen

Benötigt: Java 17 mit Compiler-Modul, Python 3, `sqlite-jdbc-3.46.1.0.jar` und `slf4j-api-1.7.36.jar` (Abhängigkeiten außerhalb des Repositorys ablegen).

```sh
python tools/prove-archive-delta.py \
  --output /tmp/wattpit-delta-proof \
  --sqlite-jar /pfad/sqlite-jdbc-3.46.1.0.jar \
  --slf4j-jar /pfad/slf4j-api-1.7.36.jar
```

Der Lauf erzeugt JSON-Ergebnisse und einzelne Abrufprotokolle. Er endet mit Status 1, solange eine Anforderung verletzt ist. Es wird ausschließlich der Java-Prüfcode kompiliert, keine Android-App.
