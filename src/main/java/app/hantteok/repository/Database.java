package app.hantteok.repository;

import app.hantteok.common.error.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL stays parameterized. Row maps are internal; controllers return explicit projections. */
@Repository
public class Database {
    final JdbcTemplate jdbc;
    final Clock clock;
    Database(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public Instant now() { return clock.instant(); }
    public Timestamp time() { return Timestamp.from(now()); }
    public static Timestamp at(Instant value) { return Timestamp.from(value); }
    public static String id(String prefix) { return prefix + UUID.randomUUID(); }
    public List<Map<String,Object>> all(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = all(sql, args);
        if (rows.isEmpty()) throw ApiException.missing();
        return rows.get(0);
    }
    public long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    public int exec(String sql, Object... args) { return jdbc.update(sql, args); }
    public void lockUser(String id) { one("SELECT id FROM app_users WHERE id=? FOR UPDATE", id); }
    public Map<String,Object> lockBenefit(String id) { return one("SELECT * FROM benefits WHERE id=? FOR UPDATE", id); }
    public void ownShop(String user, String shop) {
        if (count("SELECT COUNT(*) FROM shops WHERE id=? AND owner_id=?", shop, user) == 0) throw ApiException.forbidden();
    }
    public static String str(Map<String,Object> row, String key) { return Objects.toString(row.get(key), ""); }
    public static long num(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    public static Instant instant(Map<String,Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof OffsetDateTime t) return t.toInstant();
        if (v instanceof Timestamp t) return t.toInstant();
        return (Instant)v;
    }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i], pairs[i+1]);
        return result;
    }
    public void notice(String user, String title, String body, String target, String setting) {
        String column = switch(setting) { case "refund" -> "refund_alert"; case "exhausted" -> "exhausted_alert"; default -> "usage_alert"; };
        if (Boolean.TRUE.equals(one("SELECT " + column + " FROM app_users WHERE id=?", user).get(column)))
            exec("INSERT INTO notices(id,user_id,title,body,target_id,created_at) VALUES(?,?,?,?,?,?)", id("N"), user, title, body, target, time());
    }
}
