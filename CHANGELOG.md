# KiraCode 变更日志

> 所有显著变更将记录在此文件中。
> 格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
> 版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [Unreleased] · P0 提示词协议统一

### 🔧 协议统一
- 正常工具路径只教 function calling：系统提示词分层重写（身份→诚实→运行规则→工具协议→模式→工作流→质量规范），
  移除全部 [field:]/[book.add]/```card-update/[plan.*]/[task.*]/[subagent.request] 文本协议教学；
- 文本协议降级为显式降级路径：provider 拒绝 tools 时由内核注入 `BuiltinSystemPrompt.fallbackNotice`（与存活解析器严格一致，不含 web.search/subagent.request）；
- 降级路径的文本协议操作照常经 Diff 审查（AGENT 挂起 / YOLO 自动接受），不再绕过确认。

### 🧭 模式语义
- YOLO 非交互化：不注册 ask_user/submit_plan/complete_step，不挂起等待用户；缺失信息用合理默认值补全并强制 ai_guess 标注；
- AGENT 保留完整追问/计划/Diff 确认；CHAT 无工具。

### 🧩 规则补齐
- 自定义提示词三态（未配置/停用/启用）：默认模板不再自动注入，设置页显示状态并提供"使用默认模板"；
- 参考材料边界：只学结构/风格/工艺，禁止照抄原角色名/剧情/专有设定/原文句子，多卡按来源标注；
- 大卡改卡工作流：索引→query→get→局部修改，读取预算 1-3 条，截断内容不得整条替换；
- 工具描述与执行器对齐：alternate_greetings 换行分隔、insertion_order 0-999 语义、priority 全链路补齐（schema/操作/applier/解析器）、book_update 整段替换语义、selective 参数接通。

### 🗑️ 清理
- 删除死代码：FULL_AUTO_RULES、MAX_RULES、ChatEngine.send 旧路径（含 ChatEvent/ThinkStreamSplitter/WebSearchParser）、AgentController.start 地基入口（StartAgentUseCase）、StyleImitationUseCase；
- 保留仍被降级路径使用的 CardOps/TaskOps/PlanOps 解析器。

---

## [1.0.0] - 2026-08-28 · R18（README 重写 + 入场动画重做 + 免责声明 + 流式开关）

### 📖 README
- 全新重写：初心声明、项目介绍、20+ 功能、作者、AGPLv3、免责声明、致谢。

### ✨ 入场动画与免责声明
- 品牌页重做为 iOS 官网风格（大标题淡入缩放 + 标语错峰淡入 + 柔和渐变）；
- 新增首次进入免责声明，需「同意并继续」，偏好记录后不再打扰。

### 🌊 流式输出
- 新增「流式输出」开关（数据控制面板，默认开启），关闭后一次性显示；
- ChatEngine 支持非流式/流式双路径，与全自动/MAX 模式兼容。

---
## [1.0.0] - 2026-08-27 · R17（黑屏修复 + 移除 HTTP 服务端 + 角色卡水印）

### 🐛 黑屏修复
- 抽屉菜单加防抖(300ms)，去除 slideOut 退出动画，消除返回转场与抽屉动画叠加的黑屏竞态。

### 🗑️ 移除 HTTP 服务端
- 删除 server 包(嵌入式 HTTP 服务/前台服务/DTO)、Ktor Server 依赖、设置页/引导页/Manifest/偏好中全部 HTTP 相关代码与 UI；保留调用外部 AI API 的 Ktor Client。

### 💧 角色卡水印
- 新增 KiraWatermark：隐形水印(extensions.kira_metadata) + 明显水印(creator/creator_notes/tags)；
- card-update 建卡与 Generate 页自动注入，导入外部卡不加水印；导出往返保真。

---
## [1.0.0] - 2026-08-27 · R16（MAX 模式 + 真实子代理）

### 🤖 MAX 模式
- 新增 WorkMode.MAX：六步长任务工作流(计划书→骨架→设定/世界书→逐条写→正则→验证)；
- MAX 提示词 + 主 Agent 战略指挥/审核/亲自攻难点。

### 👥 真实子代理
- 新增 SubAgentRunner：独立上下文 + 单一任务 + 真实 API 调用(替代原模拟 AgentScheduler)；
- [subagent.request] 协议派发子代理，结果回填主 Agent 审核写入；活动流显示子代理进度。

---
## [1.0.0] - 2026-08-27 · R15（侧边栏黑屏修复）

### 🐛 侧边栏黑屏
- 根因：抽屉退出动画与页面路由转场叠加 + showDrawer 用 rememberSaveable 跨导航恢复过期状态；
- 修复：navigateGuarded 先关抽屉等退出动画(320ms)再导航；showDrawer/showModeSwitcher/showSessionPicker 改 remember。

---
## [1.0.0] - 2026-08-27 · R14（数据写入工具箱重构）

### 🐛 世界书/正则字段拼接根因修复
- 根因：提示词用 | 内联分隔键值对，解析器只认换行 → keys 吞整段、content 空；
- 解析器 | 归一化兼容 + 提示词改清晰换行键值格式；字段分离正确。
- 强化「创建卡必须先设 name」防角色名缺失；新增回归测试。

---
## [1.0.0] - 2026-08-27 · R13（入场动画 + 品牌信息）

### ✨ 入场动画
- 品牌页加入初心声明(永久免费开源/站在普通人身边)+进入按钮+赞助入口；
- 保留 Logo 动画，3 秒自动进入 + 手动进入按钮。

### ❤️ 赞助入口
- 链接 ifdian.net/a/KiraKira-APP，文案「支持开发，或给作者的泡面加根肠」；
- 入场动画底部 + 关于页卡片均可跳转浏览器。

---
## [1.0.0] - 2026-08-27 · R12（仓库存储彻底文件化 + AI 写入落卡）

### 📦 仓库存储（根因）
- DB 迁移 5→6 清空三表历史遗留大字段列 + sessions.workingCardJson，杜绝读旧行 CursorWindow 崩溃；
- 草稿表文件化（补齐最后一块，三表全走文件）。

### 🤖 AI 写入世界书/正则
- 根因：默认 AGENT 模式使 AI 操作只入 pendingOperations 不落卡，却报告"已写入"；
- 修复：WorkspaceState 默认模式 AGENT→AUTO；提示词加"诚实原则"(已写入必须带操作块)。

---
## [1.0.0] - 2026-08-27 · R11（大修：文件存储/UI整合/V3/上下文）

### 📦 角色卡仓库大文件存储
- 新增 WarehouseStorage 文件层：完整卡 JSON + 封面落盘，Room 只存元数据+路径，
  解决 V3 大卡 CursorWindow 崩溃；成品区同样文件化。

### 🎨 UI 布局整合
- 移除输入栏重复的信息行，模式/模型/参考卡统一到顶部栏信息条，输入框不再遮挡消息。

### 📝 SillyTavern V3 支持
- 提示词强化：keys/content 严禁混淆、position 全枚举、世界书条目分工指令。

### 🧠 上下文窗口管理
- 窗口大小(默认128k,最大200万token)+自动总结阈值可配置；达标自动压缩；面板加滑块。

---
## [1.0.0] - 2026-08-27 · R10（仓库"不落库"彻底修复）

### 🐛 角色卡仓库保存（根因）：列表刷新依赖 stateIn(WhileSubscribed) 订阅时序
- CardRepositoryViewModel 重写为「显式 refresh()」去掉 stateIn，init + 页面进入 LaunchedEffect 双触发，
  保存后返回仓库页必刷新，不再停留在旧缓存空列表。
- 结合此前 Result 双层吞异常修复(假成功提示)+读回校验+落库日志，
  完整解决"点保存显示成功但仓库/参考卡选择/重启后都空"。

---
## [1.0.0] - 2026-08-27 · R9（UI布局/状态更新/仓库日志/Markdown）

### 🎨 UI 布局
- 工作台面板从底部移到顶部栏下方细状态条(小字), 底部让位消息+输入框, 消息不再被遮挡。

### 🐛 状态更新
- SystemNote 条目改 DONE(不占进行中计数), 修复任务已结束仍显示「AI 工作中」。

### 📦 角色卡仓库
- 落库日志(Log.d/e 记录 uuid/name/id/异常), getByUuid 加 LIMIT 1, 读回校验三重保障。

### 📝 Markdown 渲染
- 新增 MarkdownText 轻量渲染器(标题/粗体/斜体/行内代码/代码块/引用/列表/表格),
  用户与AI消息均支持, 流式边输出边渲染。

---
## [1.0.0] - 2026-08-27 · R8（仓库健壮性 + 全自动动态感知）

### 📦 角色卡仓库
- 保存后新增读回校验：按 uuid 读回，读到才算成功（杜绝「显示成功但未落库」）。

### 🤖 全自动工作流
- 提示词动态感知会话状态：有卡注入结构化状态摘要(字段/世界书/正则数量)+完整JSON；
  无卡明确提示"先建卡再修改，禁止吐 JSON"。
- FULL_AUTO 提示词强化：五步工作流(检查卡状态→计划书→逐阶段真实改卡→进度标记→完成报告)，
  明确禁止把 JSON 吐到聊天正文、禁止无卡直接修改。

---
## [1.0.0] - 2026-08-27 · R7（定向修复：保存落库 + 全自动真实推进）

### 🐛 角色卡保存落库（真根因）
- saveAsyncWithFeedback 存 Result 双层嵌套 bug：内层 saveReferenceCard 失败被外层 runCatching
  误判成功，静默吞掉 → 显示「已保存」实则未落库；改为直接 fold 内层 Result 真实透传失败。
- Entities.kt Json 加 allowSpecialFloatingPointValues，防 NaN/Infinity 序列化异常导致写入失败。

### 🤖 全自动真实推进（提示词+执行循环双修）
- FULL_AUTO 提示词重写为专用详细版：明确「执行=用 field/book/regex/cover 实际改卡」，
  禁止只维护计划书、禁止空正文；
- 自动推进条件放宽（移除 parts 非空门槛），推进指令明确要求实际改卡；
- 占位文案由「仅包含数据操作」改为动作摘要（已完成N项修改/计划书更新/任务板更新等）。

---
## [1.0.0] - 2026-08-27 · R6（核心功能排查：三问题根因修复）

### 🤖 全自动空回复（真正的残余根因）
- 正文+思考双空时不再 emit Failed 终止，改发 SystemNote + 截断态续跑，任务不断。

### 🐛 AI 不能创建新角色卡（两处）
- 新建会话默认 workMode AGENT→AUTO（建卡直落，不再卡在等待确认）；
- 独立生成页统一走 resolveActiveConfig（新多模型配置，不再读旧 apiKey 失效）；
- card.replace 非法 JSON 预校验 toast，不再静默丢弃。

### ✅ 验证
55/55 单测全绿；三功能各排查链路确认 + 回归。

---
## [1.0.0] - 2026-08-27 · R5（深度排查：全自动空回复 + 仓库导入根因修复）

### 🐛 全自动空回复（根因彻底修复）
- 停滞看门狗 10s 误判长思考/首 token 延迟为空回复；
- 修复：零产出超时转可重试失败(≤8次)，首 token 超时放宽至 60s、已产出停滞 15s。

### 🐛 角色卡仓库导入失败
- 根因：validateRequiredFields 强制 description 非空，拦截大量合法 ST 卡，
  导致不出现保存按钮；放宽为仅 name 必填。
- 保存成功/失败加 Snackbar 明确反馈。

---
## [1.0.0] - 2026-08-27 · R4（六项 bug 排查修复）

### 🐛 修复
- 全自动空回复：空正文但有思考/指令块时改截断态自动续跑；FULL_AUTO maxTokens≥4096 兜底
- 仓库导入保存失败：保存按钮改为带回成功/失败反馈，成功才置「已保存」，失败红字提示
- 侧边栏滚动：抽屉根 Column 加 verticalScroll + navigationBarsPadding，「关于」不再被遮挡
- PNG 导出：有封面时封面铺满整图(居中裁剪)，不再绘制设定文字/明文 JSON；JSON 仅嵌 tEXt 元数据
- 聊天滚动：scrollToItem→animateScrollToItem + 贴底阈值收紧，流式期间可下滑不回扯
- 侧边栏黑屏：导航防抖锁(400ms)，杜绝快速连点重复路由/动画竞争

---
## [1.0.0] - 2026-08-27 · R3（设置/仓库/重试/MAX/封面/会话/提示词/TUI）

### 🖥️ 聊天界面 TUI 终端风格重做
- 移除气泡与头像；等宽字体 + 前缀符号着色（❯ you 青蓝 / ● kira 绿 / ◆ system 灰）；
- 思考过程缩进独立紫块可折叠、流式光标；紧凑终端排版，保留自动滚动。

### 🛠 仓库暂停存/编辑分离
- 导入默认「保存到仓库」且可留页；仓库卡片明确「编辑」按钮，点击主体=预览。

### 🔁 全自动/全局 API 自动重试
- 引擎层 ≤8 次退避重试（2s+抖动），空产出才重试，部分产出按截断保文本；
- 超限 SystemNote 提示后终止；AGENT/CHAT 不重试。

### 🧠 思考强度 MAX 档
- 自动/低/中/高/MAX 五档；MAX 弹费用警告确认后生效并持久化。

### 🖼️ 成品区封面与展示
- 三标签页：卡片 / PNG 导出 / JSON 导出（不再混排）；
- 每卡可添加/更换封面（本地选图内嵌）；导出 PNG 支持 URL 头像解析；无封面才显示占位。

### 📋 工作区记录入口
- 抽屉「新建会话」上方加入口，弹出可滑动列表：恢复/置顶/改名/删除。

### 📉 提示词精简
- 核心协议源码 2990→1576 字符（-47%），静态前缀缓存友好；新增 PromptBudgetTest 守卫。

### 📜 关于页
- 整页可滚动；AGPLv3 全文内嵌 assets 可完整查看。

---
## [1.0.0] - 2026-08-26 · R2（工作台重构与核心修复，versionCode 41）

### 🖥️ AI 工作台可视化（OpenCode 风格）
- 聊天区新增常驻工作台活动流：思考/生成/操作/工具/计划/系统六类条目，
  运行状态点实时刷新，可展开详情、全屏查看完整过程；
- 思考过程可见：解析 delta.reasoning_content/reasoning 字段 + 内联 <think> 标签
  增量拆分（跨 chunk 断裂安全），气泡内嵌折叠式思考块实时增长。

### 📋 工作区记录独立成页
- 从侧边栏移出为完整页面：长按置顶/改名/删除，点击恢复会话上下文。

### 🛡️ 角色卡仓库闪退根治（读取端脏数据）
- 真根因：实体 toDomain 对 tags/alternateGreetings 的无保护 JSON 解析，
  任一历史脏行即导致进入页面必崩；
- 四层防御：decodeStringListSafe 实体层(6处) + 逐行容错映射 + Flow .catch 兜底 + VM 降级提示。

### 🧠 思考强度重做（自动/低/中/高）
- 弃用过时关键词表 → 家族启发式(deepseek-v\d|gpt-[5-9]|o[1-9]|think/reason 词根)
  × 运行时微型请求探测（结果持久化缓存，新模型零代码学习）；
- DeepSeek V4+ 家族自动附带 thinking:{type:enabled}；选择持久化。

### ✅ 回归
52 例测试全绿；全自动/制卡师人格/成品区/公告不受影响。

---
## [1.0.0] - 2026-08-26（正式版发布 · 许可证切换 AGPLv3）

> 版本号由内部迭代号 3.0.0 切换为对外正式版 1.0.0。

### ⚖️ 许可证
- 项目许可证由 MIT 切换为 **GNU AGPLv3**：根目录 LICENSE 为官方全文 + 版权声明
  （Copyright (C) 2026 北辰星）；关于页与首次启动引导加入声明；
  第三方依赖逐一核查均为 Apache-2.0/MIT 等兼容许可，无合规风险；
  严禁利用本代码搭建闭源商业化服务。

### 🤖 全自动模式重构
- 实现 Agent 循环（计划→执行→检查→迭代）：计划书未完成时自动注入推进指令开启下一回合，
  计划缺失先建计划；流式停滞自动"继续"；搜索结果自动回注；共享 10 轮预算防死循环。
- 权限与全局模式一致，全部操作免确认直接生效。

### 🎛️ 制卡师改为开关
- 不再是独立模式；"制卡师模式"开关（默认开）可与会话/代理人/全局/全自动任意共存，
  开启后 AI 在关键抉择给 A/B/C 选项暂停询问用户。

### 🧑‍🎨 制卡师人格
- 多张角色卡全自动训练：逐批分析技术栈/文风/写作习惯/可迁移亮点 → 合成人格提示词；
  进度展示、可取消、结果可编辑保存。
- 人格管理区（数据面板右栏）：多个人格单选启用、编辑、删除、手动新建；
  启用后作为风格基线注入系统提示词（优先级：固定协议 > 会话提示 > 人格 > 自定义规则）。

### 🧠 思考强度
- 检测模型能力（o1/o3/o4/gpt-5/gpt-oss），面板提供 低/中/高/默认 选择器，
  实际传递 reasoning_effort 参数；不支持的模型明确提示并不发送该字段。

### 📥 下载 KiraKira
- 对接 KiraCore 同源公告接口：抽屉入口拉取公告（标题/版本/配图/正文/强更标）并跳转下载；
  关于页支持检查更新；失败友好提示可重试。

### 🛠 修复与发布检查
- 角色卡仓库稳定性复核通过（UUID upsert + distinctBy 双防御）；
- 单元测试增至 43 例全绿；深色主题统一；权限仅 INTERNET；APK 直装签名。

---
## [3.0.0] - 2026-08-26（第三轮全面返工与功能增强）

### 🎛️ 数据控制面板（合并版）
- 输入框左侧「+」号展开双栏面板：左栏计划书/子Agent任务板/Token细分统计/缓存命中率/
  上下文占用；右栏模式切换/参考卡细粒度选择/温度/top_p/自动总结/联网搜索/自定义提示词/存入成品区。
- 数据区支持 AI 读写（plan.* 与 task.* 指令实时上板）。

### 🤖 工作模式五分化
- 新增 **全自动模式**：AI 先写计划书(plan.goal+plan.add)再分阶段执行并实时更新进度；
  流式停滞 10s 无增量自动截断并发送"继续"接力(上限6轮)。
- 新增 **制卡师模式**：关键决策点主动给 A/B/C 选项询问用户；无超时规则。
- 操作确认策略随模式区分；输入框危险模式红/橙变色警示。

### 💬 流式与滚动修复
- 流式请求超时放宽至 10 分钟，长回复不再被掐断；
- 聊天列表仅贴近底部时跟随新消息(scrollToItem)，用户可自由上滑回看。

### 📦 成品区
- 侧边菜单新增入口：完成的卡集中管理；
- 导出 PNG(嵌入 chara/ccv3 元数据)/JSON 到公共 Download(MediaStore, 免权限)；
- 批量导出/删除/全选/编辑跳转/空态引导。

### ✍️ AI 能力全字段化
- 封面编辑：[cover.set]/[cover.clear]，URL 自动下载内嵌 data URI；PNG 导出绘制封面。
- 参考卡细粒度：整卡/风格/技术/指定单条目(世界书条目、正则脚本)，随会话持久化。

### 🔍 联网搜索
- [web.search] 协议 + DuckDuckGo 抓取(免Key)；结果回注对话供 AI 续跑；面板开关持久化。

### 🗂️ 会话管理
- 长按菜单：置顶(DB v5 isPinned)/改名/删除；置顶排序最前。

### 🧠 系统提示词 V3
- 内置 SillyTavern 规范知识库 + 八组操作协议 + 五模式行为准则全面重写。

### 其他
- top_p 参数贯通请求体；ContextInfo 细分统计；导出渲染支持封面；测试增至 38 例全绿。

---
## [2.0.2] - 2026-08-26（第二轮返工与功能补全）

### 🐛 模型选择与切换
- **404 根因修复**：新增 ApiUrlResolver，智能解析 Base URL（含 /v1 版本段、完整端点、尾斜杠），
  RealApiService 全部 8 处端点替换，根治 /v1/v1/chat/completions。
- **模型实时生效**：setChatModel 写激活配置组；ChatEngine 每次发送重新解析配置，无需重启。
- **聊天页模型快捷切换器**：点击顶栏模型名弹出可滚动列表 + 手动输入 + 配置页直达。

### 🐛 角色卡仓库崩溃
- 根因：重复导入产生相同 UUID 行 → LazyColumn duplicate key 崩溃。
- saveReferenceCard 改 UUID upsert + 列表 distinctBy 双保险。

### 💄 深色主题补漏
- MainActivity 窗口底色强制 #121212；ModelConfig/CardRepository 根布局补背景，
  根治透出系统白色窗口底导致的"配色阴间"。

### ✨ 聊天控制面板（新增）
- 顶栏 Tune 入口 → 底部面板：温度滑条(即时生效)、上下文自动总结开关(>80%窗口触发)、
  自定义提示词编辑、风格参考入口。

### ✨ 数据面板 / 任务板（新增，Codewhale 风格）
- task.add/status/delete 协议：AI 可创建与推进任务；任务板注入系统提示词实现可读；
  全模式即时应用。悬浮窗实时显示任务进度与状态色点。

### 🧠 AI 能力增强
- 世界书 BookUpdate 支持 priority 优先级字段（全链路）。
- 内置知识库：docs/知识库_SillyTavern规范.md + 系统提示词注入字段语义/码表/协议速查。

---
## [2.0.1] - 2026-08-26（返工修复）

### 🐛 崩溃与白屏
- **导入参考卡闪退（根因）**：整卡 JSON 经 URL 编码进导航路由，大卡触发
  TransactionTooLargeException。新增 CardTransferBus 内存总线，全链路改内存传参；
  Generate/Edit/Export 缺数据自动回退防白屏。
- **设置项点不开**：9 个设置对话框此前均为 TODO 空实现，现已全部实现并可交互；
  HTTP 服务端支持真实重启；版本号更新 2.0.0。

### 💄 深色主题统一
- 全局强制深色（移除浅色方案与跟随系统），规范色板：
  背景 #121212 / 卡片 #1E1E1E / 输入框 #2A2A2A；系统栏恒深色。

### 🐛 模型列表滑动
- 模型列表补 verticalScroll，修复超出高度即截断无法滚动。

### ✨ 工作区增强
- 工作卡面板新增「预览」标签：全字段+世界书+正则只读实时渲染。
- 状态悬浮窗（上下文占用/Token/缓存命中率/压缩/释放，可折叠）配色随全局主题刷新。

### 🐛 侧边栏遮挡
- 抽屉头部补 statusBarsPadding，KiraCode 标识不再被状态栏遮挡。

### 🧠 AI 协议修复（严重）
- **自闭合指令曾被静默丢弃**：[book.delete]/[book.clear]/[regex.delete] 按提示词为
  自闭合单行语法，解析器却仅支持成对闭合。改为双通道解析并同步提示词；
  新增回归测试 + AI 全流程 E2E 测试（写设定/写世界书/写正则→导出 ST v2→往返无损）。

---


> 所有显著变更将记录在此文件中。
> 格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
> 版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [2.0.0] - 2026-08-26

### 🎉 重大重构（对话式 Agent 工作流）

#### SillyTavern 格式完整兼容
- **世界书（character_book）**：条目管理、触发/次级关键词、常驻、插入顺序、位置
  （兼容规范字符串/ST 数字/旧布尔/extensions 数字四种形态）
- **正则脚本（extensions.regex_scripts）**：查找替换规则增删改，作用域 placement 支持
- **无损保真**：分层 raw 保全设计，导入导出往返未知扩展字段逐字保留（单元测试断言）
- v1/v2/v3 全版本自动识别；PNG 导入导出（tEXt chara+ccv3 双块，同名去重）

#### 对话式 Agent
- **三模式工作流**：会话模式（只读）/ 代理人模式（待确认队列，逐条或批量应用·拒绝）/
  全局模式（自动应用）
- **结构化操作协议**：field/book.add·update·delete·clear/regex.add·delete/card.replace，
  AI 不再输出 JSON 让用户复制
- **ChatEngine**：真实多轮上下文（全量历史）、工作卡状态注入、参考卡风格画像注入、SSE 流式

#### API 与配置
- RealApiService 新增 chatCompletion(Stream)；多模型配置组激活路由；
  拉取模型列表 URL 规范化；apiMode 默认 real

#### UI/UX
- ChatViewModel 工作区状态机；会话绑定工作卡/模式/参考卡选择（Room v4 迁移）
- PendingOpsPanel 待确认操作面板；WorkspaceCardSheet 三标签卡编辑器（基本/世界书/正则）
- 导入页文件选择器（.json/.png）；「直接进入对话编辑」直达工作区
- 参考卡多选总线（仓库页 → 聊天）；顶部模型与卡名展示，点击进入编辑面板

#### 工程
- 单元测试 20 例全绿（ST 往返/PNG chunk/操作协议）
- 修复：返回导航重复建会话、[field:x] 冒号解析、Base64 JVM 可测性、布尔 position 映射
- 产出 `KiraCode_V2.0.apk`（versionName 2.0.0）

---
## [1.4.0] - 2026-08-22

### 🐛 修复
- **模式切换器溢出屏幕**：`WorkModeSwitcher` 三个 Tab (会话/代理人/全局) 在小屏设备上超出宽度无法点击。使用 `weight(1f)` 等分可用宽度，文本居中单行省略，外层 `fillMaxWidth()` 自适应。
- **输入框被键盘遮挡**：`ChatScreen` 底部输入栏未响应键盘高度。增加 `.imePadding()` 修饰符，键盘弹出时输入框自动上移始终可见。
- **设置页面无法滚动**：`SettingsScreen` 根 `Column` 缺少滚动支持。添加 `.verticalScroll(rememberScrollState())`，内容超屏可完整滑动到底部。
- **拉取模型列表报错**：Base URL 拼接逻辑固定导致 `/v1/v1/models` 重复；错误信息仅含 HTTP 码无响应体。规范化 URL 拼接（已含 `/v1` 则仅拼 `/models`），非 200 时读取 `errorStream` 并附带前 200 字符到异常信息。

### 💄 UI/UX 优化
- **统一深色主题**：`KiraCodeTheme` 默认 `darkTheme = true`；清理所有硬编码浅色残留：
  - AUTO 模式警示背景 `#FFFDECEA` → `#3A1E1E`，边框 `#E53935` → `#EF5350`，标签文字 `#C62828` → `#EF9A9A`
  - Snackbar 背景 `#F0F0F0` → `#2A2A2A`，文字跟随 `onSurface`
  - 语境使用率中等阈值色 `#FF9800` → `#FFB74D`
