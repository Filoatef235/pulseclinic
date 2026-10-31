package com.pulseclinic.auth;

import com.pulseclinic.auth.dto.CurrentUserResponse;
import com.pulseclinic.auth.dto.LoginRequest;
import com.pulseclinic.auth.dto.RegisterRequest;
import com.pulseclinic.common.config.PulseClinicProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthRepository repository; private final PasswordEncoder encoder; private final JwtEncoder jwtEncoder; private final PulseClinicProperties properties;
    AuthService(AuthRepository repository, PasswordEncoder encoder, JwtEncoder jwtEncoder, PulseClinicProperties properties) { this.repository = repository; this.encoder = encoder; this.jwtEncoder = jwtEncoder; this.properties = properties; }
    public CurrentUserResponse register(RegisterRequest request) {
        if (repository.emailExists(request.email())) throw new AuthException("EMAIL_ALREADY_REGISTERED", "Email is already registered.");
        UUID id = repository.createPatient(request.email().trim().toLowerCase(), encoder.encode(request.password()), request.fullName().trim());
        return currentUser(id);
    }
    public Tokens registerAndLogin(RegisterRequest request) {
        CurrentUserResponse user = register(request);
        queueVerification(user.id());
        return issue(repository.findById(user.id()).orElseThrow());
    }
    public Tokens login(LoginRequest request) {
        AuthRepository.UserAccount user = repository.findByEmail(request.email().trim().toLowerCase()).filter(AuthRepository.UserAccount::enabled)
                .filter(candidate -> encoder.matches(request.password(), candidate.passwordHash()))
                .orElseThrow(() -> new AuthException("INVALID_CREDENTIALS", "Invalid email or password."));
        repository.recordSignIn(user.id());
        return issue(user);
    }
    public Tokens refresh(String refreshToken) {
        UUID userId = repository.activeRefreshTokenOwner(hash(refreshToken), Instant.now())
                .orElseThrow(() -> new AuthException("INVALID_REFRESH_TOKEN", "Session has expired."));
        repository.revokeRefreshToken(hash(refreshToken));
        return issue(repository.findById(userId).filter(AuthRepository.UserAccount::enabled).orElseThrow(() -> new AuthException("INVALID_REFRESH_TOKEN", "Session has expired.")));
    }
    public void logout(String refreshToken) { if (refreshToken != null) repository.revokeRefreshToken(hash(refreshToken)); }
    public CurrentUserResponse currentUser(UUID id) { AuthRepository.UserAccount user = repository.findById(id).orElseThrow(() -> new AuthException("UNAUTHENTICATED", "Authentication is required.")); return new CurrentUserResponse(user.id(), user.email(), user.role(), repository.patientIdForUser(user.id()).orElse(null)); }
    public boolean secureCookies() { return properties.getAuth().isSecureCookies(); }
    public String queueVerification(UUID userId) { String token=randomToken(); repository.createVerification(userId,hash(token),Instant.now().plus(24,ChronoUnit.HOURS)); return "/verify-email/"+token; }
    public void verify(String token) { UUID id=repository.validVerification(hash(token)).orElseThrow(()->new AuthException("INVALID_VERIFICATION_TOKEN","This verification link is invalid or expired.")); repository.verify(id,hash(token)); }
    public String requestPasswordReset(String email) { return repository.userIdByEmail(email.trim().toLowerCase()).map(id->{String token=randomToken(); repository.createReset(id,hash(token),Instant.now().plus(1,ChronoUnit.HOURS)); return "/reset-password/"+token;}).orElse(""); }
    public void resetPassword(String token,String password) { UUID id=repository.validReset(hash(token)).orElseThrow(()->new AuthException("INVALID_RESET_TOKEN","This reset link is invalid or expired.")); repository.resetPassword(id,hash(token),encoder.encode(password)); }
    private String randomToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private Tokens issue(AuthRepository.UserAccount user) {
        Instant now = Instant.now();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.id().toString()).issuedAt(now).expiresAt(now.plus(properties.getAuth().getAccessTokenMinutes(), ChronoUnit.MINUTES)).claim("roles", java.util.List.of("ROLE_" + user.role())).build())).getTokenValue();
        byte[] bytes = new byte[48]; RANDOM.nextBytes(bytes); String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); repository.saveRefreshToken(user.id(), hash(refresh), now.plus(properties.getAuth().getRefreshTokenDays(), ChronoUnit.DAYS)); return new Tokens(access, refresh, currentUser(user.id()));
    }
    private String hash(String token) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    public record Tokens(String accessToken, String refreshToken, CurrentUserResponse user) {}
}
