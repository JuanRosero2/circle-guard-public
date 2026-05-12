package com.circleguard.promotion.controller;

import com.circleguard.promotion.service.HealthStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthStatusController {
    private final HealthStatusService statusService;

    @PostMapping("/report")
    @PreAuthorize("hasAnyAuthority('HEALTH_CENTER', 'ADMIN')")
    public ResponseEntity<Void> reportStatus(@RequestBody Map<String, Object> request) {
        String anonymousId = (String) request.get("anonymousId");
        String status = (String) request.get("status");
        boolean override = (request.containsKey("adminOverride") && (boolean) request.get("adminOverride")) ||
                          (request.containsKey("override") && (boolean) request.get("override"));
        
        statusService.updateStatus(anonymousId, status, override);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirmed")
    @PreAuthorize("hasAnyAuthority('HEALTH_CENTER', 'ADMIN')")
    public ResponseEntity<Void> confirmPositive(@RequestBody Map<String, String> request) {
        String anonymousId = request.get("anonymousId");
        statusService.updateStatus(anonymousId, "CONFIRMED");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recovery/{id}")
    @PreAuthorize("hasAnyAuthority('HEALTH_CENTER', 'ADMIN')")
    public ResponseEntity<Void> recover(@PathVariable String id) {
        statusService.promoteToRecovered(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyAuthority('HEALTH_CENTER', 'ADMIN')")
    public ResponseEntity<Void> resolve(@RequestBody Map<String, Object> request) {
        String anonymousId = (String) request.get("anonymousId");
        boolean override = (request.containsKey("adminOverride") && (boolean) request.get("adminOverride")) ||
                          (request.containsKey("override") && (boolean) request.get("override"));

        statusService.resolveStatus(anonymousId, override);
        return ResponseEntity.ok().build();
    }
}