- **全应用视觉一致**：深色背景下文字对比度达标，无突兀浅色块，AUTO 模式警示在深色底下清晰可辨。

### 🔧 构建与工程
- **补齐 `settings.gradle.kts`**：配置 `pluginManagement.repositories { google(); mavenCentral(); gradlePluginPortal() }`，解决 Android Gradle Plugin 8.4.0 解析失败
- **清理废弃源码目录**：删除顶层残留 `data/`、`domain/`、`ui/` 旧副本，避免编译误包含导致 40+ 重复定义错误
- **编译与打包验证**：`compileDebugKotlin` 零错误；`assembleRelease` 成功产出 `KiraCode_V1.4.apk` (16.7 MB)

---

## [1.0.1-fix] - 2026-08-21

### 🐛 修复
- **新手引导无法滑动（阻塞性）**：`OnboardingScreen` 此前仅靠按钮切页、无任何手势，且步骤内容 `Column` 使用 `fillMaxSize()` 将底部"下一步/跳过"按钮挤出可见区域，小屏用户既滑不动也点不到，卡死无法进入主界面
  - 增加 `detectHorizontalDragGestures` 水平滑动手势（阈值 48dp，左滑下一页/右滑上一页，末页左滑触发完成）
  - 步骤内容区改为 `weight(1f)`，按钮始终可见可点
  - `SplashViewModel` 新增 `prevStep()` 支持回看上一页
