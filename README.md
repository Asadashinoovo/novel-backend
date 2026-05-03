# Novel - 在线小说阅读与创作平台


## 项目结构

```
novel-backend/          # 后端 (Spring Boot 3.5.14 + Java 17)
```

## 技术栈

### 后端
- **框架**: Spring Boot 3.5.14
- **Java 版本**: 17
- **ORM**: MyBatis-Plus 3.5.15
- **数据库**: MySQL
- **缓存**: Redis
- **安全**: Spring Security

## 功能模块


### 后端 API
- **AuthController** - 用户认证 (登录/注册)
- **UserController** - 用户管理
- **BookInfoController** - 书籍信息
- **ChapterController** - 章节管理

## 快速开始


### 后端

```bash
cd novel-backend
# 配置 application.yaml 中的数据库和 Redis 连接
./mvnw spring-boot:run
```

## 数据库配置

后端配置文件: `novel-backend/src/main/resources/application.yaml`

需要配置:
- MySQL 数据库连接
- Redis 连接

## SQL文件
创建novel库，然后运行sql
novel.sql

