package me.k1ngop.valenced.platformindicators

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.widget.TextView
import java.lang.ref.WeakReference

private const val ICON_SIZE_DP = 16f
private const val ICON_GAP_DP = 4f

/** Where a message's platform icons are shown, whichever way its header is laid out. */
internal interface IconSlot {
    val messageId: String?
    val showsAuthor: Boolean

    /** Returns whether anything changed, which means the row needs to be laid out again. */
    fun bind(messageId: String?, showsAuthor: Boolean, icons: List<PlatformIcon>): Boolean

    fun detach()
}

/**
 * Shows the icons as a compound drawable inside a text view that sits in the message header, so the
 * view itself gets wider and whatever is laid out next to it moves along, in any parent layout.
 * [atStart] puts them before the text (used on the timestamp, which follows the name and its tags),
 * otherwise they go after it.
 */
internal class NameIcons(
    target: TextView,
    private val atStart: Boolean,
    private val onMissingResource: (String) -> Unit,
) : IconSlot {
    private val target = WeakReference(target)
    private val density = target.resources.displayMetrics.density
    private val index = if (atStart) 0 else 2

    override var messageId: String? = null
        private set
    override var showsAuthor = false
        private set

    private var wanted = emptyList<PlatformIcon>()

    override fun bind(messageId: String?, showsAuthor: Boolean, icons: List<PlatformIcon>): Boolean {
        this.messageId = messageId
        this.showsAuthor = showsAuthor
        wanted = if (showsAuthor) icons else emptyList()
        return sync()
    }

    override fun detach() {
        wanted = emptyList()
        sync()
    }

    // Compared with what the view really holds, as Discord can reset its drawables when it rebinds
    private fun sync(): Boolean {
        val view = target.get() ?: return false
        val source = view.compoundDrawablesRelative
        val slots = arrayOfNulls<Drawable>(4)
        for (i in 0..3) slots[i] = source[i]

        val current = slots[index]
        val ours = current as? IconsDrawable
        val base = ours?.original ?: current

        if (wanted.isEmpty()) {
            if (ours == null) return false
            slots[index] = base
            view.setCompoundDrawablesRelative(slots[0], slots[1], slots[2], slots[3])
            return true
        }
        if (ours != null && ours.icons == wanted) return false

        val size = (ICON_SIZE_DP * density).toInt()
        val glyphs = wanted.mapNotNull { icon ->
            @Suppress("DiscouragedApi")
            val id = view.resources.getIdentifier(icon.resource, "drawable", view.context.packageName)
            if (id == 0) {
                onMissingResource(icon.resource)
                return@mapNotNull null
            }
            view.context.getDrawable(id)?.mutate()?.apply {
                setColorFilter(icon.color, PorterDuff.Mode.SRC_IN)
                val ratio = if (intrinsicWidth > 0 && intrinsicHeight > 0) intrinsicWidth.toFloat() / intrinsicHeight else 1f
                setBounds(0, 0, (size * ratio).toInt(), size)
            }
        }

        val next = if (glyphs.isEmpty()) base else {
            IconsDrawable(base, wanted, glyphs, (ICON_GAP_DP * density).toInt(), atStart)
        }
        if (next === current) return false
        slots[index] = next
        view.setCompoundDrawablesRelative(slots[0], slots[1], slots[2], slots[3])
        return true
    }
}

private fun Drawable.boxWidth() = bounds.width().takeIf { it > 0 } ?: intrinsicWidth.coerceAtLeast(0)

private fun Drawable.boxHeight() = bounds.height().takeIf { it > 0 } ?: intrinsicHeight.coerceAtLeast(0)

/** The text view's own drawable for this slot, if it had one, together with the platform icons. */
private class IconsDrawable(
    val original: Drawable?,
    val icons: List<PlatformIcon>,
    glyphs: List<Drawable>,
    private val gap: Int,
    private val iconsFirst: Boolean,
) : Drawable() {
    private val items: List<Drawable> =
        if (iconsFirst) glyphs + listOfNotNull(original) else listOfNotNull(original) + glyphs
    private val totalWidth = items.sumOf { it.boxWidth() } + gap * items.size
    private val totalHeight = items.maxOf { it.boxHeight() }

    init {
        setBounds(0, 0, totalWidth, totalHeight)
    }

    override fun getIntrinsicWidth() = totalWidth

    override fun getIntrinsicHeight() = totalHeight

    override fun draw(canvas: Canvas) {
        var x = 0
        for (item in items) {
            if (!iconsFirst) x += gap
            val box = item.bounds
            canvas.save()
            canvas.translate((x - box.left).toFloat(), (totalHeight - item.boxHeight()) / 2f - box.top)
            item.draw(canvas)
            canvas.restore()
            x += item.boxWidth()
            if (iconsFirst) x += gap
        }
    }

    override fun setAlpha(alpha: Int) {
        items.forEach { it.alpha = alpha }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // The icons keep their status colors
        original?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