- **导出完成后导航断链**：`onExportComplete` 原本 `popBackStack(IMPORT_CARD)`，会误退回导入页而非创作流程栈根；改为 `popBackStack(CHAT)`，导出后正确回到主界面
- **Release 构建被 lint 阻塞**：`backup_rules.xml` / `data_extraction_rules.xml` 的 `<exclude path="cache">` 不在已 include 路径内，触发 `FullBackupContent` lint vital 报错；移除无效 exclude，构建通过

### ✨ 新增
- **主界面"创作角色卡"入口**：用户从新手引导进入 Chat 主界面后此前无任何路径进入核心创作流程（导入→生成→编辑→导出），新增：
  - 顶部栏常驻"创作"图标按钮
  - 空状态页改为创作引导（标题 + 流程说明 + "开始创作"主按钮）
  - 均跳转 `ImportCard` 路由，闭合主创作流程
- **全局统一形状规范 `KiraCodeShapes`**：注入 `MaterialTheme`，统一所有 Material3 组件圆角（按钮 12dp / 卡片 16dp / 弹层 24dp+），替代逐处硬编码

### 💄 UI/UX 优化
- 主按钮增加 3dp 柔和阴影及按压态过渡，次级按钮 2dp 阴影
- `KiraCodeCard` 默认 elevation 由 1dp 提升至 3dp，层次感更强
- 按钮圆角由 20dp 统一至 16dp，与卡片风格一致
- 引导页步骤 Icon 增加圆形 `primaryContainer` 背景托底，进度条圆角 2→4dp，精致度提升
- 切页动画采用 `AnimatedContent` + `slideInHorizontally`/`fadeIn`，`EaseInOutCubic` 280ms，符合 iOS 风格平滑过渡

