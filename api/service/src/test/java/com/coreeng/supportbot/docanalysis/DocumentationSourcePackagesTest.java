package com.coreeng.supportbot.docanalysis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Each documentation source is a package directly under {@value #SOURCES_PACKAGE}, such as
 * {@code docanalysis.source.github}, with any subpackages of its own. Nothing outside a source's
 * package may refer to its types, so a source can be replaced without changing the analysis.
 */
class DocumentationSourcePackagesTest {

    static final String SOURCES_PACKAGE = "com.coreeng.supportbot.docanalysis.source";

    private static final String FIXTURES_PACKAGE = "com.coreeng.supportbot.docanalysis.archfixture";

    @Test
    void nothingOutsideASourcePackageRefersToItsTypes() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.coreeng.supportbot");

        sourceTypesStayInTheirPackage(SOURCES_PACKAGE).check(production);
    }

    @Test
    void reportsTheAnalysisAndAnotherSourceReferringToASourcesTypes() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES_PACKAGE);

        EvaluationResult result =
                sourceTypesStayInTheirPackage(FIXTURES_PACKAGE + ".source").evaluate(fixtures);

        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("archfixture.FixtureAnalysis")
                        .contains("source.alpha.AlphaSource"))
                .anySatisfy(detail -> assertThat(detail)
                        .contains("source.beta.BetaSource")
                        .contains("source.alpha.internal.AlphaIndex"))
                .allSatisfy(detail -> assertThat(detail).containsAnyOf("FixtureAnalysis", "BetaSource"));
    }

    static ArchRule sourceTypesStayInTheirPackage(String sourcesPackage) {
        return classes()
                .should(
                        new ArchCondition<JavaClass>(
                                "refer to a documentation source's types only from inside that source's package") {
                            @Override
                            public void check(JavaClass origin, ConditionEvents events) {
                                Optional<String> originSource = sourceOf(origin, sourcesPackage);
                                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                                    Optional<String> targetSource =
                                            sourceOf(dependency.getTargetClass(), sourcesPackage);
                                    if (targetSource.isPresent() && !Objects.equals(targetSource, originSource)) {
                                        events.add(
                                                SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                                    }
                                }
                            }
                        });
    }

    private static Optional<String> sourceOf(JavaClass javaClass, String sourcesPackage) {
        String packageName = javaClass.getBaseComponentType().getPackageName();
        String prefix = sourcesPackage + ".";
        if (!packageName.startsWith(prefix)) {
            return Optional.empty();
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
    }
}
