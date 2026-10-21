# Victron-Protokollkorrektur in 1.3.9-test.1

Dies ist eine Testfassung. Laufzeit, Empfang und vollständige Anzeige an Tims SmartSolar 75/15 und SmartShunt 300A sind noch am Telefon zu bestätigen. Die Reproduktion verwendet einen öffentlichen, bereinigten SmartShunt-Mitschnitt und Android-API-Adapter; sie ist kein BLE-Geschwindigkeitsbenchmark und kein Mitschnitt von Tims Geräten.

## Korrekturen

- Antwortzeiten dürfen innerhalb eines Geräteintervalls nach vorn gerundet sein. Gleich aufgelöste Archivwerte und bestätigte Antworten decken die zugehörigen Anfragepositionen ab, auch wenn die Metadaten nicht auf dem Messraster liegen. Ein naher Livepunkt allein erklärt weiterhin keine Lücke für vollständig.
- Normale Randantworten am alten Ende einer bereits gelesenen Stufe werden verarbeitet. Ein kurzer bzw. gröberer Rückgabeblock beendet dort die feinere Stufe. Fehlende Antworten und leere Blöcke mitten in einer Stufe bleiben Fehler-/Neuplanungsfälle. Alle tatsächlich gelieferten Werte werden unter ihren wirklichen Zeiten und Auflösungen gespeichert. Ein gerundetes Randpaket wird nicht auf eine geschätzte Punktzahl abgeschnitten.
- Alte Antworten werden vor dem Speichern gegen Kanal, Zeitfenster, Auflösung und Anfragegröße geprüft. Kanal und Stufen werden zusammenhängend abgearbeitet.
- Beide Register-Characteristics teilen sich Stream und Empfangsfenster. DATA und BULK werden jetzt beide gezählt. Auf einen Cursor-SET folgt kein unnötiger zusätzlicher GET mehr.
- Vorhandene ältere Archivstufen behalten bei der lokalen Startprüfung ihren zeitlichen Abstand zum erfassten Gerätezähler. Sie springen nicht bei jedem Öffnen fälschlich bis zur Gegenwart vor.
- Beim Speichern entfällt eine doppelte Abfrage derselben Archivzeile. Messwerte und Bestätigungen bleiben atomar gespeichert; die Datenbankstruktur und Energieformeln werden nicht geändert. Die Diagnose enthält die tatsächliche Dauer pro Gerät.

## Reproduktion

Quelle: [victron-trends](https://github.com/edilAraujo/victron-trends), [Protokoll anhand von VictronConnect-Mitschnitten](https://github.com/edilAraujo/victron-trends/blob/main/PROTOCOL.md). Die TSV-Testdaten übernehmen die Originalbytes aus dem öffentlichen bereinigten JSON-Mitschnitt. Herkunft, SHA256 und MIT-Lizenz stehen bei den Testdaten. Es werden keine privaten Gerätekennungen, Verbindungsschlüssel oder Rohmitschnitte veröffentlicht.

Der unveränderte 1.3.8-Matcher lehnt 391 von 462 regulären aufgezeichneten Antworten aufgrund ihrer gerundeten Zeit ab. Der neue Matchertest akzeptiert diese Antworten und verwirft nachgestellte verspätete Antworten anderer Kanäle, Zeiten oder Auflösungen.

Die Importwarteschlange wird zusätzlich von einem Test-Peer bedient, der ausschließlich tatsächlich aufgezeichnete Messwerte und Gerätezeiten liefert. Der Peer ordnet die vorhandenen Bytes für neue Anfragegrößen neu an; er simuliert keinen Funkverkehr. Alle 8.411 gültigen aufgezeichneten Werte werden mit den tatsächlichen HistoryDb-Methoden in SQLite geprüft. Der volle Bestand benötigt 465 Blockabrufe ohne Neuplanung. Ein frischer Prozess benötigt anschließend null Messwertabrufe und startet laut tatsächlichem Repository keinen GATT-Abruf. Nach gezieltem Entfernen der letzten drei Stunden werden 70 Blöcke benötigt; sämtliche aufgezeichneten Werte stimmen danach wieder exakt. Ein weiterer Abruf lädt nichts erneut.

Die produktive `incoming`-Methode wird mit API-Adaptern ausgeführt: Auf beide Characteristics aufgeteilte Nachrichten werden zusammengeführt und die Empfangsfreigaben nach allen Fragmenten erneuert. Eine echte Geräteverbindung wird dabei nicht aufgebaut.

[Strukturierte Ergebnisse](victron-sync-capture-test.json). Zusätzlich laufen die bisherigen Kern-, Delta-, Speicher-, Vordergrund-, JavaScript-, Browser-, Lint- und Android-Buildprüfungen. Die Messergebnisse aus diesem Test dürfen nicht als Sekundenangabe für das Telefon ausgegeben werden.
