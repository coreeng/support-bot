package com.coreeng.supportbot.testkit;

import java.math.BigDecimal;
import net.javacrumbs.jsonunit.core.ParametrizedMatcher;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class TimestampIsCloseMatcher extends BaseMatcher<Object> implements ParametrizedMatcher {
    private long timestamp;

    @Override
    public void setParameter(String parameter) {
        this.timestamp = Long.parseLong(parameter);
    }

    @Override
    public boolean matches(Object actual) {
        if (actual instanceof BigDecimal actualNum) {
            return Math.abs(actualNum.longValueExact() - timestamp) <= 1;
        } else {
            return false;
        }
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("timestamp is +-1 compared to ").appendValue(timestamp);
    }
}
