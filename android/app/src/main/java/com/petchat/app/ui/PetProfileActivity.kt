package com.petchat.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.petchat.app.R
import com.petchat.app.data.ApiClient
import com.petchat.app.data.SessionStore
import com.petchat.app.model.CreatePetRequest
import com.petchat.app.model.PetProfile
import com.petchat.app.ui.PetChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 我的宠物：创建 + 管理。
 * - 无宠物：填表提交 -> POST /api/pets（服务端文生图）-> 保存并退出。
 * - 已有宠物：可改名字/性格/特质，或一键重新生成外观（PATCH）。
 * 形象预览复用 Coil 的 AsyncImage（兼容 data:image/svg+xml 占位与远程图）。
 */
class PetProfileActivity : ComponentActivity() {

    private lateinit var session: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(this)
        val server = session.serverBase
        val token = session.token
        val existing = session.getPet()
        if (server.isNullOrBlank() || token.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            PetChatTheme {
                PetProfileScreen(
                    serverBase = server,
                    token = token,
                    existingPet = existing,
                    currentPresetId = session.presetId,
                    onSaved = { session.savePet(it) },
                    onPickPreset = { id ->
                        // 记住选择；悬浮窗运行中就即时生效（服务 onStartCommand 会刷新形象与人设）
                        session.presetId = id
                        val pet = session.getPet()
                        if (com.petchat.app.floating.FloatingPetService.isRunning) {
                            com.petchat.app.floating.FloatingPetService.start(
                                this, server, token,
                                pet?.imageUrl, pet?.name ?: "宠物",
                                com.petchat.app.data.PetPresets.resOf(id)
                            )
                        }
                        Toast.makeText(
                            this,
                            if (id == null) "已切换为 AI 生成形象" else "已换上 ${com.petchat.app.data.PetPresets.byId(id)?.displayName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

private val SPECIES = listOf(
    "cat" to "猫", "dog" to "狗", "rabbit" to "兔", "fox" to "狐",
    "bear" to "熊", "bird" to "鸟", "dragon" to "龙"
)
private val TRAITS = listOf(
    "sweet" to "软萌", "tsundere" to "傲娇", "genki" to "元气",
    "calm" to "沉稳", "tsukkomi" to "毒舌"
)
private val STYLES = listOf(
    "chibi" to "Q版", "realistic" to "写实", "pixel" to "像素", "cyber" to "赛博"
)

private fun labelOf(list: List<Pair<String, String>>, value: String): String =
    list.firstOrNull { it.first == value }?.second ?: value

@Composable
fun PetProfileScreen(
    serverBase: String,
    token: String,
    existingPet: PetProfile?,
    currentPresetId: String? = null,
    onSaved: (PetProfile) -> Unit,
    onPickPreset: (String?) -> Unit = {},
    showBack: Boolean = true,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isEdit = existingPet != null

    var name by remember { mutableStateOf(existingPet?.name ?: "") }
    var species by remember { mutableStateOf(existingPet?.species ?: "cat") }
    var personality by remember { mutableStateOf(existingPet?.personality ?: "") }
    var trait by remember { mutableStateOf(existingPet?.trait ?: "sweet") }
    var style by remember { mutableStateOf(existingPet?.style ?: "chibi") }
    var appearance by remember { mutableStateOf(existingPet?.appearancePrompt ?: "") }

    var imageUrl by remember { mutableStateOf(existingPet?.imageUrl ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var presetId by remember { mutableStateOf(currentPresetId) }
    val scheme = MaterialTheme.colorScheme

    fun submitCreate() {
        if (name.isBlank()) { error = "请先给宠物起个名字"; return }
        busy = true; error = null
        scope.launch(Dispatchers.IO) {
            try {
                val prompt = appearance.ifBlank {
                    "a ${style} style ${species}, named ${name}, cute pet avatar"
                }
                val pet = ApiClient(serverBase).createPet(
                    token,
                    CreatePetRequest(
                        name = name.trim(),
                        species = species,
                        personality = personality.trim(),
                        trait = trait,
                        style = style,
                        appearancePrompt = prompt.trim()
                    )
                )
                withContext(Dispatchers.Main) {
                    busy = false
                    imageUrl = pet.imageUrl
                    onSaved(pet)
                    Toast.makeText(context, "宠物已生成 🐾", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { busy = false; error = e.message }
            }
        }
    }

    fun submitEdit() {
        if (existingPet == null) return
        busy = true; error = null
        scope.launch(Dispatchers.IO) {
            try {
                val pet = ApiClient(serverBase).patchPet(
                    token, existingPet.id,
                    name = name.trim().ifBlank { null },
                    personality = personality.trim().ifEmpty { null },
                    trait = trait
                )
                withContext(Dispatchers.Main) {
                    busy = false
                    imageUrl = pet.imageUrl
                    onSaved(pet)
                    Toast.makeText(context, "已保存修改", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { busy = false; error = e.message }
            }
        }
    }

    fun regenerate() {
        if (existingPet == null) return
        busy = true; error = null
        scope.launch(Dispatchers.IO) {
            try {
                val pet = ApiClient(serverBase).patchPet(
                    token, existingPet.id, regenerateImage = true
                )
                withContext(Dispatchers.Main) {
                    busy = false
                    imageUrl = pet.imageUrl
                    onSaved(pet)
                    Toast.makeText(context, "外观已重新生成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { busy = false; error = e.message }
            }
        }
    }

    WaterBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                GlassTextButton(onClick = onBack) { Text("← 返回") }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Text(if (isEdit) "我的宠物" else "创建宠物", fontSize = 20.sp, color = scheme.onBackground)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        // 宠物形象预览：内置预设展示可旋转的 3D 模型，AI 形象沿用 2D 头像
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Pet3DView(
                modelAsset = Pet3DAssets.of(presetId, null),
                fallback = { PetAvatar(imageUrl = imageUrl.ifBlank { null }, size = 128) },
                modifier = Modifier.size(160.dp),
                autoRotate = true
            )
        }
        Spacer(Modifier.height(16.dp))

        // 3D 宠物形象库：内置毛绒 3D 形象，点选即换（悬浮窗运行中即时生效）
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎁 3D 宠物形象库", fontSize = 14.sp, color = scheme.onBackground)
                Text(
                    "毛绒绒 3D 小可爱，每一只性格都不同；点选立即换装",
                    fontSize = 11.sp,
                    color = scheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetTile(
                        label = "AI 形象",
                        resId = 0,
                        selected = presetId == null,
                        desc = com.petchat.app.data.PetPresets.DEFAULT_PERSONA.greeting,
                        onClick = {
                            presetId = null
                            onPickPreset(null)
                        }
                    )
                    com.petchat.app.data.PetPresets.ALL.forEach { preset ->
                        PresetTile(
                            label = preset.displayName,
                            resId = preset.resId,
                            selected = presetId == preset.id,
                            desc = preset.persona.greeting,
                            onClick = {
                                presetId = preset.id
                                onPickPreset(preset.id)
                            }
                        )
                    }
                }
                // 当前选中形象的人设预览
                val selectedPersona = if (presetId == null)
                    com.petchat.app.data.PetPresets.DEFAULT_PERSONA
                else
                    com.petchat.app.data.PetPresets.personaOf(presetId)
                Text(
                    "💬 双击打招呼：「${selectedPersona.greeting}」",
                    fontSize = 11.sp,
                    color = scheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabeledField("宠物名字", name, { name = it }, required = true)
                Spacer(Modifier.height(12.dp))

                if (!isEdit) {
                    LabeledDropdown("物种", labelOf(SPECIES, species), SPECIES) { species = it }
                    Spacer(Modifier.height(12.dp))
                    LabeledDropdown("外观风格", labelOf(STYLES, style), STYLES) { style = it }
                    Spacer(Modifier.height(12.dp))
                    LabeledField("外观描述（可选，留空则自动生成）", appearance, { appearance = it }, singleLine = false)
                    Spacer(Modifier.height(12.dp))
                }

                LabeledDropdown("性格特质", labelOf(TRAITS, trait), TRAITS) { trait = it }
                Spacer(Modifier.height(12.dp))
                LabeledField("性格描述（可选）", personality, { personality = it }, singleLine = false)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (error != null) {
            Text(error ?: "", color = Color(0xFFFF7A7A), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        GlassButton(
            onClick = { if (isEdit) submitEdit() else submitCreate() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text(if (busy) "请稍候…" else if (isEdit) "保存修改" else "生成我的宠物")
        }

        if (isEdit) {
            Spacer(Modifier.height(10.dp))
            GlassOutlinedButton(
                onClick = { regenerate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Text("重新生成外观")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "物种与风格在创建后不可更改；名字、性格特质与描述可随时修改。",
                color = scheme.secondary,
                fontSize = 11.sp
            )
        }
        } // 关闭 Column
    } // 关闭 WaterBackground
}

/** 3D 形象库里的小圆头像块（resId=0 表示"AI 生成形象"占位块） */
@Composable
private fun PresetTile(
    label: String,
    resId: Int,
    selected: Boolean,
    desc: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(92.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.42f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) scheme.primary
                    else scheme.primary.copy(alpha = 0.25f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        ) {
            Image(
                painter = painterResource(if (resId != 0) resId else R.drawable.ic_mascot),
                contentDescription = "$label（$desc）",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) scheme.primary else scheme.secondary,
            maxLines = 1
        )
    }
}

@Composable
fun PetAvatar(imageUrl: String?, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.38f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "宠物形象",
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_mascot),
                contentDescription = "宠物形象",
                modifier = Modifier.size((size * 0.9f).dp)
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
    singleLine: Boolean = true
) {
    Column {
        Text(
            label + if (required) " *" else "",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LabeledDropdown(
    label: String,
    selectedText: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            GlassOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedText)
            }
            GlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    GlassMenuItem(
                        text = text,
                        onClick = {
                            onSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
