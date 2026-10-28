package app.hantteok.controller;

import app.hantteok.dto.CommandRequest;
import app.hantteok.service.CommandService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/usages")
public class UsageController {
    private final CommandService commands;

    public UsageController(CommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/begin")
    public Map<String, Object> begin(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.begin", request.data());
    }

    @PostMapping("/find")
    public Map<String, Object> find(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.find", request.data());
    }

    @PostMapping("/amount")
    public Map<String, Object> amount(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.amount", request.data());
    }

    @PostMapping("/approve")
    public Map<String, Object> approve(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.approve", request.data());
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.cancel", request.data());
    }

    @PostMapping("/reverse")
    public Map<String, Object> reverse(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.reverse", request.data());
    }

    @PostMapping("/thanks")
    public Map<String, Object> thanks(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "usage.thanks", request.data());
    }
}
