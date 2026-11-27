package com.yuexin.bmp.agent.task.profit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuexin.bmp.agent.task.profit.source.ProfitSourceService;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Temporary assumed-success implementation: no external send or readback occurs. */
@Component
public class DubboProfitNoticeGateway implements ProfitNoticeGateway {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 public DubboProfitNoticeGateway(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new ProfitApiException("INVALID_ARGUMENT",400);}}
 @SuppressWarnings("unchecked") private Map<String,Object> decode(String value){try{return json.readValue(value,Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
 private String required(Map<String,Object> r,String key){Object v=r.get(key);if(!(v instanceof String)||((String)v).trim().isEmpty())throw new ProfitApiException("INVALID_ARGUMENT",400);return (String)v;}
 private Map<String,Object> find(String column,String value){
  List<String> rows=db.query("SELECT payload FROM profit_assumed_delivery WHERE "+column+"=?",(rs,n)->rs.getString(1),value);
  if(rows.isEmpty())throw new ProfitApiException("NOT_FOUND",404);return decode(rows.get(0));
 }
 @SuppressWarnings("unchecked") public Map<String,Object> invoke(String method,Object argument){
  if("query".equals(method))return find("idempotency_key",(String)argument);
  Map<String,Object> request=(Map<String,Object>)argument;
  if("readbackOwned".equals(method)){
   Map<String,Object> record=find("delivery_id",required(request,"deliveryId"));
   if(!Objects.equals(record.get("ownerPrincipal"),request.get("ownerPrincipal")))throw new ProfitApiException("FORBIDDEN",403);
   return record;
  }
  if(!"submit".equals(method))throw new ProfitApiException("INVALID_ARGUMENT",400);
  String key=required(request,"idempotencyKey");
  List<String> slot=new ArrayList<>();for(String field:Arrays.asList("application","dataDay","mode","recipientId"))slot.add(required(request,field));
  try{java.time.LocalDate.parse(slot.get(1));}catch(Exception e){throw new ProfitApiException("INVALID_ARGUMENT",400);}
  if(!(request.get("card") instanceof Map))throw new ProfitApiException("INVALID_ARGUMENT",400);
  for(String field:Arrays.asList("rulesetVersion","rulesetSha256","inputSha256","cardSha256"))required(request,field);
  String slotHash=ProfitSourceService.hash(encode(slot));
  // Binding includes actual card and trusted receiver, not just caller-supplied digests.
  String binding=ProfitSourceService.hash(encode(canonical(request)));
  Map<String,Object> record=new LinkedHashMap<>(request);record.remove("card");
  record.put("deliveryId","assumed-"+UUID.randomUUID());record.put("state","SENT");
  record.put("messageId",null);record.put("chatId",null);record.put("readbackConfirmed",false);
  record.put("assumedSuccess",true);record.put("deliveryMode","ASSUMED_SUCCESS_NO_SEND");record.put("binding",binding);
  try{db.update("INSERT INTO profit_assumed_delivery(slot_hash,idempotency_key,delivery_id,payload) VALUES(?,?,?,?)",slotHash,key,record.get("deliveryId"),encode(record));return record;}
  catch(org.springframework.dao.DuplicateKeyException e){
   List<String> rows=db.query("SELECT payload FROM profit_assumed_delivery WHERE slot_hash=? OR idempotency_key=?",(rs,n)->rs.getString(1),slotHash,key);
   if(rows.size()!=1)throw new ProfitApiException("IDEMPOTENCY_CONFLICT",409);
   Map<String,Object> old=decode(rows.get(0));if(!binding.equals(old.get("binding")))throw new ProfitApiException("IDEMPOTENCY_CONFLICT",409);return old;
  }
 }
 private Object canonical(Object value){if(value instanceof Map){Map<String,Object> sorted=new TreeMap<>();((Map<?,?>)value).forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}if(value instanceof List){List<Object> out=new ArrayList<>();for(Object v:(List<?>)value)out.add(canonical(v));return out;}return value;}
}
