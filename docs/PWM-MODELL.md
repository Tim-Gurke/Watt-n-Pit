# PWM in Solarprognose und Verbrauchslernen

Seit Version 1.6.9 werden PWM-Angaben ausdrücklich in Solarprognose und Verbrauchslernen berücksichtigt.

Vorhandene Solarkreise mit einer eindeutigen PWM-Angabe im Feld **Regler / Ladekreis** werden automatisch berücksichtigt. Groß-/Kleinschreibung und Bezeichnungen wie „Dokio PWM-Regler“ oder „PWM20A“ sind erlaubt. Sind gleichzeitig PWM und MPPT angegeben, bleibt der Typ unbekannt. Unter **Reglertyp** kann die Erkennung ausdrücklich überschrieben werden.

## Vorläufige Gesamtfaktoren

| Ertragsmodell | Gesamtfaktor |
| --- | --- |
| Automatisch, PWM | 0,60 |
| Automatisch, MPPT oder unbekannt | 0,80 |
| Eigener Gesamtfaktor | Der eingetragene Wert, einschließlich aller Verluste |

Der PWM-Startwert ist eine konservative Modellannahme, kein gemessener Reglerwirkungsgrad und keine genaue spannungs- oder temperaturabhängige Simulation. PWM zieht die Modulspannung näher an die Akkuspannung; der tatsächliche Unterschied zu MPPT hängt von der Modulkennlinie, Batterie, Temperatur und Einstrahlung ab. Ohne entsprechende Messwerte lässt er sich nicht eindeutig bestimmen. Grundlage: [Victron: Which solar charge controller: PWM or MPPT?](https://www.victronenergy.com/upload/documents/Technical-Information-Which-solar-charge-controller-PWM-or-MPPT.pdf).

0,60 ist bereits der **gesamte** Startfaktor: Er ersetzt 0,80. Es werden nicht erst 0,80 und anschließend noch einmal 0,60 angewendet. Ein eigener Gesamtfaktor erhält keinen weiteren PWM-Abschlag. Ausrichtung, ausdrücklich angegebener Schatten und die vorhandene vorsichtige Wetterkorrektur bleiben eigene Einflüsse.

## Bestehende Einstellungen

Die frühere Eingabemaske speicherte auch einen unveränderten Standard als `yieldFactor: 0.8`. Ein alter Wert von 0,80 ohne `yieldMode` gilt deshalb als automatischer Standard. Andere bestehende Faktoren gelten als eigene Gesamtfaktoren und bleiben erhalten. Wer ausdrücklich 0,80 verwenden möchte, kann **Eigener Gesamtfaktor** wählen. Ab jetzt werden diese Auswahl (`yieldMode`) und der optionale Reglertyp (`controllerType`) mitgespeichert und mit exportiert.

Die Erkennung wirkt sofort beim Lesen alter Einstellungen; die Anlage muss nicht neu angelegt werden. Der Prognoseschlüssel trennt das neue automatische PWM-Modell von alten PWM-Vorhersagen. Die unabhängig gemessene MPPT-Erfahrung bleibt nutzbar, wenn nur der ungemessene mobile Kreis geändert wird.

## Messwerte und Lerndaten

Browserprognose und native Verbrauchsauswertung verwenden dieselben Regeln. In geeigneten älteren Messabschnitten mit dokumentierter PWM-Aufstellung wird die geschätzte mobile Einspeisung mit dem neuen Faktor neu berechnet. Dazu wird die Lernalgorithmus-Version auf 1690 angehoben. Ein gespeicherter historischer Wetterstand wird verwendet, wenn vorhanden. Wo die notwendigen Wetter-/Aufstellungsdaten fehlen, wird kein neuer Beitrag erfunden.

Die gemessenen Wh, importierte Tageserträge, Shunt-Bilanz, Roller-Sondernutzungen und Geräte-Layouts werden nicht durch einen PWM-Abschlag verändert. Mobile Energie bleibt ausdrücklich geschätzt. Das Modell ändert keine Ladeparameter am Regler.
