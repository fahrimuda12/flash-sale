package com.fahri.audit.seeder;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audit/seed")
@RequiredArgsConstructor
public class AuditSeederController {

    private final AuditDataSeeder auditDataSeeder;

    @PostMapping
    public ResponseEntity<Map<String, Object>> seedAuditLogs(
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> summary = auditDataSeeder.seedDynamic(count, force);
        return ResponseEntity.ok(summary);
    }
}
