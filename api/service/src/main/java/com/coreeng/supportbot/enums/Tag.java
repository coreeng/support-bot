package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A ticket tag from {@code enums.tags}.
 *
 * @param product whether the tag names a product ({@code enums.tags[].product: true}). Product tags
 *     feed the Products View and the Support Summary's product breakdown, under the label as
 *     configured — nothing is inferred from or stripped off the label. Defaults to false.
 */
public record Tag(
        String label, String code, @DefaultValue("false") boolean product) implements EnumerationValue {

    /** Bound from configuration; the two-argument form exists for code and tests only. */
    @ConstructorBinding
    public Tag {}

    /** A plain, non-product tag. */
    public Tag(String label, String code) {
        this(label, code, false);
    }
}
