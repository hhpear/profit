package com.yuexin.platform.notice.service.impl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.alibaba.fastjson.JSON;
import com.yuexin.platform.notice.config.AppConfig;
import com.yuexin.platform.notice.param.Application;
import com.yuexin.platform.notice.model.NoticeApp;
import java.util.*;
class ReliableDeliveryTests {
 @Test void canonicalUsesCodePointOrderAndRejectsDecimals(){
  Map<String,Object> card=new HashMap<>();card.put("中文",Arrays.asList("金额",1));card.put("a",true);
  assertEquals("{\"a\":true,\"中文\":[\"金额\",1]}",ReliableFeishuNoticeServiceImpl.canonical(card));
  card.put("float",1.5);assertThrows(IllegalArgumentException.class,()->ReliableFeishuNoticeServiceImpl.canonical(card));
 }
 @Test void unknownIsNeverResentAndContentIsBound(){
  DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
  JdbcTemplate jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE notice_delivery(delivery_id VARCHAR PRIMARY KEY,idempotency_key VARCHAR UNIQUE,application VARCHAR,data_day VARCHAR,mode VARCHAR,recipient_id VARCHAR,binding_sha VARCHAR,request_json CLOB,state VARCHAR,message_id VARCHAR,chat_id VARCHAR,error_code VARCHAR,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(application,data_day,mode,recipient_id))");
  AppConfig config=new AppConfig();Application app=new Application();app.setNoticeApp(NoticeApp.BMP);app.setAppId("test-app");app.setAppSecret("test-secret");config.setFeishuApps(Collections.singletonList(app));
  ReliableFeishuNoticeServiceImpl svc=new ReliableFeishuNoticeServiceImpl(jdbc,config,new DataSourceTransactionManager(ds));
  Map<String,Object> r=new LinkedHashMap<>();
  r.put("ownerPrincipal","caller");r.put("application","enterprise-profit-alert");r.put("dataDay","2026-11-02");r.put("mode","test");r.put("recipientId","recipient");r.put("idempotencyKey","same-key");r.put("rulesetVersion","v1");r.put("rulesetSha256",String.join("",Collections.nCopies(64,"a")));r.put("inputSha256",String.join("",Collections.nCopies(64,"b")));r.put("receiveId","user");r.put("receiveIdType","user_id");r.put("app","BMP");r.put("card",Collections.singletonMap("schema","2.0"));r.put("cardSha256",ReliableFeishuNoticeServiceImpl.sha(ReliableFeishuNoticeServiceImpl.canonical(r.get("card"))));
  jdbc.update("INSERT INTO notice_delivery(delivery_id,idempotency_key,application,data_day,mode,recipient_id,binding_sha,request_json,state) VALUES(?,?,?,?,?,?,?,?,?)","dl-existing","same-key","enterprise-profit-alert","2026-11-02","test","recipient",ReliableFeishuNoticeServiceImpl.sha(ReliableFeishuNoticeServiceImpl.canonical(r)),JSON.toJSONString(r),"UNKNOWN");
  assertEquals("UNKNOWN",svc.submit(r).get("state"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notice_delivery",Integer.class));
  Map<String,Object> other=new HashMap<>(r);other.put("inputSha256",String.join("",Collections.nCopies(64,"c")));
  assertTrue(assertThrows(IllegalArgumentException.class,()->svc.submit(other)).getMessage().startsWith("IDEMPOTENCY_CONFLICT"));
  Map<String,Object> read=new HashMap<>();read.put("deliveryId","dl-existing");read.put("ownerPrincipal","other");
  assertTrue(assertThrows(IllegalArgumentException.class,()->svc.readbackOwned(read)).getMessage().startsWith("FORBIDDEN"));
 }
}