### 📦 构建
- Release APK 产出：`KiraCode_V1.0_fix.apk`（16.7MB，debug 签名，可直接安装）

---

## [1.0.0] - 2025-01-20

### 🎉 新增功能

#### 核心架构
- **Clean Architecture + MVVM**：完整的分层架构 (Domain/Data/UI)，依赖倒置，可测试性强
- **Hilt 依赖注入**：单例组件管理，生命周期自动绑定
- **Kotlin 协程 + Flow**：全异步响应式编程，状态管理统一

#### 数据层
- **Room 数据库 v2**：5 表设计 (CharacterCard/Draft/Session/Message/ReferenceCard)，外键级联，Flow 观察
- **DataStore Preferences**：18 项配置键 (API/HTTP/Agent/UI)，类型安全 Flow API
- **Ktor Client/Server**：统一网络栈，Client 支持 SSE 流式，Server 基于 Netty 高性能
- **kotlinx.serialization**：全链路 JSON 序列化，忽略未知字段，向后兼容

#### 领域模型
- **CharacterCard**：完整对应 SillyTavern v2/v3 规范 (23 字段)，UUID/时间戳/版本管理
- **Session/Message**：会话聊天模型，支持 system/user/assistant/tool 角色
- **GenerationRequest/Result**：生成请求/响应封装，风格模板枚举
- **StyleFeatures/ImitationMode**：风格特征提取，严格/轻度双模式

