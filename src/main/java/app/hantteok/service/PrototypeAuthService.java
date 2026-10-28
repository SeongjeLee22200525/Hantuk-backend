package app.hantteok.service;

import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.common.security.TokenHash;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class PrototypeAuthService {
    private final DatabaseRepository db;
    private final PasswordEncoder passwords;
    private final boolean demo;
    private final SecureRandom random = new SecureRandom();

    PrototypeAuthService(
            DatabaseRepository db,
            PasswordEncoder passwords,
            @Value("${app.demo-enabled}") boolean demo) {
        this.db = db;
        this.passwords = passwords;
        this.demo = demo;
    }

    public boolean demoEnabled() {
        return demo;
    }

    boolean invitationMatches(String code, String hash) {
        return passwords.matches(code, hash);
    }

    LocalDate today() {
        return db.now().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String user(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() > 256) {
            throw ApiException.unauthorized();
        }
        var sessions =
                db.all(
                        "SELECT user_id FROM prototype_sessions WHERE token_hash=? AND"
                                + " expires_at>?",
                        TokenHash.digest(header.substring(7)),
                        db.time());
        if (sessions.isEmpty()) {
            throw ApiException.unauthorized();
        }
        return str(sessions.get(0), "user_id");
    }

    Map<String, Object> profile(String user) {
        var account = db.one("SELECT * FROM prototype_accounts WHERE id=?", user);
        return map(
                "id",
                account.get("id"),
                "name",
                account.get("name"),
                "birthDate",
                account.get("birth_date").toString(),
                "email",
                account.get("email"),
                "phoneNumber",
                account.get("phone_number"));
    }

    boolean admin(String user) {
        return "ADMIN"
                .equals(db.one("SELECT role FROM prototype_accounts WHERE id=?", user).get("role"));
    }

    public boolean available(String id) {
        return id != null
                && id.matches("[A-Za-z0-9]{4,20}")
                && db.count("SELECT COUNT(*) FROM prototype_accounts WHERE id=?", normalizeKey(id))
                        == 0;
    }

    @Transactional
    public Map<String, Object> login(Map<String, Object> data) {
        String id = normalizeKey(text(data, "id", 20, true));
        String password = data.get("password") instanceof String value ? value : "";
        var rows = db.all("SELECT password_hash FROM prototype_accounts WHERE id=?", id);
        String hash =
                rows.isEmpty()
                        ? "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
                        : str(rows.get(0), "password_hash");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(password, hash)
                || rows.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "아이디 또는 비밀번호를 확인해 주세요.");
        }
        String token = newToken();
        db.exec("DELETE FROM prototype_sessions WHERE expires_at<=?", db.time());
        db.exec(
                "INSERT INTO prototype_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",
                TokenHash.digest(token),
                id,
                at(db.now().plus(Duration.ofHours(24))));
        return map("accessToken", token, "account", profile(id));
    }

    @Transactional
    public Map<String, Object> challenge(Map<String, Object> data) {
        if (!demo) {
            throw new ApiException(501, "EMAIL_PROVIDER_REQUIRED", "실제 이메일 인증 제공자를 연결해야 합니다.");
        }
        String email = normalizeKey(text(data, "email", 254, true));
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw ApiException.bad("이메일 주소를 확인해 주세요.");
        }
        String id = id("email-");
        db.exec("DELETE FROM prototype_email_challenges WHERE expires_at<=?", db.time());
        db.exec(
                "INSERT INTO prototype_email_challenges(id,email,code_hash,expires_at)"
                        + " VALUES(?,?,?,?)",
                id,
                email,
                passwords.encode("123456"),
                at(db.now().plus(Duration.ofMinutes(10))));
        return map(
                "challengeId",
                id,
                "demo",
                true,
                "message",
                "실제 메일은 발송하지 않습니다. 데모 인증번호는 123456입니다.");
    }

    // Return failure instead of throwing inside the transaction so attempt counts persist.
    @Transactional
    public Map<String, Object> verify(Map<String, Object> data) {
        String id = text(data, "challengeId", 80, true), code = text(data, "code", 20, true);
        var challenge =
                db.one("SELECT * FROM prototype_email_challenges WHERE id=? FOR UPDATE", id);
        if (Boolean.TRUE.equals(challenge.get("consumed"))
                || !instant(challenge, "expires_at").isAfter(db.now())
                || num(challenge, "attempts") >= 5) {
            return map("verified", false, "message", "만료된 인증입니다. 다시 요청해 주세요.");
        }
        boolean valid = passwords.matches(code, str(challenge, "code_hash"));
        db.exec(
                "UPDATE prototype_email_challenges SET attempts=attempts+1,verified=? WHERE id=?",
                valid,
                id);
        return map(
                "verified",
                valid,
                "verificationToken",
                valid ? id : null,
                "message",
                valid ? "인증되었습니다." : "인증번호를 확인해 주세요.");
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> data) {
        String id = normalizeKey(text(data, "id", 20, true)),
                password = text(data, "password", 72, true),
                name = text(data, "name", 50, true),
                email = normalizeKey(text(data, "email", 254, true));
        String phone = text(data, "phoneNumber", 20, true).replace("-", ""),
                proof = text(data, "verificationToken", 80, true);
        LocalDate birth = date(text(data, "birthDate", 10, true));
        if (!password.equals(data.get("password"))
                || !id.matches("[a-z0-9]{4,20}")
                || !password.matches("(?=.*[A-Za-z])(?=.*[0-9])[^\\s]{8,32}")
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !phone.matches("0[0-9]{8,10}")
                || birth.getYear() < 1900
                || birth.isAfter(today())
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw ApiException.bad("회원가입 입력값을 확인해 주세요.");
        }
        var challenge =
                db.one("SELECT * FROM prototype_email_challenges WHERE id=? FOR UPDATE", proof);
        if (!email.equals(str(challenge, "email"))
                || !Boolean.TRUE.equals(challenge.get("verified"))
                || Boolean.TRUE.equals(challenge.get("consumed"))
                || !instant(challenge, "expires_at").isAfter(db.now())) {
            throw ApiException.bad("이메일 인증을 다시 진행해 주세요.");
        }
        try {
            db.exec(
                    "INSERT INTO"
                        + " prototype_accounts(id,password_hash,name,birth_date,email,phone_number,created_at)"
                        + " VALUES(?,?,?,?,?,?,?)",
                    id,
                    passwords.encode(password),
                    name,
                    java.sql.Date.valueOf(birth),
                    email,
                    phone,
                    db.time());
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "ACCOUNT_EXISTS", "이미 사용 중인 아이디 또는 이메일입니다.");
        }
        db.exec("UPDATE prototype_email_challenges SET consumed=TRUE WHERE id=?", proof);
        return profile(id);
    }

    public void logout(String header) {
        user(header);
        db.exec(
                "DELETE FROM prototype_sessions WHERE token_hash=?",
                TokenHash.digest(header.substring(7)));
    }
}
