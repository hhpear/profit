package com.yuexin.bmp.agent.task.profit.source;
public class SourceException extends RuntimeException {
 public final String code; public final int httpStatus; public final boolean retryable;
 public SourceException(String code,int status,boolean retryable){super(code);this.code=code;this.httpStatus=status;this.retryable=retryable;}
}
