package de.wattpit.app.core;

/** Additional guard for optional copied diagnostics; raw packets are never recorded. */
public final class ArchiveDiagnostic {
    private ArchiveDiagnostic(){}
    public static String clean(String text){
        return (text==null?"Unbekannter Fehler":text).replaceAll("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}","[Geräteadresse]")
                .replaceAll("(?i)\\b[0-9a-f]{32,}\\b","[Schlüssel]")
                .replaceAll("\\b[0-9]{6}\\b","[Code]");
    }
}
