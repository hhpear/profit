package com.yuexin.bmp.agent.task.profit;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=ProfitAlertController.class)
public class ProfitExceptionAdvice {
 @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,Object>> error(Exception e,HttpServletRequest r){
  String code="SOURCE_UNAVAILABLE";int status=503;boolean retry=true;
  if(e instanceof ProfitApiException){code=((ProfitApiException)e).code;status=((ProfitApiException)e).status;retry=status>=500;}
  else try {code=String.valueOf(e.getClass().getField("code").get(e));status=((Number)e.getClass().getField("httpStatus").get(e)).intValue();retry=(Boolean)e.getClass().getField("retryable").get(e);}catch(Exception ignored){}
  Map<String,Object> b=new LinkedHashMap<>();b.put("code",code);b.put("message",code);b.put("requestId",r.getAttribute("profit.requestId"));b.put("retryable",retry);return ResponseEntity.status(status).body(b);
 }
}
