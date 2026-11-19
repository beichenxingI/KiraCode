package com.kirakira.kiracode.domain.prompt

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.RegexGenerationMode

/** 四模式正则提示词与写入角色卡 description 的输出协议。 */
object RegexGenerationPrompts {

    private fun basePromptFor(mode: RegexGenerationMode): String = when (mode) {
        RegexGenerationMode.FULL_FRONTEND -> FULL_FRONTEND_PROMPT
        RegexGenerationMode.FOLDABLE_TAGS -> FOLDABLE_TAGS_PROMPT
        RegexGenerationMode.FOLDABLE_REASONING -> FOLDABLE_REASONING_PROMPT
        RegexGenerationMode.LIGHTWEIGHT -> LIGHTWEIGHT_PROMPT
    }

    fun promptFor(mode: RegexGenerationMode): String =
        basePromptFor(mode) + FORMAT_RULES + EXECUTION_REQUIREMENTS

    fun agentPromptFor(mode: RegexGenerationMode): String =
        basePromptFor(mode) + COMMON_RULES + FORMAT_RULES + EXECUTION_REQUIREMENTS

    fun descriptionProtocolFor(mode: RegexGenerationMode): String = when (mode) {
        RegexGenerationMode.FULL_FRONTEND -> FULL_FRONTEND_DESCRIPTION_PROTOCOL
        else -> SHARED_DESCRIPTION_PROTOCOL
    }

    /** 幂等追加协议；重复编辑或切换模式时替换末尾旧协议，不重复堆积。 */
    fun appendDescriptionProtocol(description: String, mode: RegexGenerationMode): String {
        val withoutOldProtocol = TRAILING_PROTOCOL_REGEX.replace(description.trimEnd(), "").trimEnd()
        return buildString {
            if (withoutOldProtocol.isNotBlank()) {
                append(withoutOldProtocol)
                append("\n\n")
            }
            append(descriptionProtocolFor(mode))
        }
    }

    fun applyDescriptionProtocol(card: CharacterCard, mode: RegexGenerationMode): CharacterCard =
        card.copy(description = appendDescriptionProtocol(card.description, mode))

    private val TRAILING_PROTOCOL_REGEX = Regex(
        """\n*\s*━━━━━━━━ (?:输出协议|输出风格协议) ━━━━━━━━[\s\S]*?━━━━━━━━━━━━━━━━━━━━━━\s*$"""
    )

    private const val COMMON_RULES = """


【落卡要求】
- 按上述数量分别调用 update_card_regex_add；每条脚本独立，不合并成 JSON 文本回复。
- 所有脚本 placement=[2]、markdownOnly=true、promptOnly=false、disabled=false。
- 输出格式约定由 description 末尾内嵌的协议承担，不依赖角色卡 system_prompt、post_history_instructions 或世界书。
- 保留系统追加到 description 末尾的对应协议，不删除、不改写、不重复添加。"""

    private const val FORMAT_RULES = """


【所有模式共用的硬性格式要求】
- findRegex 必须是合法、可编译的 JavaScript 正则字符串，并包含斜杠和 flags。
- 中文场景不得依赖 \b 作为词边界。
- 多行内容使用非贪婪捕获（如 [\s\S]*?），避免跨段吞噬。
- replaceString 的捕获组使用 ${'$'}1、${'$'}2 或官方支持的命名捕获引用，且引用必须真实存在。
- 美化显示规则默认使用 placement=[2]、markdownOnly=true、promptOnly=false。
- 工具参数必须是合法 JSON；JSON 字符串中的真实换行按标准单次转义为 \n，解析后 HTML/CSS 必须保留真实换行。
- 模型每轮只输出格式协议约定的 XML/结构化文本，不能在角色卡对话正文中输出完整 HTML。
- 正则外层捕获标签必须与角色卡 description 中输出协议的示例完全一致。"""

    private const val EXECUTION_REQUIREMENTS = """


━━━━━━━━ 执行要求 ━━━━━━━━
输出以上正则脚本时，必须逐条调用 update_card_regex_add 工具写入当前角色卡。
每条正则对应一次工具调用：
- scriptName: [脚本名称]
- findRegex: [完整正则字符串，包含斜杠和 flags]
- replaceString: [替换字符串，模式 A 为完整 HTML 文档]
- placement: [2]
- markdownOnly: true
- promptOnly: false

禁止用 Markdown 代码块或列表展示正则内容——用户看不到文本形式的正则，
只能看到工具调用后写入角色卡的结果。"""

