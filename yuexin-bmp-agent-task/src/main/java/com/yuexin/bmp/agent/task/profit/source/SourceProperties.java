package com.yuexin.bmp.agent.task.profit.source;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;
@Component @ConfigurationProperties("profit.source")
public class SourceProperties {
 private boolean mappingConfirmed,stableReadConfirmed; private String mcpUrl="https://flux-admin.yuexin.cn/mcp/",mcpToken="",dubboRegistry="",dubboVersion="",dubboGroup="";
 private List<Long> customerIds=new ArrayList<>(); private Map<String,String> enterpriseNames=new HashMap<>();
 public boolean isMappingConfirmed(){return mappingConfirmed;} public void setMappingConfirmed(boolean x){mappingConfirmed=x;}
 public boolean isStableReadConfirmed(){return stableReadConfirmed;} public void setStableReadConfirmed(boolean x){stableReadConfirmed=x;}
 public String getMcpUrl(){return mcpUrl;}public void setMcpUrl(String x){mcpUrl=x;}public String getMcpToken(){return mcpToken;}public void setMcpToken(String x){mcpToken=x;}
 public String getDubboRegistry(){return dubboRegistry;}public void setDubboRegistry(String x){dubboRegistry=x;}public String getDubboVersion(){return dubboVersion;}public void setDubboVersion(String x){dubboVersion=x;}public String getDubboGroup(){return dubboGroup;}public void setDubboGroup(String x){dubboGroup=x;}
 public List<Long> getCustomerIds(){return customerIds;}public void setCustomerIds(List<Long>x){customerIds=x;}public Map<String,String>getEnterpriseNames(){return enterpriseNames;}public void setEnterpriseNames(Map<String,String>x){enterpriseNames=x;}
}
