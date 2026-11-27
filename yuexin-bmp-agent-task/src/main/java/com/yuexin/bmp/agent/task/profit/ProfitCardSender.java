package com.yuexin.bmp.agent.task.profit;

import java.util.Map;

/** A transport performs one real external submission; it never synthesizes success. */
public interface ProfitCardSender {
 void validate(Map<String,Object> request);
 Map<String,Object> send(Map<String,Object> request);
}
