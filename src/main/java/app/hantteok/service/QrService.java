package app.hantteok.service;

import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.dto.Requests;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class QrService {
    private final DatabaseRepository db;
    private final BenefitService benefits;

    QrService(DatabaseRepository db, BenefitService benefits) {
        this.db = db;
        this.benefits = benefits;
    }

    private Map<String, Object> view(Map<String, Object> r) {
        String status = str(r, "status");
        if ((status.equals("issued") || status.equals("pending"))
                && !instant(r, "expires_at").isAfter(db.now())) {
            status = "expired";
        }
        return map(
                "id",
                r.get("id"),
                "benefitId",
                r.get("benefit_id"),
                "status",
                status,
                "menu",
                r.get("menu"),
                "orderAmount",
                r.get("order_amount"),
                "expiresAt",
                instant(r, "expires_at"),
                "seconds",
                Math.max(0, ChronoUnit.SECONDS.between(db.now(), instant(r, "expires_at"))),
                "qrPayload",
                "hantteok:" + r.get("id"));
    }

    private void active(Map<String, Object> r, String expected) {
        if (!str(r, "status").equals(expected) || !instant(r, "expires_at").isAfter(db.now())) {
            throw ApiException.conflict("만료되었거나 이미 처리된 QR입니다. 다시 발급해 주세요.");
        }
    }

    /** All mutations use the same lock order: recipient -> benefit -> request. */
    private Map<String, Object> lock(String id, String user, boolean merchant) {
        var initial = db.one("SELECT * FROM use_requests WHERE id=?", id);
        if (!merchant && !str(initial, "user_id").equals(user)) {
            throw ApiException.forbidden();
        }
        if (merchant) {
            var b = db.one("SELECT shop_id FROM benefits WHERE id=?", initial.get("benefit_id"));
            db.ownShop(user, str(b, "shop_id"));
        }
        db.lockUser(str(initial, "user_id"));
        db.lockBenefit(str(initial, "benefit_id"));
        return db.one("SELECT * FROM use_requests WHERE id=? FOR UPDATE", id);
    }

    @Transactional
    public Map<String, Object> issue(String user, String benefit) {
        db.lockUser(user);
        var b = db.lockBenefit(benefit);
        if (benefits.allowance(user, b) <= 0) {
            throw ApiException.conflict("인증 상태와 이용 가능 금액을 확인해 주세요.");
        }
        db.exec(
                "UPDATE use_requests SET status='cancelled' WHERE user_id=? AND status IN"
                        + " ('issued','pending')",
                user);
        String id = id("QR");
        db.exec(
                "INSERT INTO use_requests(id,user_id,benefit_id,status,created_at,expires_at)"
                        + " VALUES(?,?,?,'issued',?,?)",
                id,
                user,
                benefit,
                db.time(),
                at(db.now().plus(3, ChronoUnit.MINUTES)));
        return get(user, id);
    }

    public Map<String, Object> get(String user, String id) {
        return view(db.one("SELECT * FROM use_requests WHERE id=? AND user_id=?", id, user));
    }

    public Map<String, Object> merchantGet(String user, String id) {
        var r = db.one("SELECT * FROM use_requests WHERE id=?", id);
        var b = db.one("SELECT * FROM benefits WHERE id=?", r.get("benefit_id"));
        db.ownShop(user, str(b, "shop_id"));
        var result = view(r);
        result.put("shopId", b.get("shop_id"));
        result.put("allowance", benefits.allowance(str(r, "user_id"), b));
        return result;
    }

    @Transactional
    public Map<String, Object> order(String merchant, String id, Requests.Order order) {
        var r = lock(id, merchant, true);
        active(r, "issued");
        var b = db.one("SELECT * FROM benefits WHERE id=?", r.get("benefit_id"));
        if (benefits.allowance(str(r, "user_id"), b) <= 0) {
            throw ApiException.conflict("이용 가능한 혜택이 아닙니다.");
        }
        if (db.count(
                                "SELECT COUNT(*) FROM shop_menus WHERE shop_id=? AND name=?",
                                b.get("shop_id"),
                                order.menu())
                        == 0
                || (db.count("SELECT COUNT(*) FROM benefit_menus WHERE benefit_id=?", b.get("id"))
                                > 0
                        && db.count(
                                        "SELECT COUNT(*) FROM benefit_menus WHERE benefit_id=? AND"
                                                + " name=?",
                                        b.get("id"),
                                        order.menu())
                                == 0)) {
            throw ApiException.bad("사용 가능한 메뉴를 선택해 주세요.");
        }
        db.exec(
                "UPDATE use_requests SET menu=?,order_amount=?,status='pending' WHERE id=?",
                order.menu(),
                order.amount(),
                id);
        return merchantGet(merchant, id);
    }

    @Transactional
    public Map<String, Object> approve(String user, String id) {
        var r = lock(id, user, false);
        active(r, "pending");
        var b = db.one("SELECT * FROM benefits WHERE id=?", r.get("benefit_id"));
        long amount = Math.min(benefits.allowance(user, b), num(r, "order_amount"));
        if (amount <= 0 || db.count("SELECT COUNT(*) FROM usages WHERE request_id=?", id) > 0) {
            throw ApiException.conflict("이용 한도가 없거나 이미 완료된 거래입니다.");
        }
        String usage = id("HT");
        db.exec("UPDATE benefits SET remaining=remaining-? WHERE id=?", amount, b.get("id"));
        db.exec(
                "INSERT INTO"
                    + " usages(id,request_id,user_id,benefit_id,shop_id,menu,order_amount,applied,used_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                usage,
                id,
                user,
                b.get("id"),
                b.get("shop_id"),
                r.get("menu"),
                r.get("order_amount"),
                amount,
                db.time());
        db.exec("UPDATE use_requests SET status='completed' WHERE id=?", id);
        db.notice(user, "혜택 사용이 완료되었어요", amount + "원 사용", usage, "usage");
        if (num(b, "remaining") == amount) {
            db.notice(
                    str(b, "donor_id"),
                    "한턱의 마음이 모두 전해졌어요",
                    "공동 잔액이 소진되었어요.",
                    str(b, "id"),
                    "exhausted");
        }
        return benefits.usage(user, usage);
    }

    @Transactional
    public Map<String, Object> cancel(String user, String id) {
        var r = lock(id, user, false);
        if (str(r, "status").equals("completed")) {
            throw ApiException.conflict("완료된 거래는 QR 취소로 되돌릴 수 없습니다.");
        }
        db.exec("UPDATE use_requests SET status='cancelled' WHERE id=?", id);
        return get(user, id);
    }
}
