package app.hantteok.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/** All HTTP command routes share authentication and transactional idempotency. */
@Service
public class CommandService {
    private final PrototypeAuthService auth;
    private final PrototypeService service;

    public CommandService(PrototypeAuthService auth, PrototypeService service) {
        this.auth = auth;
        this.service = service;
    }

    public Map<String, Object> execute(
            String authorization, String commandId, String action, Map<String, Object> data) {
        return service.command(auth.user(authorization), commandId, action, data);
    }
}
