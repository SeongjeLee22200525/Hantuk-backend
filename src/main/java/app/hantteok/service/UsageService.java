package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** QR lifecycle, recipient approvals, reversals, and private thanks. */
@Service
@SuppressWarnings("unchecked")
public class UsageService {
    private final DatabaseRepository db;
    private final PrototypeAuthService auth;
    private final ThanksPhotoService photos;
    private static final Duration QR_LIFETIME = Duration.ofMinutes(10);
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    public UsageService(
            DatabaseRepository db, PrototypeAuthService auth, ThanksPhotoService photos) {
        this.db = db;
        this.auth = auth;
        this.photos = photos;
    }

    private boolean available(Map<String, Object> state, Map<String, Object> offer, String user) {
        var today = auth.today();
        return member(state, str(offer, "groupId"), user)
                && number(offer, "usedAmount") < number(offer, "totalAmount")
                && !today.isBefore(date(str(offer, "startsOn")))
                && !today.isAfter(date(str(offer, "expiresOn")));
    }

    private Map<String, Object> liveOffer(Map<String, Object> state, Map<String, Object> request) {
        require(
                !Set.of("completed", "cancelled", "paymentCancelled")
                                .contains(str(request, "status"))
                        && Instant.parse(str(request, "expiresAt")).isAfter(db.now()),
                "만료되었거나 처리된 이용 코드입니다.");
        var offer = find(state, "offers", str(request, "offerId"));
        require(available(state, offer, str(request, "userId")), "소속·잔액·사용 기한을 다시 확인해 주세요.");
        return offer;
    }

    Map<String, Object> beginUsage(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var offer = find(state, "offers", text(data, "offerId", 100, true));
        require(available(state, offer, actor), "현재 소속과 선결제 잔액·기한을 확인해 주세요.");
        for (var request : rows(state, "requests")) {
            if (actor.equals(request.get("userId"))
                    && same(request.get("offerId"), offer.get("id"))
                    && Set.of("awaitingStore", "awaitingUser").contains(str(request, "status"))
                    && Instant.parse(str(request, "expiresAt")).isAfter(db.now())) {
                return map("id", request.get("id"));
            }
        }
        String code = null;
        for (int i = 0; i < 1000; i++) {
            String candidate = String.valueOf(100000 + random.nextInt(900000));
            if (rows(state, "requests").stream()
                    .noneMatch(request -> candidate.equals(request.get("code")))) {
                code = candidate;
                break;
            }
        }
        require(code != null, "이용 코드를 다시 발급해 주세요.");
        String id = id("usage-");
        rows(state, "requests")
                .add(
                        map(
                                "id",
                                id,
                                "code",
                                code,
                                "offerId",
                                offer.get("id"),
                                "storeId",
                                offer.get("storeId"),
                                "groupId",
                                offer.get("groupId"),
                                "userId",
                                actor,
                                "userName",
                                auth.profile(actor).get("name"),
                                "createdAt",
                                db.now().toString(),
                                "expiresAt",
                                db.now().plus(QR_LIFETIME).toString(),
                                "status",
                                "awaitingStore",
                                "amount",
                                null,
                                "cashierId",
                                null,
                                "receiptId",
                                null,
                                "seenBy",
                                null));
        return map("id", id);
    }

    Map<String, Object> findUsage(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var shop = owned(state, actor, data);
        String code = text(data, "code", 100, true).replaceFirst("^hantuck:prepaid:", "");
        var request =
                rows(state, "requests").stream()
                        .filter(
                                x ->
                                        code.equals(x.get("code"))
                                                && same(x.get("storeId"), shop.get("id")))
                        .findFirst()
                        .orElseThrow(() -> ApiException.bad("현재 가게의 이용 코드를 확인해 주세요."));
        liveOffer(state, request);
        request.put("seenBy", actor);
        return map("id", request.get("id"));
    }

    Map<String, Object> requestAmount(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var shop = owned(state, actor, data);
        var request = find(state, "requests", text(data, "requestId", 100, true));
        if (!same(request.get("storeId"), shop.get("id"))) {
            throw ApiException.forbidden();
        }
        var offer = liveOffer(state, request);
        long amount = amount(data, "amount");
        require("awaitingStore".equals(request.get("status")), "이미 금액 확인을 요청한 코드입니다.");
        require(
                amount <= number(offer, "totalAmount") - number(offer, "usedAmount"),
                "현재 잔액 이내의 금액을 입력해 주세요.");
        request.put("amount", amount);
        request.put("cashierId", actor);
        request.put("seenBy", actor);
        request.put("status", "awaitingUser");
        return map("id", request.get("id"));
    }

