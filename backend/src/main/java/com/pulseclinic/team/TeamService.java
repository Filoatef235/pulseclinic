package com.pulseclinic.team;

import com.pulseclinic.auth.AuthException;
import com.pulseclinic.team.dto.CompleteInvitationRequest;
import com.pulseclinic.team.dto.CreateTeamMemberRequest;
import com.pulseclinic.team.dto.TeamMemberResponse;
import com.pulseclinic.team.dto.UpdateMyProfileRequest;
import com.pulseclinic.team.dto.UpdateTeamMemberRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TeamRepository repository;
    private final PasswordEncoder passwordEncoder;

    TeamService(TeamRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TeamMemberResponse create(CreateTeamMemberRequest request, UUID actorId) {
        String email = request.email().trim().toLowerCase();
        if (repository.emailExists(email)) throw new AuthException("EMAIL_ALREADY_REGISTERED", "Email is already registered.");
        if ("DOCTOR".equals(request.role()) && (request.specialtyId() == null || !repository.specialtyExists(request.specialtyId()))) {
            throw new AuthException("INVALID_SPECIALTY", "Doctors must have a valid specialty.");
        }
        UUID userId = UUID.randomUUID();
        byte[] unusablePassword = new byte[48];
        RANDOM.nextBytes(unusablePassword);
        repository.createUser(userId, email, passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(unusablePassword)), request.role());
        UUID profileId = "DOCTOR".equals(request.role())
                ? repository.createDoctor(userId, request.specialtyId(), clean(request.fullName()), nullable(request.professionalTitle()), nullable(request.bio()), nullable(request.phone()))
                : repository.createStaff(userId, clean(request.fullName()), nullable(request.professionalTitle()), nullable(request.bio()), nullable(request.phone()));
        String rawToken = randomToken();
        repository.createInvitation(userId, hash(rawToken), Instant.now().plus(24, ChronoUnit.HOURS), actorId);
        repository.audit(actorId, "TEAM_MEMBER_CREATED", request.role(), profileId);
        return new TeamMemberResponse(userId, profileId, clean(request.fullName()), email, request.role(), nullable(request.professionalTitle()), nullable(request.bio()), nullable(request.phone()), null, true, profileComplete(request), null, null, "/invite/" + rawToken);
    }

    @Transactional
    public void completeInvitation(CompleteInvitationRequest request) {
        TeamRepository.InvitationAccount invitation = repository.invitationAccount(hash(request.token()), Instant.now())
                .orElseThrow(() -> new AuthException("INVALID_INVITATION", "This invitation is invalid, expired, or has already been used."));
        repository.completeInvitation(invitation.invitationId(), invitation.userId(), passwordEncoder.encode(request.password()));
        repository.audit(invitation.userId(), "INVITATION_ACCEPTED", invitation.role(), invitation.userId());
    }

    public List<TeamRepository.Specialty> specialties() { return repository.specialties(); }

    public List<TeamMemberResponse> members() {
        return repository.members().stream().map(this::response).toList();
    }

    public TeamMemberResponse member(UUID userId) { return repository.member(userId).map(this::response).orElseThrow(() -> new AuthException("TEAM_MEMBER_NOT_FOUND", "Team member not found.")); }

    @Transactional
    public void updateMyProfile(UUID userId, UpdateMyProfileRequest request) {
        if (!repository.teamMemberExists(userId)) throw new AuthException("TEAM_MEMBER_NOT_FOUND", "Team member not found.");
        repository.updateOwnBio(userId, request.bio() == null ? null : request.bio().trim());
        repository.audit(userId, "OWN_PROFILE_UPDATED", "TEAM_MEMBER", userId);
    }

    private TeamMemberResponse response(TeamRepository.TeamMemberRow member) { return new TeamMemberResponse(member.userId(), member.profileId(), member.fullName(), member.email(), member.role(), member.professionalTitle(), member.bio(), member.phone(), member.specialty(), member.active(), profileComplete(member), member.lastSignedInAt(), member.photoKey() == null ? null : "/api/v1/team-members/" + member.userId() + "/photo?v=" + member.profileUpdatedAt().toEpochMilli(), null); }

    private boolean profileComplete(TeamRepository.TeamMemberRow member) {
        return present(member.fullName()) && present(member.email()) && present(member.professionalTitle()) && present(member.bio()) && ("STAFF".equals(member.role()) || present(member.specialty()));
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }

    @Transactional
    public String resendInvitation(UUID userId, UUID actorId) {
        if (!repository.teamMemberExists(userId)) throw new AuthException("TEAM_MEMBER_NOT_FOUND", "Team member not found.");
        repository.revokeOpenInvitations(userId);
        String rawToken = randomToken();
        repository.createInvitation(userId, hash(rawToken), Instant.now().plus(24, ChronoUnit.HOURS), actorId);
        repository.audit(actorId, "TEAM_INVITATION_RESENT", "TEAM_MEMBER", userId);
        return "/invite/" + rawToken;
    }

    @Transactional
    public void setActive(UUID userId, boolean active, UUID actorId) {
        if (!repository.teamMemberExists(userId)) throw new AuthException("TEAM_MEMBER_NOT_FOUND", "Team member not found.");
        repository.setActive(userId, active);
        repository.audit(actorId, active ? "TEAM_MEMBER_ACTIVATED" : "TEAM_MEMBER_DEACTIVATED", "TEAM_MEMBER", userId);
    }

    @Transactional
    public void updateMember(UUID userId, UpdateTeamMemberRequest request, UUID actorId) {
        TeamMemberResponse current = member(userId);
        String email = request.email().trim().toLowerCase();
        if (!current.email().equalsIgnoreCase(email) && repository.emailExists(email)) throw new AuthException("EMAIL_ALREADY_REGISTERED", "Email is already registered.");
        if ("DOCTOR".equals(current.role()) && (request.specialtyId() == null || !repository.specialtyExists(request.specialtyId()))) throw new AuthException("INVALID_SPECIALTY", "Doctors must have a valid specialty.");
        repository.updateMember(userId, clean(request.fullName()), email, nullable(request.professionalTitle()), nullable(request.bio()), nullable(request.phone()), "DOCTOR".equals(current.role()) ? request.specialtyId() : null);
        repository.audit(actorId, "TEAM_MEMBER_UPDATED", "TEAM_MEMBER", userId);
    }

    private boolean profileComplete(CreateTeamMemberRequest request) {
        return !clean(request.fullName()).isBlank() && !request.email().isBlank() && ("STAFF".equals(request.role()) || request.specialtyId() != null);
    }
    private String randomToken() { byte[] value = new byte[32]; RANDOM.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String hash(String value) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String nullable(String value) { String clean = clean(value); return clean.isEmpty() ? null : clean; }
}
