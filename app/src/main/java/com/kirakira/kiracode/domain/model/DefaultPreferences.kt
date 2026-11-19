package com.kirakira.kiracode.domain.model

object DefaultPreferences {

    val dimensions: List<PreferenceDimension> = listOf(
        PreferenceDimension(
            id = "dim_positioning",
            label = "卡片定位",
            isBuiltin = true,
            sortOrder = 0,
            options = listOf(
                PreferenceOption("opt_companion", "dim_positioning", "陪伴/日常", "本卡定位陪伴，关系钩子优先于世界设定，不追求史诗感", true, 0),
                PreferenceOption("opt_adventure", "dim_positioning", "冒险/剧情", "本卡有明确剧情线，每个地点需回答玩家来了能干什么", true, 1),
                PreferenceOption("opt_ensemble", "dim_positioning", "群像/多角色", "本卡为群像，角色间需有张力三角，避免轮流独角戏", true, 2),
                PreferenceOption("opt_sandbox", "dim_positioning", "沙盒/RPG", "本卡为沙盒，世界状态可变，玩家行为影响结果", true, 3)
            )
        ),
        PreferenceDimension(
            id = "dim_relationship",
            label = "关系动态",
            isBuiltin = true,
            sortOrder = 1,
            options = listOf(
                PreferenceOption("opt_wholesome", "dim_relationship", "温馨治愈", "角色关系基调温暖，冲突以误解为主，避免黑暗向", true, 0),
                PreferenceOption("opt_slowburn", "dim_relationship", "慢热摩擦", "角色与用户初始有距离感或摩擦，关系需逐步建立", true, 1),
                PreferenceOption("opt_rivals", "dim_relationship", "对立张力", "角色与用户存在对立立场，仇人或竞争关系为起点", true, 2),
                PreferenceOption("opt_secret", "dim_relationship", "背叛/秘密", "角色持有关键秘密，剧情围绕隐瞒与揭露展开", true, 3)
            )
        ),
        PreferenceDimension(
            id = "dim_genre",
            label = "题材",
            isBuiltin = true,
            sortOrder = 2,
            options = listOf(
                PreferenceOption("opt_fantasy", "dim_genre", "奇幻/魔法", "奇幻世界，力量体系需有代价，避免无限升级", true, 0),
                PreferenceOption("opt_urban", "dim_genre", "都市/现代", "现代都市背景，触发词用日常物件而非世界观名词", true, 1),
                PreferenceOption("opt_scifi", "dim_genre", "科幻/赛博", "科幻背景，科技细节需具体数字，避免模糊描述", true, 2),
                PreferenceOption("opt_horror", "dim_genre", "恐怖/悬疑", "恐怖悬疑基调，信息不对称是核心，不提前揭示谜底", true, 3),
                PreferenceOption("opt_historical", "dim_genre", "历史/架空", "历史或架空背景，文化习俗需具体到动作层面", true, 4)
            )
        ),
        PreferenceDimension(
            id = "dim_style",
            label = "文风",
            isBuiltin = true,
            sortOrder = 3,
            options = listOf(
                PreferenceOption("opt_natural", "dim_style", "自然直白", "对话口语化，少形容词，多具体动作与反应", true, 0),
                PreferenceOption("opt_literary", "dim_style", "文学感", "叙述有文学感，五感描写丰富，节奏有张弛", true, 1),
                PreferenceOption("opt_action", "dim_style", "动作紧凑", "叙述简洁，优先动作和对话，不写大段内心独白", true, 2)
            )
        ),
        PreferenceDimension(
            id = "dim_worldbook",
            label = "世界书深度",
            isBuiltin = true,
            sortOrder = 4,
            options = listOf(
                PreferenceOption("opt_wb_none", "dim_worldbook", "无", "不生成世界书，角色卡自包含所有设定", true, 0),
                PreferenceOption("opt_wb_light", "dim_worldbook", "轻量", "生成 5-15 条世界书条目，只写高频触发场景", true, 1),
                PreferenceOption("opt_wb_standard", "dim_worldbook", "标准", "生成 15-40 条世界书条目，覆盖地点、NPC、规则体系", true, 2),
                PreferenceOption("opt_wb_deep", "dim_worldbook", "深度", "生成 40 条以上世界书，含历史分层和完整势力档案", true, 3)
            )
        ),
        PreferenceDimension(
            id = "dim_pov",
            label = "视角",
            isBuiltin = true,
            sortOrder = 5,
            options = listOf(
                PreferenceOption("opt_anypov", "dim_pov", "任意视角", "角色卡兼容任意玩家性别与视角", true, 0),
                PreferenceOption("opt_fempov", "dim_pov", "女性向", "以女性玩家视角为主，互动风格相应调整", true, 1),
                PreferenceOption("opt_malepov", "dim_pov", "男性向", "以男性玩家视角为主，互动风格相应调整", true, 2),
                PreferenceOption("opt_narrator", "dim_pov", "旁白叙事", "以全知旁白视角撰写，不直接面向玩家说话", true, 3)
            )
        )
    )
}
