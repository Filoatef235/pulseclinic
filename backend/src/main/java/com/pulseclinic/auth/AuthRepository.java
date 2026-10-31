package com.pulseclinic.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuthRepository {
    private final JdbcTemplate jdbc;
    AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("select id, email, password_hash, role, enabled from users where email = ?", (rs, i) ->
                new UserAccount(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("enabled")), email).stream().findFirst();
    }
    Optional<UserAccount> findById(UUID id) {
        return jdbc.query("select id, email, password_hash, role, enabled from users where id = ?", (rs, i) ->
                new UserAccount(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("enabled")), id).stream().findFirst();
    }
    boolean emailExists(String email) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where email = ?)", Boolean.class, email)); }
    UUID createPatient(String email, String passwordHash, String fullName) {
        UUID userId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, role) values (?, ?, ?, 'PATIENT')", userId, email, passwordHash);
        jdbc.update("insert into patients (id, user_id, full_name) values (?, ?, ?)", patientId, userId, fullName);
        return userId;
    }
    void saveRefreshToken(UUID userId, String hash, Instant expiresAt) { jdbc.update("insert into refresh_tokens (user_id, token_hash, expires_at) values (?, ?, ?)", userId, hash, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)); }
    Optional<UUID> activeRefreshTokenOwner(String hash, Instant now) {
        return jdbc.query("select user_id from refresh_tokens where token_hash = ? and revoked_at is null and expires_at > ?", (rs, i) -> rs.getObject(1, UUID.class), hash, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).stream().findFirst();
    }
    void revokeRefreshToken(String hash) { jdbc.update("update refresh_tokens set revoked_at = now() where token_hash = ? and revoked_at is null", hash); }
    void revokeAllRefreshTokens(UUID userId) { jdbc.update("update refresh_tokens set revoked_at = now() where user_id = ? and revoked_at is null", userId); }
    void recordSignIn(UUID userId) { jdbc.update("update users set last_signed_in_at = now(), updated_at = now() where id = ?", userId); }
    Optional<UUID> userIdByEmail(String email) { return jdbc.query("select id from users where email=?", (rs,i)->rs.getObject(1,UUID.class),email).stream().findFirst(); }
    void createVerification(UUID userId, String hash, Instant expires) { jdbc.update("insert into email_verification_tokens (user_id,token_hash,expires_at) values (?,?,?)",userId,hash,OffsetDateTime.ofInstant(expires,ZoneOffset.UTC)); }
    void createReset(UUID userId, String hash, Instant expires) { jdbc.update("insert into password_reset_tokens (user_id,token_hash,expires_at) values (?,?,?)",userId,hash,OffsetDateTime.ofInstant(expires,ZoneOffset.UTC)); }
    Optional<UUID> validVerification(String hash) { return jdbc.query("select user_id from email_verification_tokens where token_hash=? and used_at is null and expires_at>now()",(rs,i)->rs.getObject(1,UUID.class),hash).stream().findFirst(); }
    Optional<UUID> validReset(String hash) { return jdbc.query("select user_id from password_reset_tokens where token_hash=? and used_at is null and expires_at>now()",(rs,i)->rs.getObject(1,UUID.class),hash).stream().findFirst(); }
    void verify(UUID userId, String hash) { jdbc.update("update users set email_verified=true,updated_at=now() where id=?",userId); jdbc.update("update email_verification_tokens set used_at=now() where token_hash=?",hash); }
    void resetPassword(UUID userId, String hash, String passwordHash) { jdbc.update("update users set password_hash=?,updated_at=now() where id=?",passwordHash,userId); jdbc.update("update password_reset_tokens set used_at=now() where token_hash=?",hash); revokeAllRefreshTokens(userId); }
    Optional<UUID> patientIdForUser(UUID userId) { return jdbc.query("select id from patients where user_id=?", (rs,i) -> rs.getObject(1, UUID.class), userId).stream().findFirst(); }
    record UserAccount(UUID id, String email, String passwordHash, String role, boolean enabled) {}
}
