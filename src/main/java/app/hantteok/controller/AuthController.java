package app.hantteok.controller;

import app.hantteok.common.error.ApiException;
import app.hantteok.service.PrototypeAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static app.hantteok.repository.DatabaseRepository.map;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {
    private final PrototypeAuthService auth;

    public AuthController(PrototypeAuthService auth) { this.auth = auth; }

    @GetMapping("/availability")
    public Object availability(@RequestParam String id) { return map("available", auth.available(id)); }

    @PostMapping("/email-challenges")
    public Object challenge(@RequestBody Map<String, Object> data) { return auth.challenge(data); }

    @PostMapping("/email-verify")
    public Object verify(@RequestBody Map<String, Object> data) {
        var result = auth.verify(data);
        if (!Boolean.TRUE.equals(result.get("verified"))) {
            throw ApiException.bad(result.get("message").toString());
        }
        return result;
    }

    @PostMapping("/register")
    public Object register(@RequestBody Map<String, Object> data) { return auth.register(data); }

    @PostMapping("/login")
    public Object login(@RequestBody Map<String, Object> data) { return auth.login(data); }

    @PostMapping("/logout")
    public Object logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
        return map("loggedOut", true);
    }
}
