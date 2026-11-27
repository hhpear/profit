package com.yuexin.bmp.agent.task.profit;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
@Component
public class DubboProfitNoticeGateway implements ProfitNoticeGateway {
 private final Environment env;
 public DubboProfitNoticeGateway(Environment env){this.env=env;}
 @SuppressWarnings("unchecked") public Map<String,Object> invoke(String method,Object argument){
  String url=env.getProperty("profit.notice.url");
  if(url==null || !url.startsWith("dubbo://")) throw new ProfitApiException("SOURCE_UNAVAILABLE",503);
  Object ref=null;
  try {
   Class<?> rc=Class.forName("org.apache.dubbo.config.ReferenceConfig"); ref=rc.newInstance();
   Class<?> ac=Class.forName("org.apache.dubbo.config.ApplicationConfig");Object app=ac.getConstructor(String.class).newInstance("profit-alert-notice");rc.getMethod("setApplication",ac).invoke(ref,app);
   rc.getMethod("setTimeout",Integer.class).invoke(ref,Integer.valueOf(25000));
   rc.getMethod("setInterface",String.class).invoke(ref,"com.yuexin.platform.notice.service.ReliableFeishuNoticeService");
   rc.getMethod("setGeneric",String.class).invoke(ref,"true"); rc.getMethod("setUrl",String.class).invoke(ref,url);
   rc.getMethod("setRetries",Integer.class).invoke(ref,Integer.valueOf(0));
   Object service=rc.getMethod("get").invoke(ref);
   return (Map<String,Object>)Class.forName("org.apache.dubbo.rpc.service.GenericService").getMethod("$invoke",String.class,String[].class,Object[].class).invoke(service,method,new String[]{argument instanceof Map?"java.util.Map":"java.lang.String"},new Object[]{argument});
  }catch(Exception e){
   String message=String.valueOf(e); Throwable cause=e; while(cause.getCause()!=null){cause=cause.getCause();message+=" "+cause;}
   for(String code:Arrays.asList("IDEMPOTENCY_CONFLICT","INVALID_ARGUMENT","FORBIDDEN","NOT_FOUND","SOURCE_UNAVAILABLE")) if(message.contains(code))throw new ProfitApiException(code,"IDEMPOTENCY_CONFLICT".equals(code)?409:"INVALID_ARGUMENT".equals(code)?400:"FORBIDDEN".equals(code)?403:"NOT_FOUND".equals(code)?404:503);
   throw new ProfitApiException("UPSTREAM_TIMEOUT",504);
  }
  finally {if(ref!=null)try{ref.getClass().getMethod("destroy").invoke(ref);}catch(Exception ignored){}}
 }
}
