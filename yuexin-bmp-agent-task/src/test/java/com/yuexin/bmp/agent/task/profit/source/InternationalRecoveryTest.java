package com.yuexin.bmp.agent.task.profit.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class InternationalRecoveryTest {
 JdbcTemplate db;ObjectMapper json=new ObjectMapper();String day="2021-01-01";
 @BeforeEach void setup(){db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));db.execute("CREATE TABLE profit_international_day(data_day VARCHAR(10) PRIMARY KEY,attempts INT,state VARCHAR(20),payload LONGTEXT,raw_response LONGTEXT,lease_token VARCHAR(36),lease_until TIMESTAMP,error_code VARCHAR(64),error_status INT)");db.execute("CREATE TABLE profit_source_attempt(data_day VARCHAR(10),attempt_no INT,state VARCHAR(30),raw_response LONGTEXT,PRIMARY KEY(data_day,attempt_no))");}
 InternationalReceiptStore store(){return new InternationalReceiptStore(db,json);}
 Map<String,Object> response(){return Collections.singletonMap("receipt","synthetic");}
 void expired(){db.update("UPDATE profit_international_day SET lease_until=? WHERE data_day=?",new java.sql.Timestamp(0),day);}
 void seed(int attempts,String raw){db.update("INSERT INTO profit_international_day(data_day,attempts,state,raw_response,lease_token,lease_until) VALUES(?,?,'FETCHING',?,'dead-worker',?)",day,attempts,raw,new java.sql.Timestamp(0));db.update("INSERT INTO profit_source_attempt(data_day,attempt_no,state) VALUES(?,?,'STARTED')",day,attempts);}
 @Test void restartResumesRawWithoutSpendingFourthAttempt(){seed(3,"{\"receipt\":\"synthetic\"}");Map<String,Object> result=store().fetch(day,()->{throw new AssertionError("source must not be called");},r->r);assertEquals(response(),result);assertEquals(3,db.queryForObject("SELECT attempts FROM profit_international_day",Integer.class));assertEquals("SUCCESS",db.queryForObject("SELECT state FROM profit_source_attempt",String.class));}
 @Test void restartWithoutRawConsumesRemainingBudget(){seed(1,null);store().fetch(day,this::response,r->r);assertEquals(2,db.queryForObject("SELECT attempts FROM profit_international_day",Integer.class));assertEquals("INTERRUPTED",db.queryForObject("SELECT state FROM profit_source_attempt WHERE attempt_no=1",String.class));}
 @Test void crashNeverResetsExhaustedBudget(){seed(3,null);SourceException e=assertThrows(SourceException.class,()->store().fetch(day,()->{throw new AssertionError();},r->r));assertFalse(e.retryable);assertEquals(3,db.queryForObject("SELECT attempts FROM profit_international_day",Integer.class));}
 @Test void simultaneousInstancesMakeOnlyOneSourceCall() throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();AtomicInteger calls=new AtomicInteger();
  try{Future<Map<String,Object>> first=pool.submit(()->store().fetch(day,()->{calls.incrementAndGet();entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError();}catch(InterruptedException e){throw new RuntimeException(e);}return response();},r->r));assertTrue(entered.await(5,TimeUnit.SECONDS));SourceException busy=assertThrows(SourceException.class,()->store().fetch(day,()->{calls.incrementAndGet();return response();},r->r));assertTrue(busy.retryable);release.countDown();assertEquals(response(),first.get(5,TimeUnit.SECONDS));assertEquals(response(),store().fetch(day,()->{throw new AssertionError();},r->r));assertEquals(1,calls.get());}
  finally{release.countDown();pool.shutdownNow();}
 }
 @Test void staleWorkerCannotOverwriteRecoveredResult() throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
  try{Future<Map<String,Object>> old=pool.submit(()->store().fetch(day,()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return Collections.singletonMap("receipt","stale");},r->r));assertTrue(entered.await(5,TimeUnit.SECONDS));expired();assertEquals(response(),store().fetch(day,this::response,r->r));release.countDown();assertThrows(ExecutionException.class,()->old.get(5,TimeUnit.SECONDS));assertEquals(response(),store().fetch(day,()->{throw new AssertionError();},r->r));assertEquals("SUCCESS",db.queryForObject("SELECT state FROM profit_international_day",String.class));}
  finally{release.countDown();pool.shutdownNow();}
 }
 @Test void semanticFailureIsDurableAndNeverRetried(){AtomicInteger calls=new AtomicInteger();assertThrows(SourceException.class,()->store().fetch(day,()->{calls.incrementAndGet();return response();},r->{throw new SourceException("SOURCE_INCOMPLETE",422,false);}));SourceException second=assertThrows(SourceException.class,()->store().fetch(day,()->{calls.incrementAndGet();return response();},r->r));assertEquals("SOURCE_INCOMPLETE",second.code);assertEquals(1,calls.get());}
 @Test void authFailureIsNotMisreportedAsSourceDataError(){assertThrows(SourceException.class,()->store().fetch(day,()->{throw new SourceException("UNAUTHENTICATED",401,false);},r->r));assertEquals(401,assertThrows(SourceException.class,()->store().fetch(day,this::response,r->r)).httpStatus);}
 @Test void validationStorageFailureReusesSavedRaw(){AtomicInteger calls=new AtomicInteger();assertThrows(SourceException.class,()->store().fetch(day,()->{calls.incrementAndGet();return response();},r->{throw new IllegalStateException("simulated persistence fault");}));assertEquals(response(),store().fetch(day,()->{calls.incrementAndGet();return response();},r->r));assertEquals(1,calls.get());}
 @Test void reservationAndAttemptRecordRollBackTogether(){
  db.update("INSERT INTO profit_international_day(data_day,attempts,state) VALUES(?,0,'READY')",day);
  db.update("INSERT INTO profit_source_attempt(data_day,attempt_no,state) VALUES(?,1,'STARTED')",day);
  assertThrows(org.springframework.dao.DuplicateKeyException.class,()->store().fetch(day,()->{throw new AssertionError();},r->r));
  assertEquals(0,db.queryForObject("SELECT attempts FROM profit_international_day",Integer.class));
  assertEquals("READY",db.queryForObject("SELECT state FROM profit_international_day",String.class));
 }
}
