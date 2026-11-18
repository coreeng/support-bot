package com.coreeng.supportbot.troubleshooting.rest;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.TroubleshootingProps;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Always registered, so the UI gets {@code enabled: false} instead of a 404 when the feature is off. */
@RestController
@RequestMapping("/troubleshooting")
@RequiredArgsConstructor
public class TroubleshootingEnabledController {
    private final TroubleshootingProps props;
    private final LlmProps llmProps;

    @GetMapping("/enabled")
    public TroubleshootingStatusUI enabled() {
        return new TroubleshootingStatusUI(props.enabled() && llmProps.enabled());
    }
}
