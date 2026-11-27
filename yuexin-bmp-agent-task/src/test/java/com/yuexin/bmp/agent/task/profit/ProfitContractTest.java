package com.yuexin.bmp.agent.task.profit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public class ProfitContractTest {
 @Test void testIdentityCannotSendProduction(){
  MockEnvironment e=new MockEnvironment().withProperty("profit.identities.worker.mode","test");
  ProfitAlertController c=new ProfitAlertController(null,(m,a)->{throw new AssertionError("must not send");},e);
  MockHttpServletRequest r=new MockHttpServletRequest();r.setAttribute("authenticatedPrincipal","worker");
  Map<String,Object>b=new HashMap<>();b.put("mode","production");b.put("application","enterprise-profit-alert");
  assertEquals(403,assertThrows(ProfitApiException.class,()->c.submit(b,r)).status);
 }
 @Test void queryCannotReadAnotherOwner(){
  ProfitAlertController c=new ProfitAlertController(null,(m,a)->Collections.singletonMap("ownerPrincipal","other"),new MockEnvironment());
  MockHttpServletRequest r=new MockHttpServletRequest();r.setAttribute("authenticatedPrincipal","worker");
  assertEquals(403,assertThrows(ProfitApiException.class,()->c.query(Collections.singletonMap("idempotencyKey","key"),r)).status);
 }
 @Test void senderCannotOverrideConfiguredReceiver(){
  MockEnvironment e=new MockEnvironment().withProperty("profit.identities.worker.mode","test").withProperty("profit.recipients.test.bob","real-bob");
  ProfitAlertController c=new ProfitAlertController(null,(m,a)->{Map<?,?> q=(Map<?,?>)a;assertEquals("real-bob",q.get("receiveId"));assertEquals("worker",q.get("ownerPrincipal"));return Collections.singletonMap("state","SENT");},e);
  MockHttpServletRequest r=new MockHttpServletRequest();r.setAttribute("authenticatedPrincipal","worker");
  Map<String,Object>b=new HashMap<>();b.put("mode","test");b.put("application","enterprise-profit-alert");b.put("recipientId","bob");b.put("receiveId","attacker");
  assertEquals("OK",c.submit(b,r).get("code"));
 }
}
