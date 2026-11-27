package com.yuexin.bmp.agent.task.profit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yuexin.bmp.agent.task.AgentTaskProperties;
import com.yuexin.bmp.agent.task.config.HmacAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real loopback HTTP to production MVC/filter; Notice boundary is synthetic. */
class PythonHttpContractTest {
 @Test void pythonClientExercisesJavaContract() throws Exception {
  AgentTaskProperties props=new AgentTaskProperties();
  props.setPrincipalSecrets(Collections.singletonMap("synthetic-worker","synthetic-secret"));
  props.setAgentPrincipals(Collections.singleton("synthetic-worker"));
  MockEnvironment env=new MockEnvironment().withProperty("profit.identities.synthetic-worker.mode","test")
    .withProperty("profit.recipients.test.synthetic-user","synthetic-feishu-user");
  Map<String,Map<String,Object>> records=new HashMap<>();
  ProfitNoticeGateway notice=(method,arg)->{
   if("submit".equals(method)){
    Map<String,Object> request=new LinkedHashMap<>((Map<String,Object>)arg);
    String key=request.get("idempotencyKey").toString();
    if(records.containsKey(key)&&!records.get(key).get("cardSha256").equals(request.get("cardSha256")))throw new ProfitApiException("IDEMPOTENCY_CONFLICT",409);
    request.put("deliveryId",key);request.put("state","SENT");request.put("messageId","synthetic-message");records.putIfAbsent(key,request);return records.get(key);
   }
   String key=arg instanceof Map?((Map<?,?>)arg).get("deliveryId").toString():arg.toString();
   if(!records.containsKey(key))throw new ProfitApiException("NOT_FOUND",404);
   Map<String,Object> result=records.get(key);
   if("readbackOwned".equals(method)){result.put("state","VERIFIED");result.put("senderAppId","synthetic-app");result.put("contentMatch","EXACT");result.put("readbackConfirmed",true);}
   return result;
  };
  org.springframework.jdbc.core.JdbcTemplate db=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  db.execute("CREATE TABLE profit_source_receipt(receipt_id VARCHAR(64) PRIMARY KEY,binding_hash VARCHAR(64),payload LONGTEXT,expires_at TIMESTAMP)");
  db.execute("CREATE TABLE profit_international_day(data_day VARCHAR(10) PRIMARY KEY,attempts INT,state VARCHAR(20),payload LONGTEXT,raw_response LONGTEXT,lease_token VARCHAR(36),lease_until TIMESTAMP,error_code VARCHAR(64),error_status INT)");
  db.execute("CREATE TABLE profit_source_attempt(data_day VARCHAR(10),attempt_no INT,state VARCHAR(30),raw_response LONGTEXT,PRIMARY KEY(data_day,attempt_no))");
  com.yuexin.bmp.agent.task.profit.source.SourceProperties sourceProps=new com.yuexin.bmp.agent.task.profit.source.SourceProperties();
  sourceProps.setMappingConfirmed(true);sourceProps.setStableReadConfirmed(true);sourceProps.setCustomerIds(Collections.singletonList(1L));
  Map<String,String> names=new HashMap<>();names.put("E1","合成一");names.put("E2","合成二");sourceProps.setEnterpriseNames(names);
  java.util.concurrent.atomic.AtomicInteger sourceCalls=new java.util.concurrent.atomic.AtomicInteger();
  com.yuexin.bmp.agent.task.profit.source.ProfitSourceGateway gateway=new com.yuexin.bmp.agent.task.profit.source.ProfitSourceGateway(){
   public Map<String,Object> domesticPage(Map<String,Object> params){
    List<Map<String,Object>> rows=new ArrayList<>();
    long from=((Number)params.get("startTime")).longValue(), until=((Number)params.get("endTime")).longValue();
    for(long day=from;day<until;day+=86400000L)for(int i=1;i<=2;i++){Map<String,Object> row=new HashMap<>();row.put("id",day+i);row.put("customerId",1L);row.put("time",day);row.put("entCode","E"+i);row.put("smsIncome","100.00");row.put("smsOperateProfit","20.00");rows.add(row);}
    Map<String,Object> result=new HashMap<>();result.put("data",rows);result.put("totalCount",rows.size());return result;
   }
   public Map<String,Object> internationalDay(String day){
    sourceCalls.incrementAndGet();Map<String,Object> receipt=new HashMap<>();receipt.put("source_platform","Pigeon");receipt.put("start_time",day+"T00:00:00+08:00");receipt.put("end_time",day+"T23:59:59+08:00");receipt.put("group_by",Arrays.asList("enterprise_id","account_id","send_channel_id"));receipt.put("truncated",false);receipt.put("row_count",0);receipt.put("rows",Collections.emptyList());return receipt;
   }
  };
  com.yuexin.bmp.agent.task.profit.source.ProfitSourceService sources=new com.yuexin.bmp.agent.task.profit.source.ProfitSourceService(db,new ObjectMapper(),sourceProps,gateway);
  MockMvc mvc=standaloneSetup(new ProfitAlertController(sources,notice,env)).setControllerAdvice(new ProfitExceptionAdvice()).addFilters(new HmacAuthFilter(props)).build();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{try{
   MockHttpServletRequestBuilder request=post(exchange.getRequestURI().getPath()).content(org.springframework.util.StreamUtils.copyToByteArray(exchange.getRequestBody()));
   exchange.getRequestHeaders().forEach((k,v)->request.header(k,v.toArray()));
   MvcResult result=mvc.perform(request).andReturn();byte[] body=result.getResponse().getContentAsByteArray();
   exchange.sendResponseHeaders(result.getResponse().getStatus(),body.length);exchange.getResponseBody().write(body);
  }catch(Exception e){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}});
  server.start();
  try{
   ProcessBuilder process=new ProcessBuilder("python3","-m","scripts.test_profit_alert_b2_http");
   process.directory(Paths.get("..","enterprise-profit-alert-main 2").toFile());
   process.environment().put("PROFIT_B2_LOOPBACK_PORT",Integer.toString(server.getAddress().getPort()));
   process.inheritIO();Process child=process.start();
   assertTrue(child.waitFor(60,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,child.exitValue());
   assertEquals(1,sourceCalls.get(),"same international day must reuse persisted receipt");
  }finally{server.stop(0);}
 }
}
