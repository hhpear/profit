package com.yuexin.bmp.agent.task.profit;

import java.util.*;
import javax.annotation.PreDestroy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Uses existing Notice API. A normal return means queued, never Feishu delivery confirmed. */
@Component
public class LegacyNoticeCardSender implements ProfitCardSender {
 private final Environment env;
 private final NoticeCardAdapter adapter=new NoticeCardAdapter();
 private Object reference,service;
 public LegacyNoticeCardSender(Environment env){this.env=env;}
 public void validate(Map<String,Object> request){
  if(!"user_id".equals(request.get("receiveIdType"))||!(request.get("receiveId") instanceof String)||((String)request.get("receiveId")).trim().isEmpty())throw new ProfitApiException("INVALID_ARGUMENT",400);
  if(!Arrays.asList("BusinessAlarm","BMP").contains(request.get("app")))throw new ProfitApiException("INVALID_ARGUMENT",400);
  if(env.getProperty("profit.notice.url","").isEmpty()&&env.getProperty("profit.notice.registry","").isEmpty())throw new ProfitApiException("SOURCE_UNAVAILABLE",503);
  String direct=env.getProperty("profit.notice.url","");if(!direct.isEmpty()&&!direct.startsWith("dubbo://"))throw new ProfitApiException("INVALID_ARGUMENT",400);
  parameters(request);
 }
 @SuppressWarnings("unchecked") Map<String,Object> parameters(Map<String,Object> request){
  Map<String,Object> params=new LinkedHashMap<>();params.put("class","com.yuexin.platform.notice.service.FeishuNoticeService$CardParams");params.put("app",request.get("app"));params.put("contactIds",Collections.singletonList(request.get("receiveId")));
  params.put("message",adapter.adapt((Map<String,Object>)request.get("card"),env.getProperty("profit.notice.max-content-bytes",Integer.class,20000)));return params;
 }
 public synchronized Map<String,Object> send(Map<String,Object> request){
  Map<String,Object> params=parameters(request);
  try{
   if(service==null){
    Class<?> rc=Class.forName("org.apache.dubbo.config.ReferenceConfig");reference=rc.newInstance();
    Class<?> ac=Class.forName("org.apache.dubbo.config.ApplicationConfig");rc.getMethod("setApplication",ac).invoke(reference,ac.getConstructor(String.class).newInstance("profit-alert-notice"));
    rc.getMethod("setInterface",String.class).invoke(reference,"com.yuexin.platform.notice.service.FeishuNoticeService");rc.getMethod("setGeneric",String.class).invoke(reference,"true");
    String url=env.getProperty("profit.notice.url","");
    if(!url.isEmpty()){if(!url.startsWith("dubbo://"))throw new ProfitApiException("INVALID_ARGUMENT",400);rc.getMethod("setUrl",String.class).invoke(reference,url);}
    else{Class<?> registry=Class.forName("org.apache.dubbo.config.RegistryConfig");rc.getMethod("setRegistry",registry).invoke(reference,registry.getConstructor(String.class).newInstance(env.getRequiredProperty("profit.notice.registry")));}
    rc.getMethod("setRetries",Integer.class).invoke(reference,0);rc.getMethod("setTimeout",Integer.class).invoke(reference,25000);
    String version=env.getProperty("profit.notice.version","");if(!version.isEmpty())rc.getMethod("setVersion",String.class).invoke(reference,version);
    String group=env.getProperty("profit.notice.group","");if(!group.isEmpty())rc.getMethod("setGroup",String.class).invoke(reference,group);
    service=rc.getMethod("get").invoke(reference);
   }
   Class.forName("org.apache.dubbo.rpc.service.GenericService").getMethod("$invoke",String.class,String[].class,Object[].class).invoke(service,"sendCardMessage",new String[]{"com.yuexin.platform.notice.service.FeishuNoticeService$CardParams"},new Object[]{params});
   Map<String,Object> result=new LinkedHashMap<>();result.put("noticeApp",request.get("app"));result.put("adapterVersion","notice-legacy-v1");result.put("state","QUEUED");result.put("deliveryMode","NOTICE_QUEUE");result.put("transport","NOTICE_DUBBO");result.put("noticeAccepted",true);result.put("sendAttempted",true);result.put("assumedSuccess",false);result.put("messageId",null);result.put("readbackConfirmed",false);return result;
  }catch(Exception e){throw new ProfitApiException("DELIVERY_UNKNOWN",503);}
 }
 @PreDestroy public synchronized void close(){if(reference!=null)try{reference.getClass().getMethod("destroy").invoke(reference);}catch(Exception ignored){}reference=null;service=null;}
}
