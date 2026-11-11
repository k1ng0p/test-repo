package me.k1ngop.valenced.uservoiceshow

import android.content.Context
import android.graphics.PorterDuff
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

internal const val ICON_SIZE_DP = 16f
internal const val ICON_GAP_DP = 4f

/** An icon as the name of its drawable resource and its ARGB color. */
internal data class VoiceBadge(val resource: String, val color: Int)

/** The voice icon next to the author of a chat message. */
internal class VoiceIcon(
    context: Context,
    private val onMissingResource: (String) -> Unit,
) : ImageView(context) {
    var messageId: String? = null
        private set
    var showsAuthor = false
        private set

    private var shown: VoiceBadge? = null

    private val gap = ICON_GAP_DP * context.resources.displayMetrics.density

    private var host: View? = null
    private var anchor: View? = null
    private var shift = 0f

    init {
        visibility = GONE
        scaleType = ScaleType.FIT_CENTER
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reposition() }
    }

    /** Draws the icon over [host] where [anchor] starts, and moves [anchor] along to make room. */
    fun takePlaceOf(host: View, anchor: View) {
        this.host = host
        this.anchor = anchor
        anchor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reposition() }
    }

    fun detach() {
        releaseAnchor()
        (parent as? ViewGroup)?.removeView(this)
    }

    /** Returns whether the icon changed, which means the row needs to be laid out again. */
    fun bind(messageId: String?, showsAuthor: Boolean, badge: VoiceBadge?): Boolean {
        this.messageId = messageId
        this.showsAuthor = showsAuthor

        val next = badge.takeIf { showsAuthor }
        if (next == shown) return false
        shown = next

        if (next != null) {
            // Bundled JS assets are drawable resources of the app, named by what JS sent
            @Suppress("DiscouragedApi")
            val id = resources.getIdentifier(next.resource, "drawable", context.packageName)
            if (id == 0) onMissingResource(next.resource) else setImageResource(id)
            setColorFilter(next.color, PorterDuff.Mode.SRC_IN)
            visibility = if (id == 0) GONE else VISIBLE
        } else {
            visibility = GONE
        }

        reposition()
        return true
    }

    private fun releaseAnchor() {
        anchor?.let { it.translationX -= shift }
        shift = 0f
    }

    private fun reposition() {
        val host = host ?: return
        val anchor = anchor ?: return
        if (visibility != VISIBLE) {
            releaseAnchor()
            return
        }
        if (width == 0) return

        val row = anchor.parent as? ViewGroup ?: return
        var x = 0
        var y = 0
        var view: View = row
        while (view !== host) {
            x += view.left
            y += view.top
            view = view.parent as? View ?: return
        }

        // Right after whatever is before the anchor, such as the author name and their badges
        var previousRight = 0
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child === anchor || child === this || child.visibility != VISIBLE) continue
            if (child.right <= anchor.left - shift) previousRight = maxOf(previousRight, child.right)
        }

        translationX = x + previousRight + gap
        translationY = y + anchor.top + (anchor.height - height) / 2f

        // Added to the anchor's translation, so other icons that moved it before keep their room
        val next = width + gap
        anchor.translationX += next - shift
        shift = next
    }
}
