package com.coreeng.supportbot.util;

import static org.jooq.impl.DSL.field;

import java.util.Objects;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;

public class JooqUtils {
    private JooqUtils() {}

    public static Field<Long> bigCount() {
        return field("count(1)", SQLDataType.BIGINT);
    }

    public static double nullToZero(Double value) {
        return Objects.requireNonNullElse(value, 0.0);
    }

    public static long nullToZero(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
