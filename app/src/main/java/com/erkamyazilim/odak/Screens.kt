package com.erkamyazilim.odak

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

// =====================================================================
// ODAK SEKMESİ
// =====================================================================

internal fun MainActivity.odakScreen(): View {
    val root = vertical()

    val header = horizontal()
    header.addView(iconBox(R.drawable.ic_target, 0xFFFFFFFF.toInt(), C.TEAL, 40, 22, 12f))
    header.add(label("Odak", 26f, C.INK, bold = true), width = LinearLayout.LayoutParams.WRAP_CONTENT).also {
        (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(10)
    }
    root.add(header)

    // Koruma kartı
    val on = prefs.master
    val fg = if (on) 0xFFFFFFFF.toInt() else C.INK
    val hero = horizontal().apply {
        background = rounded(if (on) C.TEAL else C.OFF_CARD, 24f)
        setPadding(dp(22), dp(20), dp(18), dp(20))
    }
    val heroTexts = vertical()
    heroTexts.add(label(if (on) "Koruma açık" else "Koruma kapalı", 26f, fg, bold = true))
    val breakLeft = ((prefs.breakUntil - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
    val heroSub = when {
        !on -> "Reels şu an görünüyor"
        prefs.onBreak -> "Mola sürüyor · $breakLeft dk kaldı"
        else -> "Bugün ${prefs.blockedToday} kez Reels engellendi"
    }
    heroTexts.add(label(heroSub, 14f, if (on) 0xFFD7EEEA.toInt() else C.MUTED), topDp = 4)
    hero.add(heroTexts, width = 0, weight = 1f)
    val heroBtn = TextView(this).apply {
        text = if (on) "Kapat" else "Aç"
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        background = rounded(if (on) 0x29FFFFFF else C.TEAL, 22f)
        setPadding(dp(18), 0, dp(18), 0)
        minHeight = dp(44)
        minWidth = dp(72)
        isClickable = true
        setOnClickListener { guarded(on) { prefs.master = !on } }
    }
    hero.add(heroBtn, width = LinearLayout.LayoutParams.WRAP_CONTENT)
    root.add(hero, topDp = 20)

    // Zaman kuralları
    root.add(section("Zaman kuralları"), topDp = 22)
    val rules = card()
    rules.add(
        row(
            R.drawable.ic_moon, "Ders / gece modu",
            "${Fmt.hhmm(prefs.nightStart)} – ${Fmt.hhmm(prefs.nightEnd)} arası Instagram kilitli · saati değiştirmek için dokun",
            toggle(prefs.nightOn) { val v = !prefs.nightOn; guarded(!v) { prefs.nightOn = v } },
            last = false
        ) { guarded(true) { pickNight() } }
    )
    val usedMin = ((OdakService.instance?.todayUsageMs() ?: db.usageTotal(Db.today())) / 60_000L).toInt()
    rules.add(
        row(
            R.drawable.ic_clock, "Günlük süre sınırı",
            "Günde ${prefs.limitMin} dk · bugün $usedMin dk kullandın",
            toggle(prefs.limitOn) { val v = !prefs.limitOn; guarded(!v) { prefs.limitOn = v } },
            last = false
        ) { guarded(true) { pickLimit() } }
    )
    val onBreak = prefs.onBreak
    rules.add(
        row(
            R.drawable.ic_coffee, "5 dakika mola",
            if (onBreak) "Mola sürüyor · $breakLeft dk kaldı" else "Reels'i 5 dk açar, sonra kendiliğinden kapanır",
            outlineButton(if (onBreak) "Bitir" else "Mola ver") {
                if (onBreak) guarded(false) { prefs.breakUntil = 0L }
                else guarded(true) { prefs.breakUntil = System.currentTimeMillis() + 5 * 60_000L }
            },
            last = true
        )
    )
    root.add(rules, topDp = 10)

    // Engeller
    root.add(section("Neleri engelleyelim?"), topDp = 22)
    val blocks = card()
    val items = listOf(
        Triple(Prefs.KEY_REELS, R.drawable.ic_reels, "Reels simgesini gizle" to "Alt çubuktaki Reels düğmesi kaybolur, diğerleri ortalanır"),
        Triple(Prefs.KEY_EXPLORE, R.drawable.ic_search, "Keşfet'teki Reels'i engelle" to "Keşfet'ten açılan Reels izlenmez"),
        Triple(Prefs.KEY_DM, R.drawable.ic_chat, "DM'den gelen Reels'i engelle" to "Arkadaşın Reels atınca açılmaz"),
        Triple(Prefs.KEY_LOCK, R.drawable.ic_lock, "Kapatmayı zorlaştır" to "Gevşetmek için 30 sn beklersin")
    )
    items.forEachIndexed { i, (key, ic, texts) ->
        val master = prefs.master
        val isLock = key == Prefs.KEY_LOCK
        val state = if (isLock) prefs.lock else master && prefs.get(key)
        val click = {
            if (!isLock && !prefs.master) {
                Toast.makeText(this, "Önce korumayı aç", Toast.LENGTH_SHORT).show()
            } else {
                val v = !prefs.get(key)
                guarded(!v) { prefs.set(key, v) }
            }
        }
        val t = toggle(state, dimmed = !master && !isLock) { click() }
        t.contentDescription = texts.first
        blocks.add(row(ic, texts.first, texts.second, t, last = i == items.size - 1) { click() })
    }
    root.add(blocks, topDp = 10)

    // Bildirimler
    root.add(section("Bildirimler"), topDp = 22)
    val notif = card()
    notif.add(
        row(
            R.drawable.ic_bell, "Haftalık rapor", "Her pazar 20:00'de haftanın özeti gelir",
            toggle(prefs.weekly) {
                val v = !prefs.weekly
                prefs.weekly = v
                if (v) {
                    askNotificationPermission()
                    Report.schedule(this)
                } else {
                    Report.cancel(this)
                }
                render()
            },
            last = true
        )
    )
    root.add(notif, topDp = 10)

    root.add(
        note(R.drawable.ic_info, "Süre dolunca ya da ders saatinde Instagram açılırsa kilit ekranı çıkar. 10 dakikalığına açmak için 30 sn beklemen gerekir. Odak hiçbir şeyi internete göndermez."),
        topDp = 22
    )
    root.add(
        label("Çalışmıyor mu? Ekran bilgisini kopyala", 14f, C.TEAL, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(6))
            isClickable = true
            setOnClickListener { copyDiagnostics() }
        },
        topDp = 8
    )
    root.add(
        label("Instagram'da sorun olan ekranı aç, sonra buraya gelip bas ve kopyalanan yazıyı gönder.", 12f, C.SUB).apply { gravity = Gravity.CENTER }
    )
    root.add(label("Sürüm ${versionName()}", 12f, C.SUB).apply { gravity = Gravity.CENTER }, topDp = 10)
    return root
}

private fun MainActivity.askNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
    }
}

