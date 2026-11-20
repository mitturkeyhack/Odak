package com.erkamyazilim.odak

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Tüm analiz verileri burada, sadece telefonda tutulur. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "odak.db", null, 1) {

    companion object {
        @Volatile
        private var inst: Db? = null
        fun get(context: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(context).also { inst = it }
        }

        fun today(): String = LocalDate.now().toString()
        fun daysAgo(n: Long): String = LocalDate.now().minusDays(n).toString()

        const val SEC_FEED = "feed"
        const val SEC_STORIES = "stories"
        const val SEC_DM = "dm"
        const val SEC_EXPLORE = "explore"
        const val SEC_PROFILE = "profile"
        const val SEC_OTHER = "other"
        val SECTIONS = listOf(SEC_FEED, SEC_STORIES, SEC_DM, SEC_EXPLORE, SEC_PROFILE, SEC_OTHER)

        const val CNT_OPENS = "opens"
        const val CNT_BLOCK_DM = "block_dm"
        const val CNT_BLOCK_EXPLORE = "block_explore"
        const val CNT_BLOCK_TAB = "block_tab"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usage(day TEXT, section TEXT, ms INTEGER, PRIMARY KEY(day, section))")
        db.execSQL("CREATE TABLE counters(day TEXT, name TEXT, n INTEGER, PRIMARY KEY(day, name))")
        db.execSQL("CREATE TABLE follow_counts(day TEXT PRIMARY KEY, followers INTEGER, following INTEGER, ts INTEGER)")
        db.execSQL("CREATE TABLE scans(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, count INTEGER, done INTEGER)")
        db.execSQL("CREATE TABLE scan_users(scan_id INTEGER, username TEXT, PRIMARY KEY(scan_id, username))")
        db.execSQL("CREATE TABLE stories(id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER, last_check INTEGER)")
        db.execSQL("CREATE TABLE story_viewers(story_id INTEGER, username TEXT, first_seen INTEGER, prev_check INTEGER, PRIMARY KEY(story_id, username))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------------- Kullanım süresi ----------------

    fun addUsage(day: String, section: String, ms: Long) {
        if (ms <= 0) return
        val db = writableDatabase
        val st = db.compileStatement("UPDATE usage SET ms = ms + ? WHERE day = ? AND section = ?")
        st.bindLong(1, ms); st.bindString(2, day); st.bindString(3, section)
        if (st.executeUpdateDelete() == 0) {
            db.execSQL("INSERT OR IGNORE INTO usage(day, section, ms) VALUES(?, ?, ?)", arrayOf<Any>(day, section, ms))
        }
    }

    fun usageTotal(day: String): Long =
        readableDatabase.rawQuery("SELECT COALESCE(SUM(ms), 0) FROM usage WHERE day = ?", arrayOf(day)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }

    fun usageTotalRange(fromDay: String, toDay: String): Long =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(ms), 0) FROM usage WHERE day >= ? AND day <= ?", arrayOf(fromDay, toDay)
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun usageBySection(fromDay: String, toDay: String): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        readableDatabase.rawQuery(
            "SELECT section, SUM(ms) FROM usage WHERE day >= ? AND day <= ? GROUP BY section",
            arrayOf(fromDay, toDay)
        ).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getLong(1) }
        return out
    }

    // ---------------- Sayaçlar ----------------

    fun inc(day: String, name: String, by: Int = 1) {
        val db = writableDatabase
        val st = db.compileStatement("UPDATE counters SET n = n + ? WHERE day = ? AND name = ?")
        st.bindLong(1, by.toLong()); st.bindString(2, day); st.bindString(3, name)
        if (st.executeUpdateDelete() == 0) {
            db.execSQL("INSERT OR IGNORE INTO counters(day, name, n) VALUES(?, ?, ?)", arrayOf<Any>(day, name, by))
        }
    }

    fun counter(fromDay: String, toDay: String, name: String): Int =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(n), 0) FROM counters WHERE day >= ? AND day <= ? AND name = ?",
            arrayOf(fromDay, toDay, name)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ---------------- Takipçi sayıları ----------------

    fun saveFollowCounts(followers: Int, following: Int) {
        val v = ContentValues().apply {
            put("day", today())
            put("followers", followers)
            put("following", following)
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("follow_counts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** (gün, takipçi, takip) — eskiden yeniye. */
    fun followHistory(limit: Int): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        readableDatabase.rawQuery(
            "SELECT day, followers, following FROM follow_counts ORDER BY day DESC LIMIT ?", arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) out.add(Triple(c.getString(0), c.getInt(1), c.getInt(2))) }
        out.reverse()
        return out
    }

    // ---------------- Takipçi taramaları ----------------

    fun startScan(): Long {
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("count", 0)
            put("done", 0)
        }
        return writableDatabase.insert("scans", null, v)
    }

    fun addScanUsers(scanId: Long, users: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (u in users) db.execSQL("INSERT OR IGNORE INTO scan_users(scan_id, username) VALUES(?, ?)", arrayOf<Any>(scanId, u))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun scanCount(scanId: Long): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM scan_users WHERE scan_id = ?", arrayOf(scanId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun finishScan(scanId: Long) {
        val n = scanCount(scanId)
        if (n == 0) {
            cancelScan(scanId)
            return
        }
        writableDatabase.execSQL("UPDATE scans SET done = 1, count = ?, ts = ? WHERE id = ?", arrayOf<Any>(n, System.currentTimeMillis(), scanId))
    }

    fun cancelScan(scanId: Long) {
        writableDatabase.execSQL("DELETE FROM scan_users WHERE scan_id = ?", arrayOf<Any>(scanId))
        writableDatabase.execSQL("DELETE FROM scans WHERE id = ?", arrayOf<Any>(scanId))
    }

    class Scan(val id: Long, val ts: Long, val count: Int)

    fun lastScans(n: Int): List<Scan> {
        val out = ArrayList<Scan>()
        readableDatabase.rawQuery(
            "SELECT id, ts, count FROM scans WHERE done = 1 ORDER BY id DESC LIMIT ?", arrayOf(n.toString())
        ).use { c -> while (c.moveToNext()) out.add(Scan(c.getLong(0), c.getLong(1), c.getInt(2))) }
        return out
    }

    fun scanUsers(scanId: Long): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT username FROM scan_users WHERE scan_id = ?", arrayOf(scanId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    // ---------------- Hikâyeler ----------------

    class Story(val id: Long, val started: Long, val lastCheck: Long)

    fun latestStory(): Story? =
        readableDatabase.rawQuery("SELECT id, started, last_check FROM stories ORDER BY id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) Story(c.getLong(0), c.getLong(1), c.getLong(2)) else null
        }

    fun story(id: Long): Story? =
        readableDatabase.rawQuery("SELECT id, started, last_check FROM stories WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Story(c.getLong(0), c.getLong(1), c.getLong(2)) else null
        }

    /** 24 saatten eski değilse son hikâyeyi, değilse yenisini döndürür. */
    fun currentStory(now: Long): Story {
        val s = latestStory()
        if (s != null && now - s.started < 24L * 3600_000L) return s
        val v = ContentValues().apply {
            put("started", now)
            put("last_check", 0L)
        }
        val id = writableDatabase.insert("stories", null, v)
        return Story(id, now, 0L)
    }

    fun setStoryCheck(id: Long, ts: Long) {
        writableDatabase.execSQL("UPDATE stories SET last_check = ? WHERE id = ?", arrayOf<Any>(ts, id))
    }

    fun addStoryViewers(storyId: Long, users: Collection<String>, firstSeen: Long, prevCheck: Long): Int {
        var added = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (u in users) {
                val v = ContentValues().apply {
                    put("story_id", storyId)
                    put("username", u)
                    put("first_seen", firstSeen)
                    put("prev_check", prevCheck)
                }
                if (db.insertWithOnConflict("story_viewers", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    class Viewer(val username: String, val firstSeen: Long, val prevCheck: Long)

    fun storyViewers(storyId: Long): List<Viewer> {
        val out = ArrayList<Viewer>()
        readableDatabase.rawQuery(
            "SELECT username, first_seen, prev_check FROM story_viewers WHERE story_id = ? ORDER BY first_seen ASC, username ASC",
            arrayOf(storyId.toString())
        ).use { c -> while (c.moveToNext()) out.add(Viewer(c.getString(0), c.getLong(1), c.getLong(2))) }
        return out
    }

    /** (hikâye, görüntüleyen sayısı) — yeniden eskiye. */
    fun storyList(limit: Int): List<Pair<Story, Int>> {
        val out = ArrayList<Pair<Story, Int>>()
        readableDatabase.rawQuery(
            "SELECT s.id, s.started, s.last_check, (SELECT COUNT(*) FROM story_viewers v WHERE v.story_id = s.id) " +
                "FROM stories s ORDER BY s.id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) out.add(Story(c.getLong(0), c.getLong(1), c.getLong(2)) to c.getInt(3)) }
        return out
    }

    /** Son [n] hikâyenin id'leri, yeniden eskiye. */
    fun lastStoryIds(n: Int): List<Long> {
        val out = ArrayList<Long>()
        readableDatabase.rawQuery("SELECT id FROM stories ORDER BY id DESC LIMIT ?", arrayOf(n.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0))
        }
        return out
    }

    fun viewersOf(ids: List<Long>): Map<Long, Set<String>> {
        val out = HashMap<Long, MutableSet<String>>()
        if (ids.isEmpty()) return out
        val q = ids.joinToString(",")
        readableDatabase.rawQuery("SELECT story_id, username FROM story_viewers WHERE story_id IN ($q)", null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getLong(0)) { HashSet() }.add(c.getString(1))
        }
        return out
    }
}
