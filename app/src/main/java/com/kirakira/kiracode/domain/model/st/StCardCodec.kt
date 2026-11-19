package com.kirakira.kiracode.domain.model.st

import com.kirakira.kiracode.domain.model.CharacterCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * SillyTavern 角色卡编解码器。
 *
 * 职责：
 * 1. 将任意版本（v1 平铺 / v2 `chara_card_v2` / v3 `chara_card_v3`）的角色卡 JSON 解析为领域模型；
 * 2. 将领域模型无损序列化回对应规格的线格式 JSON —— 所有未知字段通过 [CharacterCard.rawData]
 *    与各层级 `raw` 对象原样保留，导入导出往返不丢数据；
 * 3. 世界书（character_book）与正则脚本（extensions.regex_scripts）的双向转换。
 */
object StCardCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowSpecialFloatingPointValues = true
        prettyPrint = false
    }

    // ==================== 解码 ====================

    /**
     * 从 JSON 文本解析角色卡。
     * 自动识别 v1/v2/v3 结构；解析失败返回 [Result.failure]。
     */
    fun decode(text: String): Result<CharacterCard> = runCatching {
        val element = json.parseToJsonElement(text)
        decodeElement(element.jsonObject)
    }

    /**
     * 从已解析的 JSON 对象构建角色卡（含原始数据保全）。
     */
    fun decodeElement(root: JsonObject): CharacterCard {
        // 识别包裹结构：v2/v3 为 {spec, spec_version, data:{...}}
        val wrappedSpec = root["spec"]?.jsonPrimitive?.contentOrNull
        val dataObj: JsonObject = if (wrappedSpec != null && root["data"] is JsonObject) {
            root["data"]!!.jsonObject
        } else {
            root
        }
        val spec = StSpec.fromWire(wrappedSpec)

        val extensions = dataObj["extensions"] as? JsonObject ?: JsonObject(emptyMap())
        val book = decodeBook(dataObj["character_book"])
        val regexScripts = decodeRegexScripts(extensions["regex_scripts"])

        return CharacterCard(
            name = dataObj.str("name"),
            description = dataObj.str("description"),
            personality = dataObj.str("personality"),
            firstMessage = dataObj.str("first_mes"),
            exampleDialogue = dataObj.str("mes_example"),
            scenario = dataObj.str("scenario"),
            creatorNotes = dataObj.str("creator_notes"),
            systemPrompt = dataObj.str("system_prompt"),
            postHistoryInstructions = dataObj.str("post_history_instructions"),
            creator = dataObj.str("creator").ifBlank { "" },
            version = dataObj.str("character_version").ifBlank { "main" },
            tags = dataObj.strList("tags") ?: emptyList(),
            alternateGreetings = dataObj.strList("alternate_greetings") ?: emptyList(),
            avatar = (dataObj["avatar"] as? JsonPrimitive)?.contentOrNull,
            spec = spec.wireName,
            specVersion = spec.specVersion,
            nickname = (dataObj["nickname"] as? JsonPrimitive)?.contentOrNull,
            characterBook = book,
            regexScripts = regexScripts,
            extensions = extensions.takeIf { it.isNotEmpty() },
            rawData = dataObj
        )
    }

    // ==================== 编码 ====================

    private val prettyJson: Json by lazy {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
            allowSpecialFloatingPointValues = true
        }
    }

    /**
     * 编码为完整线格式 JSON 文本。
     * @param pretty 是否美化输出
     */
    fun toJsonString(card: CharacterCard, pretty: Boolean = true): String {
        val format = if (pretty) prettyJson else json
        return format.encodeToString(JsonElement.serializer(), encode(card))
    }

    /**
     * 编码为线格式 JSON 元素：
     * - v1：平铺对象
     * - v2/v3：{spec, spec_version, data}
     *
     * 合并策略：以 [CharacterCard.rawData] 为底，覆盖全部规范字段，
     * 未知字段原样保留；世界书条目与正则脚本逐级合并各自的 raw。
     */
    fun encode(card: CharacterCard): JsonObject {
        val data = buildDataObject(card)
        return when (StSpec.fromWire(card.spec)) {
            StSpec.V1 -> data
            else -> JsonObject(
                mapOf(
                    "spec" to JsonPrimitive(card.spec),
                    "spec_version" to JsonPrimitive(card.specVersion),
                    "data" to data
                )
            )
        }
    }

    /** 构建 data 对象：rawData 底座 + 规范字段覆盖 */
    private fun buildDataObject(card: CharacterCard): JsonObject {
        val out = (card.rawData ?: JsonObject(emptyMap())).toMutableMap()

        out["name"] = JsonPrimitive(card.name)
        out["description"] = JsonPrimitive(card.description)
        out["personality"] = JsonPrimitive(card.personality)
        out["scenario"] = JsonPrimitive(card.scenario)
        out["first_mes"] = JsonPrimitive(card.firstMessage)
        out["mes_example"] = JsonPrimitive(card.exampleDialogue)
        out["creator_notes"] = JsonPrimitive(card.creatorNotes)
        out["system_prompt"] = JsonPrimitive(card.systemPrompt)
        out["post_history_instructions"] = JsonPrimitive(card.postHistoryInstructions)
        out["creator"] = JsonPrimitive(card.creator)
        out["character_version"] = JsonPrimitive(card.version)
        out["tags"] = JsonArray(card.tags.map { JsonPrimitive(it) })
        out["alternate_greetings"] = JsonArray(card.alternateGreetings.map { JsonPrimitive(it) })
        card.avatar?.let { out["avatar"] = JsonPrimitive(it) } ?: run { out.remove("avatar") }
        if (card.nickname != null) out["nickname"] = JsonPrimitive(card.nickname)

        // character_book
        if (card.characterBook != null) {
            out["character_book"] = encodeBook(card.characterBook)
        } else {
            out.remove("character_book")
        }

        // extensions：以 rawData 为底，合并托管字段 regex_scripts 与领域模型上的
        // 扩展键（如 kira_metadata 水印），card.extensions 同名键覆盖 rawData，确保水印不丢
        val rawExt = (card.rawData?.get("extensions") as? JsonObject) ?: JsonObject(emptyMap())
        val baseExt = rawExt.toMutableMap()
        (card.extensions as? JsonObject)?.forEach { (k, v) -> baseExt[k] = v }
        baseExt["regex_scripts"] = encodeRegexScripts(card.regexScripts)
        out["extensions"] = JsonObject(baseExt)

        return JsonObject(out)
    }

    // ==================== 世界书 ====================

    private val NUMERIC_POSITION_TO_STRING = mapOf(
        0L to "before_char",
        1L to "after_char"
    )

    fun decodeBook(element: JsonElement?): StCharacterBook? {
        val obj = element as? JsonObject ?: return null
        val entriesRaw = obj["entries"] as? JsonArray ?: JsonArray(emptyList())
        var index = 0L
        val entries = entriesRaw.mapNotNull { e ->
            val eo = e as? JsonObject ?: return@mapNotNull null
            decodeEntry(eo, index++)
        }
        return StCharacterBook(
            name = obj.str("name"),
            description = obj.str("description"),
            scanDepth = (obj["scan_depth"] as? JsonPrimitive)?.intOrNull,
            tokenBudget = (obj["token_budget"] as? JsonPrimitive)?.intOrNull,
            recursiveScanning = (obj["recursive_scanning"] as? JsonPrimitive)?.booleanOrNull,
            entries = entries,
            raw = obj
        )
    }

    private fun decodeEntry(eo: JsonObject, fallbackIndex: Long): StBookEntry {
        val idPrim = eo["uid"] as? JsonPrimitive ?: eo["id"] as? JsonPrimitive
        val secondary = when {
            eo["secondary_keys"] is JsonArray -> eo.strList("secondary_keys").orEmpty()
            eo["keysecondary"] is JsonArray -> eo.strList("keysecondary").orEmpty()
            else -> emptyList()
        }
        // 位置兼容四种形态：v2 规范字符串 / ST 数字 / 旧格式布尔 / extensions.position 数字
        val posValue = (eo["position"] as? JsonPrimitive)
        val extPos = ((eo["extensions"] as? JsonObject)?.get("position") as? JsonPrimitive)
        val resolvedPosition: String? = when {
            posValue == null -> null
            posValue.isString -> posValue.contentOrNull
            posValue.booleanOrNull != null -> if (posValue.booleanOrNull == true) "before_char" else "after_char"
            else -> null
        } ?: NUMERIC_POSITION_TO_STRING[posValue?.longOrNull]
            ?: NUMERIC_POSITION_TO_STRING[extPos?.longOrNull]
        val extensions = eo["extensions"] as? JsonObject ?: JsonObject(emptyMap())

        return StBookEntry(
            id = idPrim?.longOrNull ?: fallbackIndex,
            keys = eo.strList("keys") ?: eo.strList("key") ?: emptyList(),
            secondaryKeys = secondary,
            comment = eo.str("comment"),
            content = eo.str("content"),
            constant = eo.bool("constant", false),
            selective = eo.bool("selective", true),
            insertionOrder = eo.int("insertion_order", 100),
            enabled = eo.bool("enabled", eo.bool("disable", false).not()),
            position = resolvedPosition,
            priority = (eo["priority"] as? JsonPrimitive)?.intOrNull
                ?: ((eo["extensions"] as? JsonObject)?.get("priority") as? JsonPrimitive)?.intOrNull,
            depth = (eo["depth"] as? JsonPrimitive)?.intOrNull
                ?: ((eo["extensions"] as? JsonObject)?.get("depth") as? JsonPrimitive)?.intOrNull,
            // 修复：caseSensitive 此前从不赋值（永远是 null）——条目级或 extensions 双读
            caseSensitive = eo.optBool("case_sensitive") ?: extensions.optBool("case_sensitive"),
            scanDepth = eo.optInt("scan_depth") ?: extensions.optInt("scan_depth"),
            matchWholeWords = eo.optBool("match_whole_words") ?: extensions.optBool("match_whole_words"),
            automationId = eo.optStr("automation_id") ?: extensions.optStr("automation_id"),
            probability = eo.optInt("probability") ?: extensions.optInt("probability"),
            useProbability = extensions.optBool("useProbability") ?: extensions.optBool("use_probability"),
            selectiveLogic = eo.optInt("selectiveLogic") ?: extensions.optInt("selectiveLogic"),
            excludeRecursion = extensions.optBool("exclude_recursion"),
            preventRecursion = extensions.optBool("prevent_recursion"),
            group = extensions.optStr("group"),
            groupOverride = extensions.optBool("group_override"),
            groupWeight = extensions.optInt("group_weight"),
            useGroupScoring = extensions.optBool("use_group_scoring"),
            extensions = extensions,
            raw = eo
        )
    }

    fun encodeBook(book: StCharacterBook): JsonObject {
        val out = (book.raw ?: JsonObject(emptyMap())).toMutableMap()
        out["name"] = JsonPrimitive(book.name)
        out["description"] = JsonPrimitive(book.description)
        book.scanDepth?.let { out["scan_depth"] = JsonPrimitive(it) } ?: out.remove("scan_depth")
        book.tokenBudget?.let { out["token_budget"] = JsonPrimitive(it) } ?: out.remove("token_budget")
        book.recursiveScanning?.let { out["recursive_scanning"] = JsonPrimitive(it) }
            ?: out.remove("recursive_scanning")
        out["entries"] = JsonArray(book.entries.mapIndexed { i, e -> encodeEntry(e, i) })
        return JsonObject(out)
    }

    private fun encodeEntry(entry: StBookEntry, index: Int): JsonObject {
        val out = (entry.raw ?: JsonObject(emptyMap())).toMutableMap()
        out["uid"] = JsonPrimitive(if (entry.id > 0 || entry.raw?.get("uid") != null) entry.id else index.toLong())
        out["id"] = JsonPrimitive(if (entry.id > 0 || entry.raw?.get("id") != null) entry.id else index.toLong())
        out["keys"] = JsonArray(entry.keys.map { JsonPrimitive(it) })
        out["secondary_keys"] = JsonArray(entry.secondaryKeys.map { JsonPrimitive(it) })
        out["keysecondary"] = JsonArray(entry.secondaryKeys.map { JsonPrimitive(it) }) // ST 字段兼容
        out["comment"] = JsonPrimitive(entry.comment)
        out["content"] = JsonPrimitive(entry.content)
        out["constant"] = JsonPrimitive(entry.constant)
        out["selective"] = JsonPrimitive(entry.selective)
        out["insertion_order"] = JsonPrimitive(entry.insertionOrder)
        out["enabled"] = JsonPrimitive(entry.enabled)
        out["disable"] = JsonPrimitive(!entry.enabled) // ST 字段兼容
        when (entry.position) {
            null -> {} // 保留 raw 中原有 position 值不动
            else -> out["position"] = JsonPrimitive(entry.position)
        }
        // v2 规范字符串位置 + ST 实际读取的 extensions.position 数字，双写保证兼容
        val extOut = entry.extensions.toMutableMap()
        when (entry.position) {
            "before_char" -> extOut["position"] = JsonPrimitive(0)
            "after_char" -> extOut["position"] = JsonPrimitive(1)
            else -> {}
        }
        entry.priority?.let { extOut["priority"] = JsonPrimitive(it) }
        entry.depth?.let { extOut["depth"] = JsonPrimitive(it) }
        // V3 extensions 常用字段回写（键名与 ST 读取约定一致：useProbability/selectiveLogic 为驼峰）
        entry.scanDepth?.let { extOut["scan_depth"] = JsonPrimitive(it) }
        entry.matchWholeWords?.let { extOut["match_whole_words"] = JsonPrimitive(it) }
        entry.automationId?.let { extOut["automation_id"] = JsonPrimitive(it) }
        entry.probability?.let { extOut["probability"] = JsonPrimitive(it) }
        entry.useProbability?.let { extOut["useProbability"] = JsonPrimitive(it) }
        entry.selectiveLogic?.let { extOut["selectiveLogic"] = JsonPrimitive(it) }
        entry.excludeRecursion?.let { extOut["exclude_recursion"] = JsonPrimitive(it) }
        entry.preventRecursion?.let { extOut["prevent_recursion"] = JsonPrimitive(it) }
        entry.group?.let { extOut["group"] = JsonPrimitive(it) }
        entry.groupOverride?.let { extOut["group_override"] = JsonPrimitive(it) }
        entry.groupWeight?.let { extOut["group_weight"] = JsonPrimitive(it) }
        entry.useGroupScoring?.let { extOut["use_group_scoring"] = JsonPrimitive(it) }
        if (extOut.isNotEmpty()) out["extensions"] = JsonObject(extOut) else out.remove("extensions")
        return JsonObject(out)
    }

    // ==================== 正则脚本 ====================

    fun decodeRegexScripts(element: JsonElement?): List<StRegexScript> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            StRegexScript(
                id = o.str("id").ifBlank { UUID.randomUUID().toString() },
                scriptName = o.str("scriptName"),
                findRegex = o.str("findRegex"),
                replaceString = o.str("replaceString"),
                trimStrings = o.strList("trimStrings") ?: emptyList(),
                placement = o.intList("placement") ?: listOf(RegexPlacement.AI_OUTPUT),
                disabled = o.bool("disabled", false),
                markdownOnly = o.bool("markdownOnly", false),
                promptOnly = o.bool("promptOnly", false),
                runOnEdit = o.bool("runOnEdit", true),
                substituteRegex = o.int("substituteRegex", 0),
                minDepth = (o["minDepth"] as? JsonPrimitive)?.intOrNull,
                maxDepth = (o["maxDepth"] as? JsonPrimitive)?.intOrNull,
                raw = o
            )
        }
    }

    fun encodeRegexScripts(scripts: List<StRegexScript>): JsonArray =
        JsonArray(scripts.map { s ->
            val out = (s.raw ?: JsonObject(emptyMap())).toMutableMap()
            out["id"] = JsonPrimitive(s.id)
            out["scriptName"] = JsonPrimitive(s.scriptName)
            out["findRegex"] = JsonPrimitive(s.findRegex)
            out["replaceString"] = JsonPrimitive(s.replaceString)
            out["trimStrings"] = JsonArray(s.trimStrings.map { JsonPrimitive(it) })
            out["placement"] = JsonArray(s.placement.map { JsonPrimitive(it) })
            out["disabled"] = JsonPrimitive(s.disabled)
            out["markdownOnly"] = JsonPrimitive(s.markdownOnly)
            out["promptOnly"] = JsonPrimitive(s.promptOnly)
            out["runOnEdit"] = JsonPrimitive(s.runOnEdit)
            out["substituteRegex"] = JsonPrimitive(s.substituteRegex)
            s.minDepth?.let { out["minDepth"] = JsonPrimitive(it) } ?: out.remove("minDepth")
            s.maxDepth?.let { out["maxDepth"] = JsonPrimitive(it) } ?: out.remove("maxDepth")
            JsonObject(out)
        })

    // ==================== Json 取值辅助 ====================

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull ?: ""

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    private fun JsonObject.int(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: default

    private fun JsonObject.strList(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject.intList(key: String): List<Int>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }

    private fun JsonObject.optBool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.optInt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.optStr(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
