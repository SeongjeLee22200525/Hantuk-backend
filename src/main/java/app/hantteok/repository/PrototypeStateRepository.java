package app.hantteok.repository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** SQL for the persisted v2 state and idempotent command results. */
@Repository
public class PrototypeStateRepository {
    private final DatabaseRepository database;

    public PrototypeStateRepository(DatabaseRepository database) {
        this.database = database;
    }

    public Map<String, Object> loadState() {
        return database.one("SELECT * FROM prototype_state WHERE id=1");
    }

    public Map<String, Object> lockState() {
        return database.one("SELECT * FROM prototype_state WHERE id=1 FOR UPDATE");
    }

    public List<Map<String, Object>> findCommand(String userId, String commandId) {
        return database.all(
                "SELECT * FROM prototype_commands WHERE user_id=? AND command_id=?",
                userId,
                commandId);
    }

    public void saveState(long revision, String document) {
        database.exec(
                "UPDATE prototype_state SET revision=?,document=? WHERE id=1", revision, document);
    }

    public void saveCommand(String userId, String commandId, String fingerprint, String result) {
        database.exec(
                "INSERT INTO"
                    + " prototype_commands(user_id,command_id,fingerprint,result_json,created_at)"
                    + " VALUES(?,?,?,?,?)",
                userId,
                commandId,
                fingerprint,
                result,
                database.time());
    }
}
