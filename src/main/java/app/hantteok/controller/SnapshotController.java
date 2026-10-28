package app.hantteok.controller;

import app.hantteok.service.PrototypeAuthService;
import app.hantteok.service.PrototypeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/snapshot")
public class SnapshotController {
    private final PrototypeAuthService auth;
    private final PrototypeService service;

    public SnapshotController(PrototypeAuthService auth, PrototypeService service) {
        this.auth = auth;
        this.service = service;
    }

    @GetMapping
    public Object snapshot(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.snapshot(auth.user(authorization));
    }
}