    private const val FULL_FRONTEND_PROMPT = """
你正在为 SillyTavern + 酒馆助手生成一套完整的状态前端正则。

这是一个“正则替换模板”，不是让角色每轮输出 HTML。
角色每轮只输出约定的结构化 XML 内容；
正则 replaceString 负责把这些内容注入固定的 HTML 单文件模板。

硬性格式要求：
1. replaceString 必须以真实换行的 ```html 开始，以真实换行的 ``` 结束。
2. 围栏内部必须是完整、可独立运行的 HTML5 单文件：
   <!DOCTYPE html>
   <html lang="zh-CN">
   <head>...</head>
   <body>...</body>
   </html>
3. <head> 内包含 charset、viewport 与标题；CSS 写在文档内，不引用外部资源。
4. HTML 内必须有一个明确的正文注入位置，例如：
   <script type="text/plain" id="raw-scene">${'$'}1</script>
   或其他等价的安全注入槽位。
5. HTML/CSS/JS 必须使用真实换行和缩进，不能压成一行。
6. 不要把可见的字面量 \n 当作 HTML/CSS 结构换行。
   仅 JS/字符串内部需要表示换行时，可以使用 \n。
7. 不能只提交半截 <div>、单独 <style> 或单独 <script>。
8. 正则捕获的 XML 外层标签必须与角色卡 description 中输出协议 100% 一致。
9. 输出协议必须明确：模型只输出 XML/结构化正文，不能输出 HTML 文件。
10. 调用 update_card_regex_add 时设置 placement=[2]、markdownOnly=true、promptOnly=false。

【以下是示范，仅供参考，禁止原样复制到角色卡】
⚠️ 下方示范是给你理解结构用的参考，不是角色卡的输出协议。
   角色卡输出协议必须根据本次 findRegex 的顶层标签和字段重新生成，
   不能将示范内容原样粘贴进 description 或任何角色卡字段。
━━━━━━━━ 示范开始 ━━━━━━━━
```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Scene UI</title>
  <style>
    /* 由你根据题材设计 */
  </style>
</head>
<body>
  <main id="app"></main>
  <script type="text/plain" id="raw-scene">${'$'}1</script>
  <script>
    (function () {
      var raw = document.getElementById('raw-scene').textContent || '';
      // 解析 XML、转义后渲染进 #app
    })();
  </script>
</body>
</html>
```
━━━━━━━━ 示范结束 ━━━━━━━━
【示范结束，以上内容不得出现在你生成的角色卡中】
⚠️ 上方示范是给你理解结构用的参考，不是角色卡的输出协议。
   角色卡输出协议必须根据本次 findRegex 的顶层标签和字段重新生成，
   不能将示范内容原样粘贴进 description 或任何角色卡字段。

视觉设计不固定：
你可以根据题材、角色气质和用户要求自由设计前端。
可以选择工具界面、游戏 HUD、杂志排版、终端、极简暗色、复古界面或其他合适方向。
优先保证结构清楚、信息层级合理、移动端可用、内容可读。
不要为了“现代感”机械堆叠渐变、光晕、动画或装饰。

下面的视觉要求是建议，不是固定模板。你可以根据角色卡题材和用户要求自由设计，只要满足输出格式和运行兼容性要求。
- 可以考虑 CSS 自定义属性、Flexbox/Grid、响应式布局与 details/summary 折叠；
- 可以使用 oklch()、hsl() 或其他合法颜色表示；
- 可以采用玻璃质感、极简、杂志、终端、游戏 HUD 或手机界面等风格；
- 应避免明显过时、僵硬或堆砌装饰的设计，但这不是机器校验条件；
- 不强制颜色数量、圆角、字体、动效曲线、断点或具体类名。
- 用户明确提出的审美要求优先于以上默认建议。"""

