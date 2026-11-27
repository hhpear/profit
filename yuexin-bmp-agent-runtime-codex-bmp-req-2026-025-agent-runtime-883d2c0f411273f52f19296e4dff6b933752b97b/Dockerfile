FROM yuexinhub-registry.cn-zhangjiakou.cr.aliyuncs.com/yuexin_devops/maven:3.8.6-openjdk-fronts

RUN addgroup -S agent && adduser -S -G agent -h /app agent
WORKDIR /app
COPY target/yuexin-bmp-agent-runtime-*.jar /app/app.jar
USER agent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