private fun MainActivity.pickNight() {
    val theme = android.R.style.Theme_DeviceDefault_Light_Dialog
    val startDlg = TimePickerDialog(this, theme, { _, h1, m1 ->
        val endDlg = TimePickerDialog(this, theme, { _, h2, m2 ->
            prefs.nightStart = h1 * 60 + m1
            prefs.nightEnd = h2 * 60 + m2
            OdakService.instance?.refresh()
            render()
        }, prefs.nightEnd / 60, prefs.nightEnd % 60, true)
        endDlg.setTitle("Bitiş saati")
        endDlg.show()
    }, prefs.nightStart / 60, prefs.nightStart % 60, true)
    startDlg.setTitle("Başlangıç saati")
    startDlg.show()
}

private fun MainActivity.pickLimit() {
    val options = intArrayOf(15, 30, 45, 60, 90, 120, 180)
    val labels = options.map { if (it < 60) "$it dk" else if (it % 60 == 0) "${it / 60} saat" else "${it / 60} sa ${it % 60} dk" }.toTypedArray()
    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
        .setTitle("Günlük süre")
        .setItems(labels) { _, which ->
            prefs.limitMin = options[which]
            OdakService.instance?.refresh()
            render()
        }
        .setNegativeButton("Vazgeç", null)
        .show()
}

// =====================================================================
// ANALİZ SEKMESİ
// =====================================================================

private val DAY_NAMES = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

