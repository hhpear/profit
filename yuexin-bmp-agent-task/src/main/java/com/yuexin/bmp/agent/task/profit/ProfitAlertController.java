package com.yuexin.bmp.agent.task.profit;
import com.yuexin.bmp.agent.task.profit.source.ProfitSourceService;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/profit-alert/v1")
public class ProfitAlertController {
 private final ProfitSourceService sources; private final ProfitNoticeGateway notice; private final Environment env;
 public ProfitAlertController(ProfitSourceService sources,ProfitNoticeGateway notice,Environment env){this.sources=sources;this.notice=notice;this.env=env;}
 private Map<String,Object> wrap(HttpServletRequest r,Map<String,Object> data){Map<String,Object> m=new LinkedHashMap<>();m.put("code","OK");m.put("requestId",r.getAttribute("profit.requestId"));m.put("data",data);return m;}
 @PostMapping("/domestic/query") public Map<String,Object> domestic(@RequestBody Map<String,Object>b,HttpServletRequest r){return wrap(r,sources.domestic(b));}
 @PostMapping("/international/daily") public Map<String,Object> international(@RequestBody Map<String,Object>b,HttpServletRequest r){return wrap(r,sources.international(b));}
 private String owner(HttpServletRequest r){return String.valueOf(r.getAttribute("authenticatedPrincipal"));}
 private Map<String,Object> owned(Map<String,Object>d,HttpServletRequest r){if(!owner(r).equals(d.get("ownerPrincipal")))throw new ProfitApiException("FORBIDDEN",403);return d;}
 @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 @PostMapping("/deliveries") public Map<String,Object> submit(@RequestBody Map<String,Object>b,HttpServletRequest r){
  String mode=String.valueOf(b.get("mode")); if(!Arrays.asList("test","production").contains(mode))throw new ProfitApiException("INVALID_ARGUMENT",400);
  String principal=owner(r);String allowed=env.getProperty("profit.identities."+principal+".mode");
  if(!mode.equals(allowed) || !"enterprise-profit-alert".equals(b.get("application")))throw new ProfitApiException("FORBIDDEN",403);
  String id=String.valueOf(b.get("recipientId"));String receiver=env.getProperty("profit.recipients."+mode+"."+id);
  if(receiver==null||receiver.trim().isEmpty())throw new ProfitApiException("FORBIDDEN",403);
  Map<String,Object> request=new LinkedHashMap<>(b); request.put("ownerPrincipal",principal);request.put("receiveId",receiver);request.put("receiveIdType",env.getProperty("profit.notice.receive-id-type","user_id"));request.put("app",env.getProperty("profit.notice.app","BusinessAlarm"));
  return wrap(r,notice.invoke("submit",request));
 }
 @PostMapping("/deliveries/query") public Map<String,Object> query(@RequestBody Map<String,Object>b,HttpServletRequest r){return wrap(r,owned(notice.invoke("query",required(b,"idempotencyKey")),r));}
 @PostMapping("/deliveries/readback") public Map<String,Object> readback(@RequestBody Map<String,Object>b,HttpServletRequest r){
  // Existing Notice has no readback API; retain ownership checks on local submission evidence.
  Map<String,Object> request=new LinkedHashMap<>();request.put("deliveryId",required(b,"deliveryId"));request.put("ownerPrincipal",owner(r));return wrap(r,notice.invoke("readbackOwned",request));
 }
 private String required(Map<String,Object>b,String k){Object v=b.get(k);if(!(v instanceof String)||((String)v).trim().isEmpty())throw new ProfitApiException("INVALID_ARGUMENT",400);return (String)v;}
}
