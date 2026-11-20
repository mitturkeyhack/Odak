package com.erkamyazilim.odak

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Instagram ekranından tek geçişte okunan bilgiler. */
class ScreenInfo {
    val ids = LinkedHashSet<String>()
    var clipsViewerFull = false
    var reelViewer = false
    var direct = false
    var viewersKeyword = false
    var editProfile = false
    var followingTabSelected = false
    val followListUsers = LinkedHashSet<String>()
    val usernames = LinkedHashSet<String>()
    var followers: Int? = null
    var following: Int? = null
}

object ScreenReader {

    private val USERNAME = Regex("^[a-z0-9._]{1,30}$")
    private val NUMBER = Regex("^([0-9][0-9.,]*)\\s*(bin|milyon|mn|b|k|m)?$")
    private val NUMBER_PREFIX = Regex("^([0-9][0-9.,]*)\\s*(bin|milyon|mn|b|k|m)?\\s+(.*)$")
    private val VIEWER_WORDS = listOf("görüntüleyen", "viewers", "seen by", "kişi gördü", "etkinlik", "activity")
    private val FOLLOWERS_WORDS = listOf("takipçi", "followers")
    private val FOLLOWING_WORDS = listOf("takip edilen", "takip", "following")

    fun cleanUsername(raw: String?): String? {
        if (raw == null) return null
        val s = raw.trim().removePrefix("@").lowercase()
        return if (USERNAME.matches(s) && s.any { it.isLetter() }) s else null
    }

    /** "1.248", "12,5 B", "1,2 Mn", "12.5K" → sayı. */
    fun parseCount(raw: String): Int? {
        val s = raw.trim().lowercase().replace(' ', ' ')
        val m = NUMBER.find(s) ?: return null
        return fromParts(m.groupValues[1], m.groupValues[2])
    }

    private fun fromParts(num: String, suffix: String): Int? {
        if (suffix.isEmpty()) return num.replace(".", "").replace(",", "").toIntOrNull()
        val d = num.replace(",", ".").toDoubleOrNull() ?: return null
        val mul = if (suffix == "b" || suffix == "bin" || suffix == "k") 1_000.0 else 1_000_000.0
        return (d * mul).toInt()
    }

    private fun isNumber(s: String) = NUMBER.matches(s.trim().lowercase().replace(' ', ' '))

    private fun labelKind(s: String): Int {
        val t = s.trim().lowercase()
        if (FOLLOWERS_WORDS.any { t == it }) return 1
        if (FOLLOWING_WORDS.any { t == it }) return 2
        return 0
    }

    /** "1.248 takipçi" gibi tek parça yazıyı çözer. */
    private fun combined(s: String): Pair<Int, Int>? {
        val t = s.trim().lowercase().replace(' ', ' ')
        val m = NUMBER_PREFIX.find(t) ?: return null
        val n = fromParts(m.groupValues[1], m.groupValues[2]) ?: return null
        val kind = labelKind(m.groupValues[3])
        return if (kind == 0) null else kind to n
    }

    fun scan(root: AccessibilityNodeInfo, screenW: Int, screenH: Int, maxNodes: Int = 900): ScreenInfo {
        val info = ScreenInfo()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val r = Rect()
        while (queue.isNotEmpty() && count < maxNodes) {
            val n = queue.removeFirst()
            count++
            val full = n.viewIdResourceName
            val id = full?.substringAfter(":id/") ?: ""
            if (id.isNotEmpty()) info.ids.add(id)
            val text = n.text?.toString()
            val desc = n.contentDescription?.toString()
            val lt = text?.lowercase() ?: ""
            val ld = desc?.lowercase() ?: ""

            if (id.contains("clips_viewer") && n.isVisibleToUser) {
                n.getBoundsInScreen(r)
                if (r.height() >= screenH * 0.7 && r.width() >= screenW * 0.9) info.clipsViewerFull = true
            }
            if (id.contains("reel_viewer")) info.reelViewer = true
            if (id.contains("direct") && id != "direct_tab") info.direct = true
            if (VIEWER_WORDS.any { lt.contains(it) || ld.contains(it) }) info.viewersKeyword = true
            if (lt.contains("profili düzenle") || lt.contains("edit profile")) info.editProfile = true
            if (n.isSelected && (lt.contains("takip edilen") || lt.contains("following") || ld.contains("takip edilen") || ld.contains("following"))) {
                info.followingTabSelected = true
            }

            if (id.endsWith("follow_list_username")) {
                cleanUsername(text)?.let { info.followListUsers.add(it) }
            } else if (id.contains("username") || id.contains("primary_name")) {
                cleanUsername(text)?.let { info.usernames.add(it) }
            }

            if (id.contains("followers_count")) text?.let { parseCount(it) }?.let { info.followers = it }
            if (id.contains("following_count")) text?.let { parseCount(it) }?.let { info.following = it }
            for (s in listOfNotNull(text, desc)) {
                combined(s)?.let { (kind, v) ->
                    if (kind == 1 && info.followers == null) info.followers = v
                    if (kind == 2 && info.following == null) info.following = v
                }
            }

            // Ayrı satırlarda sayı + etiket ("1.248" / "takipçi")
            var prevText: String? = null
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                queue.add(c)
                val ct = c.text?.toString() ?: firstText(c)
                if (ct != null && prevText != null && isNumber(prevText)) {
                    when (labelKind(ct)) {
                        1 -> if (info.followers == null) info.followers = parseCount(prevText)
                        2 -> if (info.following == null) info.following = parseCount(prevText)
                    }
                }
                prevText = ct
            }
        }
        return info
    }

    private fun firstText(n: AccessibilityNodeInfo): String? {
        for (i in 0 until minOf(n.childCount, 3)) {
            val c = n.getChild(i) ?: continue
            val t = c.text?.toString()
            if (!t.isNullOrBlank()) return t
        }
        return null
    }
}
