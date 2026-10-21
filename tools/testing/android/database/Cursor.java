package android.database;

/** Minimal JVM storage-test surface; avoids initializing Android's ContentResolver. */
public interface Cursor extends AutoCloseable {
    boolean moveToFirst();
    boolean moveToNext();
    boolean isNull(int column);
    long getLong(int column);
    int getInt(int column);
    double getDouble(int column);
    String getString(int column);
    int getColumnIndexOrThrow(String name);
    void close();
}
