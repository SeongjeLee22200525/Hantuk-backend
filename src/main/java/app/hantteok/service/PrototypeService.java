package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.common.security.TokenHash;
import app.hantteok.repository.PrototypeStateRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Coordinates the state lock, idempotency, domain command, and persistence in one transaction. */
@Service
public class PrototypeService {
    private final PrototypeStateRepository repository;
    private final ObjectMapper json;
    private final GroupService groups;
    private final StoreService stores;
    private final DonationService donations;
    private final UsageService usages;
    private final FavoriteService favorites;
    private final SnapshotService snapshots;

    public PrototypeService(
            PrototypeStateRepository repository,
            ObjectMapper json,
            GroupService groups,
            StoreService stores,
            DonationService donations,
            UsageService usages,
            FavoriteService favorites,
            SnapshotService snapshots) {
        this.repository = repository;
        this.json = json;
        this.groups = groups;
        this.stores = stores;
        this.donations = donations;
        this.usages = usages;
        this.favorites = favorites;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(String actor) {
        var record = repository.loadState();
        return snapshots.projectSnapshot(
                decode(record.get("document")), actor, number(record, "revision"));
    }

    @Transactional
    public Map<String, Object> command(
            String actor, String commandId, String action, Map<String, Object> data) {
        if (commandId == null || !commandId.matches("[A-Za-z0-9-]{8,80}") || data == null) {
            throw ApiException.bad("명령 식별자와 입력값을 확인해 주세요.");
        }
        var record = repository.lockState();
        var state = decode(record.get("document"));
        String fingerprint;
        try {
            fingerprint =
                    TokenHash.digest(
                            action
                                    + "\n"
                                    + json.writer()
                                            .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                                            .writeValueAsString(data));
        } catch (Exception e) {
            throw ApiException.bad("입력 형식을 확인해 주세요.");
        }
        var past = repository.findCommand(actor, commandId);
        if (!past.isEmpty()) {
            require(
                    fingerprint.equals(str(past.get(0), "fingerprint")),
                    "같은 명령 ID로 다른 내용을 보낼 수 없습니다.");
            return map(
                    "result",
                    decode(past.get(0).get("result_json")),
                    "snapshot",
                    snapshots.projectSnapshot(state, actor, number(record, "revision")));
        }
        Map<String, Object> result = execute(state, actor, action, data);
        long revision = number(record, "revision") + 1;
        repository.saveState(revision, encode(state));
        repository.saveCommand(actor, commandId, fingerprint, encode(result));
        return map("result", result, "snapshot", snapshots.projectSnapshot(state, actor, revision));
    }

    private Map<String, Object> execute(
            Map<String, Object> state, String actor, String action, Map<String, Object> data) {
        return switch (action) {
            case "group.join" -> groups.joinGroup(state, actor, data, false);
            case "group.invite" -> groups.joinGroup(state, actor, data, true);
            case "group.approve" -> groups.approveGroup(state, actor, data);
            case "group.favorite" ->
                    favorites.toggleFavorite(
                            state, actor, data, "groups", "groupFavorites", "groupId");
            case "store.favorite" ->
                    favorites.toggleFavorite(
                            state, actor, data, "stores", "storeFavorites", "storeId");
            case "store.apply" -> stores.applyStore(state, actor, data);
            case "store.approve" -> stores.reviewApplication(state, actor, data, true);
            case "store.reject" -> stores.reviewApplication(state, actor, data, false);
            case "store.thumbnail" -> stores.updateThumbnail(state, actor, data);
            case "store.menu.save" -> stores.updateMenu(state, actor, data, false);
            case "store.menu.remove" -> stores.updateMenu(state, actor, data, true);
            case "donation.complete" -> donations.completeDonation(state, actor, data);
            case "usage.begin" -> usages.beginUsage(state, actor, data);
            case "usage.find" -> usages.findUsage(state, actor, data);
            case "usage.amount" -> usages.requestAmount(state, actor, data);
            case "usage.approve" -> usages.approveUsage(state, actor, data);
            case "usage.cancel" -> usages.cancelRequest(state, actor, data);
            case "usage.reverse" -> usages.reverseUsage(state, actor, data);
            case "usage.thanks" -> usages.sendThanks(state, actor, data);
            default -> throw ApiException.bad("지원하지 않는 명령입니다.");
        };
    }

    private String encode(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> decode(Object o) {
        try {
            return json.readValue(o.toString(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
