<div align="center">
  <img src="src/main/resources/assets/orbiter/icon.png" alt="Orbiter logo" width="20%"/>
  <h1>Meteor Orbiter</h1>
  <p>一个 <a href="https://meteorclient.com/">Meteor Client</a> 插件，为破坏、欺凌和画质优化带来 60 多个模块、自定义命令和 HUD 元素。</p>

  <a href="https://github.com/player19425/Meteor-Orbiter/releases"><img src="https://img.shields.io/badge/Version-1.0.6-orange" alt="Version"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.3%20%7C%2026.2%20%7C%2026.1.2%20%7C%201.21.11-blue" alt="支持的 Minecraft 版本">
  <img src="https://img.shields.io/badge/Java-25%20%7C%2021-green" alt="Java 版本">
  <a href="https://github.com/player19425/Meteor-Orbiter/releases"><img src="https://img.shields.io/github/downloads/player19425/Meteor-Orbiter/total?style=flat" alt="下载量"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/stargazers"><img src="https://img.shields.io/github/stars/player19425/Meteor-Orbiter?style=flat&cacheSeconds=3600" alt="Star 数"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/commits/26.2"><img src="https://img.shields.io/github/last-commit/player19425/Meteor-Orbiter/26.2?style=flat&cacheSeconds=3600" alt="最后提交"></a>
  <img src="https://img.shields.io/github/languages/code-size/player19425/Meteor-Orbiter?style=flat" alt="代码体积">
  <a href="https://github.com/player19425/Meteor-Orbiter/issues"><img src="https://img.shields.io/github/issues/player19425/Meteor-Orbiter?style=flat" alt="问题反馈"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/blob/26.2/LICENSE"><img src="https://img.shields.io/github/license/player19425/Meteor-Orbiter?style=flat" alt="许可证"></a>
</div>
<p align="center">
  <a href="README.md">English</a> &nbsp;&middot;&nbsp; <a href="README.es.md">Español</a> &nbsp;&middot;&nbsp; <a href="README.zh.md"><b>中文</b></a>
</p>

<hr />

# 简介