#### UseCase 业务逻辑
- **ImportCharacterCardUseCase**：JSON 解析 + 必填校验 + 错误定位
- **GenerateCharacterCardUseCase**：Mock/Real 双模式切换，流式/非流式双接口
- **SendMessageUseCase**：会话消息发送，流式回复，上下文管理，自动总结触发
- **SummarizeContextUseCase**：80% 窗口阈值检查，AI 生成摘要，注入 system message
- **AgentSchedulerUseCase**：9 类 Agent 任务分解，拓扑排序依赖，信号量并发控制，智能合并
- **StyleImitationUseCase**：7 维度特征提取，Few-shot Prompt 构造，Strict/Loose 双模式

#### UI 界面
- **SplashActivity**：品牌动画 (缩放/旋转/脉冲)，5 步引导向导，首次启动检测
- **ImportCardScreen**：JSON 粘贴/文件选择，实时解析预览，错误高亮
- **ChatScreen**：消息气泡列表，流式打字机动画 (三点跳动)，底部输入栏 (语音/发送)
- **SessionSidebar**：抽屉式会话列表，新建/切换/重命名/删除/归档，进度指示
- **GenerateScreen**：参考卡预览，描述输入，风格模板 Chip 选择，Temperature/MaxTokens 滑块
- **EditScreen**：4 分区表单 (基础/角色设定/对话设定/进阶可折叠)，标签/问候语专用编辑器
- **ExportScreen**：卡片预览，JSON/PNG 切换，头像开关，系统分享 Intent
- **SettingsScreen**：AI API 配置，HTTP 服务端控制，Agent 开关，主题/语言/自动保存

