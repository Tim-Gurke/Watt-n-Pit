package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

/** JVM test adapter only: execute the actual HistoryDb SQL against real SQLite. */
public final class SQLiteDatabase implements AutoCloseable {
    public interface CursorFactory { }
    public static final int CONFLICT_IGNORE=4,CONFLICT_REPLACE=5;
    private final Connection connection;
    private boolean successful;
    SQLiteDatabase(String path){try{connection=DriverManager.getConnection("jdbc:sqlite:"+path);}catch(SQLException e){throw new IllegalStateException(e);}}
    public void beginTransaction(){try{if(!connection.getAutoCommit())throw new IllegalStateException("Nested test transaction");connection.setAutoCommit(false);successful=false;}catch(SQLException e){throw new IllegalStateException(e);}}
    public void setTransactionSuccessful(){successful=true;}
    public void endTransaction(){try{if(successful)connection.commit();else connection.rollback();connection.setAutoCommit(true);}catch(SQLException e){throw new IllegalStateException(e);}}
    private PreparedStatement statement(String sql,Object[] args)throws SQLException{
        PreparedStatement q=connection.prepareStatement(sql);if(args!=null)for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);return q;
    }
    public void execSQL(String sql){execSQL(sql,null);}
    public void execSQL(String sql,Object[] args){try(PreparedStatement q=statement(sql,args)){q.execute();}catch(SQLException e){throw new IllegalStateException(e);}}
    public Cursor rawQuery(String sql,String[] args){
        try{
            PreparedStatement q=statement(sql,args);ResultSet r=q.executeQuery();
            return (Cursor)Proxy.newProxyInstance(Cursor.class.getClassLoader(),new Class<?>[]{Cursor.class},(proxy,method,a)->{
                switch(method.getName()){
                    case "moveToFirst":case "moveToNext":return r.next();
                    case "isNull":return r.getObject((int)a[0]+1)==null;
                    case "getLong":return r.getLong((int)a[0]+1);
                    case "getInt":return r.getInt((int)a[0]+1);
                    case "getDouble":return r.getDouble((int)a[0]+1);
                    case "getString":return r.getString((int)a[0]+1);
                    case "getColumnIndexOrThrow":return r.findColumn((String)a[0])-1;
                    case "close":r.close();q.close();return null;
                    default:throw new UnsupportedOperationException(method.getName());
                }
            });
        }catch(SQLException e){throw new IllegalStateException(e);}
    }
    private int change(String sql,Object[] args){try(PreparedStatement q=statement(sql,args)){return q.executeUpdate();}catch(SQLException e){throw new IllegalStateException(e);}}
    public long insertOrThrow(String table,String nullable,ContentValues values){return insertWithOnConflict(table,nullable,values,0);}
    public long insertWithOnConflict(String table,String nullable,ContentValues values,int conflict){
        List<String> columns=new ArrayList<>(values.keySet());List<Object> args=new ArrayList<>();for(String column:columns)args.add(values.get(column));
        String prefix=conflict==CONFLICT_IGNORE?"INSERT OR IGNORE":conflict==CONFLICT_REPLACE?"INSERT OR REPLACE":"INSERT";
        return change(prefix+" INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",args.toArray())==0?-1:1;
    }
    public int update(String table,ContentValues values,String where,String[] params){
        List<String> columns=new ArrayList<>();List<Object> args=new ArrayList<>();for(String column:values.keySet()){columns.add(column+"=?");args.add(values.get(column));}if(params!=null)args.addAll(Arrays.asList(params));
        return change("UPDATE "+table+" SET "+String.join(",",columns)+" WHERE "+where,args.toArray());
    }
    public int delete(String table,String where,String[] args){return change("DELETE FROM "+table+" WHERE "+where,args);}
    public void close(){try{connection.close();}catch(SQLException e){throw new IllegalStateException(e);}}
}
