package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.admin.service.PlatformAdminBootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2eTestSupport {
    @LocalServerPort
    private int port;

    @Autowired
    private Flyway flyway;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PlatformAdminBootstrap platformAdminBootstrap;

    protected E2eApiClient api;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
        platformAdminBootstrap.ensureBootstrapAdmin();
        api = new E2eApiClient(restTemplate, "http://localhost:" + port);
    }

    protected UserSession register(String prefix) {
        String userName = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String password = "123456";
        JsonNode auth = api.postOk("/api/auth/register", null, null, obj(
                "userName", userName,
                "password", password
        ));
        String token = auth.get("token").asText();
        long userId = auth.get("userId").asLong();
        return new UserSession(userName, password, token, userId);
    }

    protected UserSession createMerchantAccount(UserSession existingAccount, String prefix) {
        String adminToken = platformAdminToken();
        JsonNode accountPage = api.getOk(
                "/api/admin/accounts/merchant-accounts?page=1&pageSize=20&keyword=" + existingAccount.userName,
                adminToken,
                null
        );
        Long merchantId = null;
        for (JsonNode account : accountPage.get("items")) {
            if (existingAccount.userName.equals(account.get("userName").asText())) {
                merchantId = account.get("merchantId").asLong();
                break;
            }
        }
        if (merchantId == null) {
            throw new AssertionError("merchant account not found: " + existingAccount.userName);
        }

        String userName = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String password = "123456";
        api.postOk("/api/admin/merchants/" + merchantId + "/accounts", adminToken, null, obj(
                "userName", userName,
                "password", password,
                "confirmPassword", password
        ));
        JsonNode auth = api.postOk("/api/auth/login", null, null, obj(
                "userName", userName,
                "password", password
        ));
        return new UserSession(
                userName,
                password,
                auth.get("token").asText(),
                auth.get("userId").asLong()
        );
    }

    private String platformAdminToken() {
        JsonNode auth = api.postOk("/api/admin/auth/login", null, null, obj(
                "userName", "admin",
                "password", "admin123456"
        ));
        return auth.get("token").asText();
    }

    protected long createHouse(UserSession user, String name, int rows, int cols, int layers) {
        JsonNode house = api.postOk("/api/houses", user.token, null, obj(
                "name", name,
                "layoutRows", rows,
                "layoutCols", cols,
                "layoutLayers", layers,
                "remark", "e2e",
                "requestId", requestId("house")
        ));
        return house.get("id").asLong();
    }

    protected List<Long> cageIds(UserSession user, long houseId) {
        JsonNode cages = api.getOk("/api/cages", user.token, houseId);
        List<Long> ids = new ArrayList<Long>();
        for (JsonNode cage : cages) {
            ids.add(cage.get("id").asLong());
        }
        return ids;
    }

    protected long createRabbit(UserSession user, long houseId, long cageId, String type, String gender, String breed) {
        JsonNode rabbit = api.postOk("/api/rabbits", user.token, houseId, obj(
                "cageId", cageId,
                "type", type,
                "gender", gender,
                "breed", breed,
                "arrivalMethod", "1",
                "arrivalDate", now(),
                "weight", 3.2,
                "requestId", requestId("rabbit")
        ));
        return rabbit.get("id").asLong();
    }

    protected long now() {
        return System.currentTimeMillis();
    }

    protected long oneMinuteAgo() {
        return now() - 60_000L;
    }

    protected String requestId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    protected Map<String, Object> obj(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return m;
    }

    protected static class UserSession {
        final String userName;
        final String password;
        final String token;
        final long userId;

        UserSession(String userName, String password, String token, long userId) {
            this.userName = userName;
            this.password = password;
            this.token = token;
            this.userId = userId;
        }
    }
}
