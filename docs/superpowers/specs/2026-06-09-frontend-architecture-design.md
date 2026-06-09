# 前端开发与仓库目录演进方案设计

本文定义 Intelligent Outfit Recommendation System 的前端开发方案和仓库目录演进方案。
当前项目已经具备 Java 后端接口、Python AI agent 联动、AI 同步/流式问答、商品、购物车、立即购买、订单和 mock 支付能力；下一阶段要补齐一个以 AI 导购为主、传统浏览为辅的前端。

本文只定义开发边界和模块设计，不执行目录迁移，不创建前端工程，也不修改 Java 后端代码。

## 产品定位

第一版前端不是“普通商城加聊天框”，而是“**AI 为主的服装导购商城，同时保留传统浏览入口**”。

核心体验：
1. 用户可以直接和 AI 对话，说出场景、风格、预算、颜色、身材偏好。
2. AI 返回文字解释和推荐商品卡片。
3. 用户点击推荐卡片后，可以查看详情、选择 SKU、加入购物车或立即购买。
4. **安全与授权边界**：AI 只能提出建议，不能绕过用户同意执行加购、下单或支付。
5. 用户也可以进入传统浏览页，自行搜索、筛选、查看商品，并让 AI 在旁边辅助比较、搭配和选尺码。

## 仓库目标结构

保持当前项目名称 `Intelligent Outfit Recommendation System` 不变，把当前 Git 仓库根目录作为全栈项目根目录。目标只是把现有 Java 后端归入 `backend/`，并在同级新增 `frontend/`，不额外创建新的总目录。

**目标结构：**
```
Intelligent Outfit Recommendation System/
├── backend/
│   ├── src/main/java
│   ├── src/main/resources
│   ├── src/test/java
│   ├── src/test/resources
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── .mvn/
│   └── checkstyle.xml
│
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── vite.config.ts
│   └── index.html
│
├── docs/
│   ├── backend-feature-mapping.md
│   ├── api-testing-with-reqable.md
│   └── superpowers/
│
├── docker-compose.yml
├── README.md
└── .github/
```

**目录职责：**
* `backend/`：Java Spring Boot 后端。迁移后 Maven 命令在该目录执行。
* `frontend/`：前端单页应用。第一版建议使用 React、TypeScript、Vite。
* `docs/`：系统开发文档、接口文档、设计文档和测试说明。
* `docker-compose.yml`：保留在仓库根目录，作为全栈本地依赖入口。
* `.github/`：保留在仓库根目录，后续 CI 需要把后端工作目录改为 `backend/`，并新增前端构建检查。

## 目录迁移边界

目录迁移应作为一个独立步骤处理，不和前端业务开发混在同一个变更里。

**迁移时移动到 `backend/` 的内容：**
* `src/`
* `pom.xml`
* `mvnw`
* `mvnw.cmd`
* `.mvn/`
* `checkstyle.xml`
* Java 后端直接依赖的说明文件，例如后端专用 `HELP.md` 和 `AGENTS.md` (或视情况保留在文档目录)

**保留在根目录的内容：**
* `.git/`
* `.github/`
* `.gitignore`
* `.gitattributes`
* `docs/`
* `docker-compose.yml`
* 根级 `README.md`
* 后续新增的 `frontend/`

**迁移后命令变化：**
* `cd backend`
* `.\mvnw.cmd verify`

**迁移不改变：**
* Java package 名。
* Spring Boot 应用名。
* 数据库表结构。
* API 路径。
* Python agent 调用 Java 后端的 URL 语义。

## 技术栈选型

第一版前端明确采用以下技术栈：
* **框架**：React 19 (或当前最新稳定版) + Vite
* **语言**：TypeScript
* **路由**：React Router
* **状态管理**：Zustand (管理 auth、cart、assistant UI 状态)
* **样式方案**：Tailwind CSS 加少量自定义组件
* **请求库**：TanStack Query 或轻量自封装 API hooks

*选择原因*：AI 流式对话、推荐卡片、购物车抽屉和确认动作非常适合 React 的组件化组合。Vite 启动快，前后端分离联调成本低。TypeScript 能把后端 API 响应、前端动作和交易确认边界严格表达清楚。如果后续决定改用 Vue 3，业务模块边界保持不变，只替换页面和状态管理实现。

## 信息架构

**第一版页面：**
* `/login`：登录页
* `/register`：注册页
* `/ai`：AI 推荐页，以聊天为主，推荐商品和购物车在旁边辅助 (登录后默认主入口)
* `/browse`：传统浏览页，以商品搜索和列表为主，AI 助手在旁边辅助
* `/cart`：购物车页
* `/orders`：订单列表页
* `/orders/:orderNo`：订单详情和 mock 支付页

**导航结构：**
* **顶部导航**：AI 推荐、浏览商品、购物车、订单、用户入口。
* **默认入口**：AI 推荐页默认作为登录后的主入口。
* **浏览能力**：传统浏览页保留完整商城搜索能力。
* **购物车展示**：购物车可以作为独立页面，也可以在 AI 推荐页和浏览页以右侧抽屉展示摘要。
