package app.hantteok.controller;

import app.hantteok.service.PrototypeAuthService;
import org.springframework.web.bind.annotation.*;
import static app.hantteok.repository.DatabaseRepository.map;

@RestController
@RequestMapping("/api/v2/health")
public class HealthController {
    private final PrototypeAuthService auth;
    public HealthController(PrototypeAuthService auth) { this.auth = auth; }

    @GetMapping
    public Object health() {
        return map("status", "UP", "apiVersion", 2, "demo", auth.demoEnabled(), "paymentMode", "mock");
    }
}
