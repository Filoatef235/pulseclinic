package com.pulseclinic.team;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class TeamRepository {
    private final JdbcTemplate jdbc;
    TeamRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    boolean emailExists(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where email=?)", Boolean.class, email));
    }

    boolean specialtyExists(UUID specialtyId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from specialties where id=?)", Boolean.class, specialtyId));
    }

    List<Specialty> specialties() {
        return jdbc.query("select id, name from specialties order by name", (rs, row) -> new Specialty(rs.getObject("id", UUID.class), rs.getString("name")));
    }

    void createUser(UUID id, String email, String passwordHash, String role) {
        jdbc.update("insert into users (id, email, password_hash, role, enabled) values (?, ?, ?, ?, false)", id, email, passwordHash, role);
    }

    UUID createDoctor(UUID userId, UUID specialtyId, String fullName, String title, String bio, String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into doctors (id, user_id, specialty_id, full_name, professional_title, bio, phone, active) values (?, ?, ?, ?, ?, ?, ?, true)", id, userId, specialtyId, fullName, title, bio, phone);
        return id;
    }

    UUID createStaff(UUID userId, String fullName, String title, String bio, String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (id, user_id, full_name, professional_title, bio, phone, active) values (?, ?, ?, ?, ?, ?, true)", id, userId, fullName, title, bio, phone);
        return id;
    }

    void revokeOpenInvitations(UUID userId) {
        jdbc.update("update team_invitations set revoked_at=now() where user_id=? and used_at is null and revoked_at is null", userId);
    }

    void createInvitation(UUID userId, String tokenHash, Instant expiresAt, UUID actorId) {
        jdbc.update("insert into team_invitations (user_id, token_hash, expires_at, created_by) values (?, ?, ?, ?)", userId, tokenHash, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), actorId);
    }
    java.util.Optional<String> emailByUserId(UUID userId) { return jdbc.query("select email from users where id=?", (rs, row) -> rs.getString(1), userId).stream().findFirst(); }

    Optional<InvitationAccount> invitationAccount(String tokenHash, Instant now) {
        return jdbc.query("select i.id invitation_id, u.id user_id, u.email, u.role from team_invitations i join users u on u.id=i.user_id where i.token_hash=? and i.used_at is null and i.revoked_at is null and i.expires_at>?", (rs, row) -> new InvitationAccount(rs.getObject("invitation_id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getString("role")), tokenHash, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).stream().findFirst();
    }

    void completeInvitation(UUID invitationId, UUID userId, String passwordHash) {
        jdbc.update("update users set password_hash=?, enabled=true, updated_at=now() where id=?", passwordHash, userId);
        jdbc.update("update team_invitations set used_at=now() where id=? and used_at is null", invitationId);
    }

    void audit(UUID actorId, String action, String entityType, UUID entityId) {
        jdbc.update("insert into audit_log (actor_user_id, action, entity_type, entity_id, metadata) values (?, ?, ?, ?, '{}'::jsonb)", actorId, action, entityType, entityId);
    }

    boolean teamMemberExists(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where id=? and role in ('DOCTOR','STAFF'))", Boolean.class, userId));
    }

    void savePhoto(UUID userId, String storageKey) {
        jdbc.update("update doctors set photo_storage_key=?, updated_at=now() where user_id=?", storageKey, userId);
        jdbc.update("update staff_profiles set photo_storage_key=?, updated_at=now() where user_id=?", storageKey, userId);
    }
    void removePhoto(UUID userId) {
        jdbc.update("update doctors set photo_storage_key=null, updated_at=now() where user_id=?", userId);
        jdbc.update("update staff_profiles set photo_storage_key=null, updated_at=now() where user_id=?", userId);
    }
    void updateOwnBio(UUID userId, String bio) {
        jdbc.update("update doctors set bio=?, updated_at=now() where user_id=?", bio, userId);
        jdbc.update("update staff_profiles set bio=?, updated_at=now() where user_id=?", bio, userId);
    }

    void setActive(UUID userId, boolean active) {
        jdbc.update("update users set enabled=?, updated_at=now() where id=? and role in ('DOCTOR','STAFF')", active, userId);
        jdbc.update("update doctors set active=?, updated_at=now() where user_id=?", active, userId);
        jdbc.update("update staff_profiles set active=?, updated_at=now() where user_id=?", active, userId);
        if (!active) jdbc.update("update refresh_tokens set revoked_at=now() where user_id=? and revoked_at is null", userId);
    }

    void updateMember(UUID userId, String fullName, String email, String title, String bio, String phone, UUID specialtyId) {
        jdbc.update("update users set email=?, updated_at=now() where id=?", email, userId);
        if (specialtyId != null) jdbc.update("update doctors set full_name=?, professional_title=?, bio=?, phone=?, specialty_id=?, updated_at=now() where user_id=?", fullName, title, bio, phone, specialtyId, userId);
        jdbc.update("update staff_profiles set full_name=?, professional_title=?, bio=?, phone=?, updated_at=now() where user_id=?", fullName, title, bio, phone, userId);
    }

    List<TeamMemberRow> members() {
        return jdbc.query("select u.id user_id, coalesce(d.id, sp.id) profile_id, coalesce(d.full_name, sp.full_name) full_name, u.email, u.role, coalesce(d.professional_title, sp.professional_title) professional_title, coalesce(d.bio, sp.bio) bio, coalesce(d.phone, sp.phone) phone, s.name specialty, u.enabled, u.last_signed_in_at, coalesce(d.photo_storage_key, sp.photo_storage_key) photo_key, coalesce(d.updated_at, sp.updated_at) profile_updated_at from users u left join doctors d on d.user_id=u.id left join staff_profiles sp on sp.user_id=u.id left join specialties s on s.id=d.specialty_id where u.role in ('DOCTOR','STAFF') order by u.role, coalesce(d.full_name, sp.full_name)", (rs, row) -> new TeamMemberRow(rs.getObject("user_id", UUID.class), rs.getObject("profile_id", UUID.class), rs.getString("full_name"), rs.getString("email"), rs.getString("role"), rs.getString("professional_title"), rs.getString("bio"), rs.getString("phone"), rs.getString("specialty"), rs.getBoolean("enabled"), rs.getObject("last_signed_in_at", OffsetDateTime.class) == null ? null : rs.getObject("last_signed_in_at", OffsetDateTime.class).toInstant(), rs.getString("photo_key"), rs.getObject("profile_updated_at", OffsetDateTime.class).toInstant()));
    }

    Optional<TeamMemberRow> member(UUID userId) {
        return members().stream().filter(member -> member.userId().equals(userId)).findFirst();
    }
    Optional<String> photoKey(UUID userId) {
        List<String> keys = jdbc.query("select coalesce(d.photo_storage_key, sp.photo_storage_key) from users u left join doctors d on d.user_id=u.id left join staff_profiles sp on sp.user_id=u.id where u.id=?", (rs, row) -> rs.getString(1), userId);
        return keys.isEmpty() ? Optional.empty() : Optional.ofNullable(keys.get(0));
    }

    record InvitationAccount(UUID invitationId, UUID userId, String email, String role) {}
    record Specialty(UUID id, String name) {}
    record TeamMemberRow(UUID userId, UUID profileId, String fullName, String email, String role, String professionalTitle, String bio, String phone, String specialty, boolean active, Instant lastSignedInAt, String photoKey, Instant profileUpdatedAt) {}
}
