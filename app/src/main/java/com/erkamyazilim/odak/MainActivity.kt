package com.erkamyazilim.odak

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    internal lateinit var prefs: Prefs
    internal lateinit var db: Db

    /** 0 = Odak, 1 = Analiz, 2 = Hikâye */
    internal var tab = 0

    /** Alt ekran: "takipci", "arsiv", "story:<id>" ya da null. */
    internal var sub: String? = null
    internal var weekRange = false
    internal var showAllViewers = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        db = Db.get(this)
        tab = intent?.getIntExtra("tab", 0) ?: 0
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && intent.hasExtra("tab")) {
            tab = intent.getIntExtra("tab", 0)
            sub = null
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val s = sub
        when {
            s != null && s.startsWith("story:") -> { sub = "arsiv"; render() }
            s != null -> { sub = null; render() }
            tab != 0 -> { tab = 0; render() }
            else -> super.onBackPressed()
        }
    }

    internal fun render() {
        if (!serviceEnabled()) {
            setScreen(permissionScreen(), withTabs = false)
            return
        }
        val s = sub
        val content = try {
            when {
                s == "takipci" -> takipciScreen()
                s == "arsiv" -> arsivScreen()
                s != null && s.startsWith("story:") -> storyDetailScreen(s.substringAfter("story:").toLongOrNull() ?: 0L)
                tab == 1 -> analizScreen()
                tab == 2 -> hikayeScreen()
                else -> odakScreen()
            }
        } catch (e: Exception) {
            label("Bir hata oldu: ${e.message}", 14f, C.MUTED)
        }
        setScreen(content, withTabs = true)
    }

    internal fun go(newTab: Int, newSub: String? = null) {
        tab = newTab
        sub = newSub
        showAllViewers = false
        render()
    }

    private fun setScreen(content: View, withTabs: Boolean) {
        val root = vertical().apply { setBackgroundColor(C.BG) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        root.add(scroll, height = 0, weight = 1f)
        val bar = if (withTabs) tabBar() else null
        if (bar != null) root.add(bar)
        val side = dp(20)
        scroll.setPadding(side, dp(40), side, dp(24))
        root.setOnApplyWindowInsetsListener { _, insets ->
            var top = 0
            var bottom = 0
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars())
                top = i.top
                bottom = i.bottom
            } else {
                top = legacyTop(insets)
                bottom = legacyBottom(insets)
            }
            scroll.setPadding(side, top + dp(20), side, if (bar == null) bottom + dp(20) else dp(20))
            bar?.setPadding(dp(8), dp(8), dp(8), bottom + dp(8))
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun legacyTop(i: WindowInsets): Int = i.systemWindowInsetTop

    @Suppress("DEPRECATION")
    private fun legacyBottom(i: WindowInsets): Int = i.systemWindowInsetBottom

    private fun tabBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = LayerDrawable(arrayOf(ColorDrawable(C.LINE), ColorDrawable(C.CARD))).apply {
                setLayerInset(1, 0, dp(1), 0, 0)
            }
        }
        val items = listOf(Triple(0, R.drawable.ic_target, "Odak"), Triple(1, R.drawable.ic_chart, "Analiz"), Triple(2, R.drawable.ic_eye, "Hikâye"))
        for ((i, ic, name) in items) {
            val on = tab == i
            val col = if (on) C.TEAL else C.SUB
            val item = vertical().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
                isClickable = true
                contentDescription = name
                setOnClickListener { go(i) }
            }
            val pill = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                if (on) background = rounded(C.TEAL_SOFT, 15f)
                addView(icon(ic, col, 22))
            }
            item.add(pill, width = dp(56), height = dp(30))
            val lp = pill.layoutParams as LinearLayout.LayoutParams
            lp.gravity = Gravity.CENTER_HORIZONTAL
            item.add(label(name, 12f, col, bold = on).apply { gravity = Gravity.CENTER }, topDp = 4)
            bar.add(item, width = 0, weight = 1f)
        }
        return bar
    }

    internal fun serviceEnabled(): Boolean {
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, OdakService::class.java)
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // ---------------- Ortak parçalar ----------------

    internal fun title(t: String, subText: String? = null): View {
        val box = vertical()
        box.add(label(t, 30f, C.INK, bold = true))
        if (subText != null) box.add(label(subText, 14f, C.SUB), topDp = 4)
        return box
    }

    internal fun section(t: String): TextView =
        label(t, 15f, C.MUTED, bold = true).apply { setPadding(dp(4), 0, 0, 0) }

    internal fun card(): LinearLayout = vertical().apply { background = rounded(C.CARD, 20f, 1, C.LINE) }

    internal fun dividerBg(): LayerDrawable =
        LayerDrawable(arrayOf(ColorDrawable(C.DIVIDER), ColorDrawable(C.CARD))).apply { setLayerInset(1, 0, 0, 0, dp(1)) }

    /** Simge + başlık + alt yazı + sağda bir görünüm. */
    internal fun row(ic: Int, t: String, subText: String, right: View?, last: Boolean, onClick: (() -> Unit)? = null): LinearLayout {
        val row = horizontal().apply {
            setPadding(dp(16), dp(14), dp(14), dp(14))
            if (!last) background = dividerBg()
        }
        row.addView(iconBox(ic, C.TEAL, C.TEAL_SOFT, 40, 20, 12f))
        val texts = vertical()
        texts.add(label(t, 16f, C.INK, bold = true))
        texts.add(label(subText, 13f, C.SUB), topDp = 2)
        row.add(texts, width = 0, weight = 1f).also { (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(12) }
        if (right != null) row.add(right, width = LinearLayout.LayoutParams.WRAP_CONTENT)
        if (onClick != null) {
            row.isClickable = true
            row.setOnClickListener { onClick() }
        }
        return row
    }

    internal fun toggle(on: Boolean, dimmed: Boolean = false, onClick: () -> Unit): ToggleView =
        ToggleView(this).apply {
            this.on = on
            this.dimmed = dimmed
            setOnClickListener { onClick() }
        }

    internal fun chevron(): View = icon(R.drawable.ic_chev, C.SUB, 20)

    internal fun note(ic: Int, text: String): View {
        val n = horizontal().apply {
            gravity = Gravity.TOP
            background = rounded(C.NOTE, 16f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        n.addView(icon(ic, C.MUTED, 18))
        n.add(label(text, 13f, C.MUTED), width = 0, weight = 1f).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(10)
        }
        return n
    }

    internal fun backLink(text: String, onClick: () -> Unit): View {
        val r = horizontal().apply {
            isClickable = true
            setOnClickListener { onClick() }
            minimumHeight = dp(44)
        }
        r.addView(icon(R.drawable.ic_back, C.TEAL, 20))
        r.add(label(text, 15f, C.TEAL, bold = true), width = LinearLayout.LayoutParams.WRAP_CONTENT).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(6)
        }
        return r
    }

    internal fun primaryButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = rounded(C.TEAL, 27f)
        isClickable = true
        setOnClickListener { onClick() }
    }

    internal fun outlineButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(C.TEAL)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = rounded(C.CARD, 20f, 1, C.TEAL)
        setPadding(dp(14), 0, dp(14), 0)
        minHeight = dp(40)
        minWidth = dp(88)
        isClickable = true
        setOnClickListener { onClick() }
    }

    internal fun secondaryButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(C.MUTED)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private val avatarColors = intArrayOf(
        0xFF0F766E.toInt(), 0xFFB45309.toInt(), 0xFF1D4ED8.toInt(), 0xFF7C3AED.toInt(), 0xFFBE185D.toInt(), 0xFF4B5563.toInt()
    )

    internal fun person(username: String, right: String, last: Boolean): View {
        val row = horizontal().apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (!last) background = dividerBg()
        }
        val letter = username.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        val av = label(letter, 15f, 0xFFFFFFFF.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(avatarColors[Math.floorMod(username.hashCode(), avatarColors.size)], 20f)
        }
        row.add(av, width = dp(40), height = dp(40))
        row.add(label("@$username", 15f, C.INK, bold = true), width = 0, weight = 1f).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(12)
        }
        row.add(label(right, 13f, C.MUTED).apply { gravity = Gravity.END }, width = LinearLayout.LayoutParams.WRAP_CONTENT)
        return row
    }

    internal fun linkRow(text: String, last: Boolean = true, onClick: () -> Unit): View {
        val r = horizontal().apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            if (!last) background = dividerBg()
            isClickable = true
            setOnClickListener { onClick() }
        }
        r.add(label(text, 14f, C.TEAL, bold = true), width = 0, weight = 1f)
        r.addView(icon(R.drawable.ic_chev, C.TEAL, 18))
        return r
    }

    internal fun statBox(value: String, caption: String, highlight: Boolean): View {
        val b = vertical().apply {
            background = rounded(if (highlight) C.TEAL_SOFT else C.BG, 14f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        b.add(label(value, 26f, if (highlight) C.TEAL else C.INK, bold = true))
        b.add(label(caption, 13f, if (highlight) 0xFF2B4F4B.toInt() else C.MUTED), topDp = 2)
        return b
    }

    internal fun empty(text: String): View = label(text, 14f, C.SUB).apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    // ---------------- Kilit ve onay ----------------

    /** Bir ayarı gevşetirken, kilit açıksa 30 sn bekletir. */
    internal fun guarded(loosening: Boolean, action: () -> Unit) {
        val wrapped = {
            action()
            OdakService.instance?.refresh()
            render()
        }
        if (loosening && prefs.lock) confirmWithDelay(wrapped) else wrapped()
    }

    private fun confirmWithDelay(action: () -> Unit) {
        var timer: CountDownTimer? = null
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Biraz bekle")
            .setMessage("Korumayı gevşetmek için 30 saniye beklemen gerekiyor. Gerçekten istiyor musun?")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Devam") { _, _ -> action() }
            .create()
        dlg.setOnShowListener {
            val b = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            b.isEnabled = false
            timer = object : CountDownTimer(30_000L, 1_000L) {
                override fun onTick(ms: Long) {
                    b.text = "Devam (${ms / 1000 + 1})"
                }

                override fun onFinish() {
                    b.isEnabled = true
                    b.text = "Devam"
                }
            }.start()
        }
        dlg.setOnDismissListener { timer?.cancel() }
        dlg.show()
    }

    internal fun copyDiagnostics() {
        val dump = OdakService.instance?.lastDump
        if (dump.isNullOrBlank()) {
            Toast.makeText(this, "Önce Instagram'ı açıp sorunlu ekrana git", Toast.LENGTH_LONG).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Odak ekran bilgisi", "Odak ${versionName()} / Android ${Build.VERSION.RELEASE}\n$dump"))
        Toast.makeText(this, "Kopyalandı, Claude'a yapıştırabilirsin", Toast.LENGTH_LONG).show()
    }

    internal fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    // ---------------- İzin ekranı ----------------

    private fun permissionScreen(): View {
        val root = vertical()
        root.addView(iconBox(R.drawable.ic_shield, 0xFFFFFFFF.toInt(), C.TEAL, 64, 32, 20f))
        root.add(label("Bir izin gerekiyor", 30f, C.INK, bold = true), topDp = 24)
        @Suppress("DEPRECATION")
        val intro = Html.fromHtml(
            "Odak'ın Instagram'da Reels'i bulup gizleyebilmesi için <b>Erişilebilirlik</b> iznini açman lazım. Bu sadece bir kere yapılır."
        )
        root.add(label(intro, 16f, C.MUTED), topDp = 10)

        val steps = vertical().apply {
            background = rounded(C.CARD, 20f, 1, C.LINE)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val items = listOf(
            "Aşağıdaki <b>Ayarları aç</b> düğmesine bas",
            "<b>Yüklü uygulamalar</b> → <b>Odak</b>'a gir",
            "Anahtarı aç ve <b>İzin ver</b> de. Samsung uyarı gösterirse normal.",
            "<b>“Kısıtlı ayar”</b> uyarısı çıkarsa: <b>Uygulama bilgisini aç</b> → sağ üstteki <b>⋮</b> → <b>Kısıtlı ayarlara izin ver</b>, sonra 1. adımı tekrarla."
        )
        items.forEachIndexed { i, s -> steps.add(stepRow(i + 1, s, last = i == items.size - 1)) }
        root.add(steps, topDp = 24)

        root.add(note(R.drawable.ic_lock, "Odak sadece Instagram açıkken çalışır. Mesajlarını kaydetmez, hiçbir şeyi internete göndermez."), topDp = 16)
        root.add(View(this), height = 0, weight = 1f)
        root.add(
            primaryButton("Ayarları aç") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            topDp = 24, height = dp(54)
        )
        root.add(
            secondaryButton("Uygulama bilgisini aç") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            topDp = 8, height = dp(48)
        )
        return root
    }

    private fun stepRow(n: Int, html: String, last: Boolean): View {
        val row = horizontal().apply {
            gravity = Gravity.TOP
            setPadding(0, dp(14), 0, dp(14))
            if (!last) background = dividerBg()
        }
        val badge = label(n.toString(), 14f, C.TEAL, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(C.TEAL_SOFT, 14f)
        }
        row.add(badge, width = dp(28), height = dp(28))
        @Suppress("DEPRECATION")
        val t = label(Html.fromHtml(html), 15f, C.INK).apply { setPadding(0, dp(3), 0, 0) }
        row.add(t, width = 0, weight = 1f).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(14)
        }
        return row
    }
}
