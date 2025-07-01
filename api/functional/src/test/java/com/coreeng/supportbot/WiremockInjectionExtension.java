package com.coreeng.supportbot;

import com.coreeng.supportbot.wiremock.AzureWiremock;
import com.coreeng.supportbot.wiremock.GcpWiremock;
import com.coreeng.supportbot.wiremock.KubernetesWiremock;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import com.coreeng.supportbot.wiremock.WiremockManager;
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
 */
public class WiremockInjectionExtension implements TestInstancePostProcessor, ParameterResolver {
    private static final Logger logger = LoggerFactory.getLogger(WiremockInjectionExtension.class);

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        WiremockManager wiremockManager = getWiremockManager(context);
        if (wiremockManager == null) {
            return;
        }

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
    
    private Object getValueForField(Class<?> fieldType, ExtensionContext extensionContext) {
        WiremockManager wiremockManager = getWiremockManager(extensionContext);
        if (wiremockManager == null) {
            return null;
        }

        if (fieldType == WiremockManager.class) {
            return wiremockManager;
        } else if (fieldType == SlackWiremock.class) {
            return wiremockManager.slackWiremock;
        } else if (fieldType == KubernetesWiremock.class) {
            return wiremockManager.kubernetesWiremock;
        } else if (fieldType == AzureWiremock.class) {
            return wiremockManager.azureWiremock;
        } else if (fieldType == GcpWiremock.class) {
            return wiremockManager.gcpWiremock;
        }

        logger.debug("No wiremock instance of type {} available for injection", fieldType.getSimpleName());
        return null;
    }

    private static WiremockManager getWiremockManager(ExtensionContext context) {
        var wiremockManager = (WiremockManager) context.getStore(ExtensionContext.Namespace.GLOBAL).get(WiremockManager.class);
        if (wiremockManager == null) {
            logger.warn("WiremockManager not available for injection");
            return null;
        }
        return wiremockManager;
    }
}