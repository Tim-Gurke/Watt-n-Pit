# Reparaturentwurf zur Anzeige in 1.6.11

Beim Neuzeichnen der skalierten Android-Oberfläche kann `innerWidth` die Breite des Dokuments melden. Die bisherige Profilauswahl lud dann beim Wechsel zu „Anordnen“ die gespeicherte breite Ansicht. Die Auswahl folgt jetzt derselben Viewport-Medienabfrage wie die Oberfläche. Die beiden gespeicherten Anordnungen bleiben erhalten.

Verlaufs- und Modellabfragen nutzten auf dem Hauptgerät außerhalb eines Imports noch die synchrone JavaScript-Schnittstelle. Die vorhandene asynchrone Abfrage wird jetzt auch im normalen lokalen Betrieb verwendet. Wartende Abfragen teilen sich weiterhin eine begrenzte Warteschlange; vorhandene Werte bleiben ausdrücklich als letzter Stand sichtbar. Nach geänderten Messdaten wird der lokale Cache ebenfalls erneuert.

Eine noch ausstehende Energieantwort wird als ausstehend dargestellt. Sie löst keinen Hinweis auf eine veraltete Hauptgeräteversion aus. Leere Diagramme unterscheiden zwischen laufendem Abruf, fehlgeschlagenem Abruf und einer erfolgreich gelesenen leeren Datenreihe. Die mobile Energiereihe erhält dieselben Warte- und Aktualisierungskennzeichnungen wie die anderen Energiereihen.

Lokal geprüft: 180 JavaScript-Tests; Größenregler und beide Layouts/Farbschemata; unveränderte Kachelkoordinaten beim Bearbeiten unter 50, 73, 100 und 150 Prozent trotz abweichendem `innerWidth`; weiterhin bedienbare lokale Oberfläche bei angehaltener Hintergrundantwort; Importabschluss und Erhalt älterer Werte.

Die konkrete Ursache eines anhaltenden Stillstands oder fehlgeschlagener Energieabfragen auf einem physischen Hauptgerät ist damit noch nicht nachgewiesen. Dafür wird ein aktueller Diagnoseexport direkt von diesem Gerät benötigt. Ein synthetischer SQLite-Test mit etwa 82.000 Messzeilen und begrenztem JVM-Speicher liefert die Energiesummen; er ersetzt keinen Test des Android-Lebenszyklus oder der Leistung des betroffenen Telefons.
