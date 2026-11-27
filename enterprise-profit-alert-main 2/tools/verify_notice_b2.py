"""Isolated Notice regression with explicit model/annotation stubs; not a company build."""
import argparse
import shutil
import subprocess
import tempfile
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--maven',default='mvn')
args=parser.parse_args()
root=Path(__file__).resolve().parents[2]
notice=next(root.glob('yuexin-platform-notice-master-*'))
with tempfile.TemporaryDirectory(prefix='notice-b2-') as directory:
    work=Path(directory)
    def put(path,text):
        target=work/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text)
    def copy(relative,source):
        target=work/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy(source,target)
    package='com/yuexin/platform/notice/'
    for name,source in [
        ('service/ReliableFeishuNoticeService.java',notice/'yuexin-platform-notice-api/src/main/java'/package/'service/ReliableFeishuNoticeService.java'),
        ('service/impl/ReliableFeishuNoticeServiceImpl.java',notice/'yuexin-platform-notice-service/src/main/java'/package/'service/impl/ReliableFeishuNoticeServiceImpl.java')]:
        copy('src/main/java/'+package+name,source)
    copy('src/test/java/'+package+'service/impl/ReliableDeliveryTests.java',notice/'yuexin-platform-notice-service/src/test/java'/package/'service/impl/ReliableDeliveryTests.java')
    put('src/main/java/'+package+'model/NoticeApp.java','package com.yuexin.platform.notice.model; public enum NoticeApp { BMP, BusinessAlarm }')
    put('src/main/java/'+package+'config/AppConfig.java','package com.yuexin.platform.notice.config; import java.util.*; import com.yuexin.platform.notice.param.Application; public class AppConfig { private List<Application> apps; public List<Application> getFeishuApps(){return apps;} public void setFeishuApps(List<Application> a){apps=a;} }')
    put('src/main/java/'+package+'param/Application.java','package com.yuexin.platform.notice.param; import com.yuexin.platform.notice.model.NoticeApp; public class Application { private NoticeApp app; private String id,secret; public NoticeApp getNoticeApp(){return app;} public void setNoticeApp(NoticeApp a){app=a;} public String getAppId(){return id;} public void setAppId(String x){id=x;} public String getAppSecret(){return secret;} public void setAppSecret(String x){secret=x;} }')
    put('src/main/java/org/apache/dubbo/config/annotation/DubboService.java','package org.apache.dubbo.config.annotation; public @interface DubboService {}')
    put('pom.xml','''<project><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent><groupId>local</groupId><artifactId>notice-b2-isolated</artifactId><version>1</version><properties><java.version>1.8</java.version></properties><dependencies>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
<dependency><groupId>com.alibaba</groupId><artifactId>fastjson</artifactId><version>1.2.83</version></dependency>
<dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>3.14.9</version></dependency>
</dependencies></project>''')
    subprocess.run([args.maven,'-q','test'],cwd=work,check=True)
