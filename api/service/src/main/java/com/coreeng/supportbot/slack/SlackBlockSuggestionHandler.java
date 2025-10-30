package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.interactive_components.response.BlockSuggestionResponse;
import com.slack.api.bolt.context.builtin.BlockSuggestionContext;
import com.slack.api.bolt.request.builtin.BlockSuggestionRequest;

import java.util.regex.Pattern;

public interface SlackBlockSuggestionHandler {
    Pattern getPattern();
    BlockSuggestionResponse apply(BlockSuggestionRequest req, BlockSuggestionContext ctx);
}
