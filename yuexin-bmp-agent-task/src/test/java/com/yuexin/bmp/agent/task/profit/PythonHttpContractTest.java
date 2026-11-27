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
  com.yuexin.bmp.agent.task.profit.source.ProfitSourceService sources=org.mockito.Mockito.mock(com.yuexin.bmp.agent.task.profit.source.ProfitSourceService.class);
  org.mockito.Mockito.when(sources.domestic(org.mockito.ArgumentMatchers.anyMap())).thenAnswer(inv->{
   Map<String,Object> request=inv.getArgument(0);Map<String,Object> page=(Map<String,Object>)request.get("page");
   int n=((Number)page.get("number")).intValue();
   Map<String,Object> result=new LinkedHashMap<>();result.put("queryId","synthetic-frozen");result.put("success",true);result.put("code","GB:0000");result.put("totalCount",2);result.put("pageNumber",n);result.put("pageSize",1);
   result.put("data",Collections.singletonList(Collections.singletonMap("entName",n==1?"合成一":"合成二")));
   result.put("meta",Collections.singletonMap("consistency","FROZEN_RECEIPT"));return result;
  });
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
  }finally{server.stop(0);}
 }
}
