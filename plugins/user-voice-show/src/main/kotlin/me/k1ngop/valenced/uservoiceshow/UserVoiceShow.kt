@file:JvmName("UserVoiceShow")

package me.k1ngop.valenced.uservoiceshow

import app.valenced.bridge.asDelegate
import app.valenced.plugins.plugin
import app.valenced.xposed.api.registerNativeMethod

/** Shows a voice icon next to the author of chat messages whose author is in a voice channel. */
@Suppress("UNUSED")
val userVoiceShow = plugin {
    var hooks: VoiceHooks? = null

    start {
        registerNativeMethod("k1ngop.voice.put") { args ->
            val json by args.asDelegate().string()
            hooks?.put(json)
            null
        }

        registerNativeMethod("k1ngop.voice.clear") {
            hooks?.clear()
            null
        }

        // After the methods, so a failing lookup is reported instead of leaving them unregistered
        try {
            hooks = VoiceHooks(dexFinder) { errors.tryEmit(it) }
        } catch (e: Exception) {
            errors.tryEmit(e)
        }
    }

    stop {
        hooks?.dispose()
        hooks = null
    }
}
