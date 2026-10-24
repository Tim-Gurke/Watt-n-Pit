package de.wattpit.app.core;

import java.time.*;
import java.time.format.*;
import java.util.*;

/** CSV dialect handling and explicit column mapping for local trend imports. */
public final class CsvCodec {
    private CsvCodec() { }
    public static final String[] FIELDS = {"timestamp", "date", "clock", "voltage", "current", "power", "soc",
            "solar", "yieldWh", "ttg", "consumed", "temperature", "aux", "load", "state", "error", "alarm", "kind", "device", "origin", "pvVoltage", "auxRaw", "period"};
    public static final String EXPORT_HEADER = "timestamp;device;kind;origin;voltage;current;power;soc;solar;yieldWh;ttg;consumed;temperature;aux;load;state;error;alarm;pvVoltage;auxRaw;period";

    public static final class Table {
        public final List<String> headers;
        public final List<List<String>> rows;
        public Table(List<String> headers, List<List<String>> rows) { this.headers = headers; this.rows = rows; }
    }

    public static Table parse(String raw) {
        String text = raw.startsWith("\ufeff") ? raw.substring(1) : raw;
        char delimiter = delimiter(text);
        List<List<String>> lines = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (quoted || cell.toString().trim().isEmpty()) quoted = !quoted;
                else cell.append(c);
            } else if (!quoted && (c == delimiter || c == '\n' || c == '\r')) {
                row.add(cell.toString().trim()); cell.setLength(0);
                if (c != delimiter) {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    if (row.stream().anyMatch(v -> !v.isEmpty())) lines.add(row);
                    row = new ArrayList<>();
                    if (lines.size() > 500_000) throw new IllegalArgumentException("Die CSV enthält zu viele Zeilen (maximal 500.000).");
                }
            } else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Die CSV enthält eine nicht geschlossene Anführungszeichen-Zelle.");
        if (cell.length() != 0 || !row.isEmpty()) { row.add(cell.toString().trim()); lines.add(row); }
        if (lines.size() < 2) throw new IllegalArgumentException("Keine CSV mit Kopfzeile und Messwerten gefunden.");
        int header = 0, best = -1;
        for (int i = 0; i < Math.min(lines.size(), 30); i++) {
            int score = headerScore(lines.get(i));
            if (score > best) { best = score; header = i; }
        }
        if (lines.get(header).size() < 2) throw new IllegalArgumentException("CSV-Spalten konnten nicht erkannt werden.");
        return new Table(lines.get(header), lines.subList(header + 1, lines.size()));
    }

    private static char delimiter(String text) {
        String[] lines = text.split("\\r?\\n", 31);
        int[] best = new int[3];
        char[] delimiters = {';', ',', '\t'};
        for (String line : lines) {
            if (!line.matches(".*[A-Za-zÄÖÜäöü].*")) continue;
            boolean quote = false;
            int[] counts = new int[3];
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '"') quote = !quote;
                if (!quote) for (int k = 0; k < 3; k++) if (c == delimiters[k]) counts[k]++;
            }
            for (int k = 0; k < 3; k++) best[k] = Math.max(best[k], counts[k]);
        }
        int index = best[2] > best[0] && best[2] > best[1] ? 2 : best[0] >= best[1] ? 0 : 1;
        return delimiters[index];
    }

    private static int headerScore(List<String> row) {
        if (row.size() < 2) return -1;
        int score = 0;
        for (String item : row) {
            String n = normalize(item);
            if (n.matches(".*(timestamp|datetime|date|time|datum|zeit).*")) score += 8;
            if (n.matches(".*(voltage|current|power|soc|spannung|strom|leistung|yield|ertrag|charge).*")) score += 3;
        }
        return score;
    }

    public static Map<String, Integer> guess(List<String> headers) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String n = normalize(headers.get(i));
            String field = null;
            for (String exact : FIELDS) if (n.equals(exact.toLowerCase(Locale.ROOT))) field = exact;
            if (field == null) {
                if (n.contains("timestamp") || n.contains("datetime") || n.contains("date and time") || n.contains("datum und zeit")) field = "timestamp";
                else if (n.matches("(date|datum)( .*)?")) field = "date";
                else if (n.matches("(time|zeit|uhrzeit)( .*)?")) field = "clock";
                else if (n.matches(".*(state of charge|ladezustand|soc).*")) field = "soc";
                else if (n.matches(".*(yield|ertrag).*")) field = "yieldWh";
                else if (n.matches(".*(time to go|restzeit|restlaufzeit|ttg).*")) field = "ttg";
                else if (n.matches(".*(consumed|entnommen|verbrauchte ah).*")) field = "consumed";
                else if (n.matches(".*(temperature|temperatur).*")) field = "temperature";
                else if (n.matches(".*(load current|laststrom).*")) field = "load";
                else if (n.matches(".*(aux voltage|auxiliary voltage|zusatzspannung|mid voltage).*")) field = "aux";
                else if (n.matches(".*(solar|pv|panel).*(power|leistung).*")) field = "solar";
                else if (!n.matches(".*(solar|pv|panel).*")) {
                    if (n.matches(".*(voltage|spannung).*")) field = "voltage";
                    else if (n.matches(".*(current|strom).*")) field = "current";
                    else if (n.matches(".*(power|leistung).*")) field = "power";
                    else if (n.matches(".*(charger state|device state|ladephase|ladestatus).*")) field = "state";
                }
            }
            if (field != null && !map.containsKey(field)) map.put(field, i);
        }
        // "Time" in many Victron exports includes the complete date/time.
        if (!map.containsKey("timestamp") && !map.containsKey("date") && map.containsKey("clock"))
            map.put("timestamp", map.remove("clock"));
        return map;
    }

    public static Reading reading(Table table, List<String> row, Map<String, Integer> map, String defaultKind, ZoneId zone) {
        String time = cell(row, map, "timestamp");
        if (time.isEmpty()) time = (cell(row, map, "date") + " " + cell(row, map, "clock")).trim();
        Reading r = new Reading();
        r.timestamp = parseTime(time, zone);
        String rowKind = cell(row, map, "kind");
        r.kind = rowKind.isEmpty() ? defaultKind : rowKind;
        if (!r.kind.equals("shunt") && !r.kind.equals("solar")) throw new IllegalArgumentException("Unbekannter Gerätetyp.");
        String rowDevice = cell(row, map, "device");
        r.device = rowDevice.isEmpty() ? "csv:" + r.kind : rowDevice;
        String origin = cell(row, map, "origin");
        r.origin = origin.equals("live") ? "live" : origin.equals("archive")?"archive":"csv";
        r.voltage = number(table, row, map, "voltage");
        r.current = number(table, row, map, "current");
        r.power = number(table, row, map, "power");
        r.soc = number(table, row, map, "soc");
        r.solar = number(table, row, map, "solar");
        r.yieldWh = number(table, row, map, "yieldWh");
        r.ttg = number(table, row, map, "ttg");
        r.consumed = number(table, row, map, "consumed");
        r.temperature = number(table, row, map, "temperature");
        r.aux = number(table, row, map, "aux");
        r.load = number(table, row, map, "load");
        r.state = integer(table, row, map, "state");
        r.error = integer(table, row, map, "error");
        r.alarm = integer(table, row, map, "alarm");
        r.pvVoltage=number(table,row,map,"pvVoltage");r.auxRaw=number(table,row,map,"auxRaw");
        Integer period=integer(table,row,map,"period");r.period=period==null?0:Math.max(0,Math.min(86400,period));
        boolean unavailableCurrent = r.discardUnavailableCurrent();
        if (!unavailableCurrent) r.calculatePower();
        if (r.soc != null && (r.soc < 0 || r.soc > 100)) throw new IllegalArgumentException("SOC muss zwischen 0 und 100 liegen.");
        if (!unavailableCurrent && r.voltage == null && r.current == null && r.power == null
                && r.soc == null && r.solar == null && r.yieldWh == null && r.ttg == null
                && r.consumed == null && r.temperature == null && r.pvVoltage == null
                && r.auxRaw == null && r.aux == null && r.load == null && r.state == null
                && r.error == null && r.alarm == null)
            throw new IllegalArgumentException("Kein zugeordneter Messwert in dieser Zeile.");
        return r;
    }

    private static String cell(List<String> row, Map<String, Integer> map, String field) {
        int index = map.getOrDefault(field, -1);
        return index >= 0 && index < row.size() ? row.get(index).trim() : "";
    }
    private static Double number(Table table, List<String> row, Map<String, Integer> map, String field) {
        String v = cell(row, map, field);
        if (v.isEmpty() || v.matches("(?i)(n/?a|null|nan|--?|–|—)")) return null;
        v = v.replace("\u00a0", "").replace(" ", "").replace(",", ".");
        double out;
        try { out = Double.parseDouble(v); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Ungültiger Zahlenwert in " + field + ": " + v); }
        if (!Double.isFinite(out)) return null;
        String header = normalize(table.headers.get(map.get(field)));
        if ((field.equals("power") || field.equals("solar")) && header.matches(".*\\bkw\\b.*")) out *= 1000;
        if (field.equals("yieldWh") && header.matches(".*\\bkwh\\b.*")) out *= 1000;
        if ((field.equals("current") || field.equals("load")) && header.matches(".*\\bma\\b.*")) out /= 1000;
        if (field.equals("voltage") && header.matches(".*\\bmv\\b.*")) out /= 1000;
        if (field.equals("ttg") && header.matches(".*\\b(h|hours|stunden)\\b.*")) out *= 60;
        if (field.equals("ttg") && header.matches(".*\\b(s|seconds|sekunden)\\b.*")) out /= 60;
        return out;
    }
    private static Integer integer(Table table, List<String> row, Map<String, Integer> map, String field) {
        Double value = number(table, row, map, field);
        return value == null ? null : value.intValue();
    }
    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("[\\[\\]()]", " ").replaceAll("\\s+", " ").trim();
    }

    public static long parseTime(String input, ZoneId zone) {
        String text = input.trim();
        if (text.matches("[0-9]{10,13}")) {
            long value = Long.parseLong(text);
            return text.length() <= 10 ? value * 1000 : value;
        }
        try { return Instant.parse(text).toEpochMilli(); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(text).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) { }
        for (String pattern : new String[]{"uuuu-MM-dd'T'HH:mm:ss[.SSS]", "uuuu-MM-dd'T'HH:mm", "uuuu-MM-dd HH:mm:ss[.SSS]",
                "uuuu-MM-dd HH:mm", "dd.MM.uuuu HH:mm:ss[.SSS]", "dd.MM.uuuu HH:mm", "dd/MM/uuuu HH:mm:ss", "dd/MM/uuuu HH:mm"}) {
            try { return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)).atZone(zone).toInstant().toEpochMilli(); }
            catch (DateTimeParseException ignored) { }
        }
        try { return LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli(); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Datum/Zeit nicht erkannt: " + text); }
    }

    public static String exportRow(Reading r) {
        Object[] values = {Instant.ofEpochMilli(r.timestamp).toString(), r.device, r.kind, r.origin,
                r.voltage, r.current, r.power, r.soc, r.solar, r.yieldWh, r.ttg, r.consumed,
                r.temperature, r.aux, r.load, r.state, r.error, r.alarm, r.pvVoltage, r.auxRaw, r.period};
        List<String> cells = new ArrayList<>();
        for (Object value : values) {
            String s = value == null ? "" : value.toString();
            if (s.matches("^[=+@].*") && value instanceof String) s = "'" + s;
            if (s.contains(";") || s.contains("\n") || s.contains("\r") || s.contains("\"")) s = "\"" + s.replace("\"", "\"\"") + "\"";
            cells.add(s);
        }
        return String.join(";", cells);
    }
}
