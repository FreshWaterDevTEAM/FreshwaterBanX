# FreshwaterBanX

一套面向 Velocity 群组服的封禁/踢出管理系统，与 [Matrix](https://matrix.rip) 反作弊集成。

- **FreshwaterBanX**（Velocity 插件）——将处罚记录存入 MySQL，按可配置的 VL 阈值决定踢出 / 临时封禁 / 永久封禁，在登录时拦截被封玩家，渲染完全可自定义的 MiniMessage 断开连接界面，并对外提供 HTTP REST API 与 Java API。
- **FreshwaterBanX-Bridge**（Paper/Spigot 插件）——运行在每个后端服上（与 Matrix 同服），监听 Matrix 的违规事件，取消 Matrix 自带的处罚命令，并把违规转发给代理端。

## 为什么是两个插件？

Matrix 运行在后端（Bukkit）服务器，它的事件（`PlayerViolationEvent`、`PlayerViolationCommandEvent`）使用 `org.bukkit.entity.Player`——只能在后端监听，Velocity 无法直接接收。桥接插件负责监听这些事件并转发到代理端。

```
Matrix(后端)  ->  FreshwaterBanX-Bridge  --插件消息-->  FreshwaterBanX(Velocity)
                     (取消 Matrix 处罚命令)                  决策 + 存储 + 踢出/封禁 + API
```

所有处罚决策都在代理端做出，因此一个被封玩家会被整个群组服拦截，无论他尝试进入哪个后端。

## 构建

需要 **JDK 17+**。在项目根目录执行：

```bash
./gradlew build
```

产物：

- `velocity/build/libs/FreshwaterBanX-1.0.0.jar` —— 安装到 Velocity 代理端。
- `bridge/build/libs/FreshwaterBanX-Bridge-1.0.0.jar` —— 安装到每个运行 Matrix 的后端服。

> 桥接插件基于内置的 Matrix API 编译期桩（`bridge/src/matrixStub`）编译，因此**无需** Matrix 商业 jar 即可构建。运行时使用真实的 Matrix 类（桥接插件 `softdepend` 于 Matrix）。

## 安装

1. 创建 MySQL 数据库，例如 `CREATE DATABASE freshwaterbanx;`。
2. 把 `FreshwaterBanX-*.jar` 放进代理端 `plugins/` 目录，启动一次代理端以生成 `plugins/freshwaterbanx/config.yml`。
3. 编辑配置中的 `database` 部分并重启代理端，数据表会自动创建。
4. 把 `FreshwaterBanX-Bridge-*.jar` 放进每个后端服的 `plugins/` 目录（与 Matrix 同服）并启动。
5. 确认 Velocity 与后端之间的玩家信息转发已正常配置（modern/BungeeGuard），这是插件消息通信所必需的。

## VL 阈值规则

在代理端 `config.yml` 的 `rules` 中配置。当 Matrix 转发一条违规时，插件会应用 `vl` 小于等于玩家当前违规等级的**最高**档：

```yaml
rules:
  default:
    - { vl: 15, action: KICK, reason: "可疑行为" }
    - { vl: 30, action: TEMPBAN, duration: 1h, reason: "作弊" }
    - { vl: 60, action: BAN, reason: "确认作弊" }
  KILLAURA:
    - { vl: 10, action: KICK, reason: "战斗异常" }
    - { vl: 35, action: BAN, reason: "Kill Aura" }
```

- `action`：`KICK`、`TEMPBAN` 或 `BAN`。
- `duration`（仅 TEMPBAN）：例如 `30m`、`1d12h`、`2w`。
- 键名为 Matrix 的 `HackType` 名称（KILLAURA、SPEED、FLY、SCAFFOLD……）。`default` 覆盖所有没有单独规则的作弊类型。

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/ban <玩家> [原因]` | 永久封禁 | `freshwaterbanx.ban` |
| `/tempban <玩家> <时长> [原因]` | 临时封禁 | `freshwaterbanx.tempban` |
| `/kick <玩家> [原因]` | 踢出在线玩家 | `freshwaterbanx.kick` |
| `/unban <玩家>` | 解除封禁 | `freshwaterbanx.unban` |
| `/banlist` | 列出活跃封禁 | `freshwaterbanx.banlist` |
| `/baninfo <玩家>` | 查看玩家封禁详情 | `freshwaterbanx.baninfo` |
| `/fbanx reload` | 重载配置 | `freshwaterbanx.reload` |

其他权限节点：

- `freshwaterbanx.bypass` —— 免疫 Matrix 触发的自动处罚。
- `freshwaterbanx.notify` —— 接收处罚广播。
- `freshwaterbanx.admin` —— 拥有以上全部命令权限。

`<时长>` 支持 `s`/`m`/`h`/`d`/`w` 的组合（例如 `90m`、`1d6h`），或 `perm` 表示永久封禁。

## 自定义界面

`messages.screens` 下的断开连接界面使用 [MiniMessage](https://docs.advntr.dev/minimessage/format.html) 格式。可用占位符：
`<player> <uuid> <reason> <operator> <hacktype> <vl> <id> <type> <created> <expires> <duration> <remaining>`。

## HTTP REST API

在配置的 `http-api` 中开启。若设置了 `token`，请求需带请求头 `Authorization: Bearer <token>`。

| 方法与路径 | 返回 |
| --- | --- |
| `GET /api/bans` | 活跃封禁列表（JSON 数组） |
| `GET /api/bans/{uuid}` | 单个玩家的活跃封禁（含 `remainingMillis` 剩余时间），无则 404 |
| `GET /api/stats/today` | `{ "date": "...", "count": N }`（今日封禁人数） |

示例：

```bash
curl -H "Authorization: Bearer change-me" http://代理地址:8085/api/stats/today
```

## Java API

以 `compileOnly` 方式依赖 `api` 模块，在 FreshwaterBanX 初始化完成后通过静态 Provider 使用：

```java
import io.freshwater.banx.api.*;

if (FreshwaterBanXProvider.isAvailable()) {
    FreshwaterBanXAPI api = FreshwaterBanXProvider.get();

    int today = api.getTodayBanCount();          // 今日封禁人数
    List<BanEntry> bans = api.getActiveBans();    // 封禁列表
    long left = api.getRemainingMillis(uuid);     // 封禁剩余时间（毫秒）

    api.tempBan(uuid, "Notch", 3_600_000L, "原因", "ConsolePlugin");
}
```

## 说明与限制

- 离线玩家只有在曾被记录过（存在于处罚表中）或用原始 UUID 引用时，才能被命令指定；Velocity 没有内置的离线 UUID 查询。
- `/fbanx reload` 会重载消息与 VL 规则。修改 `database` 或 `http-api` 部分需要重启代理端。
- 多代理部署时，让所有代理指向同一个 MySQL 数据库；封禁检查以数据库为准。
