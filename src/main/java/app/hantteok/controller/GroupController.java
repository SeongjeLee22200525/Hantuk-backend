package app.hantteok.controller;

import app.hantteok.dto.CommandRequest;
import app.hantteok.service.CommandService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/groups")
public class GroupController {
    private final CommandService commands;

    public GroupController(CommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/join")
    public Map<String, Object> join(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "group.join", request.data());
    }

    @PostMapping("/invite")
    public Map<String, Object> invite(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "group.invite", request.data());
    }

    @PostMapping("/approve")
    public Map<String, Object> approve(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "group.approve", request.data());
    }

    @PostMapping("/favorite")
    public Map<String, Object> favorite(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "group.favorite", request.data());
    }
}
