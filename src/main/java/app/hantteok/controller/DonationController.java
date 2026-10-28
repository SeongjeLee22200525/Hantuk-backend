package app.hantteok.controller;

import app.hantteok.dto.CommandRequest;
import app.hantteok.service.CommandService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/donations")
public class DonationController {
    private final CommandService commands;

    public DonationController(CommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CommandRequest request) {
        return commands.execute(
                authorization, request.commandId(), "donation.complete", request.data());
    }
}