Orbiter 是一套为 [Meteor Client](https://meteorclient.com/) 打造的模块与工具合集，最初是为了生存、破坏服和创造/OP 模式而写的。

所有内容在游戏内分为五个分类：

- **Orbiter Survival** • 通用模块与防滥用模块
- **Orbiter Vanilla** • 只使用原版机制的模块
- **Orbiter Creative/OP** • 需要创造模式或管理员权限的模块
- **Orbiter Stupid** • 玩笑 / 实验性模块（在 Meteor Client 设置里开启）
- **Orbiter W.I.P** • 开发中的模块，能用但还比较粗糙

# 运行要求

| Minecraft | Meteor | Java | Loom 插件 |
|---|---|---|---|
| 26.3 | 26.3 | 25+ | `net.fabricmc.fabric-loom` |
| 26.2 | 26.2 | 25+ | `net.fabricmc.fabric-loom` |
| 26.1.2 | 26.1.2 | 25+ | `net.fabricmc.fabric-loom` |
| 1.21.11 | 1.21.11 | 21+ | `net.fabricmc.fabric-loom` |

所有版本都需要 [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 或更高。

每个支持的版本都有自己完整的一套源码和自己的 Gradle 工程，所以它们都可以单独编译、打标签或者单独发布。详见 [docs/multi-version.md](docs/multi-version.md)。

# 编译

仓库根目录编译的是 Minecraft 26.3：

```
gradlew build
```

其他版本在各自的文件夹里编译：

```
cd versions/26.2
gradlew build
```

每个工程都会生成 `build/libs/meteor-orbiter-1.0.6-<mc>.jar`。

| 命令 | 作用 |
|---|---|
| `gradlew build` | 编译该文件夹 `version.properties` 里指定的 Minecraft 版本的 jar |
| `gradlew runClient` | 启动一个加载了本插件的开发客户端 |
| `gradlew printTarget` | 打印该工程对应的 Minecraft/Meteor 组合 |

# 安装

1. 下载与你所用 Minecraft 版本对应的 [发布版本](https://github.com/player19425/Meteor-Orbiter/releases)。
2. 把 `.jar` 放进 `.minecraft/mods` 文件夹（具体位置取决于你用的启动器）。
3. 装有 Fabric 和 Meteor Client 的情况下启动游戏。
4. 到这里就已经装好了。

Orbiter 还能自己保持更新：当你进入服务器或世界时，它会检查有没有新版本，并在游戏内弹出窗口，提供更新 / 忽略 / 查看更新日志三个选项；打开开关后它甚至能自己下载并安装新的 jar。所有设置都在 Meteor Config → Orbiter 里，两个开关都由你决定（弹窗默认开启）。

每个 Minecraft 版本各有各的发布版本，所以检查器只会给你当前所运行版本对应的 jar，绝不会把别的 Minecraft 版本的 jar 塞给你。

# 模块：

## 战斗：

| 模块 | 说明 |
|---|---|
| **Aim Assist Plus** | 带预测的自瞄。支持静默瞄准和基于弹道计算的俯仰角。 |
| **Anti Knockback** | 免疫击退。 |
| **Auto Totem+** | 在真实的物品栏会话中站着不动时自动更换图腾。 |
| **Mace Assist** | 自动瞄准并用重锤敲击。 |
| **No Friend Hit** | 不会打到 Meteor 的好友。 |
| **Out Of Reach** | 在玩家触及范围之外作战。 |

所有辅助瞄准模块都可以选择要瞄准哪些实体类型（玩家、盔甲架等等）。

下面这些位于 Orbiter W.I.P 分类：

| 模块 | 说明 |
|---|---|
| **Bow Assist** (W.I.P) | 自动瞄准弓。 |
| **Crossbow Assist** (W.I.P) | 自动瞄准弩。 |
| **Trident Assist** (W.I.P) | 自动瞄准并投掷三叉戟。 |
| **Spear Assist** (W.I.P) | 近战辅助。 |
| **Shield Assist** (W.I.P) | 自动用盾牌格挡。 |
| **Precision Shot** (W.I.P) | 准星处的静默瞄准。 |

## 移动：

| 模块 | 说明 |
|---|---|
| **Anti Push** | 阻止液体和实体推挤。 |
| **Auto Clutch** | 自动拉扯以避免摔落伤害。 |
| **Force Invisibility** | 服务端隐身。 |
| **Jump A** | 越过墙壁并够到方块。 |
| **Noclip** | 无碰撞、无重力地穿过方块。会屏蔽服务器发来的纠正传送，因此穿出另一侧后位置不会回弹。每隔几秒轻微下沉一点，这样原版的浮空踢出就不会触发。 |
| **Slime Jump** | 在史莱姆上跳得更高。 |

## 玩家：

| 模块 | 说明 |
|---|---|
| **Auto Craft Plus** | 以最快速度自动合成物品。 |
| **Client Side Mine** | 在客户端瞬间破坏方块。 |
| **Restock** | 自动补充你的快捷栏。 |

## 世界：

| 模块 | 说明 |
|---|---|
| **Auto Farming** | 自动种植作物和养殖动物。 |
| **Bed Defender** | 在床周围自动建造防护圈。 |
| **Command Block Placer** | 放置命令方块。创造 + OP。 |
| **Control Player** | 让周围的玩家旋转起来。OP。 |
| **Death Override** | 当 kill 命令方块触发时隐藏死亡界面。 |
| **Entity Spammer** | 生成并动画化实体。需要 OP。 |
| **Item Creator** | 制作自定义物品。仅创造模式。 |
| **Item Generator** | 生成带随机附魔的物品。创造模式。 |
| **Operator Nuker** | 用 /fill 清除方块。OP。 |
| **RNG Spammer** | 在玩家周围生成战利品表。OP。 |
| **TNT Rain** | TNT 雨。OP。 |
| **UUID Ban (W.I.P)** | 用 UUID 把玩家锁在门外。OP。 |
| **World Downloader** | 把服务器世界保存到本地。 |
| **World Edit** | 客户端版 WorldEdit。`.we <命令>` |
| **World Eraser** | 清除半径内的方块。切换两次触发。OP。 |

## 渲染：

| 模块 | 说明 |
|---|---|
| **Block Spam** | 让方块下雨。可以下雨、动画化，或在目标周围 setblock。OP。 |
| **Block Spoof** | 在客户端伪造方块贴图。 |
| **Bossbar Flash** | 随机闪烁 Boss 血条。需要 OP。 |
| **Camera 360** | 无限角度的摄像机旋转。 |
| **Firework Show** | 带形状和颜色的烟花表演。 |
| **Particle Control** | 在目标周围生成粒子形状。 |
| **Particle Spam** | 在半径内刷粒子。需要 OP。 |
| **Playsound Spam** | 刷出所有音效。需要 OP。 |
| **View Blocks** | 透视隐形方块。 |

## 杂项：

| 模块 | 说明 |
|---|---|
| **Actions** | 响应式触发器与动作。 |
| **Anti Staff** | 检测管理员并自动离开。 |
| **Auto Find** | 扫描藏宝点和基地。 |
| **Auto Login** | 自动回答登录和注册提示、聊天和对话框。 |
| **Auto Shop** | 自动在服务器商店购买。 |
| **Client Side Things** | 本地视觉伪装。 |
| **Enchantment Cracker** | 破解附魔台报价里隐藏的附魔种子，并准确报出全部三行。 |
| **Exploit Preventer** | 拦截常见的服务器漏洞利用。 |
| **I Sell Wand** | 用卖出魔杖自动出售。 |
| **Infini Reach** | 无限攻击距离。 |
| **Item Info** | 在物品提示里显示额外信息。 |
| **Item Stealer** | 从 GUI 里偷物品。 |
| **Leave Message** | 离开前发送一条聊天消息。 |
| **Message Formatter** | 格式化发送出去的聊天。 |
| **Peak Plugin Scanner** | 检测服务器插件。 |
| **Ping Spoof** | 伪装你的延迟。 |
| **Server Protect** | 拦截崩溃包和恶意物品。 |
| **Spam Plus** | 用字母阶梯刷屏。 |

# 命令：

| 命令 | 说明 | 别名 |
|---|---|---|
| `.autoshop` | 开关并控制 AutoShop 模块。 | `autoshopdetect` |
| `.copy` | 把坐标、摄像机位置、手持物品、NBT、注视目标等复制到剪贴板。 | |
| `.copypos` | 把摄像机位置复制到剪贴板（配合 Freecam 使用）。 | `cpos` |
| `.escape` | 控制 ForceInvisibility 的脱身逻辑。 | |
| `.enchantmentcracker` | 破解附魔种子，精确预测每一行并给出完整报价。可用 `status` 或 `reset` 作为子命令。 | `enchantcracked`, `encc` |
| `.exportmodulelist` | 把所有模块名导出到剪贴板。 | |
| `.fixdeath` | 停止假死亡循环并强制重新同步客户端/服务端状态。 | |
| `.ghostblock` | 只有你能看见的客户端幽灵方块。 | `gb` |
| `.givepresetitems` | 200 多个仅限 OP 和命令方块的预设物品。 | `gpi` |
| `.hidekeybind` | 在控制项界面隐藏 Meteor 的按键绑定。 | |
| `.isellwand` | 控制 ISellWand 模块。 | `sellwand` |
| `.itemcrash` | 给手持物品塞入极端附魔、属性和 NBT 数据。 | |
| `.itemstealer` | 保存 / 读取 / 管理克隆出来的物品。 | `is`, `steal` |
| `.multicommand` | 用选择器对多个玩家执行命令。 | |
| `.nbt` | 把手持物品的完整 NBT 复制到剪贴板。 | |
| `.orbitergivepreset` | 发放 120 多个带说明文字的特殊预设物品。 | `ogp` |
| `.peakscan` | 插件扫描器 • 检测服务器插件。 | |
| `.setprefix` | 设置 Meteor 聊天命令的前缀。 | |
| `.tntrain` | 用指定参数触发 TNT 雨。 | |
| `.transfer` | 不掉线直接切换到另一个服务器。 | |
| `.uuidban` | 通过召唤一个 UUID 实体来封禁玩家。（W.I.P） | |
| `.verify-protect` | 用已知的攻击载荷测试 ServerProtect 的崩溃物品检测。 | |
| `.we` | WorldEdit 的克隆版，不过用的是 /fill。 | `worldedit` |

# HUD：

| 元素 | 说明 |
|---|---|
| **Custom Text** | 在 HUD 上显示带占位符的自定义文本。 |
| **Nearest Player** | 显示最近的玩家以及距离。 |
| **Render Distance** | 显示当前渲染距离。 |
| **Server TPS** | 显示服务器的每秒刻数。 |
| **Server Time** | 显示游戏内的日期和时间。 |
| **Server Version Note** | 当服务器运行的是不同版本时给出提示（协议桥接）。 |
| **Server Difficulty** | 显示世界难度。 |
| **Server Protocol** | 显示服务器协议版本。 |
| **Server Players** | 显示在线人数。 |
| **Server Brand** | 显示服务器品牌。 |
| **Server Real Version** | 显示从服务器列表里拿到的真实服务器版本。 |
| **Server Version** | 显示服务器版本。 |
| **Server Real IP** | 显示连接的真实 IP 地址。 |
| **Server IP** | 显示服务器 IP（域名）。 |
| **Server Plugins** | 显示检测到的插件数量。 |
| **Weapon Cooldown** | 显示当前武器攻击冷却时间（秒）。 |

# 从源码编译：

### 前置条件：
- [JDK](https://adoptium.net/temurin/releases) 25 或更高
- Gradlew

### 步骤：
```bash
git clone https://github.com/player19425/Meteor-Orbiter
cd Meteor-Orbiter

./gradlew build
```

编译好的 JAR 会在 `build/libs/` 里。

# 安全须知

- 破坏性功能都需要明确确认，只应该在有备份的一次性本地服务器上测试。
- 产生命令的模块会尽量使用能力检测和有限长度的队列。命令可用并不代表你有权限。
- 本 Meteor Client 插件造成的任何损害 __不__ 由插件作者负责，责任完全在于执行操作的人。
- 下载本插件的唯一官方途径是 [它的 GitHub 页面。](https://github.com/player19425/Meteor-Orbiter/releases/latest) 从其他地方下载 __不__ 建议。


# 致谢

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) • 本插件所依赖的客户端。
- 这是个个人项目，但我不想把它藏起来 ♥️

# 许可证

本项目基于 [MIT 许可证](LICENSE)。