    private Map<String, Object> ownRequest(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var request = find(state, "requests", text(data, "requestId", 100, true));
        if (!actor.equals(request.get("userId"))) {
            throw ApiException.forbidden();
        }
        return request;
    }

    Map<String, Object> approveUsage(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var request = ownRequest(state, actor, data);
        if ("completed".equals(request.get("status"))) {
            return map("id", request.get("receiptId"));
        }
        var offer = liveOffer(state, request);
        require(
                "awaitingUser".equals(request.get("status"))
                        && request.get("amount") != null
                        && request.get("cashierId") != null
                        && owner(state, str(request, "storeId"), str(request, "cashierId")),
                "가게에서 요청한 금액을 먼저 확인해 주세요.");
        long amount = number(request, "amount"),
                remaining = number(offer, "totalAmount") - number(offer, "usedAmount");
        require(amount > 0 && amount <= remaining, "남은 잔액을 확인해 주세요.");
        String id = id("usage-receipt-");
        offer.put("usedAmount", number(offer, "usedAmount") + amount);
        rows(state, "receipts")
                .add(
                        map(
                                "id",
                                id,
                                "requestId",
                                request.get("id"),
                                "offerId",
                                offer.get("id"),
                                "groupId",
                                offer.get("groupId"),
                                "storeId",
                                offer.get("storeId"),
                                "donorId",
                                offer.get("donorId"),
                                "userId",
                                actor,
                                "userName",
                                auth.profile(actor).get("name"),
                                "amount",
                                amount,
                                "remainingAmount",
                                remaining - amount,
                                "createdAt",
                                db.now().toString(),
                                "thanks",
                                null,
                                "cancellation",
                                null));
        request.put("receiptId", id);
        request.put("status", "completed");
        return map("id", id);
    }

    Map<String, Object> cancelRequest(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var request = ownRequest(state, actor, data);
        require(request.get("receiptId") == null, "사용 완료된 결제는 가게에서 취소할 수 있습니다.");
        request.put("status", "cancelled");
        return map("id", request.get("id"));
    }

    Map<String, Object> reverseUsage(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var store = owned(state, actor, data);
        var receipt = find(state, "receipts", text(data, "receiptId", 100, true));
        if (!same(store.get("id"), receipt.get("storeId"))) {
            throw ApiException.forbidden();
        }
        if (receipt.get("cancellation") != null) {
            return map("id", receipt.get("id"));
        }
        var request = find(state, "requests", str(receipt, "requestId"));
        require("completed".equals(request.get("status")), "사용 완료된 결제만 취소할 수 있습니다.");
        var offer = find(state, "offers", str(receipt, "offerId"));
        long amount = number(receipt, "amount");
        require(number(offer, "usedAmount") >= amount, "복구할 원래 선결제를 확인해 주세요.");
        offer.put("usedAmount", number(offer, "usedAmount") - amount);
        receipt.put(
                "cancellation",
                map(
                        "ownerId",
                        actor,
                        "reason",
                        graphemeText(data, "reason", 300, false),
                        "createdAt",
                        db.now().toString(),
                        "remainingAmount",
                        number(offer, "totalAmount") - number(offer, "usedAmount"),
                        "expiresOn",
                        offer.get("expiresOn")));
        request.put("status", "paymentCancelled");
        return map("id", receipt.get("id"));
    }

    Map<String, Object> sendThanks(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var receipt = find(state, "receipts", text(data, "receiptId", 100, true));
        if (!actor.equals(receipt.get("userId"))) {
            throw ApiException.forbidden();
        }
        require(receipt.get("cancellation") == null, "취소된 결제에는 감사 메시지를 보낼 수 없습니다.");
        String text = graphemeText(data, "message", 500, false);
        Map<String, Object> photo =
                data.get("photo") == null ? null : photos.validatePhoto(object(data, "photo"));
        require(!text.isEmpty() || photo != null, "메시지 또는 사진을 입력해 주세요.");
        if (receipt.get("thanks") != null) {
            var old = (Map<String, Object>) receipt.get("thanks");
            require(
                    same(old.get("text"), text) && same(old.get("photo"), photo),
                    "이미 감사 메시지를 보냈습니다.");
            return map("id", receipt.get("id"));
        }
        receipt.put("thanks", map("text", text, "photo", photo, "createdAt", db.now().toString()));
        return map("id", receipt.get("id"));
    }
}
