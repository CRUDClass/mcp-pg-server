FROM eclipse-temurin:21-jre-alpine

RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app
COPY target/pg-server-1.0.0.jar mcp-pg-server.jar

EXPOSE 15432
ENTRYPOINT ["java", "-jar", "/app/mcp-pg-server.jar"]
