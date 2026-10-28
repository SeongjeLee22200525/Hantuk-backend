package app.hantteok.legacy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Requests {
    private Requests() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=8,max=72) String password,
        @NotBlank @Size(min=2,max=12) String nickname, @AssertTrue boolean agreed) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record Profile(@NotBlank @Size(min=2,max=12) String nickname, @Size(max=20) @NotNull String realName,
        @NotBlank String communityId) {}
    public record Settings(boolean usageAlert, boolean exhaustedAlert, boolean refundAlert) {}
    public record Verification(@Size(max=254) String email, @NotBlank @Size(max=100) String code) {}
    public record Payment(@NotBlank String shopId, @NotBlank String communityId,
        @Min(1000) @Max(1000000000) long amount, @Min(1) @Max(1000000000) long limit,
        @NotNull Instant expiresAt, @NotBlank @Size(max=20) String donor,
        @NotNull @Size(max=500) String message,
        @NotNull @Size(max=50) List<@NotBlank @Size(max=100) String> menus,
        @NotBlank @Size(max=100) String paymentKey, boolean simulateFailure) {}
    public record Issue(@NotBlank String benefitId) {}
    public record Order(@NotBlank @Size(max=100) String menu, @Min(1) @Max(1000000000) long amount) {}
    public record Ticket(@NotBlank String targetId, @NotBlank String type,
        @NotBlank @Size(min=5,max=1000) String reason, @NotNull @Size(max=1000) String evidence) {}
    public record Menu(@NotBlank @Size(max=100) String name, @Min(1) @Max(1000000000) long price) {}
    public record ShopEdit(@NotNull @Size(max=1000) String description, @NotBlank @Size(max=200) String hours,
        @NotEmpty @Size(max=50) List<@Valid Menu> menu) {}
}
