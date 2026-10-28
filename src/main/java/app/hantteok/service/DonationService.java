package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Donation checkout validation and prepaid offer creation. */
@Service
@SuppressWarnings("unchecked")
public class DonationService {
    private final DatabaseRepository db;
    private final PrototypeAuthService auth;

    public DonationService(DatabaseRepository db, PrototypeAuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    Map<String, Object> completeDonation(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        String checkout = text(data, "checkoutId", 100, true),
                type = text(data, "type", 20, true),
                group = text(data, "groupId", 80, true);
        if (!Set.of("money", "prepaid").contains(type)) {
            throw ApiException.bad("후원 유형을 확인해 주세요.");
        }
        find(state, "groups", group);
        long amount = amount(data, "amount");
        String store = text(data, "storeId", 100, false),
                deadline = text(data, "expiresOn", 10, false);
        for (var old : rows(state, "donations")) {
            if (actor.equals(old.get("donorId")) && checkout.equals(old.get("checkoutId"))) {
                require(
                        type.equals(old.get("type"))
                                && group.equals(old.get("groupId"))
                                && amount == number(old, "amount")
                                && store.equals(str(old, "storeId"))
                                && deadline.equals(str(old, "expiresOn")),
                        "이미 완료한 결제 정보가 다릅니다.");
                return map("id", old.get("id"));
            }
        }
        if (type.equals("prepaid")) {
            find(state, "stores", store);
            require(!date(deadline).isBefore(auth.today()), "오늘 이후의 사용 기한을 입력해 주세요.");
        } else if (!store.isEmpty() || !deadline.isEmpty()) {
            throw ApiException.bad("금액 후원에는 가게와 사용 기한을 지정하지 않습니다.");
        }
        String id = id("donation-");
        String name = str(auth.profile(actor), "name");
        rows(state, "donations")
                .add(
                        map(
                                "id",
                                id,
                                "checkoutId",
                                checkout,
                                "type",
                                type,
                                "groupId",
                                group,
                                "donorId",
                                actor,
                                "donorName",
                                name,
                                "amount",
                                amount,
                                "createdAt",
                                db.now().toString(),
                                "storeId",
                                store.isEmpty() ? null : store,
                                "expiresOn",
                                deadline.isEmpty() ? null : deadline));
        if (type.equals("prepaid")) {
            rows(state, "offers")
                    .add(
                            map(
                                    "id",
                                    "prepaid-" + id,
                                    "storeId",
                                    store,
                                    "groupId",
                                    group,
                                    "storeName",
                                    find(state, "stores", store).get("name"),
                                    "totalAmount",
                                    amount,
                                    "usedAmount",
                                    0,
                                    "startsOn",
                                    auth.today().toString(),
                                    "expiresOn",
                                    deadline,
                                    "donorId",
                                    actor,
                                    "donorName",
                                    name));
        }
        return map("id", id);
    }
}
