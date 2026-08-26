package com.qrmenu.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #15: MediaStoragePort upload endpoints. Uploads only need to match each
 * format's magic-byte signature (DetectedFileType.sniff) to pass validation - the test
 * fixtures below are not real decodable images/PDFs, just correctly-prefixed byte
 * arrays, since the adapter never actually decodes the content.
 */
class MediaUploadFlowIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PNG_BYTES =
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] PDF_BYTES = "%PDF-1.4 fake receipt".getBytes(StandardCharsets.US_ASCII);

    @Test
    void businessAdminCanUploadAndAttachAProductImage() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Media Business 1");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Pizza", 25000);
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "media-admin-1@example.com", "BUSINESS_ADMIN");

        JsonNode uploadResult = objectMapper.readTree(mockMvc.perform(multipart("/api/staff/media/product-images")
                        .file(new MockMultipartFile("file", "dish.png", "image/png", PNG_BYTES))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String imageUrl = uploadResult.get("url").asText();
        assertThat(imageUrl).contains("/media/product-images/").endsWith(".png");

        JsonNode product = objectMapper.readTree(mockMvc.perform(patch("/api/staff/products/{productId}", productId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pizza\",\"description\":null,\"basePriceMinorUnits\":25000,"
                                + "\"active\":true,\"estimatedPreparationMinutes\":null,"
                                + "\"allergens\":[],\"imageUrl\":\"" + imageUrl + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(product.get("imageUrl").asText()).isEqualTo(imageUrl);
    }

    @Test
    void businessAdminCanUploadAPdfReceipt() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Media Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "media-admin-2@example.com");

        JsonNode uploadResult = objectMapper.readTree(mockMvc.perform(multipart("/api/staff/media/receipts")
                        .file(new MockMultipartFile("file", "fis.pdf", "application/pdf", PDF_BYTES))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String receiptUrl = uploadResult.get("url").asText();
        assertThat(receiptUrl).contains("/media/receipts/").endsWith(".pdf");

        // Unlike product images, receipts must not be reachable on the public /media/** path.
        mockMvc.perform(get(receiptUrl.replaceFirst("^https?://[^/]+", ""))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadWithUnrecognizedContentIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Media Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "media-admin-3@example.com");

        mockMvc.perform(multipart("/api/staff/media/product-images")
                        .file(new MockMultipartFile(
                                "file", "not-an-image.txt", "image/png", "just plain text, not a real image".getBytes(StandardCharsets.UTF_8)))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedProductImageIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Media Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "media-admin-4@example.com");

        byte[] oversized = new byte[6 * 1024 * 1024];
        System.arraycopy(PNG_BYTES, 0, oversized, 0, PNG_BYTES.length);
        Arrays.fill(oversized, PNG_BYTES.length, oversized.length, (byte) 0);

        mockMvc.perform(multipart("/api/staff/media/product-images")
                        .file(new MockMultipartFile("file", "huge.png", "image/png", oversized))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cashierCannotUploadProductImagesOrReceipts() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Media Business 5");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube 1");
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"media-cashier-5@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, "media-cashier-5@example.com");

        mockMvc.perform(multipart("/api/staff/media/product-images")
                        .file(new MockMultipartFile("file", "dish.png", "image/png", PNG_BYTES))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/staff/media/receipts")
                        .file(new MockMultipartFile("file", "fis.pdf", "application/pdf", PDF_BYTES))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isForbidden());
    }
}