internal fun MainActivity.analizScreen(): View {
    val root = vertical()
    root.add(title("Analiz", "Hepsi sadece senin telefonunda tutulur"))

    // Bugün / Bu hafta
    val seg = horizontal().apply {
        background = rounded(C.NOTE, 14f)
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    for ((i, name) in listOf("Bugün", "Bu hafta").withIndex()) {
        val sel = (i == 1) == weekRange
        val b = TextView(this).apply {
            text = name
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (sel) C.INK else C.MUTED)
            if (sel) {
                background = rounded(C.CARD, 10f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            isClickable = true
            setOnClickListener {
                weekRange = i == 1
                render()
            }
        }
        seg.add(b, width = 0, height = dp(40), weight = 1f)
    }
    root.add(seg, topDp = 18)

    val todayKey = Db.today()
    val todayDb = db.usageTotal(todayKey)
    val todayMs = OdakService.instance?.todayUsageMs() ?: todayDb
    val extraToday = (todayMs - todayDb).coerceAtLeast(0L)

    val main = card().apply { setPadding(dp(20), dp(20), dp(20), dp(16)) }
    if (!weekRange) {
        main.add(label("Bugün Instagram'da", 14f, C.SUB))
        main.add(label(Fmt.dur(todayMs), 40f, C.INK, bold = true), topDp = 2)
        val opens = db.counter(todayKey, todayKey, Db.CNT_OPENS)
        val y = db.usageTotal(Db.daysAgo(1))
        val cmp = when {
            y == 0L -> ""
            todayMs < y -> " · dünden ${Fmt.dur(y - todayMs)} az"
            todayMs > y -> " · dünden ${Fmt.dur(todayMs - y)} fazla"
            else -> ""
        }
        main.add(label("$opens kez açtın$cmp", 14f, C.MUTED), topDp = 2)
    } else {
        val week = db.usageTotalRange(Db.daysAgo(6), todayKey) + extraToday
        val prev = db.usageTotalRange(Db.daysAgo(13), Db.daysAgo(7))
        val opens = db.counter(Db.daysAgo(6), todayKey, Db.CNT_OPENS)
        main.add(label("Bu hafta Instagram'da", 14f, C.SUB))
        main.add(label(Fmt.dur(week), 40f, C.INK, bold = true), topDp = 2)
        val cmp = when {
            prev == 0L -> ""
            week < prev -> " · geçen haftadan ${Fmt.dur(prev - week)} az"
            week > prev -> " · geçen haftadan ${Fmt.dur(week - prev)} fazla"
            else -> ""
        }
        main.add(label("Günde ortalama ${Fmt.dur(week / 7)} · $opens kez açtın$cmp", 14f, C.MUTED), topDp = 2)
    }
    val values = ArrayList<Int>()
    val labels = ArrayList<String>()
    for (i in 6 downTo 0) {
        val d = LocalDate.now().minusDays(i.toLong())
        val ms = if (i == 0) todayMs else db.usageTotal(d.toString())
        values.add((ms / 60_000L).toInt())
        labels.add(DAY_NAMES[d.dayOfWeek.value - 1])
    }
    main.add(BarChartView(this, values, labels, 6), topDp = 14)
    main.add(label("dakika / gün", 12f, C.SUB).apply { gravity = Gravity.CENTER }, topDp = 4)
    root.add(main, topDp = 14)

    // Bölümler
    root.add(section("Nerede vakit geçirdin?"), topDp = 22)
    val from = if (weekRange) Db.daysAgo(6) else todayKey
    val bySec = db.usageBySection(from, todayKey).filter { it.value >= 30_000L }.toList().sortedByDescending { it.second }
    val secCard = card().apply { setPadding(dp(18), dp(8), dp(18), dp(18)) }
    if (bySec.isEmpty()) {
        secCard.add(label("Henüz veri yok. Instagram'ı kullandıkça burası dolacak.", 14f, C.SUB), topDp = 10)
    } else {
        val max = bySec.first().second.toFloat()
        val shades = intArrayOf(0xFF0F766E.toInt(), 0xFF2F8F86.toInt(), 0xFF5FAEA5.toInt(), 0xFF9FCFC8.toInt(), 0xFFB9DDD7.toInt(), 0xFFCFE6E2.toInt())
        bySec.forEachIndexed { i, (sec, ms) ->
            val line = horizontal()
            line.add(label(Fmt.sectionNames[sec] ?: sec, 14f, C.INK, bold = true), width = 0, weight = 1f)
            line.add(label(Fmt.dur(ms), 14f, C.MUTED), width = LinearLayout.LayoutParams.WRAP_CONTENT)
            secCard.add(line, topDp = 10)
            val track = LinearLayout(this).apply { background = rounded(C.DIVIDER, 5f) }
            val fill = View(this).apply { background = rounded(shades[i % shades.size], 5f) }
            val frac = (ms / max).coerceIn(0.02f, 1f)
            track.add(fill, width = 0, height = dp(10), weight = frac)
            if (frac < 1f) track.add(View(this), width = 0, height = dp(10), weight = 1f - frac)
            secCard.add(track, topDp = 6, height = dp(10))
        }
    }
    root.add(secCard, topDp = 10)

    // Reels raporu (hep son 7 gün)
    root.add(section("Reels raporu · son 7 gün"), topDp = 22)
    val f7 = Db.daysAgo(6)
    val bDm = db.counter(f7, todayKey, Db.CNT_BLOCK_DM)
    val bEx = db.counter(f7, todayKey, Db.CNT_BLOCK_EXPLORE)
    val bTab = db.counter(f7, todayKey, Db.CNT_BLOCK_TAB)
    val total = bDm + bEx + bTab
    val rep = card().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
    val stats = horizontal().apply { gravity = Gravity.TOP }
    stats.add(statBox(total.toString(), "kez engellendi", true), width = 0, weight = 1f)
    stats.add(View(this), width = dp(12), height = 1)
    stats.add(statBox("~" + Fmt.dur(total * 5 * 60_000L), "kazandığın tahmini süre", false), width = 0, weight = 1f)
    rep.add(stats)
    val top = listOf("DM'den gelen Reels'te" to bDm, "Keşfet'te" to bEx, "Reels sekmesinde" to bTab).maxByOrNull { it.second }
    val repText = if (top == null || top.second == 0) "Bu hafta hiç Reels'e takılmadın."
    else "En çok ${top.first} takıldın (${top.second} kez). Süre, her engel için 5 dk sayılarak tahmin edilir."
    rep.add(label(repText, 13f, C.MUTED), topDp = 12)
    root.add(rep, topDp = 10)

    // Takipçiler
    root.add(section("Takipçiler"), topDp = 22)
    val fc = card()
    val hist = db.followHistory(30)
    if (hist.isEmpty()) {
        fc.add(empty("Instagram'da kendi profiline bir kez gir, Odak takipçi sayını not alsın."))
    } else {
        val box = vertical().apply { setPadding(dp(18), dp(18), dp(18), dp(8)) }
        val nums = horizontal().apply { gravity = Gravity.TOP }
        val last = hist.last()
        val first = hist.first()
        val delta = last.second - first.second
        val c1 = vertical()
        c1.add(label(Fmt.num(last.second), 26f, C.INK, bold = true))
        val dtxt = if (hist.size < 2) "takipçi" else "takipçi · ${if (delta >= 0) "+" else ""}$delta (${hist.size} gün)"
        c1.add(label(dtxt, 13f, C.SUB))
        nums.add(c1, width = LinearLayout.LayoutParams.WRAP_CONTENT)
        val c2 = vertical()
        c2.add(label(Fmt.num(last.third), 26f, C.INK, bold = true))
        c2.add(label("takip ettiğin", 13f, C.SUB))
        nums.add(c2, width = LinearLayout.LayoutParams.WRAP_CONTENT).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(28)
        }
        box.add(nums)
        if (hist.size >= 2) box.add(LineChartView(this, hist.map { it.second }), topDp = 10)
        box.add(label("Profiline her girdiğinde sayı kaydedilir", 12f, C.SUB), topDp = 6)
        fc.add(box)
    }
    val scans = db.lastScans(2)
    val unfSub = when {
        prefs.scanId > 0L -> "Tarama sürüyor"
        scans.size >= 2 -> "Son taramada ${(db.scanUsers(scans[1].id) - db.scanUsers(scans[0].id)).size} kişi"
        scans.size == 1 -> "1 tarama kayıtlı"
        else -> "Henüz tarama yok"
    }
    val unfRow = row(R.drawable.ic_users, "Takipten çıkanlar", unfSub, chevron(), last = true) { go(1, "takipci") }
    if (!hist.isEmpty()) unfRow.background = android.graphics.drawable.LayerDrawable(
        arrayOf(android.graphics.drawable.ColorDrawable(C.DIVIDER), android.graphics.drawable.ColorDrawable(C.CARD))
    ).apply { setLayerInset(1, 0, dp(1), 0, 0) }
    fc.add(unfRow)
    root.add(fc, topDp = 10)
    return root
}

// =====================================================================
// TAKİPTEN ÇIKANLAR
// =====================================================================

internal fun MainActivity.takipciScreen(): View {
    val root = vertical()
    root.add(backLink("Analiz") { go(1) })
    root.add(title("Takipten çıkanlar"), topDp = 8)

    val scanId = prefs.scanId
    if (scanId > 0L) {
        val c = card().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
        c.add(label("Tarama sürüyor", 18f, C.INK, bold = true))
        c.add(label("${Fmt.num(db.scanCount(scanId))} kişi toplandı", 15f, C.TEAL, bold = true), topDp = 4)
        c.add(
            label("Instagram'da profiline gir → Takipçiler'e bas → listeyi en alta kadar yavaşça kaydır. Bitince buraya dönüp \"Taramayı bitir\"e bas.", 13f, C.MUTED),
            topDp = 10
        )
        c.add(primaryButton("Taramayı bitir") {
            db.finishScan(scanId)
            prefs.scanId = 0L
            OdakService.instance?.resetScanMemory()
            render()
        }, topDp = 14, height = dp(50))
        c.add(outlineButton("Instagram'ı aç") { openInstagram() }, topDp = 8, height = dp(44))
        c.add(secondaryButton("Taramayı iptal et") {
            db.cancelScan(scanId)
            prefs.scanId = 0L
            OdakService.instance?.resetScanMemory()
            render()
        }, topDp = 4, height = dp(44))
        root.add(c, topDp = 16)
    } else {
        val scans = db.lastScans(2)
        val c = card().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
        if (scans.isEmpty()) {
            c.add(label("Henüz tarama yapılmadı", 18f, C.INK, bold = true))
        } else {
            c.add(label("Son tarama", 14f, C.SUB))
            c.add(label("${Fmt.dayMonthTime(scans[0].ts)} · ${Fmt.num(scans[0].count)} kişi", 18f, C.INK, bold = true), topDp = 2)
            if (scans.size >= 2) {
                c.add(label("Önceki tarama: ${Fmt.dayMonth(scans[1].ts)} · ${Fmt.num(scans[1].count)} kişi", 13f, C.MUTED), topDp = 2)
            }
        }
        c.add(primaryButton("Yeni tarama başlat") {
            val id = db.startScan()
            prefs.scanId = id
            OdakService.instance?.resetScanMemory()
            Toast.makeText(this, "Profiline gir → Takipçiler → listeyi en alta kadar kaydır", Toast.LENGTH_LONG).show()
            openInstagram()
        }, topDp = 14, height = dp(50))
        c.add(
            label("Basınca Instagram açılır. Takipçi listeni en alta kadar yavaşça kaydır, Odak isimleri kendisi not alır.", 13f, C.MUTED),
            topDp = 12
        )
        root.add(c, topDp = 16)

        if (scans.size >= 2) {
            val a = db.scanUsers(scans[0].id)
            val b = db.scanUsers(scans[1].id)
            val gone = (b - a).sorted()
            val fresh = (a - b).sorted()
            val range = "${Fmt.dayMonth(scans[1].ts)} – ${Fmt.dayMonth(scans[0].ts)}"
            val followers = db.followHistory(1).lastOrNull()?.second
            if (followers != null && scans[0].count < followers * 0.95) {
                root.add(
                    note(R.drawable.ic_info, "Son taramada ${Fmt.num(scans[0].count)} kişi toplandı ama takipçi sayın ${Fmt.num(followers)}. Liste sonuna kadar kaydırılmamış olabilir; bazı kişiler yanlışlıkla \"çıkmış\" görünebilir."),
                    topDp = 14
                )
            }
            root.add(section("Çıkanlar · ${gone.size}"), topDp = 22)
            val gc = card()
            if (gone.isEmpty()) gc.add(empty("Bu arada kimse takipten çıkmamış."))
            gone.forEachIndexed { i, u -> gc.add(person(u, range, last = i == gone.size - 1)) }
            root.add(gc, topDp = 10)

            root.add(section("Yeni takip edenler · ${fresh.size}"), topDp = 22)
            val fcard = card()
            if (fresh.isEmpty()) fcard.add(empty("Yeni takipçi yok."))
            val shown = fresh.take(30)
            shown.forEachIndexed { i, u -> fcard.add(person(u, "yeni", last = i == shown.size - 1 && fresh.size <= 30)) }
            if (fresh.size > 30) fcard.add(empty("+${fresh.size - 30} kişi daha"))
            root.add(fcard, topDp = 10)
        } else if (scans.size == 1) {
            root.add(note(R.drawable.ic_info, "İlk tarama kaydedildi. Birkaç gün sonra tekrar tara, kimin çıktığını burada göreceksin."), topDp = 14)
        }
    }
    root.add(
        note(R.drawable.ic_lock, "İsimler yalnızca bu telefonda saklanır. Instagram şifren istenmez, Odak Instagram'a hiçbir istek göndermez."),
        topDp = 22
    )
    return root
}

private fun MainActivity.openInstagram() {
    val i = packageManager.getLaunchIntentForPackage(OdakService.IG)
    if (i != null) startActivity(i) else Toast.makeText(this, "Instagram bulunamadı", Toast.LENGTH_SHORT).show()
}

// =====================================================================
// HİKÂYE SEKMESİ
// =====================================================================

private fun viewerTime(v: Db.Viewer): String =
    if (v.prevCheck <= 0L) "en geç ${Fmt.hm(v.firstSeen)}" else "${Fmt.hm(v.prevCheck)} – ${Fmt.hm(v.firstSeen)}"

internal fun MainActivity.hikayeScreen(): View {
    val root = vertical()
    root.add(title("Hikâye", "Hikâyene bakanlar, Instagram silse bile burada kalır"))

    val st = db.latestStory()
    if (st == null) {
        val c = card()
        c.add(empty("Henüz kayıtlı hikâye yok. Bir hikâye paylaş, sonra Instagram'da kendi hikâyeni açıp alttaki görüntüleyenler (Etkinlik) listesine bak. Odak isimleri ve yaklaşık saatleri kendisi kaydeder."))
        root.add(c, topDp = 16)
        return root
    }

    val viewers = db.storyViewers(st.id)
    val newCount = if (st.lastCheck > 0L) viewers.count { it.firstSeen >= st.lastCheck } else 0
    val list10 = db.storyList(10)
    val avg = if (list10.isEmpty()) 0 else list10.sumOf { it.second } / list10.size

    val sum = card().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
    val topRow = horizontal()
    val thumb = label("hikâye", 11f, C.SUB).apply {
        gravity = Gravity.CENTER
        background = rounded(0xFFD9D6CC.toInt(), 12f)
    }
    topRow.add(thumb, width = dp(64), height = dp(96))
    val tx = vertical()
    tx.add(label("Son hikâyen · ilk kontrol ${Fmt.hm(st.started)}", 13f, C.SUB))
    tx.add(label("${viewers.size} kişi", 30f, C.INK, bold = true), topDp = 2)
    val lastTxt = if (st.lastCheck > 0L) "Son kontrol ${Fmt.hm(st.lastCheck)} · $newCount yeni" else ""
    if (lastTxt.isNotEmpty()) tx.add(label(lastTxt, 13f, C.MUTED), topDp = 2)
    topRow.add(tx, width = 0, weight = 1f).also { (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(14) }
    sum.add(topRow)
    val stats = horizontal().apply { gravity = Gravity.TOP }
    stats.add(statBox(newCount.toString(), "son kontrolde yeni", true), width = 0, weight = 1f)
    stats.add(View(this), width = dp(10), height = 1)
    stats.add(statBox(avg.toString(), "hikâye ortalaman", false), width = 0, weight = 1f)
    sum.add(stats, topDp = 14)
    root.add(sum, topDp = 16)

    root.add(section("Görenler (yaklaşık saat)"), topDp = 22)
    root.add(viewersCard(viewers, if (showAllViewers) Int.MAX_VALUE else 8), topDp = 10)
    root.add(
        label("Saatler, listene baktığın anlara göre çıkar. Ne kadar sık bakarsan o kadar kesinleşir. Aynı gün attığın hikâyeler birlikte sayılır.", 12f, C.SUB).apply {
            setPadding(dp(4), 0, dp(4), 0)
        },
        topDp = 8
    )

    // Sadık izleyiciler ve artık izlemeyenler
    val ids = db.lastStoryIds(12)
    if (ids.size >= 2) {
        val map = db.viewersOf(ids)
        val counts = HashMap<String, Int>()
        for (set in map.values) for (u in set) counts[u] = (counts[u] ?: 0) + 1
        val loyal = counts.entries.filter { it.value >= 2 }.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5)
        root.add(section("En sadık izleyicilerin"), topDp = 22)
        val lc = card()
        if (loyal.isEmpty()) lc.add(empty("Biraz daha hikâye paylaşınca burada görünecek."))
        loyal.forEachIndexed { i, e -> lc.add(person(e.key, "${e.value} / ${ids.size} hikâye", last = i == loyal.size - 1)) }
        root.add(lc, topDp = 10)

        if (ids.size >= 5) {
            val recent = HashSet<String>()
            ids.take(3).forEach { id -> map[id]?.let { recent.addAll(it) } }
            val older = HashMap<String, Int>()
            ids.drop(3).forEach { id -> map[id]?.forEach { u -> older[u] = (older[u] ?: 0) + 1 } }
            val stopped = older.entries.filter { it.value >= 2 && it.key !in recent }.sortedByDescending { it.value }.take(5)
            root.add(section("Artık izlemeyenler"), topDp = 22)
            val sc = card()
            if (stopped.isEmpty()) sc.add(empty("Herkes izlemeye devam ediyor."))
            stopped.forEachIndexed { i, e -> sc.add(person(e.key, "son 3 hikâyeye bakmadı", last = i == stopped.size - 1)) }
            root.add(sc, topDp = 10)
        }
    }

    val total = db.storyList(1000).size
    val ac = card()
    ac.add(row(R.drawable.ic_archive, "Hikâye arşivi", "$total hikâye ve görenleri kayıtlı", chevron(), last = true) { go(2, "arsiv") })
    root.add(ac, topDp = 22)
    return root
}

private fun MainActivity.viewersCard(viewers: List<Db.Viewer>, limit: Int): View {
    val c = card()
    if (viewers.isEmpty()) {
        c.add(empty("Henüz kimse kaydedilmedi. Instagram'da hikâyeni açıp görüntüleyenler listesine bak."))
        return c
    }
    val sorted = viewers.sortedByDescending { it.firstSeen }
    val shown = sorted.take(limit)
    val more = sorted.size > shown.size
    shown.forEachIndexed { i, v -> c.add(person(v.username, viewerTime(v), last = i == shown.size - 1 && !more)) }
    if (more) {
        c.add(linkRow("${sorted.size} kişinin hepsini gör") {
            showAllViewers = true
            render()
        })
    }
    return c
}

internal fun MainActivity.arsivScreen(): View {
    val root = vertical()
    root.add(backLink("Hikâye") { go(2) })
    root.add(title("Hikâye arşivi"), topDp = 8)
    val list = db.storyList(200)
    val c = card()
    if (list.isEmpty()) c.add(empty("Henüz kayıtlı hikâye yok."))
    list.forEachIndexed { i, (s, n) ->
        c.add(row(R.drawable.ic_eye, Fmt.dayMonthTime(s.started), "$n kişi gördü", chevron(), last = i == list.size - 1) {
            go(2, "story:${s.id}")
        })
    }
    root.add(c, topDp = 16)
    return root
}

internal fun MainActivity.storyDetailScreen(id: Long): View {
    val root = vertical()
    root.add(backLink("Arşiv") { go(2, "arsiv") })
    val st = db.story(id)
    if (st == null) {
        root.add(empty("Hikâye bulunamadı."))
        return root
    }
    val viewers = db.storyViewers(id)
    root.add(title("${viewers.size} kişi gördü", "İlk kontrol: ${Fmt.dayMonthTime(st.started)}"), topDp = 8)
    root.add(viewersCard(viewers, Int.MAX_VALUE), topDp = 16)
    return root
}
