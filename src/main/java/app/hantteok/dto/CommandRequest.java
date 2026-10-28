package app.hantteok.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

/** Feature endpoints select their action on the server. */
public record CommandRequest(
    @NotBlank @Size(min = 8, max = 80) String commandId,
    @NotNull Map<String, Object> data
) {}
