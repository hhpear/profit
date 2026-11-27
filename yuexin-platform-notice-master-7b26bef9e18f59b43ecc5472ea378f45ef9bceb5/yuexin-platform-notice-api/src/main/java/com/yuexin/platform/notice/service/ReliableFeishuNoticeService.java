package com.yuexin.platform.notice.service;
import java.util.Map;
/** Trusted internal callers only. Per-recipient durable delivery; return is not proof of verification. */
public interface ReliableFeishuNoticeService {
    Map<String,Object> submit(Map<String,Object> request);
    Map<String,Object> query(String idempotencyKey);
    Map<String,Object> readback(String deliveryId);
    Map<String,Object> readbackOwned(Map<String,Object> request);
}
