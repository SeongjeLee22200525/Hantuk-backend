package app.hantteok.common.state;

import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;

import java.util.*;

/** Access helpers for the existing v2 JSON schema; no database writes or request state. */
@SuppressWarnings("unchecked")
public final class StateData {
    public static final long MAX_AMOUNT = 9007199254740991L;

    private StateData() {}

    public static List<Map<String, Object>> rows(Map<String, Object> state, String field) {
        return (List<Map<String, Object>>) state.get(field);
    }

    public static Map<String, Object> object(Map<String, Object> record, String field) {
        if (!(record.get(field) instanceof Map)) {
            throw ApiException.bad(field + " 형식을 확인해 주세요.");
        }
        return (Map<String, Object>) record.get(field);
    }

    public static Map<String, Object> find(Map<String, Object> state, String table, String id) {
        return rows(state, table).stream()
                .filter(record -> id.equals(str(record, "id")))
                .findFirst()
                .orElseThrow(ApiException::missing);
    }

    public static boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }

    public static List<String> members(Map<String, Object> state, String groupId) {
        return (List<String>)
                ((Map<String, Object>) state.get("members"))
                        .computeIfAbsent(groupId, k -> new ArrayList<>());
    }

    public static boolean member(Map<String, Object> state, String groupId, String user) {
        return members(state, groupId).contains(user);
    }

    public static boolean owner(Map<String, Object> state, String storeId, String actor) {
        return actor.equals(str(find(state, "stores", storeId), "ownerId"));
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.conflict(message);
        }
    }

    public static long amount(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() < 1
                || ((Number) value).longValue() > MAX_AMOUNT) {
            throw ApiException.bad("금액은 1원 이상의 정수로 입력해 주세요.");
        }
        return ((Number) value).longValue();
    }

    public static long number(Map<String, Object> data, String field) {
        return ((Number) data.get(field)).longValue();
    }

    public static String normalized(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public static Map<String, Object> owned(
            Map<String, Object> state, String actor, Map<String, Object> d) {
        var store = find(state, "stores", text(d, "storeId", 100, true));
        if (!actor.equals(store.get("ownerId"))) {
            throw ApiException.forbidden();
        }
        return store;
    }
}
