package me.k1ngop.valenced.platformindicators

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import app.valenced.xposed.api.DexFinder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

// The MessageView hook and the lookups of its views follow Show Timeout Duration by Palm (GPL-3.0)
private const val MESSAGE_VIEW = "com.discord.chat.presentation.message.MessageView"
private const val MESSAGE = "com.discord.chat.bridge.Message"
private const val CHAT_LIST_VIEW_MANAGER = "com.discord.chat.presentation.list.ChatListViewManager"

/** LRU cache. */
private const val CACHE_CAPACITY = 2000

private const val MARGIN_DP = 4f

/** Shows the author's platform icons right after their name in chat messages, however the header is laid out. */
internal class ChatHooks(dexFinder: DexFinder, private val onError: (Throwable) -> Unit) {
    private val icons = object : LinkedHashMap<String, List<PlatformIcon>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlatformIcon>>) =
            size > CACHE_CAPACITY
    }

    // Main thread only
    private val slots = WeakHashMap<RelativeLayout, IconSlot>()
    private var ids: ViewIds? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val chatListView: Class<*> = dexFinder.classLoader.loadClass(CHAT_LIST_VIEW_MANAGER).declaredMethods
        .single { it.name == "setFloatingChatInputEnabled" && !it.isBridge }
        .parameterTypes[0]

    private val messageIdField = dexFinder.classLoader.loadClass(MESSAGE).getDeclaredField("id")
        .apply { isAccessible = true }

    // Main thread only
    private val reported = mutableSetOf<String>()

    private val unhooks: List<XC_MethodHook.Unhook>

    init {
        val setMessage = dexFinder.findMethod {
            matcher {
                declaredClass(MESSAGE_VIEW)
                usingEqStrings("message", "messageContext", "eventHandler")
            }
        } ?: error("MessageView.setMessage not found")

        unhooks = listOf(XposedBridge.hookMethod(setMessage, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as RelativeLayout
                val id = messageIdField.get(param.args[0]) as String
                val ids = ids ?: ViewIds(view).also { ids = it }
                if (ids.header == 0 || ids.authorName == 0) {
                    report("Message view has no header or author_name id")
                    return
                }

                val header = view.childWithId(ids.header) as? ViewGroup ?: return
                val author = header.findViewById<View>(ids.authorName) ?: return

                // Only messages that start a group show their author
                val showsAuthor = author.visibility == View.VISIBLE
                val platforms = synchronized(icons) { icons[id] }.orEmpty()

                val slot = slots[view]
                    ?: if (showsAuthor && platforms.isNotEmpty()) createSlot(view, author) ?: return else return
                if (slot.bind(id, showsAuthor, platforms)) relayout(view)
            }
        }))
    }

    /** IDs of the message views Discord's binding holds. R8 merged the binding classes, the IDs keep their names. */
    private class ViewIds(view: View) {
        val header = view.idOf("header")
        val authorName = view.idOf("author_name")
    }

    /** Stores icons from JS, and updates the strips already showing their messages. */
    fun put(json: String) {
        val updates = JSONObject(json).let { obj -> obj.keys().asSequence().associateWith { parseIcons(obj.getJSONArray(it)) } }
        synchronized(icons) { icons.putAll(updates) }

        mainHandler.post {
            for ((view, slot) in slots) {
                val id = slot.messageId ?: continue
                val update = updates[id] ?: continue
                if (slot.bind(id, slot.showsAuthor, update)) relayout(view)
            }
        }
    }

    fun clear() {
        synchronized(icons) { icons.clear() }
        mainHandler.post {
            for ((view, slot) in slots) if (slot.bind(null, false, emptyList())) relayout(view)
        }
    }

    fun dispose() {
        for (unhook in unhooks) unhook.unhook()
        synchronized(icons) { icons.clear() }
        mainHandler.post {
            for ((view, slot) in slots) {
                slot.detach()
                relayout(view)
            }
            slots.clear()
        }
    }

    private fun parseIcons(array: JSONArray) = List(array.length()) { index ->
        val icon = array.getJSONArray(index)
        PlatformIcon(icon.getString(0), icon.getInt(1))
    }

    /** Surfaces a problem once, however many messages hit it. */
    private fun report(message: String) {
        if (reported.add(message)) onError(IllegalStateException(message))
    }

    private fun createSlot(view: RelativeLayout, author: View): IconSlot? {
        val parent = author.parent as? ViewGroup ?: return null
        val onMissing = { name: String -> report("No drawable resource named \"$name\"") }

        val slot: IconSlot = when {
            // A plain row can simply take the strip as a sibling of the name
            parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL -> {
                val strip = PlatformStrip(view.context, onMissing)
                val margin = (MARGIN_DP * view.resources.displayMetrics.density).toInt()
                parent.addView(strip, parent.indexOfChild(author) + 1, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = margin
                    gravity = Gravity.CENTER_VERTICAL
                })
                strip
            }

            // Anywhere else (ConstraintLayout...) the icons become part of the name's own text view,
            // so whatever is laid out after the name (the timestamp) moves along with them
            author is TextView -> NameIcons(author, onMissing)

            else -> {
                report("The author name is a ${author.javaClass.name} in ${parent.javaClass.name}, which isn't supported")
                return null
            }
        }

        slots[view] = slot
        return slot
    }

    /**
     * React Native ignores layout requests from native views, Discord does this after its own updates too
     * (`ChatListView.measureAndLayout`). Views with a pending layout request are measured again.
     */
    private fun relayout(view: View) {
        val list = generateSequence(view.parent) { it.parent }.firstOrNull(chatListView::isInstance) as View? ?: return
        list.post { list.measureAndLayout() }
    }
}

@Suppress("DiscouragedApi")
private fun View.idOf(name: String) = resources.getIdentifier(name, "id", context.packageName)

// Not findViewById, as nested messages (such as replies) have views with the same IDs
private fun ViewGroup.childWithId(id: Int): View? =
    (0 until childCount).asSequence().map(::getChildAt).firstOrNull { it.id == id }

private fun View.measureAndLayout() {
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    layout(left, top, right, bottom)
}
