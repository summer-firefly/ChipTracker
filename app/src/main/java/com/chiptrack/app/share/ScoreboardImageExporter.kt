package com.chiptrack.app.share

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.model.Player
import com.chiptrack.app.model.PlayerStatus
import com.chiptrack.app.model.SessionPhase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 用 WebView 渲染 HTML/CSS 表格，再截成高清 PNG。
 *
 * 关键：必须把 WebView 物理宽度设为「CSS 宽度 × density」，
 * 否则高分屏上 720 CSS px 的页面只会露出左边一截。
 */
object ScoreboardImageExporter {
    /** HTML / CSS 设计宽度（css px） */
    private const val CSS_WIDTH = 720
    private const val BG_COLOR = 0xFFFFF6EB.toInt()

    fun capture(
        activity: Activity,
        session: GameSession,
        deltas: List<PlayerShareDelta> = emptyList(),
        onResult: (Result<Uri>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        val density = activity.resources.displayMetrics.density.coerceAtLeast(1f)
        val widthPx = (CSS_WIDTH * density).roundToInt()
        val html = buildHtml(session, deltas)
        val container = activity.findViewById<ViewGroup>(android.R.id.content)

        val webView = WebView(activity).apply {
            setBackgroundColor(BG_COLOR)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.allowFileAccess = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            visibility = View.VISIBLE
            translationX = 100_000f
            setInitialScale(100)
        }

        container.addView(webView, ViewGroup.LayoutParams(widthPx, widthPx))

        var finished = false
        fun finish(result: Result<Uri>) {
            if (finished) return
            finished = true
            container.post {
                runCatching {
                    container.removeView(webView)
                    webView.destroy()
                }
                onResult(result)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.postDelayed({
                    snapshotAfterLayout(view, density, widthPx, attempt = 0, onDone = ::finish)
                }, 320)
            }
        }

        // 用 asset 基址加载，便于引用分享配图
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun snapshotAfterLayout(
        webView: WebView,
        density: Float,
        widthPx: Int,
        attempt: Int,
        onDone: (Result<Uri>) -> Unit
    ) {
        webView.evaluateJavascript(
            "(function(){return Math.ceil(Math.max(" +
                "document.body.scrollHeight," +
                "document.documentElement.scrollHeight," +
                "document.body.offsetHeight," +
                "document.documentElement.offsetHeight" +
                "));})();"
        ) { rawHeight ->
            val cssHeight = rawHeight
                ?.replace("\"", "")
                ?.toFloatOrNull()
                ?.toInt()
                ?.coerceIn(280, 6000)
                ?: 640
            val heightPx = ceil(cssHeight * density).toInt().coerceAtLeast(320)

            val lp = webView.layoutParams
            lp.width = widthPx
            lp.height = heightPx
            webView.layoutParams = lp

            webView.post {
                try {
                    webView.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                    )
                    webView.layout(0, 0, widthPx, heightPx)

                    val bitmap = renderWebView(webView, widthPx, heightPx)
                    if (isMostlyBlank(bitmap) && attempt < 2) {
                        bitmap.recycle()
                        webView.postDelayed({
                            snapshotAfterLayout(webView, density, widthPx, attempt + 1, onDone)
                        }, 220)
                        return@post
                    }

                    val activity = webView.context as Activity
                    val uri = saveToCache(activity, bitmap)
                    bitmap.recycle()
                    onDone(Result.success(uri))
                } catch (t: Throwable) {
                    onDone(Result.failure(t))
                }
            }
        }
    }

    private fun renderWebView(webView: WebView, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)

        val picture = Picture()
        val recordCanvas = picture.beginRecording(width, height)
        recordCanvas.drawColor(BG_COLOR)
        webView.draw(recordCanvas)
        picture.endRecording()
        canvas.drawPicture(picture)

        if (isMostlyBlank(bitmap)) {
            canvas.drawColor(BG_COLOR)
            webView.draw(canvas)
        }
        return bitmap
    }

    private fun isMostlyBlank(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return true
        val samples = listOf(
            w / 2 to h / 8,
            w / 5 to h / 6,
            w / 2 to h / 4,
            w / 4 to h / 3,
            w * 3 / 4 to h / 3,
            w / 2 to h / 2
        )
        var nonBg = 0
        for ((x, y) in samples) {
            val c = bitmap.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            if (!isNearBg(c)) nonBg++
        }
        return nonBg == 0
    }

    private fun isNearBg(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r >= 230 && g >= 230 && b >= 230
    }

    private fun saveToCache(activity: Activity, bitmap: Bitmap): Uri {
        val dir = File(activity.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "chiptrack_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file
        )
    }

