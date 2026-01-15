package com.coreeng.supportbot;

import com.coreeng.supportbot.testkit.SlackWiremock;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * JUnit extension that injects wiremock instances into test classes based on field types.
 * Supports injection of WiremockManager and individual wiremock instances.
 * Also verifies that all test stubs are cleaned up after each test.
 */
public class TestKitExtension implements TestInstancePostProcessor, ParameterResolver, AfterEachCallback {
    static final ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(TestKitExtension.class);
    private static final Logger logger = LoggerFactory.getLogger(TestKitExtension.class);

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        Class<?> testClass = testInstance.getClass();
        Field[] fields = testClass.getDeclaredFields();

        for (Field field : fields) {
            Object valueToInject = getValueForField(field.getType(), context);
            if (valueToInject != null) {
                field.setAccessible(true);
                field.set(testInstance, valueToInject);
                logger.debug("Injected {} into field {} of class {}",
                    field.getType().getSimpleName(), field.getName(), testClass.getSimpleName());
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return getValueForField(parameterContext.getParameter().getType(), extensionContext) != null;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> parameterType = parameterContext.getParameter().getType();
        Object value = getValueForField(parameterType, extensionContext);
        if (value != null) {
            logger.debug("Resolved parameter of type {} for method {}",
                parameterType.getSimpleName(), parameterContext.getDeclaringExecutable().getName());
        }
        return value;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        SlackWiremock slackWiremock = (SlackWiremock) getValueForField(SlackWiremock.class, context);
        if (slackWiremock != null) {
            try {
                // First, assert that no test stubs remain (this will fail the test if stubs are left)
                slackWiremock.assertNoTestStubsRemaining();
                // Then, assert that no unhandled requests were made
                slackWiremock.assertNoUnhandledRequests();
            } finally {
                // Always clean up remaining test stubs to prevent test interference
                slackWiremock.cleanupTestStubs();
                // Clear request journal to prevent interference between tests
                slackWiremock.clearRequestJournal();
            }
        }
    }

    private Object getValueForField(Class<?> fieldType, ExtensionContext extensionContext) {
        Object result = extensionContext.getStore(namespace).get(fieldType, fieldType);
        if (result != null) {
            return result;
        }
        logger.debug("No instance of type {} available for injection", fieldType.getSimpleName());
        return null;
    }
}