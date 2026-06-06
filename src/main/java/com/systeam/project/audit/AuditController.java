package com.systeam.project.audit;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.project.audit.dto.AuditFindingRequest;
import com.systeam.project.audit.dto.AuditFindingResponse;
import com.systeam.security.JwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{id}/audit")
@PreAuthorize("hasAuthority('project:audit')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * POST /api/projects/{id}/audit
     * Submit an audit finding for the given project.
     * Requires: project:audit authority
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditFindingResponse submitFinding(
            @PathVariable Long id,
            @RequestBody @Valid AuditFindingRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return auditService.submitFinding(id, principal.userId(), request);
    }

    /**
     * GET /api/projects/{id}/audit
     * List all audit findings for the given project, ordered by created_at DESC.
     * Requires: project:audit authority
     */
    @GetMapping
    public List<AuditFindingResponse> listFindings(@PathVariable Long id) {
        return auditService.listFindings(id);
    }
}
