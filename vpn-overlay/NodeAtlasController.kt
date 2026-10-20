package io.nekohasekai.sfa.nodeatlas

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Owns the one NodeAtlas profile and starts SFA's VPN service without opening its UI. */
object NodeAtlasController {
    interface Callback {
        fun onResult(status: String, message: String?)
    }

    private const val PREFS = "nodeatlas-vpn"
    private const val PROFILE_ID = "profile_id"
    private const val PROFILE_NAME = "NodeAtlas 当前节点"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    fun start(context: Context, title: String?, json: String?, callback: Callback) {
        val safeJson = json ?: ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Settings.dataStore.initialize()
                    if (safeJson.isBlank()) error("节点配置为空")
                    Libbox.checkConfig(safeJson)

                    val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
                    val configFile = File(configDirectory, "nodeatlas.json")
                    configFile.writeText(safeJson)

                    val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val savedId = preferences.getLong(PROFILE_ID, -1L)
                    val existing = if (savedId > 0) ProfileManager.get(savedId) else null
                    if (existing == null) {
                        val typed = TypedProfile().apply {
                            type = TypedProfile.Type.Local
                            path = configFile.path
                        }
                        val profile = Profile(
                            name = PROFILE_NAME,
                            typed = typed,
                            userOrder = ProfileManager.nextOrder(),
                        )
                        val created = ProfileManager.create(profile, andSelect = true)
                        preferences.edit().putLong(PROFILE_ID, created.id).apply()
                    } else {
                        existing.name = PROFILE_NAME
                        existing.typed.type = TypedProfile.Type.Local
                        existing.typed.path = configFile.path
                        ProfileManager.update(existing)
                        Settings.selectedProfile = existing.id
                    }

                    Settings.serviceMode = ServiceMode.VPN
                    Settings.startedByUser = true
                    Settings.dataStore.flush()
                }

                // Stop an older profile first. The delay avoids racing its command server.
                BoxService.stop()
                delay(350)
                callback.onResult("starting", "配置已在当前页面应用，VPN 正在启动")
                BoxService.start()
            } catch (exception: Exception) {
                callback.onResult("error", exception.message ?: exception.javaClass.simpleName)
            }
        }
    }

    @JvmStatic
    fun stop(context: Context) {
        BoxService.stop()
    }
}
