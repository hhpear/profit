package com.yuexin.bmp.agent.task.profit;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Lossless business-text conversion; Card 2.0 charts become explicitly labelled indices. */
public final class NoticeCardAdapter {
 @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){if(!(value instanceof Map))throw new ProfitApiException("INVALID_ARGUMENT",400);return (Map<String,Object>)value;}
 private static String text(Object value){if(!(value instanceof String)||((String)value).trim().isEmpty())throw new ProfitApiException("INVALID_ARGUMENT",400);return (String)value;}
 public Map<String,Object> adapt(Map<String,Object> card,int maxBytes){
  if(!"2.0".equals(card.get("schema")))throw new ProfitApiException("INVALID_ARGUMENT",400);
  Map<String,Object> header=map(card.get("header"));String title=text(map(header.get("title")).get("content"));
  StringBuilder content=new StringBuilder();
  if(header.get("subtitle") instanceof Map)append(content,text(map(header.get("subtitle")).get("content")));
  elements(map(card.get("body")).get("elements"),content);
  if(content.length()==0||title.getBytes(StandardCharsets.UTF_8).length>256||content.toString().getBytes(StandardCharsets.UTF_8).length>maxBytes)throw new ProfitApiException("INVALID_ARGUMENT",400);
  Map<String,Object> subtitle=new LinkedHashMap<>();subtitle.put("class","com.yuexin.platform.notice.param.Subtitle");subtitle.put("fontColor","Black");subtitle.put("bold",false);subtitle.put("content",content.toString());
  Map<String,Object> result=new LinkedHashMap<>();result.put("class","com.yuexin.platform.notice.param.Message");result.put("title",title);
  String color=String.valueOf(header.getOrDefault("template","red"));
  if(!Arrays.asList("red","orange","yellow","green","blue","purple","grey","white").contains(color))throw new ProfitApiException("INVALID_ARGUMENT",400);
  result.put("titleColor",color.substring(0,1).toUpperCase(Locale.ROOT)+color.substring(1));result.put("subtitles",Collections.singletonList(subtitle));return result;
 }
 private void elements(Object value,StringBuilder out){if(!(value instanceof List))throw new ProfitApiException("INVALID_ARGUMENT",400);for(Object item:(List<?>)value){Map<String,Object> element=map(item);String tag=String.valueOf(element.get("tag"));
  switch(tag){
   case "markdown":append(out,text(element.get("content")).replaceAll("<text_tag(?:\\s[^>]*)?>(.*?)</text_tag>","$1"));break;
   case "hr":append(out,"────────");break;
   case "column_set":elements(element.get("columns"),out);break;
   case "column":elements(element.get("elements"),out);break;
   case "chart":
    Map<String,Object> spec=map(element.get("chart_spec"));Object values=map(spec.get("data")).get("values");
    if(!"line".equals(spec.get("type"))||!"date".equals(spec.get("xField"))||!"trendIndex".equals(spec.get("yField"))||!(values instanceof List))throw new ProfitApiException("INVALID_ARGUMENT",400);
    StringJoiner series=new StringJoiner("；");for(Object point:(List<?>)values){Map<String,Object> row=map(point);Object index=row.get("trendIndex");if(!(index instanceof Integer)||((Integer)index)<0||((Integer)index)>100)throw new ProfitApiException("INVALID_ARGUMENT",400);series.add(text(row.get("date"))+":"+index);}
    append(out,"近15日相对走势（0–100，非利润金额）："+series);break;
   default:throw new ProfitApiException("INVALID_ARGUMENT",400);
  }
 }}
 private static void append(StringBuilder out,String value){if(out.length()>0)out.append("\n\n");out.append(value);}
}
