package com.pulseclinic.team;

import com.pulseclinic.auth.AuthException;
import com.pulseclinic.team.dto.CompleteInvitationRequest;
import com.pulseclinic.team.dto.CreateTeamMemberRequest;
import com.pulseclinic.team.dto.TeamMemberResponse;
import com.pulseclinic.team.dto.UpdateMyProfileRequest;
import com.pulseclinic.team.dto.UpdateTeamMemberRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.CacheControl;

@RestController
@RequestMapping("/api/v1")
public class TeamController {
    private final TeamService teamService;
    private final TeamRepository repository;
    private final ProfilePhotoService photos;
    TeamController(TeamService teamService, TeamRepository repository, ProfilePhotoService photos) { this.teamService = teamService; this.repository = repository; this.photos = photos; }

    @PostMapping("/admin/team-members")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberResponse create(@Valid @RequestBody CreateTeamMemberRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teamService.create(request, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/admin/specialties")
    public List<TeamRepository.Specialty> specialties(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teamService.specialties();
    }

    @GetMapping("/admin/team-members")
    public List<TeamMemberResponse> members(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teamService.members();
    }

    @GetMapping("/team/profile")
    public TeamMemberResponse myProfile(@AuthenticationPrincipal Jwt jwt) {
        return teamService.member(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/team/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMyProfile(@jakarta.validation.Valid @RequestBody UpdateMyProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        teamService.updateMyProfile(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping(path = "/team/profile/photo", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMyPhoto(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (!repository.teamMemberExists(userId)) throw new AccessDeniedException("This profile is not available.");
        String previousKey = repository.photoKey(userId).orElse(null);
        String newKey = photos.save(userId, file);
        repository.savePhoto(userId, newKey);
        if (previousKey != null && !previousKey.equals(newKey)) photos.delete(previousKey);
        repository.audit(userId, "OWN_PROFILE_PHOTO_REPLACED", "TEAM_MEMBER", userId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/team/profile/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMyPhoto(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String key = repository.photoKey(userId).orElse(null);
        repository.removePhoto(userId);
        if (key != null) photos.delete(key);
        repository.audit(userId, "OWN_PROFILE_PHOTO_REMOVED", "TEAM_MEMBER", userId);
    }

    @GetMapping("/team-members/{userId}/photo")
    public ResponseEntity<Resource> photo(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        UUID actor = UUID.fromString(jwt.getSubject());
        if (!actor.equals(userId) && !jwt.getClaimAsStringList("roles").contains("ROLE_ADMIN")) throw new AccessDeniedException("Profile photo is not available.");
        String key = repository.photoKey(userId).orElseThrow(() -> new AuthException("PROFILE_PHOTO_NOT_FOUND", "Profile photo not found."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(photos.load(key));
    }

    @PostMapping(path = "/admin/team-members/{userId}/photo", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadPhoto(@PathVariable UUID userId, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        if (!repository.teamMemberExists(userId)) throw new AuthException("TEAM_MEMBER_NOT_FOUND", "Team member not found.");
        String previousKey = repository.photoKey(userId).orElse(null);
        String newKey = photos.save(userId, file);
        repository.savePhoto(userId, newKey);
        if (previousKey != null && !previousKey.equals(newKey)) photos.delete(previousKey);
        repository.audit(UUID.fromString(jwt.getSubject()), "PROFILE_PHOTO_REPLACED", "TEAM_MEMBER", userId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/admin/team-members/{userId}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        if (!repository.teamMemberExists(userId)) throw new AuthException("TEAM_MEMBER_NOT_FOUND", "Team member not found.");
        String key = repository.photoKey(userId).orElse(null);
        repository.removePhoto(userId);
        if (key != null) photos.delete(key);
        repository.audit(UUID.fromString(jwt.getSubject()), "PROFILE_PHOTO_REMOVED", "TEAM_MEMBER", userId);
    }

    @PostMapping("/admin/team-members/{userId}/resend-invitation")
    public String resendInvitation(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teamService.resendInvitation(userId, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/admin/team-members/{userId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(@PathVariable UUID userId, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        teamService.setActive(userId, request.active(), UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/admin/team-members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMember(@PathVariable UUID userId, @Valid @RequestBody UpdateTeamMemberRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        teamService.updateMember(userId, request, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/auth/invitations/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeInvitation(@Valid @RequestBody CompleteInvitationRequest request) {
        teamService.completeInvitation(request);
    }

    private void requireAdmin(Jwt jwt) {
        if (!jwt.getClaimAsStringList("roles").contains("ROLE_ADMIN")) throw new AccessDeniedException("Admin access is required.");
    }
    public record ActiveRequest(boolean active) {}
}