    private fun buildHtml(session: GameSession, deltas: List<PlayerShareDelta>): String {
        val settled = session.phase == SessionPhase.SETTLED
        val title = if (settled) "猫和老鼠 · 本局结算表" else "猫和老鼠 · 对局实时表"
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val deltaById = deltas.associateBy { it.player.id }
        val changed = deltas.filter { it.hasChange }
        val sumNet = session.players.sumOf { it.netTaken }
        val sumDeltaNet = changed.sumOf { it.deltaNet }

        val summary = if (settled) {
            val t = session.totalProfit
            val profitText = if (t > 0) "+$t" else "$t"
            "盈亏合计 $profitText · 拿取 ${session.totalTakenSum} · 退还 ${session.totalReturnedSum}"
        } else {
            buildString {
                append("积分合计 $sumNet")
                if (sumDeltaNet != 0) append("（${signed(sumDeltaNet)}）")
                val exitedCount = session.exitedPlayers.size
                if (exitedCount > 0) append(" · 已离桌 $exitedCount 人")
            }
        }

        val status = if (settled) {
            val gap = session.reconcileGap
            when {
                gap == 0 -> "对账：账目平衡"
                gap > 0 -> "对账：多退了 $gap"
                else -> "对账：少退了 ${abs(gap)}"
            }
        } else {
            "对局进行中"
        }
        val statusClass = when {
            !settled -> "status-live"
            session.reconcileGap == 0 -> "status-ok"
            else -> "status-bad"
        }

        val players = if (settled) {
            session.players.sortedByDescending { it.profit }
        } else {
            // 在桌在前，离桌在后；同组按名字
            session.players.sortedWith(
                compareBy<Player> { it.status != PlayerStatus.ACTIVE }
                    .thenBy { it.name }
            )
        }

        val changesBlock = if (changed.isEmpty()) {
            ""
        } else {
            val items = changed.joinToString("\n") { d ->
                val parts = mutableListOf<String>()
                if (d.isNew) parts += """<span class="tag">新加入</span>"""
                if (d.player.status == PlayerStatus.EXITED) {
                    parts += """<span class="tag-exit">离桌</span>"""
                }
                if (d.deltaNet != 0 || !d.isNew) {
                    val cls = when {
                        d.deltaNet > 0 -> "delta-up"
                        d.deltaNet < 0 -> "delta-down"
                        else -> "delta-flat"
                    }
                    parts += """<span class="$cls">${signed(d.deltaNet)}</span>"""
                }
                """
                <div class="change-item">
                  <span class="change-name">${escape(d.player.name)}</span>
                  <span class="change-detail">${parts.joinToString(" ")}</span>
                </div>
                """.trimIndent()
            }
            """
            <div class="changes">
              <div class="changes-title">自上次分享后的变动</div>
              $items
            </div>
            """.trimIndent()
        }

        val rows = players.joinToString("\n") { p ->
            val d = deltaById[p.id]
            val exited = p.status == PlayerStatus.EXITED
            val changedRow = d?.hasChange == true
            val rowClass = when {
                exited && changedRow -> """ class="changed exited""""
                exited -> """ class="exited""""
                changedRow -> """ class="changed""""
                else -> ""
            }
            val nameExtra = buildString {
                if (exited) append(""" <span class="tag-exit">离桌</span>""")
                when {
                    d?.isNew == true -> append(""" <span class="tag">新</span>""")
                    changedRow -> append(""" <span class="tag">变</span>""")
                }
            }

            if (settled) {
                val profit = p.profit
                val profitClass = when {
                    profit > 0 -> "pos"
                    profit < 0 -> "neg"
                    else -> "zero"
                }
                val profitText = if (profit > 0) "+$profit" else "$profit"
                """
                <tr$rowClass>
                  <td class="name">${escape(p.name)}$nameExtra</td>
                  <td class="num $profitClass">$profitText</td>
                </tr>
                """.trimIndent()
            } else {
                val net = p.netTaken
                val deltaNet = d?.deltaNet ?: 0
                val deltaCls = when {
                    deltaNet > 0 -> "delta-up"
                    deltaNet < 0 -> "delta-down"
                    else -> "delta-flat"
                }
                val valueCell = amountWithDelta(net, deltaNet, deltaCls)
                """
                <tr$rowClass>
                  <td class="name">${escape(p.name)}$nameExtra</td>
                  <td class="num">$valueCell</td>
                </tr>
                """.trimIndent()
            }
        }

        val valueHeader = if (settled) {
            """<th class="num">盈亏</th>"""
        } else {
            """<th class="num">积分</th>"""
        }

        val profitTotal = if (settled) {
            val t = session.totalProfit
            val cls = when {
                t > 0 -> "pos"
                t < 0 -> "neg"
                else -> "zero"
            }
            val text = if (t > 0) "+$t" else "$t"
            """盈亏合计 <span class="$cls">$text</span>"""
        } else {
            ""
        }

        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=$CSS_WIDTH, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  html, body {
    width: ${CSS_WIDTH}px;
    min-height: 100%;
    background: #FFF6EB;
    color: #121212;
    font-family: sans-serif;
  }
  .page {
    width: ${CSS_WIDTH}px;
    padding: 28px;
    background:
      linear-gradient(180deg, rgba(255,246,235,0.92) 0%, rgba(255,246,235,0.96) 100%),
      url('share_header.png') top center / 100% auto no-repeat,
      #FFF6EB;
  }
  .card {
    background: rgba(255,255,255,0.94);
    border: 1px solid #F0E0CC;
    border-radius: 20px;
    overflow: hidden;
  }
  .hero {
    padding: 22px 28px 18px;
    background: linear-gradient(180deg, #FFE7C8 0%, #FFF8F0 100%);
    border-bottom: 1px solid #F0E0CC;
  }
  .brand-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }
  .brand-logo {
    width: 36px;
    height: 36px;
    border-radius: 10px;
  }
  .brand {
    font-size: 12px;
    letter-spacing: 0.06em;
    color: #C45500;
    font-weight: 700;
  }
  h1 {
    font-size: 26px;
    line-height: 1.25;
    color: #A84500;
    font-weight: 700;
    margin-bottom: 12px;
  }
  .meta { font-size: 14px; line-height: 1.55; color: #6B5A4A; }
  .meta .line { margin-top: 2px; }
  .status-live { color: #C45500; font-weight: 700; }
  .status-ok { color: #1B7A3D; font-weight: 700; }
  .status-bad { color: #B3261E; font-weight: 700; }
  .changes {
    margin: 0;
    padding: 16px 22px;
    background: #FFF1D6;
    border-bottom: 1px solid #F0E2C4;
  }
  .changes-title {
    font-size: 13px;
    font-weight: 700;
    color: #9A6B16;
    margin-bottom: 10px;
  }
  .change-item {
    font-size: 15px;
    line-height: 1.55;
    color: #121212;
    margin-top: 4px;
  }
  .change-name { font-weight: 700; margin-right: 8px; }
  .change-detail { color: #334155; }
  .tag {
    display: inline-block;
    font-size: 11px;
    font-weight: 700;
    color: #9A6B16;
    background: #FFE7B0;
    border-radius: 4px;
    padding: 1px 6px;
    vertical-align: middle;
  }
  .tag-exit {
    display: inline-block;
    font-size: 11px;
    font-weight: 700;
    color: #5A6B78;
    background: #E8EEF2;
    border-radius: 4px;
    padding: 1px 6px;
    vertical-align: middle;
  }
  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  thead th {
    background: #FFE8D0;
    color: #6B5A4A;
    font-size: 13px;
    font-weight: 700;
    text-align: left;
    padding: 14px 22px;
    border-bottom: 1px solid #F0E0CC;
  }
  thead th.num { text-align: right; width: 42%; }
  tbody td {
    padding: 16px 22px;
    font-size: 18px;
    border-bottom: 1px solid #F5EADF;
    word-break: break-all;
    vertical-align: middle;
  }
  tbody tr:nth-child(even) { background: #FFF9F2; }
  tbody tr.changed { background: #FFF4D6 !important; }
  tbody tr.exited { opacity: 0.72; }
  tbody tr.exited td.name { color: #6B5A4A; }
  td.name { font-weight: 700; color: #121212; }
  td.num { text-align: right; font-weight: 700; }
  .amount-wrap { display: inline-block; text-align: right; }
  .delta {
    display: block;
    font-size: 13px;
    font-weight: 700;
    margin-top: 2px;
  }
  .delta-up { color: #C45500; font-weight: 700; }
  .delta-down { color: #1B7A3D; font-weight: 700; }
  .delta-flat { color: #6B5A4A; font-weight: 700; }
  .pos { color: #1B7A3D; font-weight: 700; }
  .neg { color: #B3261E; font-weight: 700; }
  .zero { color: #6B5A4A; font-weight: 700; }
  .footer {
    padding: 16px 22px 20px;
    overflow: hidden;
    border-top: 1px solid #F5EADF;
    color: #6B5A4A;
    font-size: 13px;
  }
  .foot-line { float: left; }
  .logo { float: right; font-weight: 700; color: #C45500; }
</style>
</head>
<body>
  <div class="page">
    <div class="card">
      <div class="hero">
        <div class="brand-row">
          <img class="brand-logo" src="share_logo.png" alt="" />
          <div class="brand">猫和老鼠计分器</div>
        </div>
        <h1>$title</h1>
        <div class="meta">
          <div class="line">$time</div>
          <div class="line">$summary</div>
          <div class="line $statusClass">$status</div>
        </div>
      </div>
      $changesBlock
      <table>
        <thead>
          <tr>
            <th>玩家</th>
            $valueHeader
          </tr>
        </thead>
        <tbody>
          $rows
        </tbody>
      </table>
      <div class="footer">
        <div class="foot-line">$profitTotal</div>
        <div class="logo">猫和老鼠计分器</div>
      </div>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
    }

    private fun amountWithDelta(total: Int, delta: Int, deltaClass: String): String {
        if (delta == 0) return "$total"
        return """
          <span class="amount-wrap">
            $total
            <span class="delta $deltaClass">${signed(delta)}</span>
          </span>
        """.trimIndent()
    }

    private fun signed(value: Int): String =
        if (value > 0) "+$value" else "$value"

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
