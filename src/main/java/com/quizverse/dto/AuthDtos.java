package com.quizverse.dto;

import com.quizverse.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}",
                    message = "must be 3-30 letters, digits or underscores") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 60) String displayName,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {}

    /** {@code login} is a username or an email address. */
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 60) String displayName,
            @NotBlank @Email @Size(max = 254) String email) {}

    /** {@code currentPassword} is required only when the account already has one (not Google-only). */
    public record ChangePasswordRequest(
            String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String newPassword) {}

    /** The password, or for a Google-only account without one, the username typed out as confirmation. */
    public record DeleteAccountRequest(String password, String confirmUsername) {}

    /** The ID token from the "Sign in with Google" button. */
    public record GoogleSignInRequest(@NotBlank @Size(max = 4096) String credential) {}

    public record UserResponse(Long id, String username, String email, String displayName, Instant createdAt,
                               boolean admin, boolean hasPassword, boolean googleLinked) {
        public static UserResponse of(User u, boolean admin) {
            return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getCreatedAt(),
                    admin, u.hasPassword(), u.getGoogleSubject() != null);
        }
    }
}
