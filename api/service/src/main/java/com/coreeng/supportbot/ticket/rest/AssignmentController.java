package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assignment")
@RequiredArgsConstructor
public class AssignmentController {
    private final TicketAssignmentProps assignmentProps;
    private final BulkReassignmentService bulkReassignmentService;

    @GetMapping("/enabled")
    public ResponseEntity<AssignmentStatusUI> getAssignmentStatus() {
        return ResponseEntity.ok(new AssignmentStatusUI(assignmentProps.enabled()));
    }

    @PostMapping("/bulk-reassign")
    public ResponseEntity<BulkReassignResultUI> bulkReassign(@RequestBody BulkReassignRequest request) {
        BulkReassignResultUI result = bulkReassignmentService.bulkReassign(request);

        if (result.successCount() == 0 && result.skippedCount() == 0) {
            return ResponseEntity.badRequest().body(result);
        }
        
        return ResponseEntity.ok(result);
    }
}

