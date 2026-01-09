package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assignment")
@RequiredArgsConstructor
public class AssignmentController {
    private final TicketAssignmentProps assignmentProps;

    @GetMapping("/enabled")
    public ResponseEntity<AssignmentStatusUI> isAssignmentEnabled() {
        return ResponseEntity.ok(new AssignmentStatusUI(assignmentProps.enabled()));
    }
}

