package com.coreeng.supportbot.util;

import static org.jooq.impl.DSL.field;

import org.jooq.Field;
import org.jooq.impl.SQLDataType;

public class JooqUtils {
    private JooqUtils() {}

    public static Field<Long> bigCount() {
        return field("count(1)", SQLDataType.BIGINT);
    }
}