#### 通用组件库
- **Buttons**：Primary/Secondary/Outlined，圆角 20dp，Material 3 色板
- **Cards**：Standard/Elevated，圆角 16dp，阴影分级
- **Animations**：消息进入/退出 (300ms)，页面转场 (400ms)，打字机指示器，骨架屏闪烁
- **StateComponents**：空态 (会话/消息/参考卡/搜索/网络)，骨架屏 (卡片/消息/会话/设置)
- **Accessibility**：错误边界/网络/权限降级，语义化容器/卡片/表单/进度，LiveRegion，高对比度/大字体/减少动画/触摸目标/颜色对比度检查

#### HTTP 服务端
- **HttpServerForegroundService**：FOREGROUND_SERVICE_DATA_SYNC，通知栏常驻 (启动/停止/重启)
- **KiraCodeHttpServer**：Ktor Netty 嵌入式，ContentNegotiation/CallLogging/JWT 认证
- **REST API**：
  - `GET /health` - 健康检查
  - `POST /generate` - 角色卡生成 (参考卡+描述+风格)
  - `POST /session` - 创建会话
  - `GET /session` - 会话列表 (分页)
  - `GET /session/{id}` - 会话详情 + 消息历史
  - `POST /session/{id}/message` - 发送消息 (流式/非流式)
  - `GET /reference-card` - 参考卡列表
