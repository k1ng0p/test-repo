package me.k1ngop.valenced.platformindicators

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

private const val ICON_SIZE_DP = 16f
private const val ICON_GAP_DP = 4f

/** A platform's icon, as the name of its drawable resource and its ARGB color. */
internal data class PlatformIcon(val resource: String, val color: Int)

/** The icons shown next to the author of a chat message. */
internal class PlatformStrip(
    context: Context,
    private val onMissingResource: (String) -> Unit,
) : LinearLayout(context), IconSlot {
    override var messageId: String? = null
        private set
    override var showsAuthor = false
        private set

    private var shown = emptyList<PlatformIcon>()

    private val density = context.resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }

    /** Returns whether the icons changed, which means the row needs to be laid out again. */
    override fun bind(messageId: String?, showsAuthor: Boolean, icons: List<PlatformIcon>): Boolean {
        this.messageId = messageId
        this.showsAuthor = showsAuthor

        val next = if (showsAuthor) icons else emptyList()
        if (next == shown) return false
        shown = next

        removeAllViews()
        for (icon in next) {
            val view = newIcon(icon) ?: continue
            val first = childCount == 0

            addView(view, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (ICON_SIZE_DP * density).toInt()).apply {
                if (!first) marginStart = (ICON_GAP_DP * density).toInt()
            })
        }

        visibility = if (childCount > 0) VISIBLE else GONE
        return true
    }

    override fun detach() {
        (parent as? ViewGroup)?.removeView(this)
    }

    // Bundled JS assets are drawable resources of the app, named by what JS sent
    @Suppress("DiscouragedApi")
    private fun newIcon(icon: PlatformIcon): ImageView? {
        val id = resources.getIdentifier(icon.resource, "drawable", context.packageName)
        if (id == 0) {
            onMissingResource(icon.resource)
            return null
        }

        return ImageView(context).apply {
            setImageResource(id)
            setColorFilter(icon.color, PorterDuff.Mode.SRC_IN)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
}
