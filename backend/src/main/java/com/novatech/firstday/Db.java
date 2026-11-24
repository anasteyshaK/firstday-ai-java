package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Db {
    final JdbcTemplate jdbc;
    final ObjectMapper json;
    public Db(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs,n) -> {
            Map<String,Object> row=new LinkedHashMap<>();
            var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {
                Object value=rs.getObject(i);
                if(value instanceof Timestamp time) value=time.toInstant().toString();
                else if(value instanceof java.sql.Date date) value=date.toLocalDate().toString();
                else if(value instanceof UUID id) value=id.toString();
                if("jsonb".equals(meta.getColumnTypeName(i)) && value!=null) {
                    try { value=json.readValue(value.toString(),Object.class); }
                    catch(Exception ex) { throw new IllegalStateException("Invalid stored JSON"); }
                }
                row.put(meta.getColumnLabel(i),value);
            }
            return row;
        }, args);
    }
    public Map<String,Object> one(String sql, Object... args) {
        var rows=rows(sql,args); if(rows.isEmpty()) throw ApiException.missing("Record not found"); return rows.getFirst();
    }
    public Map<String,Object> optional(String sql,Object... args) { var rows=rows(sql,args);return rows.isEmpty()?null:rows.getFirst(); }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public String json(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) {throw new IllegalStateException("JSON serialization failed");} }
    public static UUID id(Object value) { return value==null?null:UUID.fromString(value.toString()); }
    public static String text(Map<String,?> value,String key) { Object item=value.get(key);return item==null?"":item.toString(); }
    public static int count(Object value) { return ((Number)value).intValue(); }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> row=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2) row.put(pairs[i].toString(),pairs[i+1]);return row;
    }
    public static int progress(long done,long total) { return total==0?0:(int)Math.rint(100.0*done/total); }
}
