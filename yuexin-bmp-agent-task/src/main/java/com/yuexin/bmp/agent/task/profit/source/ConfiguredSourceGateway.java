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
 private String session;private RestTemplate http;
 @SuppressWarnings("unchecked") private Map<String,Object> rpc(String method,Object params,int id){
  if(http==null){SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(5000);f.setReadTimeout(120000);http=new RestTemplate(f);}
  Map<String,Object>b=new LinkedHashMap<>();b.put("jsonrpc","2.0");b.put("method",method);b.put("params",params);if(id>0)b.put("id",id);
  HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("Accept","application/json, text/event-stream");h.set("x-litellm-api-key","Bearer "+p.getMcpToken());if(session!=null)h.set("Mcp-Session-Id",session);
  ResponseEntity<String>r;try{r=http.postForEntity(p.getMcpUrl(),new HttpEntity<>(b,h),String.class);}catch(org.springframework.web.client.HttpClientErrorException e){if(e.getRawStatusCode()==429)throw new SourceException("RATE_LIMITED",429,true);throw new SourceException("FORBIDDEN",403,false);}catch(org.springframework.web.client.RestClientException e){throw new SourceException("SOURCE_UNAVAILABLE",503,true);}if(r.getHeaders().getFirst("Mcp-Session-Id")!=null)session=r.getHeaders().getFirst("Mcp-Session-Id");
  try {String raw=r.getBody();if(raw==null||raw.trim().isEmpty())return Collections.emptyMap();if(raw.trim().startsWith("data:")||raw.contains("\ndata:")){String chosen=null;for(String block:raw.replace("\r\n","\n").split("\n\n")){StringBuilder s=new StringBuilder();for(String line:block.split("\n"))if(line.startsWith("data:"))s.append(line.substring(5).trim());if(s.length()>0){Map<?,?>event=mapper.readValue(s.toString(),Map.class);if(event.containsKey("result")||event.containsKey("error"))chosen=s.toString();}}if(chosen==null)throw new SourceException("SOURCE_UNAVAILABLE",503,true);raw=chosen;}Map<String,Object>v=mapper.readValue(raw,Map.class);if(v.containsKey("error"))throw new SourceException("SOURCE_INCOMPLETE",422,false);return v;}catch(SourceException e){throw e;}catch(Exception e){throw new SourceException("SOURCE_UNAVAILABLE",503,true);}
 }
 public synchronized Map<String,Object> internationalDay(String day){
  if(p.getMcpToken().isEmpty())throw new SourceException("SOURCE_UNAVAILABLE",503,false);
  session=null;Map<String,Object>init=new LinkedHashMap<>();init.put("protocolVersion","2025-06-18");init.put("capabilities",Collections.emptyMap());init.put("clientInfo",Collections.singletonMap("name","profit-alert"));if(!rpc("initialize",init,1).containsKey("result"))throw new SourceException("SOURCE_UNAVAILABLE",503,true);rpc("notifications/initialized",Collections.emptyMap(),0);
  String tool="yuexin_mcp_gateway-billing_mcp__query_daily_stats_summary";boolean found=false;Map<String,Object>list=new HashMap<>();Set<String>seen=new HashSet<>();
  for(int i=0;i<100;i++){Map<String,Object>r=map(rpc("tools/list",list,2+i).get("result"));Object ts=r.get("tools");if(ts instanceof List)for(Object t:(List<?>)ts)if(tool.equals(map(t).get("name")))found=true;Object c=r.get("nextCursor");if(c==null)break;if(!seen.add(c.toString()))throw new SourceException("SOURCE_INCOMPLETE",422,false);list.put("cursor",c);}
  if(!found)throw new SourceException("FORBIDDEN",403,false);Map<String,Object>a=new LinkedHashMap<>();a.put("start_time",day+"T00:00:00");a.put("end_time",day+"T23:59:59");a.put("source_platform","Pigeon");a.put("group_by",Arrays.asList("enterprise_id","account_id","send_channel_id"));Map<String,Object>call=new HashMap<>();call.put("name",tool);call.put("arguments",a);return rpc("tools/call",call,200);
 }
 @SuppressWarnings("unchecked")static Map<String,Object>map(Object o){return o instanceof Map?(Map<String,Object>)o:Collections.emptyMap();}
}