- **认证**：Bearer Token (可选)，JWT 验证中间件
- **配置**：端口 (默认 8080) + Auth Token，DataStore 持久化

#### 主题与国际化
- **Material 3 动态配色**：种子色 #6750A4，Light/Dark/System 三模式
- **Typography**：中文优化行高/字重，等宽字体 MonoTextStyle
- **SystemBarsColorSync**：Edge-to-Edge 沉浸式状态栏/导航栏
- **Strings.xml**：完整中文本地化，ContentDescription 无障碍支持

---

## [0.1.0] - 2025-01-15 (MVP)

### 新增
- 基础 Clean Architecture 搭建
- Room 数据库 (CharacterCard/Draft)
- DataStore Preferences (12 项配置)
- MockApiService (4 个预设卡，模拟延迟/失败率)
- Import/Generate/Edit/Export 线性导航流程
- JSON/PNG 导出 (tEXt chunk 嵌入)
- Mock API 生成 (4 种风格预设卡)
- Material 3 基础主题 + 基础组件库

---

## 计划中 / 未来版本

### [1.1.0] - 计划中
- [ ] 语音输入 (Speech-to-Text) 集成
- [ ] 角色卡分享社区 (导入/导出分享码)
- [ ] 多模态支持 (图片生成/头像上传)
- [ ] 插件系统 (自定义 Agent/导出格式)
- [ ] 云同步 (可选 Account/备份)

### [1.2.0] - 规划中
- [ ] 多语言支持 (English/Japanese)
- [ ] 桌面端 (Compose Multiplatform Desktop)
- [ ] Web 管理后台 (HTTP API 可视化)
- [ ] 性能基准测试报告自动化

---

*生成时间：2025-01-20*  
*KiraCode V1.0 - 基于 SillyTavern 格式的 AI 角色卡生成器*