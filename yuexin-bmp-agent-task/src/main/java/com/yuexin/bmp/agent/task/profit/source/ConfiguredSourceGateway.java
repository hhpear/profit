package com.yuexin.bmp.agent.task.profit.source;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.*;
@Component
public class ConfiguredSourceGateway implements ProfitSourceGateway {
 private final SourceProperties p;private final ObjectMapper mapper;private Object reference, service;
 public ConfiguredSourceGateway(SourceProperties p,ObjectMapper m){this.p=p;mapper=m;}
 @SuppressWarnings("unchecked") public synchronized Map<String,Object> domesticPage(Map<String,Object> params){
  try {
   if(p.getDubboRegistry().isEmpty())throw new SourceException("SOURCE_UNAVAILABLE",503,false);
   if(service==null){
    Class<?> rc=Class.forName("org.apache.dubbo.config.ReferenceConfig");reference=rc.newInstance();
    rc.getMethod("setInterface",String.class).invoke(reference,"com.yuexin.bmp.bi.service.CustomerProfitReportService");rc.getMethod("setGeneric",String.class).invoke(reference,"true");
    Class<?> ac=Class.forName("org.apache.dubbo.config.ApplicationConfig");Object app=ac.getConstructor(String.class).newInstance("profit-alert-source");rc.getMethod("setApplication",ac).invoke(reference,app);
    Class<?> reg=Class.forName("org.apache.dubbo.config.RegistryConfig");rc.getMethod("setRegistry",reg).invoke(reference,reg.getConstructor(String.class).newInstance(p.getDubboRegistry()));
    rc.getMethod("setRetries",Integer.class).invoke(reference,0);rc.getMethod("setTimeout",Integer.class).invoke(reference,120000);
    if(!p.getDubboVersion().isEmpty())rc.getMethod("setVersion",String.class).invoke(reference,p.getDubboVersion());if(!p.getDubboGroup().isEmpty())rc.getMethod("setGroup",String.class).invoke(reference,p.getDubboGroup());service=rc.getMethod("get").invoke(reference);
   }
   return (Map<String,Object>)Class.forName("org.apache.dubbo.rpc.service.GenericService").getMethod("$invoke",String.class,String[].class,Object[].class).invoke(service,"query",new String[]{"com.yuexin.bmp.bi.service.CustomerProfitReportService$QueryParams"},new Object[]{params});
  }catch(SourceException e){throw e;}catch(Exception e){throw new SourceException("SOURCE_UNAVAILABLE",503,true);}
 }
 public synchronized Map<String,Object> internationalDay(String day){
  String python=System.getenv("PROFIT_MCP_PYTHON"), bridge=System.getenv("PROFIT_MCP_BRIDGE");
  if(python==null||bridge==null)throw new SourceException("SOURCE_UNAVAILABLE",503,false);
  java.nio.file.Path output=null;
  Process process=null;
  try{
   output=java.nio.file.Files.createTempFile("profit-mcp-", ".json");
   ProcessBuilder pb=new ProcessBuilder(python,bridge,day);
   pb.environment().put("MCP_GATEWAY_BASE_URL",p.getMcpUrl());
   pb.redirectOutput(output.toFile());pb.redirectError(new java.io.File("/dev/null"));
   process=pb.start();
   if(!process.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new SourceException("UPSTREAM_TIMEOUT",504,true);}
   Map<String,Object> result=mapper.readValue(output.toFile(),Map.class);
   if(process.exitValue()!=0){Object status=result.get("status");if(Integer.valueOf(403).equals(status)||Integer.valueOf(401).equals(status))throw new SourceException("FORBIDDEN",403,false);throw new SourceException("SOURCE_UNAVAILABLE",503,false);}
   return result;
  }catch(SourceException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SourceException("SOURCE_UNAVAILABLE",503,true);}catch(Exception e){throw new SourceException("SOURCE_UNAVAILABLE",503,true);}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(output!=null)try{java.nio.file.Files.deleteIfExists(output);}catch(Exception ignored){}}
 }
 @SuppressWarnings("unchecked")static Map<String,Object>map(Object o){return o instanceof Map?(Map<String,Object>)o:Collections.emptyMap();}
}
