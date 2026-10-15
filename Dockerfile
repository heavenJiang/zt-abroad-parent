# syntax=docker/dockerfile:1
# zt-abroad-parent 镜像：第一阶段用 Maven 构建全部模块，第二阶段只保留 JRE 运行管理后台 zt-admin

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
# 缓存本地仓库，重复构建不再重新下载依赖
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre AS admin
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m"
# 中文字体：服务端渲染分享海报、图卡（Java2D）需要
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-wqy-microhei \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/zt-admin/target/zt-admin.jar /app/zt-admin.jar
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/zt-admin.jar"]
