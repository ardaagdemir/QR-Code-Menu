package com.qrmenu.branchprovisioning;

import com.qrmenu.menu.MenuService;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.tenant.DeliveryModel;
import com.qrmenu.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Covers BranchProvisioningService.createBranchWithDefaultCatalog's rollback guarantee:
 * TenantService.createBranch and MenuService.assignActiveCatalogToBranch now run inside one
 * outer @Transactional method, so if catalog provisioning throws, the branch insert (and its
 * CREATED audit entry) must roll back too - no half-created branch left behind that the
 * two-separate-calls design used to leave, since each of those calls used to commit in its
 * own transaction.
 */
class BranchProvisioningRollbackIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private MenuService menuService;

    @Autowired
    private BranchProvisioningService branchProvisioningService;

    @Autowired
    private TenantService tenantService;

    @Test
    void catalogProvisioningFailureRollsBackTheBranchCreation() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Rollback Business");
        UUID businessUuid = UUID.fromString(businessId);

        doThrow(new RuntimeException("boom")).when(menuService).assignActiveCatalogToBranch(any(), any(), any());

        assertThatThrownBy(() -> branchProvisioningService.createBranchWithDefaultCatalog(
                        businessUuid, "Rollback Şube", true, null, DeliveryModel.CUSTOMER_PICKUP, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        assertThat(tenantService.listBranches(businessUuid)).isEmpty();
    }
}
