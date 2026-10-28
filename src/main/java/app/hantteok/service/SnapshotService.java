package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.repository.DatabaseRepository.str;

import app.hantteok.repository.DatabaseRepository;

import org.springframework.stereotype.Service;

import java.util.*;

/** Applies account visibility rules before returning state to the frontend. */
@Service
public class SnapshotService {
    private final DatabaseRepository database;
    private final PrototypeAuthService auth;

    public SnapshotService(DatabaseRepository database, PrototypeAuthService auth) {
        this.database = database;
        this.auth = auth;
    }

    Map<String, Object> projectSnapshot(Map<String, Object> state, String actor, long revision) {
        var groups = publicGroups(state);
        var requests = visibleRequests(state, actor);
        boolean administrator = auth.admin(actor);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("account", auth.profile(actor));
        snapshot.put("isAdmin", administrator);
        snapshot.put("revision", revision);
        snapshot.put("groups", groups);
        snapshot.put("members", visibleMemberships(state, actor, groups, requests));
        snapshot.put("groupApplications", visibleGroupApplications(state, actor));
        snapshot.put("stores", rows(state, "stores"));
        snapshot.put("storeApplications", visibleStoreApplications(state, actor, administrator));
        snapshot.put("offers", visibleOffers(state, actor));
        snapshot.put("donations", visibleDonations(state, actor));
        snapshot.put("requests", requests);
        snapshot.put("receipts", visibleReceipts(state, actor));
        snapshot.put("groupFavorites", ownFavorites(state, "groupFavorites", actor));
        snapshot.put("storeFavorites", ownFavorites(state, "storeFavorites", actor));
        snapshot.put("serverTime", database.now().toString());
        return snapshot;
    }

    private List<Map<String, Object>> publicGroups(Map<String, Object> state) {
        return rows(state, "groups").stream()
                .map(
                        group -> {
                            Map<String, Object> visible = new LinkedHashMap<>(group);
                            visible.remove("inviteHash");
                            return visible;
                        })
                .toList();
    }

    private List<Map<String, Object>> visibleRequests(Map<String, Object> state, String actor) {
        return rows(state, "requests").stream()
                .filter(
                        request ->
                                actor.equals(request.get("userId"))
                                        || actor.equals(request.get("seenBy")))
                .toList();
    }

    private Map<String, Object> visibleMemberships(
            Map<String, Object> state,
            String actor,
            List<Map<String, Object>> groups,
            List<Map<String, Object>> requests) {
        Map<String, Object> memberships = new LinkedHashMap<>();
        for (var group : groups) {
            String groupId = str(group, "id");
            var visibleUsers = new ArrayList<String>();
            if (member(state, groupId, actor)) {
                visibleUsers.add(actor);
            }
            // A cashier may only see eligibility for recipients whose QR was scanned.
            for (var request : requests) {
                String recipient = str(request, "userId");
                if (same(request.get("groupId"), group.get("id"))
                        && member(state, groupId, recipient)
                        && !visibleUsers.contains(recipient)) {
                    visibleUsers.add(recipient);
                }
            }
            memberships.put(groupId, visibleUsers);
        }
        return memberships;
    }

    private List<Map<String, Object>> visibleGroupApplications(
            Map<String, Object> state, String actor) {
        return rows(state, "groupApplications").stream()
                .filter(
                        application ->
                                actor.equals(application.get("userId"))
                                        || actor.equals(
                                                find(state, "groups", str(application, "groupId"))
                                                        .get("ownerId")))
                .toList();
    }

    private List<Map<String, Object>> visibleStoreApplications(
            Map<String, Object> state, String actor, boolean administrator) {
        return rows(state, "storeApplications").stream()
                .filter(application -> administrator || actor.equals(application.get("ownerId")))
                .toList();
    }

    private List<Map<String, Object>> visibleOffers(Map<String, Object> state, String actor) {
        return rows(state, "offers").stream()
                .filter(
                        offer ->
                                member(state, str(offer, "groupId"), actor)
                                        || actor.equals(offer.get("donorId"))
                                        || owner(state, str(offer, "storeId"), actor))
                .toList();
    }

    private List<Map<String, Object>> visibleDonations(Map<String, Object> state, String actor) {
        return rows(state, "donations").stream()
                .filter(
                        donation ->
                                actor.equals(donation.get("donorId"))
                                        || actor.equals(
                                                find(state, "groups", str(donation, "groupId"))
                                                        .get("ownerId"))
                                        || (donation.get("storeId") != null
                                                && owner(state, str(donation, "storeId"), actor)))
                .toList();
    }

    private List<Map<String, Object>> visibleReceipts(Map<String, Object> state, String actor) {
        return rows(state, "receipts").stream()
                .filter(
                        receipt ->
                                actor.equals(receipt.get("userId"))
                                        || actor.equals(receipt.get("donorId"))
                                        || owner(state, str(receipt, "storeId"), actor))
                .map(
                        receipt -> {
                            Map<String, Object> visible = new LinkedHashMap<>(receipt);
                            // Store owners can inspect the transaction, but never private thanks.
                            if (!actor.equals(receipt.get("userId"))
                                    && !actor.equals(receipt.get("donorId"))) {
                                visible.put("thanks", null);
                            }
                            return visible;
                        })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Object ownFavorites(Map<String, Object> state, String field, String actor) {
        return ((Map<String, Object>) state.get(field)).getOrDefault(actor, List.of());
    }
}
