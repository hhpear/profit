package com.yuexin.bmp.agent.task.profit;
import java.util.Map;
import org.springframework.stereotype.Component;
/** Fails closed until the task-owned delivery adapter is implemented.
 * The existing Notice service is read-only and has no reliable raw-card API.
 */
@Component
public class DubboProfitNoticeGateway implements ProfitNoticeGateway {
 public Map<String,Object> invoke(String method,Object argument){
  throw new ProfitApiException("SOURCE_UNAVAILABLE",503);
 }
}
