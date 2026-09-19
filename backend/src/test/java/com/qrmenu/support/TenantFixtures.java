package com.qrmenu.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Builds business/branch/table/QR-token chains through the real internal HTTP API
 * (not by touching repositories directly), shared by every module's integration tests
 * that need "a table with an active QR token" as a starting point.
 */
public final class TenantFixtures {

    private TenantFixtures() {
    }

    public record TableFixture(String businessId, String branchId, String tableId, String qrToken) {
    }

    public static TableFixture createTableWithActiveQrToken(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String label) throws Exception {
        String businessId = createBusiness(mockMvc, objectMapper, adminToken, "Fixture Business " + label);
        String branchId = createBranch(mockMvc, objectMapper, adminToken, businessId, "Fixture Branch " + label);
        String tableId = createTable(mockMvc, objectMapper, adminToken, businessId, branchId, label);
        String qrToken = createQrToken(mockMvc, objectMapper, adminToken, businessId, tableId);
        return new TableFixture(businessId, branchId, tableId, qrToken);
    }

    public static String createBusiness(MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String name)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses").header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\"}",
                201);
        return body.get("id").asText();
    }

    public static String createBranch(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String businessId, String name)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/branches", businessId).header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\"}",
                201);
        return body.get("id").asText();
    }

    /** deliveryModel: "CUSTOMER_PICKUP" or "WAITER_DELIVERY" (Milestone 9). */
    public static String createBranch(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String businessId, String name, String deliveryModel)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/branches", businessId).header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\",\"deliveryModel\":\"" + deliveryModel + "\"}",
                201);
        return body.get("id").asText();
    }

    public static String createTable(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String adminToken,
            String businessId,
            String branchId,
            String label)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/branches/{branchId}/tables", businessId, branchId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"label\":\"" + label + "\"}",
                201);
        return body.get("id").asText();
    }

    public static String createQrToken(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String businessId, String tableId)
            throws Exception {
        return regenerateQrToken(mockMvc, objectMapper, adminToken, businessId, tableId).get("token").asText();
    }

    /** Rotates the table's QR token (revoking any current ACTIVE one) and returns the full new-token JSON. */
    public static JsonNode regenerateQrToken(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String businessId, String tableId)
            throws Exception {
        return perform(
                mockMvc,
                post("/internal/businesses/{businessId}/tables/{tableId}/qr-tokens", businessId, tableId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                null,
                201);
    }

    public static void revokeQrToken(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String businessId, String qrTokenId)
            throws Exception {
        mockMvc.perform(post("/internal/businesses/{businessId}/qr-tokens/{qrTokenId}/revoke", businessId, qrTokenId)
                        .header("X-Internal-Admin-Token", adminToken))
                .andExpect(status().isNoContent());
    }

    public static String createMenuCategory(
            MockMvc mockMvc, ObjectMapper objectMapper, String adminToken, String businessId, String name)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/menu-categories", businessId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\"}",
                201);
        return body.get("id").asText();
    }

    public static String createProduct(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String adminToken,
            String businessId,
            String categoryId,
            String name,
            long basePriceMinorUnits)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/products", businessId).header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name + "\",\"basePriceMinorUnits\":"
                        + basePriceMinorUnits + "}",
                201);
        return body.get("id").asText();
    }

    public static String createOptionGroup(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String adminToken,
            String businessId,
            String productId,
            String name,
            String selectionType)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/products/{productId}/option-groups", businessId, productId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\",\"selectionType\":\"" + selectionType + "\"}",
                201);
        return body.get("id").asText();
    }

    public static String createOptionGroup(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String adminToken,
            String businessId,
            String productId,
            String name,
            String selectionType,
            boolean required)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post("/internal/businesses/{businessId}/products/{productId}/option-groups", businessId, productId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\",\"selectionType\":\"" + selectionType + "\",\"required\":" + required + "}",
                201);
        return body.get("id").asText();
    }

    public static String createOption(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String adminToken,
            String businessId,
            String productId,
            String optionGroupId,
            String name,
            long priceDeltaMinorUnits)
            throws Exception {
        JsonNode body = perform(
                mockMvc,
                post(
                                "/internal/businesses/{businessId}/products/{productId}/option-groups/{optionGroupId}/options",
                                businessId,
                                productId,
                                optionGroupId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"name\":\"" + name + "\",\"priceDeltaMinorUnits\":" + priceDeltaMinorUnits + "}",
                201);
        return body.get("id").asText();
    }

    public static JsonNode upsertBranchProduct(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String adminToken,
            String businessId,
            String branchId,
            String productId,
            String availability,
            Long priceOverrideMinorUnits)
            throws Exception {
        String priceJson = priceOverrideMinorUnits == null ? "null" : priceOverrideMinorUnits.toString();
        return perform(
                mockMvc,
                put("/internal/businesses/{businessId}/branches/{branchId}/products/{productId}", businessId, branchId, productId)
                        .header("X-Internal-Admin-Token", adminToken),
                objectMapper,
                "{\"availability\":\"" + availability + "\",\"priceOverrideMinorUnits\":" + priceJson + "}",
                200);
    }

    public record CheckedInVisit(String tableVisitId, String businessId, String branchId, String sessionCookieValue) {
    }

    /**
     * Performs the real public check-in call (POST /api/qr/{token}/visit) and captures
     * the qrmenu_session cookie value from the Set-Cookie response header so callers can
     * reuse it (via a MockCookie - MockMvc does NOT parse a raw "Cookie" request header
     * into request.getCookies(), so @CookieValue only sees it that way) on subsequent
     * calls, e.g. the ordering module's cart endpoints, which require the caller to own
     * the TableVisit via this same session.
     */
    public static CheckedInVisit checkIn(MockMvc mockMvc, ObjectMapper objectMapper, String qrToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/qr/{token}/visit", qrToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        return new CheckedInVisit(
                body.get("tableVisitId").asText(), body.get("businessId").asText(), body.get("branchId").asText(), cookieValue);
    }

    private static JsonNode perform(
            MockMvc mockMvc, MockHttpServletRequestBuilder request, ObjectMapper objectMapper, String jsonBody, int expectedStatus)
            throws Exception {
        if (jsonBody != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        String response = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
