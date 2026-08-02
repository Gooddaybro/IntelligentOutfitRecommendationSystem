package com.recommendation.intelligentoutfitrecommendationsystem.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = ModuleArchitectureTests.BASE_PACKAGE,
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTests {

    static final String BASE_PACKAGE =
            "com.recommendation.intelligentoutfitrecommendationsystem";

    @ArchTest
    static final ArchRule TOP_LEVEL_MODULES_MUST_BE_FREE_OF_CYCLES =
            slices().matching(BASE_PACKAGE + ".(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_MAPPERS =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().resideInAnyPackage("..mapper..");

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEPEND_ON_BUSINESS_MODULES =
            noClasses().that().resideInAPackage(BASE_PACKAGE + ".common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..aftersale..",
                            "..assistant..",
                            "..auth..",
                            "..behavior..",
                            "..cart..",
                            "..conversation..",
                            "..favorite..",
                            "..inventory..",
                            "..order..",
                            "..payment..",
                            "..product..",
                            "..user..");

    @ArchTest
    static final ArchRule ADMIN_SERVICES_MUST_NOT_DEPEND_ON_JDBC =
            noClasses().that().resideInAPackage(BASE_PACKAGE + ".admin.service..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.jdbc..",
                            "java.sql..",
                            "javax.sql..");

    @ArchTest
    static final ArchRule MAPPERS_MUST_STAY_INSIDE_THEIR_MODULE =
            classes().should(new ArchCondition<>(
                    "access only Mappers from their own module") {
                @Override
                public void check(JavaClass source, com.tngtech.archunit.lang.ConditionEvents events) {
                    String sourceModule = moduleOf(source.getPackageName());
                    for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        if (!target.getPackageName().contains(".mapper")) {
                            continue;
                        }
                        String targetModule = moduleOf(target.getPackageName());
                        boolean allowed = sourceModule.equals(targetModule);
                        events.add(new SimpleConditionEvent(
                                dependency,
                                allowed,
                                source.getName() + " accesses " + target.getName()));
                    }
                }
            });

    @ArchTest
    static final ArchRule ASSISTANT_MUST_USE_PUBLIC_CATALOG_AND_CONVERSATION_BOUNDARIES =
            noClasses().that().resideInAPackage(BASE_PACKAGE + ".assistant..")
                    .should().dependOnClassesThat().haveFullyQualifiedName(
                            BASE_PACKAGE + ".product.service.ProductCatalogService")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName(
                            BASE_PACKAGE + ".conversation.service.ConversationService")
                    .orShould().dependOnClassesThat().resideInAnyPackage(
                            BASE_PACKAGE + ".conversation.mapper..",
                            BASE_PACKAGE + ".conversation.model..");

    private static String moduleOf(String packageName) {
        String prefix = BASE_PACKAGE + ".";
        if (!packageName.startsWith(prefix)) {
            return "external";
        }
        String remaining = packageName.substring(prefix.length());
        int separator = remaining.indexOf('.');
        return separator < 0 ? remaining : remaining.substring(0, separator);
    }
}
