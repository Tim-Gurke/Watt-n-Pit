package de.wattpit.app;

/** Rolling calendar years, including leap days. Raw measurements have their own retention. */
final class LearningWindow {
    static long since(long now){return java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).minusYears(3).toInstant().toEpochMilli();}
    private LearningWindow(){}
}
