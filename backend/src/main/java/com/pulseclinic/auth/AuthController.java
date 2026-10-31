package com.pulseclinic.auth;

import com.pulseclinic.auth.dto.CurrentUserResponse;
import com.pulseclinic.auth.dto.LoginRequest;
import com.pulseclinic.auth.dto.RegisterRequest;
import com.pulseclinic.auth.dto.TokenRequest;
import com.pulseclinic.auth.dto.PasswordResetRequest;
import com.pulseclinic.auth.dto.CompletePasswordResetRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    AuthController(AuthService authService) { this.authService = authService; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public CurrentUserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) { AuthService.Tokens tokens = authService.registerAndLogin(request); setCookies(tokens, response); return tokens.user(); }
    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) { AuthService.Tokens tokens = authService.login(request); setCookies(tokens, response); return tokens.user(); }
    @PostMapping("/refresh")
    public void refresh(HttpServletRequest request, HttpServletResponse response) { AuthService.Tokens tokens = authService.refresh(cookie(request, "REFRESH_TOKEN")); setCookies(tokens, response); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) { authService.logout(cookie(request, "REFRESH_TOKEN")); expire(response, "ACCESS_TOKEN"); expire(response, "REFRESH_TOKEN"); }
    @GetMapping("/me") public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) { return authService.currentUser(UUID.fromString(jwt.getSubject())); }
    @GetMapping("/csrf") public CsrfToken csrf(CsrfToken token) { return token; }
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT) public void verify(@Valid @RequestBody TokenRequest request) { authService.verify(request.token()); }
    @PostMapping("/password-reset/request") public String requestReset(@Valid @RequestBody PasswordResetRequest request) { return authService.requestPasswordReset(request.email()); }
    @PostMapping("/password-reset/complete") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@Valid @RequestBody CompletePasswordResetRequest request) { authService.resetPassword(request.token(),request.password()); }
    private void setCookies(AuthService.Tokens tokens, HttpServletResponse response) { response.addHeader("Set-Cookie", cookie("ACCESS_TOKEN", tokens.accessToken(), Duration.ofMinutes(15)).toString()); response.addHeader("Set-Cookie", cookie("REFRESH_TOKEN", tokens.refreshToken(), Duration.ofDays(30)).toString()); }
    private ResponseCookie cookie(String name, String value, Duration ignored) { return ResponseCookie.from(name, value).httpOnly(true).secure(authService.secureCookies()).sameSite("Lax").path("/").build(); }
    private void expire(HttpServletResponse response, String name) { response.addHeader("Set-Cookie", ResponseCookie.from(name, "").httpOnly(true).secure(authService.secureCookies()).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString()); }
    private String cookie(HttpServletRequest request, String name) { if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue(); throw new AuthException("INVALID_REFRESH_TOKEN", "Session has expired."); }
}
