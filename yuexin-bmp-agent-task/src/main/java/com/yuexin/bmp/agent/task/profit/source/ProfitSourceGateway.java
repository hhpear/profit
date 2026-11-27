package com.yuexin.bmp.agent.task.profit.source;
import java.util.Map;
public interface ProfitSourceGateway { Map<String,Object> domesticPage(Map<String,Object> params); Map<String,Object> internationalDay(String day); }
