package com.yuexin.bmp.agent.task.profit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuexin.bmp.agent.task.profit.source.ProfitSourceService;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;

/** Durable submission guard for the existing asynchronous Notice API. */
@Component
public class DubboProfitNoticeGateway implements ProfitNoticeGateway {
 private final JdbcTemplate db;private final ObjectMapper json;private final ProfitCardSender sender;
 public DubboProfitNoticeGateway(JdbcTemplate db,ObjectMapper json,ProfitCardSender sender){this.db=db;this.json=json;this.sender=sender;}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new ProfitApiException("INVALID_ARGUMENT",400);}}
 @SuppressWarnings("unchecked") private Map<String,Object> decode(String value){try{return json.readValue(value,Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
 private String required(Map<String,Object> r,String key){Object v=r.get(key);if(!(v instanceof String)||((String)v).trim().isEmpty())throw new ProfitApiException("INVALID_ARGUMENT",400);return (String)v;}
 private List<Map<String,Object>> rows(String column,String value){return db.queryForList("SELECT * FROM profit_real_delivery WHERE "+column+"=?",value);}
 private Map<String,Object> view(Map<String,Object> row){
  Map<String,Object> out=decode((String)row.get("payload"));if(row.get("result")!=null)out.putAll(decode((String)row.get("result")));
  out.put("sendAttempted",!"PREPARED".equals(row.get("state")));out.put("state",row.get("state"));out.put("deliveryId",row.get("delivery_id"));out.put("errorCode",row.get("error_code"));return out;
 }
 private Map<String,Object> find(String column,String value){List<Map<String,Object>> records=rows(column,value);if(!records.isEmpty())return view(records.get(0));
  String legacyColumn="delivery_id".equals(column)?"delivery_id":"idempotency_key";
  List<String> old=db.query("SELECT payload FROM profit_assumed_delivery WHERE "+legacyColumn+"=?",(rs,n)->rs.getString(1),value);
  if(!old.isEmpty())return decode(old.get(0));throw new ProfitApiException("NOT_FOUND",404);
 }
 @SuppressWarnings("unchecked") public Map<String,Object> invoke(String method,Object argument){
  if("query".equals(method))return find("idempotency_key",(String)argument);
  Map<String,Object> request=(Map<String,Object>)argument;
  if("readbackOwned".equals(method)){
   Map<String,Object> record=find("delivery_id",required(request,"deliveryId"));
   if(!Objects.equals(record.get("ownerPrincipal"),request.get("ownerPrincipal")))throw new ProfitApiException("FORBIDDEN",403);
   record.put("readbackSupported",false);return record;
  }
  if(!"submit".equals(method))throw new ProfitApiException("INVALID_ARGUMENT",400);
  String key=required(request,"idempotencyKey");if(key.length()>255)throw new ProfitApiException("INVALID_ARGUMENT",400);
  List<String> slot=new ArrayList<>();for(String field:Arrays.asList("application","dataDay","mode","recipientId"))slot.add(required(request,field));
  if(!"enterprise-profit-alert".equals(slot.get(0))||!Arrays.asList("test","production").contains(slot.get(2)))throw new ProfitApiException("INVALID_ARGUMENT",400);
  try{if(!LocalDate.parse(slot.get(1)).isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))))throw new IllegalArgumentException();}catch(Exception e){throw new ProfitApiException("INVALID_ARGUMENT",400);}
  required(request,"ownerPrincipal");required(request,"rulesetVersion");
  if(!(request.get("card") instanceof Map))throw new ProfitApiException("INVALID_ARGUMENT",400);
  for(String field:Arrays.asList("rulesetSha256","inputSha256","cardSha256"))if(!required(request,field).matches("[0-9a-f]{64}"))throw new ProfitApiException("INVALID_ARGUMENT",400);
  String actualCardHash=ProfitSourceService.hash(encode(canonical(request.get("card"))));
  if(!actualCardHash.equals(request.get("cardSha256")))throw new ProfitApiException("INVALID_ARGUMENT",400);
  String slotHash=ProfitSourceService.hash(encode(slot)),binding=ProfitSourceService.hash(encode(canonical(request)));
  List<Map<String,Object>> existing=db.queryForList("SELECT * FROM profit_real_delivery WHERE slot_hash=? OR idempotency_key=?",slotHash,key);
  Map<String,Object> row;
  if(existing.isEmpty()){
   Integer old=db.queryForObject("SELECT COUNT(*) FROM profit_assumed_delivery WHERE slot_hash=? OR idempotency_key=?",Integer.class,slotHash,key);
   if(old!=null&&old>0)throw new ProfitApiException("IDEMPOTENCY_CONFLICT",409);
   sender.validate(request); // Validate configuration and conversion before any submission intent.
   Map<String,Object> payload=new LinkedHashMap<>(request);payload.remove("card");payload.put("sendAttempted",false);payload.put("noticeAccepted",false);payload.put("assumedSuccess",false);payload.put("messageId",null);payload.put("readbackConfirmed",false);payload.put("deliveryMode","NOTICE_QUEUE");
   String id="dl-"+UUID.randomUUID();
   try{db.update("INSERT INTO profit_real_delivery(slot_hash,idempotency_key,delivery_id,owner_principal,binding_hash,state,payload) VALUES(?,?,?,?,?,'PREPARED',?)",slotHash,key,id,request.get("ownerPrincipal"),binding,encode(payload));}
   catch(org.springframework.dao.DuplicateKeyException ignored){}
   existing=db.queryForList("SELECT * FROM profit_real_delivery WHERE slot_hash=? OR idempotency_key=?",slotHash,key);
  }
  if(existing.size()!=1||!binding.equals(existing.get(0).get("binding_hash")))throw new ProfitApiException("IDEMPOTENCY_CONFLICT",409);
  row=existing.get(0);String id=(String)row.get("delivery_id");
  if("PREPARED".equals(row.get("state")))sender.validate(request);
  if(db.update("UPDATE profit_real_delivery SET state='SUBMITTING' WHERE delivery_id=? AND state='PREPARED'",id)==1){
   try{
    Map<String,Object> result=sender.send(request);
    if(!"QUEUED".equals(result.get("state"))||!Boolean.TRUE.equals(result.get("noticeAccepted")))throw new IllegalStateException("Notice did not accept submission");
    db.update("UPDATE profit_real_delivery SET state='QUEUED',result=? WHERE delivery_id=? AND state='SUBMITTING'",encode(result),id);
   }catch(Exception error){
    Map<String,Object> unknown=new LinkedHashMap<>();unknown.put("sendAttempted",true);unknown.put("noticeAccepted",false);
    db.update("UPDATE profit_real_delivery SET state='UNKNOWN',error_code='DELIVERY_UNKNOWN',result=? WHERE delivery_id=? AND state='SUBMITTING'",encode(unknown),id);
    throw new ProfitApiException("DELIVERY_UNKNOWN",503);
   }
  }
  return find("delivery_id",id);
 }
 // Unicode code-point ordering matches Python sort_keys, including supplementary characters.
 private static int compareKeys(String left,String right){PrimitiveIterator.OfInt a=left.codePoints().iterator(),b=right.codePoints().iterator();while(a.hasNext()&&b.hasNext()){int cmp=Integer.compare(a.nextInt(),b.nextInt());if(cmp!=0)return cmp;}return a.hasNext()?1:b.hasNext()?-1:0;}
 private Object canonical(Object value){
  if(value instanceof Float||value instanceof Double||value instanceof java.math.BigDecimal)throw new ProfitApiException("INVALID_ARGUMENT",400);
  if(value instanceof Map){Map<String,Object> sorted=new TreeMap<>(DubboProfitNoticeGateway::compareKeys);((Map<?,?>)value).forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
  if(value instanceof List){List<Object> out=new ArrayList<>();for(Object v:(List<?>)value)out.add(canonical(v));return out;}return value;
 }
}
