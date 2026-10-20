# Watt’n’Pit 1.3.6 direkt auf GitHub veröffentlichen

Die APK ist bereits gebaut und mit Tests, Lint und Browserprüfungen bestätigt. Dieser Ablauf lädt exakt diese APK, kontrolliert Quelle, Version und SHA-256 und signiert sie mit der bisherigen Updatesignatur. Er erzeugt keinen neuen Schlüssel.

## Einmalig die private Sicherung einrichten

1. Die Datei **WattPit-Signatur-privat.zip** aus den eigenen ChatGPT-Dateien herunterladen.
2. [Die lokale Umwandlungshilfe herunterladen](https://github.com/Tim-Gurke/Watt-n-Pit/raw/refs/heads/main/tools/signing-backup-base64.html), als HTML-Datei im Browser öffnen und dort die ZIP-Datei wählen. **Base64 kopieren** drücken. Die Umwandlung läuft lokal ohne Netzwerk; die Sicherung wird dabei nicht hochgeladen.
3. Auf GitHub unter **Settings → Secrets and variables → Actions → New repository secret** ein Secret namens **WATTPIT_SIGNING_BACKUP_BASE64** anlegen und den kopierten Text als Wert einfügen.

Die Sicherung enthält private Signierungsdaten. ZIP, Passwort und Base64-Text gehören ausschließlich in die private Sicherung beziehungsweise das geschützte Secret, niemals in öffentliche Repository-Dateien, Kommentare oder Screenshots.

## Veröffentlichen

Unter **Actions → Watt’n’Pit 1.3.6 signieren und veröffentlichen → Run workflow** den Branch **main** wählen. Die Auswahl zum Veröffentlichen eingeschaltet lassen und starten.

Ohne eingeschaltete Veröffentlichung wird nur die bereits gebaute APK erneut verifiziert. Das prüft keinen privaten Schlüssel.

Der Veröffentlichungslauf entschlüsselt die private Sicherung nur im temporären Runner-Verzeichnis, prüft vor jeder Veröffentlichung den bisherigen Zertifikatfingerabdruck und löscht die temporäre Schlüsseldatei nach dem Signieren. Der Passworteinsatz erfolgt über eine maskierte Umgebungsvariable. Die Sicherung wird nicht als Build-Artefakt gespeichert.

Der Quellstand wird erst nach erfolgreicher Signierung nach main übernommen. Danach werden APK und Prüfsumme als v1.3.6 veröffentlicht und nur die drei neuesten Releases behalten. Der bisherige Quellstand 1.3.5 bleibt bis dahin die Hauptversion.

Voraussetzungen: Schreibrechte auf das Repository, aktivierte GitHub Actions und die originale, passende Signierungssicherung. Geschützte Hauptbranches können eine zusätzliche Freigabe durch den Repository-Eigentümer verlangen.
