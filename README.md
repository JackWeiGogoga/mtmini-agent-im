# 外卖大师智能体

<p align="center">
  <img src="frontend/src/assets/logo.jpg" alt="Logo" width="120"/>
</p>

<p align="center">
  <strong>一个功能完善的外卖大师智能体</strong>
</p>

<p align="center">
  <a href="#功能特性">功能特性</a> •
  <a href="#技术栈">技术栈</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#项目结构">项目结构</a> •
  <a href="#配置说明">配置说明</a>
</p>

---

## ✨ 功能特性

### 🔐 用户权限系统

- 用户注册与登录
- OAuth 第三方登录（GitHub、Google）
- 基于角色的权限控制（RBAC）
- 菜单权限管理

### ⚙️ 系统管理

- 用户管理
- 角色管理
- 菜单管理

### 💬 美团 IM 自动回复

- `/imMsg` 回调可靠入库，并在同一事务创建异步回复任务
- 门店顾客长期 Conversation + 30 分钟空闲切分 Session
- 当前 Session 最近 100 轮短期记忆，不使用历史 Session 做长期记忆
- 有门店知识时执行向量召回，作为简单 RAG 上下文
- 实际模型 token 幂等扣减算力，敏感词改写后使用稳定消息 ID 发送
- 总控台、管理员双 Tab 页面与 H5 日志均可查看顾客消息、AI 回复和跨 Session 完整历史

---

## 🛠 技术栈

### 后端

| 技术                   | 版本     | 说明                               |
| ---------------------- | -------- | ---------------------------------- |
| Spring Boot            | 3.4.1    | 应用框架                           |
| Java                   | 17       | 开发语言                           |
| MyBatis                | 3.0.4    | ORM 框架                           |
| MySQL                  | 8.0.33   | 数据库                             |
| Redis                  | -        | 缓存                               |
| Spring Cloud OpenFeign | 2024.0.0 | 微服务调用                         |
| Spring WebFlux         | -        | 响应式 HTTP 客户端（LLM 流式请求） |

### 前端

| 技术         | 版本   | 说明        |
| ------------ | ------ | ----------- |
| React        | 19.2.0 | UI 框架     |
| TypeScript   | 5.9.3  | 开发语言    |
| Vite         | 7.2.4  | 构建工具    |
| Ant Design   | 6.2.1  | UI 组件库   |
| React Router | 7.12.0 | 路由管理    |
| Axios        | 1.13.2 | HTTP 客户端 |
| antd-style   | 4.1.0  | CSS-in-JS   |

---

## 🚀 快速开始

### 环境要求

- **Java**: 17+
- **Node.js**: 18+
- **pnpm**: 8+
- **Maven**: 3.8+
- **MySQL**: 8.0+
- **Redis**: 6.0+

### Java 版本说明

本项目后端必须使用 JDK 17 运行和验证。如果本地安装了多个 Java 版本，请在启动后端或执行 Maven 验证命令前显式切换到 JDK 17。

推荐在当前 shell 中执行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -v
```

预期输出中的 `Java version` 应为 `17.x`。如果仍然显示 `1.8` 或 `11`，请不要继续执行 `mvn test` 或 `spring-boot:run`。

### 本地开发启动

#### 1. 克隆项目

```bash
git clone https://github.com/your-org/project-template.git
cd project-template
```

#### 2. 数据库配置与本地 IM 测试数据

创建 MySQL 数据库并执行初始化脚本（如有），配置数据库连接信息到 `backend/src/main/env/dev/application.yml`。

需要联调 IM 自动回复、日志和跨 Session 历史时，可执行幂等测试夹具：

```bash
mysql -h127.0.0.1 -P3306 -uroot -p mtmini_ai_agent_im < deploy-local/本地测试数据.sql
```

所有美团 SDK 出站调用默认启用 dry-run；只有真实联调时才设置 `MT_API_DRY_RUN=false`。

#### 3. 启动后端服务

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean compile -Pdev spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动。

#### 4. 启动前端服务

```bash
cd frontend
pnpm install
pnpm run dev
```

管理后台将在 `http://localhost:5174` 启动。

#### 5. 启动 H5

```bash
cd h5
pnpm install
pnpm run dev:h5
```

H5 将在 `http://localhost:5173` 启动。

#### 6. 访问应用

