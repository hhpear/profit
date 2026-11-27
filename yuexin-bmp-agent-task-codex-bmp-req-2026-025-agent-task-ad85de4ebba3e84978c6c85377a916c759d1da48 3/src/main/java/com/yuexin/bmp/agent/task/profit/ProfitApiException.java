package com.yuexin.bmp.agent.task.profit;
public class ProfitApiException extends RuntimeException {
 public final String code; public final int status;
 public ProfitApiException(String code,int status){super(code);this.code=code;this.status=status;}
}
