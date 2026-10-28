package app.hantteok.service;

import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.common.security.TokenHash;
import app.hantteok.dto.Requests;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {
    private final DatabaseRepository db;
    private final PasswordEncoder passwords;
    private final int sessionHours;
    private final SecureRandom random = new SecureRandom();

    AuthService(
            DatabaseRepository db,
            PasswordEncoder passwords,
            @Value("${app.session-hours}") int sessionHours) {
        this.db = db;
        this.passwords = passwords;
        this.sessionHours = sessionHours;
    }

    @Transactional
    public Map<String, Object> register(Requests.Register r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT), nickname = r.nickname().trim();
        if (nickname.length() < 2 || r.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.bad("닉네임 또는 비밀번호 길이를 확인해 주세요.");
        }
        String id = id("U");
        try {
            db.exec(
                    "INSERT INTO app_users(id,email,password_hash,nickname,created_at)"
                            + " VALUES(?,?,?,?,?)",
                    id,
                    email,
                    passwords.encode(r.password()),
                    nickname,
                    db.time());
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "EMAIL_EXISTS", "이미 가입한 이메일입니다.");
        }
        return session(id);
    }

    @Transactional
    public Map<String, Object> login(Requests.Login r) {
        if (r.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        var rows =
                db.all(
                        "SELECT id,password_hash FROM app_users WHERE email=?",
                        r.email().trim().toLowerCase(Locale.ROOT));
        // A BCrypt check also runs for unknown accounts to avoid a fast account enumeration path.
        String hash =
                rows.isEmpty()
                        ? "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
                        : str(rows.get(0), "password_hash");
        boolean matches = passwords.matches(r.password(), hash);
        if (rows.isEmpty() || !matches) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        return session(str(rows.get(0), "id"));
    }

    private Map<String, Object> session(String user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expires = db.now().plus(sessionHours, ChronoUnit.HOURS);
        db.exec("DELETE FROM auth_sessions WHERE expires_at<=?", db.time());
        db.exec(
                "INSERT INTO auth_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",
                TokenHash.digest(token),
                user,
                at(expires));
        return map(
                "accessToken",
                token,
                "tokenType",
                "Bearer",
                "expiresAt",
                expires,
                "user",
                profile(user));
    }

    public String authenticate(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.length() > 256) {
            throw ApiException.unauthorized();
        }
        var rows =
                db.all(
                        "SELECT user_id FROM auth_sessions WHERE token_hash=? AND expires_at>?",
                        TokenHash.digest(authorization.substring(7)),
                        db.time());
        if (rows.isEmpty()) {
            throw ApiException.unauthorized();
        }
        return str(rows.get(0), "user_id");
    }

    public void logout(String authorization) {
        db.exec(
                "DELETE FROM auth_sessions WHERE token_hash=?",
                TokenHash.digest(authorization.substring(7)));
    }

    public Map<String, Object> profile(String user) {
        var u = db.one("SELECT * FROM app_users WHERE id=?", user);
        return map(
                "id",
                user,
                "email",
                u.get("email"),
                "nickname",
                u.get("nickname"),
                "realName",
                u.get("real_name"),
                "communityId",
                u.get("community_id"),
                "verified",
                db
                        .all(
                                "SELECT community_id FROM memberships WHERE user_id=? ORDER BY"
                                        + " community_id",
                                user)
                        .stream()
                        .map(x -> x.get("community_id"))
                        .toList(),
                "merchantShopIds",
                db.all("SELECT id FROM shops WHERE owner_id=? ORDER BY id", user).stream()
                        .map(x -> x.get("id"))
                        .toList(),
                "role",
                u.get("role"),
                "notificationSettings",
                map(
                        "사용 알림",
                        u.get("usage_alert"),
                        "소진 알림",
                        u.get("exhausted_alert"),
                        "환불 알림",
                        u.get("refund_alert")));
    }

    @Transactional
    public Map<String, Object> updateProfile(String user, Requests.Profile r) {
        db.one("SELECT id FROM communities WHERE id=?", r.communityId());
        if (r.nickname().trim().length() < 2) {
            throw ApiException.bad("닉네임은 2자 이상이어야 합니다.");
        }
        db.exec(
                "UPDATE app_users SET nickname=?,real_name=?,community_id=? WHERE id=?",
                r.nickname().trim(),
                r.realName().trim(),
                r.communityId(),
                user);
        return profile(user);
    }

    public Map<String, Object> settings(String user, Requests.Settings r) {
        db.exec(
                "UPDATE app_users SET usage_alert=?,exhausted_alert=?,refund_alert=? WHERE id=?",
                r.usageAlert(),
                r.exhaustedAlert(),
                r.refundAlert(),
                user);
        return profile(user);
    }

    void admin(String user) {
        if (!"ADMIN".equals(str(db.one("SELECT role FROM app_users WHERE id=?", user), "role"))) {
            throw ApiException.forbidden();
        }
    }
}