管理后台访问：**http://localhost:5174/**

本地测试门店 H5 直登：**http://localhost:5173/?wmPoiId=TEST_EPOI_REPLY_001&platform=local-test**

---

## 📁 项目结构

```
project-template/
├── backend/                    # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gogoga/backend/
│   │   │   │   ├── annotation/     # 自定义注解
│   │   │   │   ├── config/         # 配置类
│   │   │   │   ├── controller/     # 控制器
│   │   │   │   ├── entity/         # 实体类
│   │   │   │   ├── enums/          # 枚举类
│   │   │   │   ├── exception/      # 异常类
│   │   │   │   ├── interceptor/    # 拦截器
│   │   │   │   ├── mapper/         # MyBatis Mapper
│   │   │   │   ├── param/          # 请求参数类
│   │   │   │   ├── service/        # 服务层
│   │   │   │   ├── util/           # 工具类
│   │   │   │   └── vo/             # 视图对象
│   │   │   ├── resources/          # 资源文件
│   │   │   └── env/                # 环境配置
│   │   │       ├── dev/            # 开发环境
│   │   │       ├── test/           # 测试环境
│   │   │       └── online/         # 生产环境
│   │   └── test/                   # 测试代码
│   └── pom.xml
│
├── frontend/                   # 前端项目
│   ├── src/
│   │   ├── components/         # 通用组件
│   │   ├── config/             # 配置文件
│   │   ├── contexts/           # React Context
│   │   ├── hooks/              # 自定义 Hooks
│   │   ├── layouts/            # 布局组件
│   │   ├── pages/              # 页面组件
│   │   │   ├── avatar/         # 虚拟人物管理
│   │   │   ├── chat-test/      # 对话测试
│   │   │   ├── llm-config/     # LLM 配置
│   │   │   ├── login/          # 登录页
│   │   │   ├── menu-manage/    # 菜单管理
│   │   │   ├── role-manage/    # 角色管理
│   │   │   └── user-manage/    # 用户管理
│   │   ├── router/             # 路由配置
│   │   ├── services/           # API 服务
│   │   ├── types/              # TypeScript 类型
│   │   └── utils/              # 工具函数
│   ├── package.json
│   └── vite.config.ts
│
├── docs/                       # 文档
├── openspec/                   # OpenSpec 规范
└── README.md
```

---

## ⚙️ 配置说明

### 后端配置

后端使用 Maven Profile 管理多环境配置，配置文件位于 `backend/src/main/env/` 目录：

- `dev/` - 开发环境配置
- `test/` - 测试环境配置
- `online/` - 生产环境配置

主要配置项：

| 配置项                | 说明                 |
| --------------------- | -------------------- |
| `spring.datasource.*` | 数据库连接配置       |
| `spring.data.redis.*` | Redis 连接配置       |
| `oauth.*`             | OAuth 第三方登录配置 |
| `mt.im-auto-reply.*`  | IM 自动回复、Session、上下文、RAG、模型和重试配置 |

自动回复全局开关 `mt.im-auto-reply.enabled` 默认关闭；完成数据库升级和灰度验证后再按环境显式开启。

### 前端配置

前端环境配置通过 Vite 环境变量管理：

- `.env.development` - 开发环境
- `.env.production` - 生产环境

---

## 🔌 支持的 LLM 提供商

| 提供商        | 类型   | 说明                         |
| ------------- | ------ | ---------------------------- |
| OpenAI        | 标准   | GPT 系列模型                 |
| Anthropic     | 标准   | Claude 系列模型              |
| Google Gemini | 标准   | Gemini 系列模型              |
| DeepSeek      | 标准   | DeepSeek 系列模型            |
| Azure OpenAI  | 自定义 | Azure 托管的 OpenAI          |
| 自定义        | 自定义 | 兼容 OpenAI API 的任意提供商 |

---

## 📦 构建部署

### 后端构建

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
# 开发环境
mvn clean package -Pdev

# 生产环境
mvn clean package -Ponline
```

### 后端验证

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

如果本机存在多个 JDK，请先确认 `mvn -v` 中显示的运行时为 JDK 17，再执行测试。

生成的 JAR 包位于 `backend/target/backend.jar`

### 前端构建

```bash
cd frontend
pnpm run build
```

构建产物位于 `frontend/dist/` 目录
