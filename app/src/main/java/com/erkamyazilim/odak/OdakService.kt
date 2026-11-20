package com.erkamyazilim.odak

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Odak'ın kalbi. Instagram açıkken:
 *  - alt çubuğu kendi çubuğuyla örter, Reels hariç simgeleri eşit aralıkla dizer,
 *  - Reels sekmesi seçilirse ana sayfaya döner,
 *  - DM'den / Keşfet'ten açılan tam ekran Reels'i geri atar ve engel ekranı gösterir.
 */
class OdakService : AccessibilityService() {

    companion object {
        const val IG = "com.instagram.android"

        @Volatile
        var instance: OdakService? = null
            private set

        private val VIEWER_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_viewer_root",
            "clips_viewer_fragment_container"
        )
        private val TAB_IDS = listOf(
            "feed_tab", "search_tab", "clips_tab", "creation_tab",
            "direct_tab", "profile_tab", "notifications_tab", "shopping_tab"
        )
    }

    private class Tab(
        val node: AccessibilityNodeInfo,
        val rect: Rect,
        val id: String,
        val desc: String,
        val isReels: Boolean,
        val isSearch: Boolean,
        val selected: Boolean
    )

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null

    private var barView: BarView? = null
    private val barRect = Rect()
    private var lastBg: Int? = null

    private var blockView: View? = null

    private var scheduled = false
    private var lastBlockAt = 0L
    private var lastRedirectAt = 0L
    private var lastShotAt = 0L
    private var shotInFlight = false
    private var screenshotFailures = 0
    private var lastSig = ""
    private var lastViewerScanAt = 0L
    private var lastViewerResult = false
    private var lastDumpAt = 0L

    /** "Ekran bilgisini kopyala" için son görülen Instagram ekranının özeti (sadece kimlikler). */
    @Volatile
    var lastDump: String = ""
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        schedule(50)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        schedule(100)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        instance = null
        super.onDestroy()
    }

    /** Ana ekranda ayar değişince çağrılır. */
    fun refresh() {
        handler.post {
            lastSig = ""
            safeProcess()
        }
    }

    private fun schedule(delay: Long) {
        if (scheduled) return
        scheduled = true
        handler.postDelayed({
            scheduled = false
            safeProcess()
        }, delay)
    }

    private fun safeProcess() {
        try {
            process()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------

    private fun process() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == packageName && blockView != null) return
        if (pkg != IG) {
            removeBar()
            removeBlock()
            return
        }

        val prefs = Prefs(this)
        val tabs = findTabs(root)
        val viewer = hasFullScreenViewer(root)
        maybeDump(root, tabs, viewer)

        if (!prefs.master) {
            removeBar()
            removeBlock()
            return
        }

        val selected = tabs.firstOrNull { it.selected }
        val now = SystemClock.uptimeMillis()
        if (selected != null && selected.isReels) {
            if (prefs.reels && now - lastRedirectAt > 700) {
                lastRedirectAt = now
                tabs.firstOrNull { !it.isReels }?.let { clickNode(it.node) }
                schedule(250)
            }
        } else if (viewer) {
            val fromExplore = selected?.isSearch == true
            if ((fromExplore && prefs.explore) || (!fromExplore && prefs.dm)) {
                block(prefs)
            }
        }

        if (prefs.reels && tabs.size >= 3 && tabs.any { it.isReels }) {
            updateBar(root, tabs)
        } else {
            removeBar()
        }
    }

    // ---------------- Instagram ekranını okuma ----------------

    private fun screenSize(): Pair<Int, Int> {
        try {
            val w = wm
            if (Build.VERSION.SDK_INT >= 30 && w != null) {
                val b = w.maximumWindowMetrics.bounds
                return b.width() to b.height()
            }
        } catch (_: Exception) {
        }
        val dm = resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    private fun findTabs(root: AccessibilityNodeInfo): List<Tab> {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val bar = root.findAccessibilityNodeInfosByViewId("$IG:id/tab_bar").firstOrNull { it.isVisibleToUser }
        if (bar != null) {
            for (i in 0 until bar.childCount) bar.getChild(i)?.let { nodes.add(it) }
        }
        if (nodes.size < 3) {
            nodes.clear()
            for (id in TAB_IDS) {
                root.findAccessibilityNodeInfosByViewId("$IG:id/$id").forEach { nodes.add(it) }
            }
        }
        val h = screenSize().second
        val out = ArrayList<Tab>()
        for (n in nodes) {
            if (!n.isVisibleToUser) continue
            val r = Rect()
            n.getBoundsInScreen(r)
            if (r.width() <= 0 || r.height() <= 0) continue
            if (r.top < h * 0.6) continue
            val id = n.viewIdResourceName ?: ""
            val desc = descOf(n, 2).lowercase()
            val isReels = id.endsWith("/clips_tab") || desc.contains("reels")
            val isSearch = id.endsWith("/search_tab") || desc.contains("search") ||
                desc.contains("keşfet") || desc.startsWith("ara")
            out.add(Tab(n, r, id, desc, isReels, isSearch, isSelected(n, 2)))
        }
        out.sortBy { it.rect.left }
        return out
    }

    private fun descOf(n: AccessibilityNodeInfo, depth: Int): String {
        val d = n.contentDescription?.toString()
        if (!d.isNullOrBlank()) return d
        if (depth <= 0) return ""
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val s = descOf(c, depth - 1)
            if (s.isNotEmpty()) return s
        }
        return ""
    }

    private fun isSelected(n: AccessibilityNodeInfo, depth: Int): Boolean {
        if (n.isSelected) return true
        if (depth <= 0) return false
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (isSelected(c, depth - 1)) return true
        }
        return false
    }

    private fun isFullScreen(n: AccessibilityNodeInfo): Boolean {
        if (!n.isVisibleToUser) return false
        val r = Rect()
        n.getBoundsInScreen(r)
        val (w, h) = screenSize()
        return r.height() >= h * 0.7 && r.width() >= w * 0.9
    }

    private fun hasFullScreenViewer(root: AccessibilityNodeInfo): Boolean {
        for (id in VIEWER_IDS) {
            for (n in root.findAccessibilityNodeInfosByViewId("$IG:id/$id")) {
                if (isFullScreen(n)) return true
            }
        }
        // Yedek: kimliğinde "clips_viewer" geçen tam ekran bir görünüm ara (sık tarama yapma).
        val now = SystemClock.uptimeMillis()
        if (now - lastViewerScanAt < 450) return lastViewerResult
        lastViewerScanAt = now
        var found = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 400) {
            val n = queue.removeFirst()
            count++
            val id = n.viewIdResourceName
            if (id != null && id.contains("clips_viewer") && isFullScreen(n)) {
                found = true
                break
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        lastViewerResult = found
        return found
    }

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (clickNode(c)) return true
        }
        val p = n.parent
        if (p != null && p.isClickable) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return false
    }

    private fun maybeDump(root: AccessibilityNodeInfo, tabs: List<Tab>, viewer: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDumpAt < 2000) return
        lastDumpAt = now
        val sb = StringBuilder()
        sb.append("viewer=").append(viewer).append('\n')
        sb.append("tabs=").append(tabs.size).append('\n')
        for (t in tabs) {
            sb.append(" - ").append(t.id.substringAfter(":id/")).append(" | ").append(t.desc.take(30))
                .append(" | sel=").append(t.selected).append(" | reels=").append(t.isReels)
                .append(" | ").append(t.rect.toShortString()).append('\n')
        }
        val ids = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 600 && ids.size < 120) {
            val n = queue.removeFirst()
            count++
            n.viewIdResourceName?.let { ids.add(it.substringAfter(":id/")) }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        sb.append("ids=").append(ids.joinToString(","))
        lastDump = sb.toString()
    }

    // ---------------- Alt çubuk ----------------

    private fun defaultBg(): Int {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) Color.BLACK else Color.WHITE
    }

    private fun updateBar(root: AccessibilityNodeInfo, tabs: List<Tab>) {
        val reels = tabs.first { it.isReels }
        val others = tabs.filter { !it.isReels }
        val union = Rect(tabs[0].rect)
        for (t in tabs) union.union(t.rect)

        // Önce Reels simgesini hemen kapat (yama), sonra tam çubuğu hazırla.
        val current = barView
        if (current == null || !current.full) {
            ensureBar(reels.rect, false, lastBg ?: defaultBg(), emptyList())
        }

        if (Build.VERSION.SDK_INT < 34 || screenshotFailures >= 5) return

        val sig = union.toShortString() + others.joinToString("") { if (it.selected) "1" else "0" }
        val now = SystemClock.uptimeMillis()
        val stale = sig != lastSig || now - lastShotAt > 5000
        if (!stale || shotInFlight) return
        if (now - lastShotAt < 500) {
            schedule(520)
            return
        }
        lastSig = sig
        lastShotAt = now
        shotInFlight = true
        shoot(root, Rect(reels.rect), others.map { Rect(it.rect) }, union)
    }

    private fun shoot(root: AccessibilityNodeInfo, reelsRect: Rect, otherRects: List<Rect>, union: Rect) {
        val winRect = Rect()
        root.window?.getBoundsInScreen(winRect)
        takeScreenshotOfWindow(root.windowId, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                shotInFlight = false
                try {
                    val hb = result.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    hb.close()
                    if (bmp == null) return
                    val crops = ArrayList<Bitmap>()
                    for (r in otherRects) {
                        val c = crop(bmp, r, winRect) ?: return
                        crops.add(c)
                    }
                    val bx = (reelsRect.left + dp(3) - winRect.left).coerceIn(0, bmp.width - 1)
                    val by = (reelsRect.top + dp(3) - winRect.top).coerceIn(0, bmp.height - 1)
                    val bg = bmp.getPixel(bx, by) or 0xFF000000.toInt()
                    lastBg = bg
                    if (barView != null) ensureBar(union, true, bg, crops)
                    screenshotFailures = 0
                } catch (_: Exception) {
                    screenshotFailures++
                }
            }

            override fun onFailure(errorCode: Int) {
                shotInFlight = false
                screenshotFailures++
                if (screenshotFailures < 5) schedule(600)
            }
        })
    }

    private fun crop(bmp: Bitmap, r: Rect, win: Rect): Bitmap? {
        val x = (r.left - win.left).coerceAtLeast(0)
        val y = (r.top - win.top).coerceAtLeast(0)
        val w = minOf(r.width(), bmp.width - x)
        val h = minOf(r.height(), bmp.height - y)
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    private fun overlayParams(r: Rect?, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val lp = WindowManager.LayoutParams(
            r?.width() ?: WindowManager.LayoutParams.MATCH_PARENT,
            r?.height() ?: WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = r?.left ?: 0
        lp.y = r?.top ?: 0
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 30) lp.fitInsetsTypes = 0
        return lp
    }

    private fun ensureBar(rect: Rect, full: Boolean, bg: Int, crops: List<Bitmap>) {
        val w = wm ?: return
        var v = barView
        if (v == null) {
            v = BarView(this)
            try {
                w.addView(v, overlayParams(rect, false))
            } catch (_: Exception) {
                return
            }
            barView = v
            barRect.set(rect)
        } else if (rect != barRect) {
            try {
                w.updateViewLayout(v, overlayParams(rect, false))
            } catch (_: Exception) {
            }
            barRect.set(rect)
        }
        v.bg = bg
        v.full = full
        v.crops = crops
        v.invalidate()
    }

    private fun removeBar() {
        val v = barView ?: return
        barView = null
        lastSig = ""
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun tapTab(index: Int) {
        val root = rootInActiveWindow ?: return
        val others = findTabs(root).filter { !it.isReels }
        others.getOrNull(index)?.let { clickNode(it.node) }
        lastSig = ""
        schedule(200)
    }

    /** Reels'i gizleyen, kalan simgeleri eşit aralıkla çizen çubuk. */
    private inner class BarView(ctx: Context) : View(ctx) {
        var bg: Int = Color.WHITE
        var full: Boolean = false
        var crops: List<Bitmap> = emptyList()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(bg)
            if (!full || crops.isEmpty()) return
            val slot = width.toFloat() / crops.size
            crops.forEachIndexed { i, b ->
                canvas.drawBitmap(b, i * slot + (slot - b.width) / 2f, (height - b.height) / 2f, null)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP && full && crops.isNotEmpty()) {
                val slot = width.toFloat() / crops.size
                val i = (event.x / slot).toInt().coerceIn(0, crops.size - 1)
                tapTab(i)
            }
            return true
        }
    }

    // ---------------- Engel ekranı ----------------

    private fun block(prefs: Prefs) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < 900) return
        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        prefs.blockedToday = prefs.blockedToday + 1
        showBlock()
    }

    private inner class BlockLayout(ctx: Context) : LinearLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) removeBlock()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private fun showBlock() {
        if (blockView != null) return
        val w = wm ?: return
        val root = BlockLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(C.DARK)
            setPadding(dp(28), dp(64), dp(28), dp(48))
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val center = vertical().apply { gravity = Gravity.CENTER }
        center.addView(iconBox(R.drawable.ic_blocked, C.MINT, 0x247FD6CB, 96, 44, 48f))
        center.add(label("Bu Reels engellendi", 30f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER }, topDp = 22)
        center.add(
            label("Odak açık olduğu için Reels izlenmiyor. Geri dönebilirsin.", 16f, C.DARK_TEXT).apply {
                gravity = Gravity.CENTER
            },
            topDp = 12
        )
        root.add(center, height = 0, weight = 1f)
        val btn = TextView(this).apply {
            text = "Geri dön"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(C.DARK)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            background = rounded(Color.WHITE, 27f)
            isClickable = true
            setOnClickListener { removeBlock() }
        }
        root.add(btn, height = dp(54))
        root.add(
            label("Korumayı Odak uygulamasından kapatabilirsin.", 13f, C.DARK_SUB).apply { gravity = Gravity.CENTER },
            topDp = 12
        )
        try {
            w.addView(root, overlayParams(null, true))
            blockView = root
            root.requestFocus()
        } catch (_: Exception) {
        }
    }

    private fun removeBlock() {
        val v = blockView ?: return
        blockView = null
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        removeBar()
        removeBlock()
    }
}
