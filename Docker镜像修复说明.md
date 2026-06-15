# Docker 镜像修复说明

## 问题原因

`openjdk:17-jre-slim` 镜像在 Docker Hub 上已经不再维护，导致微信云托管部署时无法拉取镜像。

**错误信息**：
```
openjdk:17-jre-slim: not found
```

---

## 解决方案

使用 Eclipse Temurin 镜像替代 OpenJDK 镜像。

**Eclipse Temurin** 是 Adoptium（原 AdoptOpenJDK）提供的免费、开源、生产就绪的 JDK 发行版。

---

## 修改内容

### Dockerfile 修改

**修改前**：
```dockerfile
# 阶段1：编译
FROM maven:3.8.7-openjdk-17 AS build

# 阶段2：运行
FROM openjdk:17-jre-slim
```

**修改后**：
```dockerfile
# 阶段1：编译
FROM maven:3.8.7-eclipse-temurin-17 AS build

# 阶段2：运行（使用 eclipse-temurin 镜像）
FROM eclipse-temurin:17-jre
```

---

## 完整的 Dockerfile

```dockerfile
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
```

---

## 镜像说明

### maven:3.8.7-eclipse-temurin-17

- **用途**：编译阶段
- **包含**：Maven 3.8.7 + JDK 17
- **大小**：约 400MB
- **来源**：Docker Hub 官方镜像

### eclipse-temurin:17-jre

- **用途**：运行阶段
- **包含**：JRE 17（Java 运行环境）
- **大小**：约 200MB
- **来源**：Eclipse Temurin（Adoptium）

---

## 验证方法

### 1. 本地测试

```bash
# 构建镜像
docker build -t campus-express-backend .

# 运行容器
docker run -p 8080:8080 campus-express-backend

# 测试访问
curl http://localhost:8080/h2-console
```

### 2. 微信云托管部署

1. 提交代码到 GitHub
2. 微信云托管 → 服务管理 → 重新部署
3. 查看构建日志，应该显示：

```
Step 1/10 : FROM maven:3.8.7-eclipse-temurin-17 AS build
...
Step 10/10 : CMD ["java", "-jar", "app.jar"]
Successfully built xxxxx
Successfully tagged xxxxx
```

---

## 其他可选镜像

如果 Eclipse Temurin 镜像也有问题，可以使用以下替代方案：

### 方案 1：Amazon Corretto

```dockerfile
# 编译
FROM maven:3.8.7-amazoncorretto-17 AS build

# 运行
FROM amazoncorretto:17-alpine
```

### 方案 2：Azul Zulu

```dockerfile
# 编译
FROM maven:3.8.7-zulu-17 AS build

# 运行
FROM azul/zulu-openjdk:17-jre
```

### 方案 3：使用 Alpine 减小体积

```dockerfile
# 运行
FROM eclipse-temurin:17-jre-alpine
```

---

## 常见问题

### Q1: 构建时提示 "permission denied"

**原因**：Docker 权限问题

**解决**：
```bash
# Linux/Mac
sudo docker build -t campus-express-backend .

# 或添加用户到 docker 组
sudo usermod -aG docker $USER
```

### Q2: 构建很慢

**原因**：首次拉取镜像需要时间

**解决**：
1. 使用国内镜像源
2. 配置 Docker 镜像加速器

### Q3: 微信云托管构建失败

**原因**：代码库配置错误

**解决**：
1. 检查 Dockerfile 是否在项目根目录
2. 检查 pom.xml 是否正确
3. 查看云托管构建日志

---

## 镜像大小对比

| 镜像 | 大小 | 说明 |
|------|------|------|
| openjdk:17-jre-slim | ~200MB | 已废弃 |
| eclipse-temurin:17-jre | ~200MB | 推荐 |
| eclipse-temurin:17-jre-alpine | ~100MB | 更小 |
| amazoncorretto:17-alpine | ~100MB | AWS 支持 |

---

## 总结

### 修改内容

- ✅ 编译镜像：`maven:3.8.7-openjdk-17` → `maven:3.8.7-eclipse-temurin-17`
- ✅ 运行镜像：`openjdk:17-jre-slim` → `eclipse-temurin:17-jre`

### 优势

- ✅ 镜像可用，不会出现 "not found" 错误
- ✅ Eclipse Temurin 是官方推荐的生产级 JDK
- ✅ 长期维护，稳定性好
- ✅ 与 OpenJDK 完全兼容

---

**修复完成！** 现在可以成功构建 Docker 镜像并部署到微信云托管。
