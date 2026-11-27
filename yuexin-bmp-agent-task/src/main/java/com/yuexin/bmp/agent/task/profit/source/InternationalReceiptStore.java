package com.yuexin.bmp.agent.task.profit.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Database leases fence stale workers; source attempts never reset after a crash. */
final class InternationalReceiptStore {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 private final TransactionTemplate tx;
 InternationalReceiptStore(JdbcTemplate db,ObjectMapper json){
  this.db=db;this.json=json;this.tx=new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()));
 }
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 @SuppressWarnings("unchecked") private Map<String,Object> decode(String value){try{return json.readValue(value,Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
 private static SourceException busy(){return new SourceException("SOURCE_UNAVAILABLE",503,true);}
 Map<String,Object> fetch(String day,Supplier<Map<String,Object>> source,Function<Map<String,Object>,Map<String,Object>> validate){
  try{db.update("INSERT INTO profit_international_day(data_day,attempts,state) VALUES(?,0,'READY')",day);}catch(org.springframework.dao.DuplicateKeyException ignored){}
  String token=UUID.randomUUID().toString();
  Map<String,Object> claim=tx.execute(status->{
   Map<String,Object> row=db.queryForMap("SELECT * FROM profit_international_day WHERE data_day=? FOR UPDATE",day);
   if("SUCCESS".equals(row.get("state")))return row;
   if("FAILED".equals(row.get("state")))throw new SourceException(row.get("error_code")==null?"SOURCE_INCOMPLETE":row.get("error_code").toString(),row.get("error_status")==null?422:((Number)row.get("error_status")).intValue(),false);
   Timestamp now=db.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class);
   if("FETCHING".equals(row.get("state"))&&row.get("lease_until")!=null&&((Timestamp)row.get("lease_until")).after(now))throw busy();
   int attempt=((Number)row.get("attempts")).intValue();
   // A durable raw response can be validated without another source call or attempt.
   if(row.get("raw_response")==null){
    if(attempt>=3)throw new SourceException("SOURCE_UNAVAILABLE",503,false);
    if("FETCHING".equals(row.get("state")))db.update("UPDATE profit_source_attempt SET state='INTERRUPTED' WHERE data_day=? AND attempt_no=?",day,attempt);
    attempt++;
    db.update("INSERT INTO profit_source_attempt(data_day,attempt_no,state) VALUES(?,?,'STARTED')",day,attempt);
   }
   db.update("UPDATE profit_international_day SET attempts=?,state='FETCHING',lease_token=?,lease_until=? WHERE data_day=?",attempt,token,new Timestamp(now.getTime()+180000),day);
   row.put("attempts",attempt);return row;
  });
  if("SUCCESS".equals(claim.get("state")))return decode((String)claim.get("payload"));
  int attempt=((Number)claim.get("attempts")).intValue();
  try{
   Map<String,Object> raw=claim.get("raw_response")==null?source.get():decode((String)claim.get("raw_response"));
   String encoded=encode(raw);
   tx.execute(status->{
    if(db.update("UPDATE profit_international_day SET raw_response=? WHERE data_day=? AND lease_token=? AND state='FETCHING'",encoded,day,token)!=1)throw busy();
    db.update("UPDATE profit_source_attempt SET raw_response=? WHERE data_day=? AND attempt_no=?",encoded,day,attempt);return null;
   });
   Map<String,Object> result=validate.apply(raw);
   tx.execute(status->{
    if(db.update("UPDATE profit_international_day SET state='SUCCESS',payload=?,lease_token=NULL,lease_until=NULL,error_code=NULL,error_status=NULL WHERE data_day=? AND lease_token=? AND state='FETCHING'",encode(result),day,token)!=1)throw busy();
    db.update("UPDATE profit_source_attempt SET state='SUCCESS' WHERE data_day=? AND attempt_no=?",day,attempt);return null;
   });
   return result;
  }catch(Exception error){
   SourceException failure=error instanceof SourceException?(SourceException)error:new SourceException("SOURCE_UNAVAILABLE",503,true);
   tx.execute(status->{
    int changed=db.update("UPDATE profit_international_day SET state=?,lease_token=NULL,lease_until=NULL,error_code=?,error_status=? WHERE data_day=? AND lease_token=? AND state='FETCHING'",failure.retryable?"RETRYABLE":"FAILED",failure.code,failure.httpStatus,day,token);
    if(changed==1)db.update("UPDATE profit_source_attempt SET state=? WHERE data_day=? AND attempt_no=?",failure.retryable?"TRANSPORT_FAILED":"SOURCE_REJECTED",day,attempt);
    return null;
   });
   throw failure;
  }
 }
}
