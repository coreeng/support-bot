package com.infradna.tool.bridge_method_injector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Test-only compile shim for Hub4j's optional bridge-method annotation. This is
 * needed because Hub4jPullRequestTest's concrete GHPullRequest subclass causes
 * javac to load annotation metadata from inherited Hub4j methods, but the real
 * optional annotation jar is not on this project's test compile classpath.
 *
 * Remove this once the tests no longer subclass GHPullRequest directly, or once
 * the real bridge-method annotation is provided on the test compile classpath.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeMethodsAdded {}
