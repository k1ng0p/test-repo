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
 * Shows the icons as a compound drawable at the end of the author's name, so they sit right after
 * the text and whatever is laid out after the name (the timestamp) is pushed along with them.
 * Works in any parent layout, as the name view itself just gets wider.
 */
internal class NameIcons(
    author: TextView,
    private val onMissingResource: (String) -> Unit,
) : IconSlot {
    private val author = WeakReference(author)
    private val density = author.resources.displayMetrics.density

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
        val view = author.get() ?: return false
        val ends = view.compoundDrawablesRelative
        val current = ends[2]
        val ours = current as? IconsDrawable
        val base = ours?.original ?: current

        if (wanted.isEmpty()) {
            if (ours == null) return false
            view.setCompoundDrawablesRelative(ends[0], ends[1], base, ends[3])
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
            IconsDrawable(base, wanted, glyphs, (ICON_GAP_DP * density).toInt())
        }
        if (next === current) return false
        view.setCompoundDrawablesRelative(ends[0], ends[1], next, ends[3])
        return true
    }
}

/** The name's own end drawable, if it had one, followed by the platform icons. */
private class IconsDrawable(
    val original: Drawable?,
    val icons: List<PlatformIcon>,
    private val glyphs: List<Drawable>,
    private val gap: Int,
) : Drawable() {
    private val originalWidth = original?.let { it.bounds.width().takeIf { w -> w > 0 } ?: it.intrinsicWidth.coerceAtLeast(0) } ?: 0
    private val originalHeight = original?.let { it.bounds.height().takeIf { h -> h > 0 } ?: it.intrinsicHeight.coerceAtLeast(0) } ?: 0
    private val totalWidth: Int
    private val totalHeight: Int

    init {
        var x = originalWidth
        for (glyph in glyphs) x += gap + glyph.bounds.width()
        totalWidth = x
        totalHeight = maxOf(originalHeight, glyphs.maxOf { it.bounds.height() })
        setBounds(0, 0, totalWidth, totalHeight)
    }

    override fun getIntrinsicWidth() = totalWidth

    override fun getIntrinsicHeight() = totalHeight

    override fun draw(canvas: Canvas) {
        var x = 0
        if (original != null) {
            canvas.save()
            canvas.translate(0f, (totalHeight - originalHeight) / 2f)
            original.draw(canvas)
            canvas.restore()
            x = originalWidth
        }
        for (glyph in glyphs) {
            x += gap
            canvas.save()
            canvas.translate(x.toFloat(), (totalHeight - glyph.bounds.height()) / 2f)
            glyph.draw(canvas)
            canvas.restore()
            x += glyph.bounds.width()
        }
    }

    override fun setAlpha(alpha: Int) {
        original?.alpha = alpha
        glyphs.forEach { it.alpha = alpha }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // The icons keep their status colors
        original?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
