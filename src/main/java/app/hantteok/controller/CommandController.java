package app.hantteok.controller;

import app.hantteok.dto.LegacyCommandRequest;
import app.hantteok.service.CommandService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Backward compatibility for clients using the original v2 command endpoint. */
@RestController
@RequestMapping("/api/v2/commands")
public class CommandController {
    private final CommandService commands;
    public CommandController(CommandService commands) { this.commands = commands; }

    @PostMapping
    public Map<String, Object> command(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody LegacyCommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), request.action(), request.data());
    }
}
