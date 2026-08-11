package com.qrmenu.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith boundary rule from docs/product-requirements.md
 * Section 2: "modüller arası doğrudan repository erişimi yasaktır, iletişim açık
 * servis arayüzleri ... üzerinden yapılır." Cross-module code may depend on a module's
 * top-level public types (e.g. tenant.TableReference, tenant.TenantService) but never
 * reach into its .repository package directly.
 *
 * Uses ArchUnit as a plain assertion library from ordinary JUnit 5 @Test methods
 * rather than the separate archunit-junit5 test engine (@AnalyzeClasses/@ArchTest):
 * that engine was not being picked up by Maven Surefire's multi-engine discovery in
 * this project and silently ran zero tests.
 */
class ModuleBoundaryTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.qrmenu");
    }

    @Test
    void tenantRepositoriesAreOnlyUsedWithinTheTenantModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.tenant..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.tenant.repository..")
                .because("cross-module access must go through TenantService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void customerSessionRepositoriesAreOnlyUsedWithinTheCustomerSessionModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.customersession..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.customersession.repository..")
                .because("cross-module access must go through CustomerSessionService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void menuRepositoriesAreOnlyUsedWithinTheMenuModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.menu..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.menu.repository..")
                .because("cross-module access must go through MenuService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void orderingRepositoriesAreOnlyUsedWithinTheOrderingModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.ordering..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.ordering.repository..")
                .because("cross-module access must go through OrderingService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void paymentRepositoriesAreOnlyUsedWithinThePaymentModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.payment..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.payment.repository..")
                .because("cross-module access must go through PaymentService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void refundRepositoriesAreOnlyUsedWithinTheRefundModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.refund..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.refund.repository..")
                .because("cross-module access must go through RefundService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void staffAccessRepositoriesAreOnlyUsedWithinTheStaffAccessModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.staffaccess..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.staffaccess.repository..")
                .because("cross-module access must go through StaffAuthService, not its repositories");

        rule.check(importedClasses);
    }

    @Test
    void auditRepositoriesAreOnlyUsedWithinTheAuditModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.qrmenu.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.qrmenu.audit.repository..")
                .because("cross-module access must go through AuditService, not its repositories");

        rule.check(importedClasses);
    }
}
