package com.coreeng.supportbot.ticket;

import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.ContextActionsBlock;
import com.slack.api.model.block.ContextBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.RichTextBlock;
import com.slack.api.model.block.UnknownBlock;
import com.slack.api.model.block.UnknownBlockElement;
import com.slack.api.model.block.UnknownContextActionsBlockElement;
import com.slack.api.model.block.UnknownContextBlockElement;
import com.slack.api.model.block.composition.TextObject;
import com.slack.api.model.block.composition.UnknownTextObject;
import com.slack.api.model.block.element.RichTextSectionElement;
import com.slack.api.model.block.element.RichTextUnknownElement;
import org.junit.jupiter.api.Test;

class SlackModalBlockSanitizerTest {
    @Test
    void removesSdkUnknownFallbacksBeforeRenderingModal() {
        RichTextSectionElement.Text beforeUnknown =
                RichTextSectionElement.Text.builder().text("Before ").build();
        RichTextUnknownElement unknown =
                RichTextUnknownElement.builder().type("message_mention").build();
        RichTextSectionElement.Text afterUnknown =
                RichTextSectionElement.Text.builder().text(" after").build();
        RichTextSectionElement section = RichTextSectionElement.builder()
                .elements(ImmutableList.of(beforeUnknown, unknown, afterUnknown))
                .build();
        RichTextBlock richTextBlock = RichTextBlock.builder()
                .blockId("message-block")
                .elements(ImmutableList.of(section))
                .build();
        TextObject contextText = markdownText("Known context");
        ContextBlock contextBlock = ContextBlock.builder()
                .blockId("context-block")
                .elements(ImmutableList.of(
                        UnknownTextObject.builder()
                                .type("unknown_text")
                                .text("ignored")
                                .build(),
                        UnknownContextBlockElement.builder()
                                .type("unknown_context")
                                .build(),
                        contextText))
                .build();

        ImmutableList<LayoutBlock> result = SlackModalBlockSanitizer.sanitize(ImmutableList.of(
                UnknownBlock.builder()
                        .type("unknown_block")
                        .blockId("unknown-block")
                        .build(),
                richTextBlock,
                ActionsBlock.builder()
                        .blockId("actions-block")
                        .elements(ImmutableList.of(UnknownBlockElement.builder()
                                .type("unknown_action")
                                .build()))
                        .build(),
                ContextActionsBlock.builder()
                        .blockId("context-actions-block")
                        .elements(ImmutableList.of(UnknownContextActionsBlockElement.builder()
                                .type("unknown_context_action")
                                .build()))
                        .build(),
                contextBlock));

        assertThat(result)
                .hasSize(2)
                .noneMatch(UnknownBlock.class::isInstance)
                .noneMatch(ActionsBlock.class::isInstance)
                .noneMatch(ContextActionsBlock.class::isInstance);
        RichTextBlock sanitizedBlock = (RichTextBlock) result.getFirst();
        RichTextSectionElement sanitizedSection =
                (RichTextSectionElement) sanitizedBlock.getElements().getFirst();
        assertThat(sanitizedSection.getElements())
                .containsExactly(beforeUnknown, afterUnknown)
                .noneMatch(RichTextUnknownElement.class::isInstance);
        ContextBlock sanitizedContextBlock = (ContextBlock) result.get(1);
        assertThat(sanitizedContextBlock.getElements())
                .containsExactly(contextText)
                .noneMatch(UnknownTextObject.class::isInstance)
                .noneMatch(UnknownContextBlockElement.class::isInstance);
    }
}
