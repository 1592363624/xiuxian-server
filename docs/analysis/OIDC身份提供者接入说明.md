# OIDC 身份提供者 —— 接入说明

> 让第三方系统（New API、Grafana、GitLab……）把本服当成**登录方式**。
> 玩家在对方登录页点一下，用修仙账号直接进去，不用另注册。

---

## 一、先分清两个方向

这套系统里有两个反方向的 OAuth：

| | 本服当客户端 | **本服当 IdP（本文）** |
|---|---|---|
| 场景 | 第三方来读游戏数据 | 第三方用本服账号登录 |
| 文档 | `OAuth授权码流程使用说明.md` | 本文 |
| 端点前缀 | `/api/auth/oauth/*` | `/api/auth/oidc/*` + `/api/auth/oauth/*` |

两者共用授权码流程，但本文这套多了 OIDC 标准件：Discovery、JWKS、userinfo、id_token。

---

## 二、三步接入

### 第 1 步：服务端开启 OIDC

编辑 `config/application.yml`：

```yaml
oidc:
  enabled: true
  issuer: https://game.example.com     # ← 玩家浏览器能访问到的对外地址
  display-name: 修仙服务器
```

`issuer` **必须写对外域名**。服务端不知道自己在反向代理后面是什么域名，
填错了的表现是"对方自动发现成功，但跳转地址 404"。

### 第 2 步：登记这个客户端

同一个配置文件里的 `oauth2.clients`（和上一套流程共用登记表）：

```yaml
oauth2:
  clients:
    - client_id: newapi
      client_secret: 换成一串32位以上的随机字符
      name: New API
      redirect_uris:
        - https://newapi.example.com/oauth/callback
      scopes:
        - openid        # 必须有，否则对方拿不到身份
        - profile       # 想要用户名就加
        - email         # 想要邮箱就加
      enabled: true
```

`redirect_uris` 要填 New API 那边**给你看的**回调地址，必须逐字符一致。

### 第 3 步：在 New API 里填

Root 登录后进「系统设置 → 自定义 OAuth → 添加提供商」：

| 字段 | 填什么 |
|---|---|
| Well-Known URL | `https://game.example.com/api/auth/oidc/.well-known/openid-configuration` |
| Client ID | `newapi` |
| Client Secret | 你上面配的那串 |
| 认证方式 | `Params (in body)` 或 `Header (Basic Auth)` 都支持 |
| 显示名称 | 登录页按钮上显示的文字 |

填完点「自动发现」，其余端点会被自动填好。保存后登录页就出现入口了。

---

## 三、玩家看到的流程

1. 在 New API 登录页点「修仙服务器」
2. 跳到本服 —— 若未登录，先登录游戏账号
3. 看到同意页：「New API 请求访问你的修仙世界账号数据」，列出要哪些权限
4. 点同意 → 自动跳回 New API，已登录

玩家可以在本服 WebUI 的「已授权应用」页面随时撤销，撤销后立即失效。

---

## 四、技术细节

### 端点一览

| 端点 | 用途 |
|---|---|
| `/.well-known/openid-configuration` | 自动发现 |
| `/jwks.json` | 公钥（验证 id_token 签名） |
| `/userinfo` | 用 access_token 换用户身份 |
| `/api/auth/oauth/authorize` | 授权请求（同意页） |
| `/api/auth/oauth/token` | 换 token |

以上前三个在 `/api/auth/oidc` 下。

### 支持的 scope

| scope | 返回什么 |
|---|---|
| `openid` | **必需**。`sub`，决定能否登录 |
| `profile` | `preferred_username`、`name` |
| `email` | `email`、`email_verified` |

加上游戏数据 scope（`player:read` 等）可以同时授权读数据。

### `sub` 是怎么算的

```
sub = "uid_" + 用户名 + "_" + HMAC-SHA256(用户名) 的前16位
```

**为什么不用 user_id**：它是自增主键，换库或迁移后同一个 id 会指向另一个人，
对方存的映射就串号了 —— A 的账号登进了 B。

**为什么不直接用用户名**：可预测。攻击者能抢先用自己的客户端完成一次登录，
把对方的 `sub` 抢注走。加了 HMAC 后算不出来。

代价：**玩家改名会导致 `sub` 变化**，对方会当成新账号。本服目前没有改名功能，
所以暂时不构成问题。将来若加改名，必须同步处理。

### 关于邮箱

`email_verified` 一律返回 **`false`**。

本服的邮箱是玩家自己填的，没有验证流程。谎报 `true` 会让下游把未验证的邮箱
当成可信身份，可能被用来接管其它账号。

---

## 五、安全设计

| 风险 | 处理 |
|---|---|
| 私钥泄露 → 伪造任意身份 | 密钥文件在 `config/oidc-key.pem`，仅属主可读 |
| 重启换密钥 → 对方验签全挂 | 密钥持久化，`kid` 跨重启不变 |
| JWKS 泄露私钥 | `publicJwkJson()` 从构造上只取 `n`/`e` |
| 用了 HS256（对称） | 强制 RS256，且与业务 JWT 完全分开两套密钥 |
| 抢占别人的 sub | HMAC 后缀，不可猜测 |
| nonce 丢失 → 重放 | 从授权请求一路透传到 id_token |
| 应用身份冒充人 | 客户端凭据型 token 无 `uid`，userinfo 直接拒绝 |
| 撤销后仍能登录 | id_token 依赖 access_token 的 jti，撤销即时生效 |
| userinfo 被中间层缓存 | `Cache-Control: no-store` |

### 一个已知限制

`issuer` 若在运行中改动，**已签发的 id_token 全部失效**（`iss` 不匹配）。
这是 OIDC 规范要求的，不是 bug。改之前请通知所有接入方。

---

## 六、测试覆盖

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `OidcKeyManagerTest` | 6 | 生成/持久化/跨重启一致/JWK 不含私钥/损坏报错 |
| `OidcCoreTest` | 9 | sub 稳定性与不可猜测、id_token claim、防篡改、scope 最小化 |
| `OidcEndpointTest` | 10 | Discovery/JWKS/userinfo 行为、撤销即时失效 |
| `OidcLoginFlowTest` | 3 | **完整链路**：授权→换 token→验签→userinfo |

合计 28 个。

其中 `OidcLoginFlowTest` 最有价值：单看每个端点都"能用"，
但串起来常会暴露参数没透传、issuer 不一致、nonce 丢失这类问题 ——
而它们在对方那边只表现为一句"登录失败"。

### 两个踩过的坑（已写进代码注释）

1. **测试初始化顺序**：`OAuthService` 的静态块会用配置文件里的
   `oauth2.clients` **整个覆盖**注册表。先登记再触发类初始化，登记就丢了，
   表现为「`find()` 查得到但 `authorize()` 报未登记」。
2. **重复授权时 `created_at` 不同步**（上一轮修的）：内存刷新了、库里没刷新，
   重启后页面上的授权时间会"跳回"很久以前。
