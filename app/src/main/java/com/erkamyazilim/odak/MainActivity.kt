package com.erkamyazilim.odak

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
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

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val content = if (serviceEnabled()) mainScreen() else permissionScreen()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(C.BG)
            clipToPadding = false
            addView(content)
        }
        applyInsets(scroll)
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    private fun applyInsets(v: View) {
        val side = dp(20)
        v.setPadding(side, dp(40), side, dp(24))
        v.setOnApplyWindowInsetsListener { view, insets ->
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
            view.setPadding(side, top + dp(20), side, bottom + dp(20))
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyTop(i: WindowInsets): Int = i.systemWindowInsetTop

    @Suppress("DEPRECATION")
    private fun legacyBottom(i: WindowInsets): Int = i.systemWindowInsetBottom

    private fun serviceEnabled(): Boolean {
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, OdakService::class.java)
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // ---------------- Ana ekran ----------------

    private fun mainScreen(): View {
        val root = vertical()

        // Başlık
        val header = horizontal()
        header.addView(iconBox(R.drawable.ic_target, 0xFFFFFFFF.toInt(), C.TEAL, 40, 22, 12f))
        val title = label("Odak", 26f, C.INK, bold = true)
        header.add(title, width = LinearLayout.LayoutParams.WRAP_CONTENT).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(10)
        }
        root.add(header)

        // Koruma kartı
        val on = prefs.master
        val fg = if (on) 0xFFFFFFFF.toInt() else C.INK
        val hero = vertical().apply {
            background = rounded(if (on) C.TEAL else C.OFF_CARD, 24f)
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        hero.add(label("INSTAGRAM KORUMASI", 13f, fg, bold = true).apply { letterSpacing = 0.05f; alpha = 0.85f })
        hero.add(label(if (on) "Koruma açık" else "Koruma kapalı", 30f, fg, bold = true), topDp = 6)
        hero.add(
            label(
                if (on) "Instagram'da Reels gizleniyor. Mesajlar, hikâyeler ve akış normal çalışıyor."
                else "Reels şu an görünüyor. Açmak için aşağıya bas.",
                15f, fg
            ).apply { alpha = 0.92f },
            topDp = 6
        )
        val blocked = prefs.blockedToday
        if (on && blocked > 0) {
            hero.add(label("Bugün $blocked kez Reels engellendi", 13f, fg).apply { alpha = 0.85f }, topDp = 8)
        }
        val heroBtn = TextView(this).apply {
            text = if (on) "Korumayı kapat" else "Korumayı aç"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            background = rounded(if (on) 0x29FFFFFF else C.TEAL, 24f)
            isClickable = true
            setOnClickListener { change(Prefs.KEY_MASTER, !prefs.master) }
        }
        hero.add(heroBtn, topDp = 18, height = dp(48))
        root.add(hero, topDp = 22)

        // Seçenekler
        root.add(label("Neleri engelleyelim?", 15f, C.MUTED, bold = true).apply { setPadding(dp(4), 0, 0, 0) }, topDp = 22)
        val card = vertical().apply { background = rounded(C.CARD, 20f, 1, C.LINE) }
        val rows = listOf(
            Triple(Prefs.KEY_REELS, R.drawable.ic_reels, "Reels simgesini gizle" to "Alt çubuktaki Reels düğmesi kaybolur, diğerleri ortalanır"),
            Triple(Prefs.KEY_EXPLORE, R.drawable.ic_search, "Keşfet'teki Reels'i engelle" to "Keşfet'ten açılan Reels izlenmez"),
            Triple(Prefs.KEY_DM, R.drawable.ic_chat, "DM'den gelen Reels'i engelle" to "Arkadaşın Reels atınca açılmaz"),
            Triple(Prefs.KEY_LOCK, R.drawable.ic_lock, "Kapatmayı zorlaştır" to "Korumayı kapatmak için 30 sn beklersin")
        )
        rows.forEachIndexed { i, (key, ic, texts) ->
            card.add(optionRow(key, ic, texts.first, texts.second, last = i == rows.size - 1))
        }
        root.add(card, topDp = 10)

        // Not
        root.add(note(R.drawable.ic_info, "Odak ekranındaki hiçbir şeyi kaydetmez ve internete göndermez. Sadece telefonunda, Instagram açıkken çalışır."), topDp = 22)

        // Sorun bildir
        val report = label("Çalışmıyor mu? Ekran bilgisini kopyala", 14f, C.TEAL, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            setOnClickListener { copyDiagnostics() }
        }
        root.add(report, topDp = 8)
        root.add(
            label("Instagram'da sorun olan ekranı aç, sonra buraya gelip bas ve kopyalanan yazıyı gönder.", 12f, C.SUB).apply { gravity = Gravity.CENTER },
            topDp = 0
        )
        root.add(label("Sürüm ${versionName()}", 12f, C.SUB).apply { gravity = Gravity.CENTER }, topDp = 12)
        return root
    }

    private fun optionRow(key: String, ic: Int, title: String, sub: String, last: Boolean): View {
        val row = horizontal().apply {
            setPadding(dp(16), dp(14), dp(14), dp(14))
            if (!last) {
                background = android.graphics.drawable.LayerDrawable(
                    arrayOf(
                        android.graphics.drawable.ColorDrawable(C.DIVIDER),
                        android.graphics.drawable.ColorDrawable(C.CARD)
                    )
                ).apply { setLayerInset(1, 0, 0, 0, dp(1)) }
            }
        }
        row.addView(iconBox(ic, C.TEAL, C.TEAL_SOFT, 40, 20, 12f))
        val texts = vertical()
        texts.add(label(title, 16f, C.INK, bold = true))
        texts.add(label(sub, 13f, C.SUB), topDp = 2)
        row.add(texts, width = 0, weight = 1f).also {
            (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(12)
        }
        val toggle = ToggleView(this).apply {
            val master = prefs.master
            on = if (key == Prefs.KEY_LOCK) prefs.lock else master && prefs.get(key)
            dimmed = !master && key != Prefs.KEY_LOCK
            contentDescription = title
        }
        row.add(toggle, width = LinearLayout.LayoutParams.WRAP_CONTENT)
        val click = View.OnClickListener {
            if (key != Prefs.KEY_LOCK && !prefs.master) {
                Toast.makeText(this, "Önce korumayı aç", Toast.LENGTH_SHORT).show()
            } else {
                change(key, !prefs.get(key))
            }
        }
        row.isClickable = true
        row.setOnClickListener(click)
        toggle.setOnClickListener(click)
        return row
    }

    private fun note(ic: Int, text: String): View {
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

    /** Bir ayarı değiştirir; gevşetme ise ve kilit açıksa 30 sn bekletir. */
    private fun change(key: String, value: Boolean) {
        val loosening = !value
        val apply = {
            if (key == Prefs.KEY_MASTER) prefs.master = value else prefs.set(key, value)
            OdakService.instance?.refresh()
            render()
        }
        if (loosening && prefs.lock) confirmWithDelay(apply) else apply()
    }

    private fun confirmWithDelay(action: () -> Unit) {
        var timer: CountDownTimer? = null
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Biraz bekle")
            .setMessage("Korumayı gevşetmek için 30 saniye beklemen gerekiyor. Gerçekten istiyor musun?")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kapat") { _, _ -> action() }
            .create()
        dlg.setOnShowListener {
            val b = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            b.isEnabled = false
            timer = object : CountDownTimer(30_000L, 1_000L) {
                override fun onTick(ms: Long) {
                    b.text = "Kapat (${ms / 1000 + 1})"
                }

                override fun onFinish() {
                    b.isEnabled = true
                    b.text = "Kapat"
                }
            }.start()
        }
        dlg.setOnDismissListener { timer?.cancel() }
        dlg.show()
    }

    private fun copyDiagnostics() {
        val dump = OdakService.instance?.lastDump
        if (dump.isNullOrBlank()) {
            Toast.makeText(this, "Önce Instagram'ı açıp sorunlu ekrana git", Toast.LENGTH_LONG).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Odak ekran bilgisi", "Odak ${versionName()} / Android ${Build.VERSION.RELEASE}\n$dump"))
        Toast.makeText(this, "Kopyalandı, Claude'a yapıştırabilirsin", Toast.LENGTH_LONG).show()
    }

    private fun versionName(): String = try {
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

        val spacer = View(this)
        root.add(spacer, height = 0, weight = 1f)

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
            if (!last) {
                background = android.graphics.drawable.LayerDrawable(
                    arrayOf(
                        android.graphics.drawable.ColorDrawable(C.DIVIDER),
                        android.graphics.drawable.ColorDrawable(C.CARD)
                    )
                ).apply { setLayerInset(1, 0, 0, 0, dp(1)) }
            }
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

    private fun primaryButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        background = rounded(C.TEAL, 27f)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(C.MUTED)
        isClickable = true
        setOnClickListener { onClick() }
    }
}
