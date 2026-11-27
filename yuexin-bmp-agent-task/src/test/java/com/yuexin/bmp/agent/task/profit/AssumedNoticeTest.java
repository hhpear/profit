package com.yuexin.bmp.agent.task.profit;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AssumedNoticeTest {
 JdbcTemplate db; DubboProfitNoticeGateway gateway;
 @BeforeEach void setup(){db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));db.execute("CREATE TABLE profit_assumed_delivery(slot_hash VARCHAR(64) PRIMARY KEY,idempotency_key VARCHAR(255) UNIQUE,delivery_id VARCHAR(64) UNIQUE,payload LONGTEXT)");gateway=new DubboProfitNoticeGateway(db,new ObjectMapper());}
 Map<String,Object> request(){Map<String,Object> r=new HashMap<>();for(String k:Arrays.asList("application","recipientId","ownerPrincipal","receiveId","rulesetVersion","rulesetSha256","inputSha256","cardSha256"))r.put(k,k);r.put("dataDay","2026-11-26");r.put("mode","test");r.put("idempotencyKey","key");r.put("card",Collections.singletonMap("schema","2.0"));return r;}
 @Test void repeatedRequestAfterRestartReturnsSameAssumedRecord(){Map<String,Object> r=request(),first=gateway.invoke("submit",r);Map<String,Object> next=new DubboProfitNoticeGateway(db,new ObjectMapper()).invoke("submit",r);assertEquals(first.get("deliveryId"),next.get("deliveryId"));assertEquals(true,next.get("assumedSuccess"));assertEquals(false,next.get("readbackConfirmed"));assertNull(next.get("messageId"));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM profit_assumed_delivery",Integer.class));}
 @Test void changedCardOrAliasCannotSubmitAgain(){Map<String,Object> r=request();gateway.invoke("submit",r);r.put("card",Collections.singletonMap("schema","changed"));assertEquals(409,assertThrows(ProfitApiException.class,()->gateway.invoke("submit",r)).status);final Map<String,Object> other=request();other.put("idempotencyKey","new-key");assertEquals(409,assertThrows(ProfitApiException.class,()->gateway.invoke("submit",other)).status);}
 @Test void readbackDoesNotInventVerificationAndEnforcesOwner(){Map<String,Object> record=gateway.invoke("submit",request());Map<String,Object> query=new HashMap<>();query.put("deliveryId",record.get("deliveryId"));query.put("ownerPrincipal","other");assertEquals(403,assertThrows(ProfitApiException.class,()->gateway.invoke("readbackOwned",query)).status);query.put("ownerPrincipal","ownerPrincipal");assertEquals(false,gateway.invoke("readbackOwned",query).get("readbackConfirmed"));}
}
