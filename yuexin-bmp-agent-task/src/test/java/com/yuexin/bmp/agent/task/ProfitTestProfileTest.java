package com.yuexin.bmp.agent.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "PROFIT_AGENT_TEST_HMAC_SECRET=synthetic-profile-secret")
@ActiveProfiles("profit-test")
class ProfitTestProfileTest {
    @Autowired AgentTaskProperties properties;
    @Autowired Environment environment;

    @Test void bindsIndependentProfitIdentityAndKeepsBillingIdentity() {
        assertTrue(properties.getAgentPrincipals().contains("profit-alert-test"));
        assertTrue(properties.getAgentPrincipals().contains("customer-billing-agent"));
        assertEquals("synthetic-profile-secret", properties.getPrincipalSecrets().get("profit-alert-test"));
        assertEquals("test", environment.getProperty("profit.identities.profit-alert-test.mode"));
    }
}
