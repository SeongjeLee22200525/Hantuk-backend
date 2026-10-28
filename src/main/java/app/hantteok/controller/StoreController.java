package app.hantteok.controller;

import app.hantteok.dto.CommandRequest;
import app.hantteok.service.CommandService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/stores")
public class StoreController {
    private final CommandService commands;

    public StoreController(CommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/favorite")
    public Map<String, Object> favorite(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.favorite", request.data());
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.apply", request.data());
    }

    @PostMapping("/approve")
    public Map<String, Object> approve(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.approve", request.data());
    }

    @PostMapping("/reject")
    public Map<String, Object> reject(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.reject", request.data());
    }

    @PostMapping("/thumbnail")
    public Map<String, Object> thumbnail(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.thumbnail", request.data());
    }

    @PostMapping("/menus/save")
    public Map<String, Object> menusSave(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.menu.save", request.data());
    }

    @PostMapping("/menus/remove")
    public Map<String, Object> menusRemove(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CommandRequest request
    ) {
        return commands.execute(authorization, request.commandId(), "store.menu.remove", request.data());
    }
}
