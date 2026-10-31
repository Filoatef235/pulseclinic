package com.pulseclinic.patient;

import com.pulseclinic.patient.dto.WalkInPatientRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/patients")
public class PatientController {
    private final PatientRepository repository;
    PatientController(PatientRepository repository) { this.repository = repository; }
    @GetMapping
    public List<PatientRepository.PatientRow> search(@RequestParam(defaultValue = "") String query, @AuthenticationPrincipal Jwt jwt) { requireOperations(jwt); return repository.search(query); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UUID create(@Valid @RequestBody WalkInPatientRequest request, @AuthenticationPrincipal Jwt jwt) { requireOperations(jwt); return repository.createWalkIn(request.fullName().trim(), request.phone()); }
    private void requireOperations(Jwt jwt) { if (!jwt.getClaimAsStringList("roles").stream().anyMatch(role -> role.equals("ROLE_STAFF") || role.equals("ROLE_ADMIN"))) throw new AccessDeniedException("Staff or admin access is required."); }
}
