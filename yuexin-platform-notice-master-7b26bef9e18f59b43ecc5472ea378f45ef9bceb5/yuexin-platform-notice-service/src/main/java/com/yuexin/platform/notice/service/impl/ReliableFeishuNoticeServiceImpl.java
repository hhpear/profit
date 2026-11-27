package com.yuexin.platform.notice.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yuexin.platform.notice.config.AppConfig;
import com.yuexin.platform.notice.param.Application;
import com.yuexin.platform.notice.service.ReliableFeishuNoticeService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import okhttp3.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/** Never reclaims SUBMITTING/UNKNOWN: the remote side effect may already have happened. */
@DubboService
public class ReliableFeishuNoticeServiceImpl implements ReliableFeishuNoticeService {
 private final JdbcTemplate jdbc;
 private final AppConfig config;
 private final TransactionTemplate tx;
 private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS)
   .readTimeout(25,TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
 public ReliableFeishuNoticeServiceImpl(JdbcTemplate jdbc, AppConfig config, PlatformTransactionManager manager) {
  this.jdbc=jdbc; this.config=config; this.tx=new TransactionTemplate(manager);
  this.tx.setPropagationBehavior(3); // REQUIRES_NEW: commit ledger before remote side effects
 }
 private static String required(Map<String,Object> r,String key) {
  Object v=r.get(key); if(v==null || String.valueOf(v).trim().isEmpty()) throw error("INVALID_ARGUMENT",key);
  return String.valueOf(v);
 }
 private static IllegalArgumentException error(String c,String m){return new IllegalArgumentException(c+": "+m);}
 private Application app(Map<String,Object> r){
  String name=required(r,"app");
  return config.getFeishuApps().stream().filter(a->a.getNoticeApp().name().equals(name)).findFirst()
    .orElseThrow(()->error("FORBIDDEN","application unavailable"));
 }
 public Map<String,Object> submit(Map<String,Object> request) {
  // Defensive JSON copy prevents mutation after binding.
  Map<String,Object> r=JSON.parseObject(JSON.toJSONString(request));
  for(String k:Arrays.asList("ownerPrincipal","application","dataDay","mode","recipientId","idempotencyKey","rulesetVersion","rulesetSha256","inputSha256","cardSha256","receiveId","receiveIdType")) required(r,k);
  LocalDate.parse(required(r,"dataDay"));
  if(!Arrays.asList("test","production").contains(r.get("mode"))) throw error("INVALID_ARGUMENT","mode");
  if(!Arrays.asList("user_id","open_id").contains(r.get("receiveIdType"))) throw error("INVALID_ARGUMENT","receiveIdType");
  for(String k:Arrays.asList("rulesetSha256","inputSha256","cardSha256"))
   if(!required(r,k).matches("[a-f0-9]{64}")) throw error("INVALID_ARGUMENT",k);
  if(!(r.get("card") instanceof Map) || !"2.0".equals(((Map<?,?>)r.get("card")).get("schema"))) throw error("INVALID_ARGUMENT","Card 2.0 required");
  if(!sha(canonical(r.get("card"))).equals(r.get("cardSha256"))) throw error("INVALID_ARGUMENT","card digest");
  app(r);
  String binding=sha(canonical(r));
  String id="dl-"+UUID.randomUUID();
  final String initialId=id;
  try {tx.execute(status->{jdbc.update("INSERT INTO notice_delivery(delivery_id,idempotency_key,application,data_day,mode,recipient_id,binding_sha,request_json,state) VALUES(?,?,?,?,?,?,?,?,?)",
    initialId,r.get("idempotencyKey"),r.get("application"),r.get("dataDay"),r.get("mode"),r.get("recipientId"),binding,JSON.toJSONString(r),"PREPARED");return null;});}
  catch(DuplicateKeyException e){
   List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM notice_delivery WHERE idempotency_key=? OR (application=? AND data_day=? AND mode=? AND recipient_id=?)",
    r.get("idempotencyKey"),r.get("application"),r.get("dataDay"),r.get("mode"),r.get("recipientId"));
   if(rows.size()!=1 || !binding.equals(rows.get(0).get("binding_sha"))) throw error("IDEMPOTENCY_CONFLICT","slot already bound");
   id=String.valueOf(rows.get(0).get("delivery_id"));
  }
  final String did=id;
  Integer owned=tx.execute(status->jdbc.update("UPDATE notice_delivery SET state='SUBMITTING',updated_at=CURRENT_TIMESTAMP WHERE delivery_id=? AND state='PREPARED'",did));
  if(owned!=null && owned==1){
   try {
    Application a=app(r); String token=token(a);
    JSONObject body=new JSONObject(); body.put("receive_id",r.get("receiveId")); body.put("msg_type","interactive");body.put("content",canonical(r.get("card")));
    JSONObject result=call("POST","https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type="+r.get("receiveIdType"),token,body);
    if(result.getIntValue("code")!=0){ finish(did,"REJECTED",null,null,"FEISHU_"+result.getIntValue("code")); }
    else {
     JSONObject data=result.getJSONObject("data"); String mid=data==null?null:data.getString("message_id");
     if(mid==null || mid.isEmpty()) finish(did,"UNKNOWN",null,null,"MISSING_MESSAGE_ID");
     else finish(did,"SENT",mid,data.getString("chat_id"),null);
    }
   } catch(Exception e){finish(did,"UNKNOWN",null,null,"UPSTREAM_UNCONFIRMED");}
  }
  return query(required(r,"idempotencyKey"));
 }
 private void finish(String id,String state,String mid,String chat,String code){tx.execute(s->{jdbc.update("UPDATE notice_delivery SET state=?,message_id=?,chat_id=?,error_code=?,updated_at=CURRENT_TIMESTAMP WHERE delivery_id=? AND state='SUBMITTING'",state,mid,chat,code,id);return null;});}
 public Map<String,Object> query(String key){
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM notice_delivery WHERE idempotency_key=?",key);
  if(rows.size()!=1)throw error("NOT_FOUND","delivery");return publicRecord(rows.get(0));
 }
 private Map<String,Object> publicRecord(Map<String,Object> row){
  Map<String,Object> result=new LinkedHashMap<>();
  String[][] fields={{"deliveryId","delivery_id"},{"idempotencyKey","idempotency_key"},{"application","application"},{"dataDay","data_day"},{"mode","mode"},{"recipientId","recipient_id"},{"state","state"},{"messageId","message_id"},{"chatId","chat_id"},{"errorCode","error_code"},{"updatedAt","updated_at"}};
  for(String[] f:fields)result.put(f[0],row.get(f[1]));
  Map<String,Object> request=JSON.parseObject(String.valueOf(row.get("request_json")));
  for(String k:Arrays.asList("ownerPrincipal","rulesetVersion","rulesetSha256","inputSha256","cardSha256"))result.put(k,request.get(k));
  result.put("readbackConfirmed","VERIFIED".equals(row.get("state")));return result;
 }
 public Map<String,Object> readbackOwned(Map<String,Object> request){
  String id=required(request,"deliveryId");
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT request_json FROM notice_delivery WHERE delivery_id=?",id);
  if(rows.size()!=1)throw error("NOT_FOUND","delivery");
  Map<String,Object> original=JSON.parseObject(String.valueOf(rows.get(0).get("request_json")));
  if(!required(request,"ownerPrincipal").equals(original.get("ownerPrincipal")))throw error("FORBIDDEN","delivery owner");
  return readback(id);
 }
 public Map<String,Object> readback(String id){
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM notice_delivery WHERE delivery_id=?",id);
  if(rows.size()!=1)throw error("NOT_FOUND","delivery"); Map<String,Object> row=rows.get(0);
  if("VERIFIED".equals(row.get("state")))return publicRecord(row);
  if(!"SENT".equals(row.get("state")))throw error("SOURCE_UNAVAILABLE","message not confirmed sent");
  Map<String,Object> r=JSON.parseObject(String.valueOf(row.get("request_json")));Application a=app(r);
  try{
   JSONObject reply=call("GET","https://open.feishu.cn/open-apis/im/v1/messages/"+row.get("message_id"),token(a),null);
   if(reply.getIntValue("code")!=0)throw error("SOURCE_UNAVAILABLE","readback rejected");
   JSONObject data=reply.getJSONObject("data");
   if(data==null || data.getJSONArray("items")==null || data.getJSONArray("items").size()!=1)throw error("SOURCE_UNAVAILABLE","readback missing");
   JSONObject msg=data.getJSONArray("items").getJSONObject(0);
   JSONObject sender=msg.getJSONObject("sender");
   if(sender==null || !a.getAppId().equals(sender.getString("id")) || !"app".equals(sender.getString("sender_type"))
     || !Objects.equals(row.get("message_id"),msg.getString("message_id")) || row.get("chat_id")==null || !Objects.equals(row.get("chat_id"),msg.getString("chat_id")))
    throw error("IDEMPOTENCY_CONFLICT","sender or target mismatch");
   // Fail closed when Feishu returns rendered content instead of exact original card.
   Object actual=JSON.parse(msg.getJSONObject("body").getString("content"));
   if(!canonical(actual).equals(canonical(r.get("card"))))throw error("IDEMPOTENCY_CONFLICT","readback content mismatch; rendered-marker verifier required");
   tx.execute(s->{jdbc.update("UPDATE notice_delivery SET state='VERIFIED',updated_at=CURRENT_TIMESTAMP WHERE delivery_id=? AND state='SENT'",id);return null;});
   Map<String,Object> result=query(String.valueOf(r.get("idempotencyKey")));result.put("contentMatch","EXACT");result.put("senderAppId",a.getAppId());return result;
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw error("SOURCE_UNAVAILABLE","readback unavailable");}
 }
 private String token(Application app)throws Exception{
  JSONObject body=new JSONObject();body.put("app_id",app.getAppId());body.put("app_secret",app.getAppSecret());
  JSONObject result=call("POST","https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",null,body);
  if(result.getIntValue("code")!=0 || result.getString("tenant_access_token")==null)throw error("SOURCE_UNAVAILABLE","application token unavailable");return result.getString("tenant_access_token");
 }
 private JSONObject call(String method,String url,String token,JSONObject body)throws Exception{
  Request.Builder builder=new Request.Builder().url(url);if(token!=null)builder.header("Authorization","Bearer "+token);
  if("GET".equals(method))builder.get();else builder.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toJSONString()));
  try(Response response=http.newCall(builder.build()).execute()){
   if(!response.isSuccessful() || response.body()==null)throw error("SOURCE_UNAVAILABLE","upstream transport");
   JSONObject result=JSON.parseObject(response.body().string());
   if(!(result.get("code") instanceof Number))throw error("SOURCE_UNAVAILABLE","invalid upstream response");return result;
  }
 }
 static String canonical(Object value){
  if(value instanceof Map){
   TreeMap<String,Object> sorted=new TreeMap<>((a,b)->{int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}return Integer.compare(a.length()-i,b.length()-j);});
   ((Map<?,?>)value).forEach((k,v)->sorted.put(String.valueOf(k),v));StringJoiner join=new StringJoiner(",","{","}");sorted.forEach((k,v)->join.add(JSON.toJSONString(k)+":"+canonical(v)));return join.toString();
  }
  if(value instanceof List){StringJoiner j=new StringJoiner(",","[","]");for(Object v:(List<?>)value)j.add(canonical(v));return j.toString();}
  if(value instanceof Number && !(value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte || value instanceof java.math.BigInteger))throw error("INVALID_ARGUMENT","only integer JSON numbers allowed");
  return JSON.toJSONString(value);
 }
 static String sha(String value){try{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))b.append(String.format("%02x",v&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
