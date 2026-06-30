package com.coreeng.supportbot.ticket;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.ContextActionsBlock;
import com.slack.api.model.block.ContextActionsBlockElement;
import com.slack.api.model.block.ContextBlock;
import com.slack.api.model.block.ContextBlockElement;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.RichTextBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.UnknownBlock;
import com.slack.api.model.block.UnknownBlockElement;
import com.slack.api.model.block.UnknownContextActionsBlockElement;
import com.slack.api.model.block.UnknownContextBlockElement;
import com.slack.api.model.block.composition.TextObject;
import com.slack.api.model.block.composition.UnknownTextObject;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.RichTextElement;
import com.slack.api.model.block.element.RichTextListElement;
import com.slack.api.model.block.element.RichTextPreformattedElement;
import com.slack.api.model.block.element.RichTextQuoteElement;
import com.slack.api.model.block.element.RichTextSectionElement;
import com.slack.api.model.block.element.RichTextUnknownElement;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class SlackModalBlockSanitizer {
    private SlackModalBlockSanitizer() {}

    static ImmutableList<LayoutBlock> sanitize(List<LayoutBlock> blocks) {
        return blocks.stream()
                .map(SlackModalBlockSanitizer::sanitizeLayoutBlock)
                .filter(Objects::nonNull)
                .collect(toImmutableList());
    }

    @Nullable private static LayoutBlock sanitizeLayoutBlock(LayoutBlock block) {
        switch (block) {
            case UnknownBlock unknownBlock -> {
                return null;
            }
            case RichTextBlock richTextBlock -> {
                ImmutableList<BlockElement> elements = richTextBlock.getElements().stream()
                        .map(SlackModalBlockSanitizer::sanitizeBlockElement)
                        .filter(Objects::nonNull)
                        .collect(toImmutableList());
                if (elements.isEmpty()) {
                    return null;
                }

                return RichTextBlock.builder()
                        .blockId(richTextBlock.getBlockId())
                        .elements(elements)
                        .build();
            }
            case ActionsBlock actionsBlock -> {
                ImmutableList<BlockElement> elements = actionsBlock.getElements().stream()
                        .map(SlackModalBlockSanitizer::sanitizeBlockElement)
                        .filter(Objects::nonNull)
                        .collect(toImmutableList());
                if (elements.isEmpty()) {
                    return null;
                }

                return ActionsBlock.builder()
                        .blockId(actionsBlock.getBlockId())
                        .elements(elements)
                        .build();
            }
            case ContextBlock contextBlock -> {
                ImmutableList<ContextBlockElement> elements = contextBlock.getElements().stream()
                        .map(SlackModalBlockSanitizer::sanitizeContextBlockElement)
                        .filter(Objects::nonNull)
                        .collect(toImmutableList());
                if (elements.isEmpty()) {
                    return null;
                }

                return ContextBlock.builder()
                        .blockId(contextBlock.getBlockId())
                        .elements(elements)
                        .build();
            }
            case ContextActionsBlock contextActionsBlock -> {
                ImmutableList<ContextActionsBlockElement> elements = contextActionsBlock.getElements().stream()
                        .map(SlackModalBlockSanitizer::sanitizeContextActionsBlockElement)
                        .filter(Objects::nonNull)
                        .collect(toImmutableList());
                if (elements.isEmpty()) {
                    return null;
                }

                return ContextActionsBlock.builder()
                        .blockId(contextActionsBlock.getBlockId())
                        .elements(elements)
                        .build();
            }
            case SectionBlock sectionBlock -> {
                TextObject text = sanitizeTextObject(sectionBlock.getText());
                ImmutableList<TextObject> fields = sectionBlock.getFields() == null
                        ? ImmutableList.of()
                        : sectionBlock.getFields().stream()
                                .map(SlackModalBlockSanitizer::sanitizeTextObject)
                                .filter(Objects::nonNull)
                                .collect(toImmutableList());
                BlockElement accessory =
                        sectionBlock.getAccessory() != null ? sanitizeBlockElement(sectionBlock.getAccessory()) : null;
                if (text == null && fields.isEmpty()) {
                    return null;
                }

                return SectionBlock.builder()
                        .blockId(sectionBlock.getBlockId())
                        .text(text)
                        .fields(fields.isEmpty() ? null : fields)
                        .accessory(accessory)
                        .expand(sectionBlock.getExpand())
                        .build();
            }
            case InputBlock inputBlock -> {
                BlockElement element =
                        inputBlock.getElement() != null ? sanitizeBlockElement(inputBlock.getElement()) : null;
                if (element == null) {
                    return null;
                }

                return InputBlock.builder()
                        .blockId(inputBlock.getBlockId())
                        .label(inputBlock.getLabel())
                        .element(element)
                        .dispatchAction(inputBlock.getDispatchAction())
                        .hint(inputBlock.getHint())
                        .optional(inputBlock.isOptional())
                        .build();
            }
            default -> {}
        }

        return block;
    }

    @Nullable private static TextObject sanitizeTextObject(@Nullable TextObject textObject) {
        return textObject instanceof UnknownTextObject ? null : textObject;
    }

    @Nullable private static ContextBlockElement sanitizeContextBlockElement(ContextBlockElement element) {
        if (element instanceof UnknownContextBlockElement || element instanceof UnknownTextObject) {
            return null;
        }
        return element;
    }

    @Nullable private static ContextActionsBlockElement sanitizeContextActionsBlockElement(ContextActionsBlockElement element) {
        if (element instanceof UnknownContextActionsBlockElement) {
            return null;
        }
        return element;
    }

    @Nullable private static BlockElement sanitizeBlockElement(BlockElement element) {
        if (element instanceof UnknownBlockElement) {
            return null;
        }
        if (!(element instanceof RichTextElement richTextElement)) {
            return element;
        }
        RichTextElement sanitized = sanitizeRichTextElement(richTextElement);
        return sanitized instanceof BlockElement blockElement ? blockElement : null;
    }

    @Nullable private static RichTextElement sanitizeRichTextElement(RichTextElement element) {
        if (element instanceof RichTextUnknownElement) {
            return null;
        }

        if (element instanceof RichTextSectionElement sectionElement) {
            ImmutableList<RichTextElement> elements = sanitizeRichTextElements(sectionElement.getElements());
            if (elements.isEmpty()) {
                return null;
            }
            return RichTextSectionElement.builder().elements(elements).build();
        }

        if (element instanceof RichTextListElement listElement) {
            ImmutableList<RichTextElement> elements = sanitizeRichTextElements(listElement.getElements());
            if (elements.isEmpty()) {
                return null;
            }
            return RichTextListElement.builder()
                    .elements(elements)
                    .style(listElement.getStyle())
                    .indent(listElement.getIndent())
                    .offset(listElement.getOffset())
                    .border(listElement.getBorder())
                    .build();
        }

        if (element instanceof RichTextQuoteElement quoteElement) {
            ImmutableList<RichTextElement> elements = sanitizeRichTextElements(quoteElement.getElements());
            if (elements.isEmpty()) {
                return null;
            }
            return RichTextQuoteElement.builder()
                    .elements(elements)
                    .border(quoteElement.getBorder())
                    .build();
        }

        if (element instanceof RichTextPreformattedElement preformattedElement) {
            ImmutableList<RichTextElement> elements = sanitizeRichTextElements(preformattedElement.getElements());
            if (elements.isEmpty()) {
                return null;
            }
            return RichTextPreformattedElement.builder()
                    .elements(elements)
                    .border(preformattedElement.getBorder())
                    .build();
        }

        return element;
    }

    private static ImmutableList<RichTextElement> sanitizeRichTextElements(List<RichTextElement> elements) {
        return elements.stream()
                .map(SlackModalBlockSanitizer::sanitizeRichTextElement)
                .filter(Objects::nonNull)
                .collect(toImmutableList());
    }
}
