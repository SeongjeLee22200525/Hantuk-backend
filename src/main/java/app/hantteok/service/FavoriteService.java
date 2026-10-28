package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Shared group/store favorite toggles. */
@Service
@SuppressWarnings("unchecked")
public class FavoriteService {

    Map<String, Object> toggleFavorite(
            Map<String, Object> state,
            String actor,
            Map<String, Object> data,
            String table,
            String field,
            String idField) {
        String id = text(data, idField, 80, true);
        find(state, table, id);
        var favorites =
                (List<String>)
                        ((Map<String, Object>) state.get(field))
                                .computeIfAbsent(actor, k -> new ArrayList<>());
        boolean added = !favorites.remove(id);
        if (added) {
            favorites.add(id);
        }
        return map("added", added);
    }
}
