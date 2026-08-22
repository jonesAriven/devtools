# 用 DaoCloud 镜像源，无需登录
FROM eclipse-temurin:21-jre-jammy
WORKDIR /activecode
COPY target/activation-code-server-1.0.0.jar activation-code-server-1.0.0.jar
EXPOSE 8080

# 设置容器时区为上海时间
ENV TZ=Asia/Shanghai

# 数据库连接指向 MySQL GR 集群（3节点多主 failover）
ENV SPRING_DATASOURCE_URL=jdbc:mysql://192.168.31.105:3306,192.168.31.182:3307,192.168.31.182:3308/tools?loadBalanceStrategy=random&loadBalanceHostRemoval=true&failOverReadOnly=false&retriesAllDown=3&connectTimeout=5000&socketTimeout=30000&autoReconnect=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
ENV SPRING_DATASOURCE_USERNAME=tools
ENV SPRING_DATASOURCE_PASSWORD=toolsmarschat

ENTRYPOINT ["java", "-jar", "activation-code-server-1.0.0.jar"]
