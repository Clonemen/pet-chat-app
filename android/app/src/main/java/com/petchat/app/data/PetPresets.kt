package com.petchat.app.data

import com.petchat.app.R
import com.petchat.app.model.ActionType

/**
 * 宠物人设：同一套动作标签（HAPPY/SURPRISED/...）配不同性格的台词，
 * 让每只预设宠物的"戳一戳 / 自言自语 / 打招呼"都差异化。
 */
data class PetPersona(
    /** 双击打招呼台词 */
    val greeting: String,
    /** 单击戳一戳：随机动作 + 文案 */
    val poke: List<Pair<ActionType, String>>,
    /** 闲置自言自语：随机动作 + 文案 */
    val idle: List<Pair<ActionType, String>>
)

/**
 * 3D 预设宠物（Pixar 风毛绒质感，内置 drawable，无需网络生成）。
 * 四只宠物：种类、外观细节、性格神态、台词全面差异化，视觉风格统一。
 */
data class PetPreset(
    val id: String,
    val displayName: String,
    val species: String,
    val desc: String,
    val resId: Int,
    val persona: PetPersona
)

object PetPresets {

    /** 未选预设（使用 AI 生成形象）时的通用台词 */
    val DEFAULT_PERSONA = PetPersona(
        greeting = "嗨！我在~",
        poke = listOf(
            ActionType.HAPPY to "嘿嘿~",
            ActionType.SURPRISED to "哇！吓我一跳",
            ActionType.GREETING to "在呢在呢~",
            ActionType.THINKING to "嗯…让我想想",
            ActionType.HAPPY to "再戳一下试试？",
            ActionType.SAD to "呜…轻一点"
        ),
        idle = listOf(
            ActionType.THINKING to "主人去哪了呀…",
            ActionType.GREETING to "我一直都在哦~",
            ActionType.HAPPY to "今天也要开心呀！",
            ActionType.THINKING to "（数星星中…）",
            ActionType.SAD to "有点想你了…",
            ActionType.GREETING to "要不要聊聊天？"
        )
    )

    // ── 四款 3D 毛绒宠物 ──────────────────────────────────────────────

    /** 柯基犬「布丁」：元气黏人，永远在摇尾巴 */
    private val corgi = PetPreset(
        id = "corgi",
        displayName = "柯基 · 布丁",
        species = "dog",
        desc = "元气小短腿，尾巴停不下来",
        resId = R.drawable.pet_preset_corgi,
        persona = PetPersona(
            greeting = "汪汪！布丁随时待命！",
            poke = listOf(
                ActionType.HAPPY to "汪汪！再摸摸！",
                ActionType.HAPPY to "尾巴要摇飞出去啦！",
                ActionType.GREETING to "叫我？我第一时间冲过来！",
                ActionType.SURPRISED to "嗷呜！耳朵都竖起来了！",
                ActionType.HAPPY to "陪我玩球好不好嘛！",
                ActionType.HAPPY to "嘿嘿，最喜欢你了！"
            ),
            idle = listOf(
                ActionType.GREETING to "要出门遛弯吗？嗯？",
                ActionType.HAPPY to "今天也是超级开心的一天！",
                ActionType.THINKING to "（嗅嗅）好像有零食的味道…",
                ActionType.GREETING to "我就趴在这儿等你回家！",
                ActionType.HAPPY to "汪！小短腿也有大能量！"
            )
        )
    )

    /** 橘猫「年糕」：傲娇慵懒，口嫌体正直 */
    private val cat = PetPreset(
        id = "cat",
        displayName = "橘猫 · 年糕",
        species = "cat",
        desc = "傲娇大脸橘，口嫌体正直",
        resId = R.drawable.pet_preset_cat,
        persona = PetPersona(
            greeting = "喵。算你有空理我。",
            poke = listOf(
                ActionType.SURPRISED to "喵？！别碰我肚子！",
                ActionType.THINKING to "哼，本喵心情好才让你摸",
                ActionType.HAPPY to "咕噜咕噜…勉强及格的撸法",
                ActionType.SAD to "本喵要冷暴力你三分钟",
                ActionType.HAPPY to "这波摸得…还凑合吧",
                ActionType.SURPRISED to "爪子收好了，但没完全收"
            ),
            idle = listOf(
                ActionType.THINKING to "猫粮什么时候能升级成冻干…",
                ActionType.SAD to "无聊…假装自己是尊雕像",
                ActionType.THINKING to "今天才睡了18小时，有点累",
                ActionType.GREETING to "不是想你，只是刚好路过",
                ActionType.THINKING to "（用尾巴尖有节奏地拍打地面）"
            )
        )
    )

