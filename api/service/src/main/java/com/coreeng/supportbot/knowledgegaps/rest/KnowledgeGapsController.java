package com.coreeng.supportbot.knowledgegaps.rest;

import com.coreeng.supportbot.config.KnowledgeGapsProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/knowledge-gaps")
@RequiredArgsConstructor
public class KnowledgeGapsController {
    private final KnowledgeGapsProps knowledgeGapsProps;

    @GetMapping("/enabled")
    public ResponseEntity<KnowledgeGapsStatusUI> getKnowledgeGapsStatus() {
        return ResponseEntity.ok(new KnowledgeGapsStatusUI(knowledgeGapsProps.enabled()));
    }
}

