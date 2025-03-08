package com.coreeng.supportbot.util;

import org.jooq.Field;
import org.jooq.impl.SQLDataType;

import static org.jooq.impl.DSL.field;

public class JooqUtils {
    private JooqUtils() {}

    public static Field<Long> bigCount() {
        return field("count(1)", SQLDataType.BIGINT);
    }
}
