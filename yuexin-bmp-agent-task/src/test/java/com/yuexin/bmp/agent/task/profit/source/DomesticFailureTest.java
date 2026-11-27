package com.yuexin.bmp.agent.task.profit.source;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DomesticFailureTest {
 @Test void wrappedSchemaMismatchIsNotRetriedOrExposed(){
  Throwable sql=new RuntimeException("SQLSyntaxErrorException: Unknown column 'source' in 'where clause'");
  SourceException error=ConfiguredSourceGateway.domesticFailure(new java.lang.reflect.InvocationTargetException(sql));
  assertEquals("MAPPING_UNCONFIRMED",error.code);assertEquals(422,error.httpStatus);assertFalse(error.retryable);
  assertEquals("MAPPING_UNCONFIRMED",error.getMessage());
 }
 @Test void connectionFailureRetainsReadRetrySemantics(){
  SourceException error=ConfiguredSourceGateway.domesticFailure(new java.net.ConnectException("connection unavailable"));
  assertEquals("SOURCE_UNAVAILABLE",error.code);assertEquals(503,error.httpStatus);assertTrue(error.retryable);
 }
}
