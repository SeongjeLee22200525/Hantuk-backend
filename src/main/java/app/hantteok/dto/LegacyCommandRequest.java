package app.hantteok.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

/** Original command envelope retained for older clients. */
public record LegacyCommandRequest(
    @NotBlank @Size(min = 8, max = 80) String commandId,
    @NotBlank @Size(max = 50) String action,
    @NotNull Map<String, Object> data
) {}