    private const val FOLDABLE_TAGS_PROMPT = """
你正在为角色卡生成「语义折叠」正则，输出 3 条独立正则脚本。

这些模式生成的是局部 HTML 替换规则，不是完整 HTML 文档。

允许：
- <details>/<summary>
- <span>
- <div>
- inline style
- class 名称
- 多条独立正则

不要求：
- <!DOCTYPE html>
- <html>/<head>/<body>
- ```html 围栏
- 完整 CSS 文件
- 固定视觉风格

三条规则覆盖：
1. 心理活动：匹配 /thinking([\s\S]*?)<\/think>/g，替换为局部折叠结构；
2. 动作描写：匹配 /\*([^*\r\n]{1,300})\*/g，替换为带样式的行内结构；
3. 中文对话：匹配 /「([^」\r\n]{1,200})」/g，保留「」并包裹正文。

必须保证：
- 正则可编译，捕获组与 ${'$'}1 等替换引用对应；
- 不依赖中文 \b 边界；
- 多行标签使用非贪婪捕获；
- replaceString 不能压成一行；JSON 参数换行使用标准单次 \n 转义，解析后保留真实换行；
- markdownOnly=true、promptOnly=false；
- 不破坏原始对话内容。

局部 HTML 的视觉样式由模型根据题材和用户要求自由设计，不固定颜色、字号、圆角、动效或 class 命名。"""

    private const val FOLDABLE_REASONING_PROMPT = """
你正在为角色卡生成「折叠思维链」正则，只输出一条。

scriptName: 思维链折叠
findRegex: /thinking([\s\S]*?)<\/think>/g
replaceString: 局部 <details> 元素，包含 <summary> 与折叠内容。
允许 inline style 或 class；视觉样式由题材和用户要求决定。
只要求结构可折叠、内容可读、不破坏原始思考文本。

placement: [2], markdownOnly: true, promptOnly: false, disabled: false
不使用中文 \b 边界；replaceString 使用真实换行，JSON 参数中按标准单次 \n 转义。"""

    private const val LIGHTWEIGHT_PROMPT = """
你正在为角色卡生成「轻量着色」正则，最多输出 3 条。

原则：
- 只处理有明确定界符的内容，不猜测自然语言边界
- 允许 inline style 或 class，不要求固定颜色、字体、布局或动效
- 每条正则保持职责单一，避免破坏原始对话内容
- 不用中文 \b 边界，单行内容用 [^结束符\r\n]+ 限制范围
- replaceString 使用真实换行；JSON 参数中按标准单次 \n 转义

适合：只需局部着色或轻量样式区分，无复杂 UI 需求的角色卡。"""

    private const val FULL_FRONTEND_DESCRIPTION_PROTOCOL = """━━━━━━━━ 输出协议 ━━━━━━━━
每次回复必须完整包裹于 <scene_body></scene_body>。
外壳标签之外的任何文字（包括"好的"、"以下是"）都会破坏前端渲染，严禁输出。

标准结构（顺序锁死）：
<scene_body>
<calendar>时间: 14:30 / 天气: 阴，微雨</calendar>
<memory_summary>上一轮核心事件摘要，300字以内</memory_summary>
<user_status>等级: 12 · 位置: 港口区 · 生命值: 88/100 · 职业: 游侠</user_status>
<physiology>体力: 70/100 · 饱食度: 55/100 · 睡眠: 40/100</physiology>
<inventory>短刃: 1把 · 干粮: 2份</inventory>
<quest_log>【主线】找到失踪的信使（进行中）</quest_log>
<factions>自由公会: 60% · 帝国近卫: 20%</factions>
<narrative>
*她把斗篷拉紧，扫视码头上的人群。*
{艾拉}「你说你在找人——说说特征。」
（语气轻描淡写，眼神却极为专注。）
</narrative>
<lore>类型: 地理 / 名称: 自由港 / 解释: 三方势力交汇的无主之地</lore>
<characters>无</characters>
<restricted>无</restricted>
</scene_body>
━━━━━━━━━━━━━━━━━━━━━━"""

    private const val SHARED_DESCRIPTION_PROTOCOL = """━━━━━━━━ 输出风格协议 ━━━━━━━━
回复中用以下标签区分内容类型，前端自动渲染对应样式，
标签本身不会显示给读者：

<think>在这里写内心独白，折叠显示</think>
*动作描写用星号包裹*
对话用「」包裹

示例：
<think>他的眼神有些不对，保持距离。</think>
*她慢慢后退半步，手放到腰间。*
{角色名}「有什么我能帮到你的？」

请将以上格式自然融入写作，不要在标签外解释格式本身。
━━━━━━━━━━━━━━━━━━━━━━"""
}
