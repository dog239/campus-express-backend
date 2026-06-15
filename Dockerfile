# 多阶段构建：先编译，再运行

# 阶段1：编译
FROM maven:3.8.7-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段2：运行（使用 eclipse-temurin 镜像）
FROM eclipse-temurin:17-jre
WORKDIR /app

# 从编译阶段复制 jar 文件
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
CMD ["java", "-jar", "app.jar"]