    /** 垂耳兔「棉花」：软萌胆小，耳朵会害羞耷拉 */
    private val bunny = PetPreset(
        id = "bunny",
        displayName = "垂耳兔 · 棉花",
        species = "rabbit",
        desc = "软萌小胆子，耳朵会害羞",
        resId = R.drawable.pet_preset_bunny,
        persona = PetPersona(
            greeting = "你、你来啦…好开心！",
            poke = listOf(
                ActionType.SURPRISED to "呀！吓、吓我一跳！",
                ActionType.SAD to "耳朵都耷拉下来了…",
                ActionType.HAPPY to "嘿嘿…棉花最喜欢你了",
                ActionType.SURPRISED to "还、还没做好心理准备！",
                ActionType.HAPPY to "摸我是不是像摸云朵一样？",
                ActionType.THINKING to "（小声）其实…还想再摸一会儿"
            ),
            idle = listOf(
                ActionType.THINKING to "（安静地啃草）",
                ActionType.SAD to "小胡萝卜快吃完了…",
                ActionType.GREETING to "想听棉花讲兔兔尾巴的故事吗？",
                ActionType.THINKING to "躲起来，就没有人找得到我啦…",
                ActionType.SAD to "今天的云也软软的，像棉花…"
            )
        )
    )

    /** 小熊猫「栗子」：好奇吃货，什么都想尝一口 */
    private val panda = PetPreset(
        id = "panda",
        displayName = "小熊猫 · 栗子",
        species = "red_panda",
        desc = "好奇小吃货，万物皆可尝",
        resId = R.drawable.pet_preset_panda,
        persona = PetPersona(
            greeting = "哦嗷！一起去探索世界吧！",
            poke = listOf(
                ActionType.HAPPY to "橡果！我闻到橡果的味道了！",
                ActionType.SURPRISED to "哦哦？！有新东西看？！",
                ActionType.THINKING to "我在想…晚饭吃什么",
                ActionType.HAPPY to "抱住！在你身上蹭蹭！",
                ActionType.THINKING to "这个…能吃吗？",
                ActionType.HAPPY to "尾巴蓬松度 +1，开心度 +100！"
            ),
            idle = listOf(
                ActionType.THINKING to "（盯着藏零食的小树洞发呆）",
                ActionType.HAPPY to "今天又悄悄变蓬松了一点！",
                ActionType.THINKING to "橡果烤着吃会不会更香…",
                ActionType.GREETING to "走走走，带你去看好玩的！",
                ActionType.THINKING to "（抱着尾巴当围巾取暖）"
            )
        )
    )

    val ALL = listOf(corgi, cat, bunny, panda)

    fun byId(id: String?): PetPreset? = ALL.firstOrNull { it.id == id }

    fun resOf(id: String?): Int? = byId(id)?.resId

    fun personaOf(id: String?): PetPersona = byId(id)?.persona ?: DEFAULT_PERSONA

    /** 由 drawable 资源 id 反查预设（取物种用于动画引擎眼皮标定等） */
    fun speciesOfRes(resId: Int?): String? =
        ALL.firstOrNull { it.resId == resId }?.species

    /** 按物种匹配本地 3D 预设形象（好友宠物未用预设但物种相同时可借用） */
    fun resBySpecies(species: String?): Int? = when (species) {
        "dog" -> corgi.resId
        "cat" -> cat.resId
        "rabbit" -> bunny.resId
        "red_panda" -> panda.resId
        else -> null
    }
}
