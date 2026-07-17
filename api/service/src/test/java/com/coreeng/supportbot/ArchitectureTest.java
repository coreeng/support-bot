package com.coreeng.supportbot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.coreeng.supportbot");

    @Test
    void restControllersDoNotDependDirectlyOnRepositories() {
        DescribedPredicate<JavaClass> repositoryTypes = new DescribedPredicate<>("repository types") {
            @Override
            public boolean test(JavaClass javaClass) {
                return isRepositoryOrNestedType(javaClass);
            }
        };
        ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .dependOnClassesThat(repositoryTypes)
                .because("REST controllers must use an application service boundary for persistence access");

        rule.check(PRODUCTION_CLASSES);
    }

    private static boolean isRepositoryOrNestedType(JavaClass javaClass) {
        if (javaClass.getSimpleName().endsWith("Repository") || javaClass.isAnnotatedWith(Repository.class)) {
            return true;
        }
        return javaClass
                .getEnclosingClass()
                .map(ArchitectureTest::isRepositoryOrNestedType)
                .orElse(false);
    }
}
