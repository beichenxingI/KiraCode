# KiraCode 内置知识库：SillyTavern 角色卡规范

> 本文档是 KiraCode 内置 AI 知识库的人类可读版本。
> 核心要点已注入 `BuiltinSystemPrompt`，AI 在创作时会遵循本规范；
> 完整字段级无损支持由 `StCardCodec` 实现。

---

## 一、角色卡规格（Character Card Spec v2 / v3）

### 1.1 顶层结构

```json
{
  "spec": "chara_card_v2",          // 或 chara_card_v3
  "spec_version": "2.0",
  "data": { ... }                    // v2/v3 全部字段在 data 内；v1 为平铺
}
```

KiraCode 兼容三种形态：v1 平铺、v2 包裹、v3 包裹。导入自动识别，导出按卡规格输出。

### 1.2 data 字段总表

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 角色名 |
| `description` | string | ✅ | 主设定，第三人称，支持 `{{char}}`/`{{user}}` 占位符 |
| `personality` | string | 建议 | 性格概述（v3 倾向合并进 description） |
| `scenario` | string | 建议 | 当前场景/世界观背景 |
| `first_mes` | string | ✅ | 开场白，应立即展现角色特质并可推动剧情 |
| `mes_example` | string | 可选 | 对话示例，`<START>` 分隔多轮，格式 `{{user}}: ... {{char}}: ...` |
| `alternate_greetings` | string[] | 可选 | 备选开场白 |
| `creator_notes` | string | 可选 | 创作者备注（使用说明） |
| `system_prompt` | string | 可选 | 覆盖默认主提示词（多数前端可开关） |
| `post_history_instructions` | string | 可选 | 注入对话历史末尾的强指令（JIT/jailbreak 位） |
| `tags` | string[] | 可选 | 标签 |
| `creator` | string | 可选 | 作者 |
| `character_version` | string | 可选 | 卡版本号（约定 "main"） |
| `nickname` | string | v3 可选 | 昵称 |
| `character_book` | object | 可选 | **角色世界书**（见第二节） |
| `extensions` | object | 可选 | 扩展区，含 **regex_scripts**（见第三节）与 depth_prompt 等 |

**写作要领**
- description 结构建议：外貌 → 身份/身世 → 性格矛盾点 → 关系 → 口头禅/习惯；
- first_mes 用动作+对话开场，避免大段独白；50–200 字为佳；
- mes_example 至少两个 `<START>` 场景，示范说话风格而非剧情复述。

---

## 二、角色世界书（Character Book）

世界书 = 随卡携带的**条件触发知识库**：命中关键词时把条目正文注入上下文。

### 2.1 条目结构

```json
{
  "uid": 0,
  "keys": ["银针", "医术"],
  "secondary_keys": ["治疗"],
  "content": "条目正文…",
  "comment": "条目标题",
  "constant": false,
  "selective": true,
  "insertion_order": 100,
  "enabled": true,
  "position": "before_char",
  "extensions": { "position": 0, "priority": 10, "depth": 4, "probability": 100 }
}
```

### 2.2 字段语义（AI 创作必读）

| 字段 | 语义 |
|------|------|
| `keys` | 主触发词，任一出现在近期对话即候选 |
| `secondary_keys` | 次级词；`selective=true` 时需与主词**同时**出现才注入 |
| `constant` | 蓝灯常驻：无视关键词始终注入（核心世界观用） |
| `selective` | 是否启用次级词联动（绿灯逻辑） |
| `insertion_order` | **注入顺序优先级：数值越小越靠前**（0–200 常用） |
| `position` | v2 字符串 `before_char`/`after_char`；ST 扩展数字 0=角色定义前 1=后 2=AN顶 3=AT_DEPTH |
| `enabled`/`disable` | 启用开关（ST 世界书文件常用反义 `disable`） |
| `extensions.priority` | ST 扩展优先级（预算紧张时先丢弃低值） |
| `extensions.probability` | 触发概率（默认 100） |

### 2.3 创作准则

1. 单卡 3–10 条为宜：核心设定常驻（constant），场景细节配触发词；
2. 条目正文 ≤300 字，信息密度优先；
3. keys 选「会在对话中自然出现」的词，避免生僻专有名词做唯一线索；
4. 同主题多条目时用 insertion_order 控制阅读顺序（总纲 < 细节）。

---

## 三、角色正则（Regex Scripts）

位于 `data.extensions.regex_scripts[]`，对消息文本做正则替换，用于格式化输出。

### 3.1 脚本结构

```json
{
  "id": "uuid",
  "scriptName": "去思考标签",
  "findRegex": "<think>[\\s\\S]*?</think>",
  "replaceString": "",
  "trimStrings": [],
  "placement": [2],
  "disabled": false,
  "markdownOnly": false,
  "promptOnly": false,
  "runOnEdit": true,
  "substituteRegex": false,
  "minDepth": null,
  "maxDepth": null
}
```

### 3.2 placement 作用域码表

| 值 | 作用对象 |
|----|----------|
| 1 | 用户输入 |
| 2 | AI 输出（最常用） |
| 3 | 斜杠命令 |
| 5 | 世界信息 |

### 3.3 典型配方

| 目的 | findRegex | replaceString | placement |
|------|-----------|---------------|-----------|
| 去思考标签 | `<think>[\s\S]*?</think>` | （空） | 2 |
| 思考内容折叠引用 | `<think>([\s\S]*?)</think>` | `> 💭 $1` | 2 |
| 统一省略号 | `。。。+` | `……` | 2 |
| 去首尾空行 | `^\s+|\s+$` | （空） | 2 |

---

## 四、KiraCode 扩展协议速查（card-update）

```
[field:name]…[/field]                      文本字段（白名单见系统提示词）
[book.add] keys/content/comment/constant/
           insertion_order/position        新增条目
[book.update uid=N] …priority/…            按 uid 更新（含 priority 优先级）
[book.delete uid=N]                        自闭合单行
[book.clear]
[regex.add] script_name/find_regex/
            replace_string/placement       新增正则
[regex.delete name=x]                      自闭合单行
[task.add] title/detail/status             任务板新增
[task.status id=x status=DONE]             任务状态推进（自闭合）
[task.delete id=x]
[card.replace] {json}                      整卡替换（慎用）
```

## 五、参考来源

- SillyTavern 官方仓库 `docs/CHARACTER_CARD_SPEC.md`（V2/V3 spec）
- SillyTavern World Info 与 Regex 模块内置文档
- 社区通行实践（chub.ai / character card repo 样本逆向校验）
