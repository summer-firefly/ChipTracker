package com.chiptrack.app.share

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.chiptrack.app.model.GameSession
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
    private const val BG_COLOR = 0xFFF3F8FC.toInt()

    fun capture(
        activity: Activity,
        session: GameSession,
        onResult: (Result<Uri>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        val density = activity.resources.displayMetrics.density.coerceAtLeast(1f)
        val widthPx = (CSS_WIDTH * density).roundToInt()
        val html = buildHtml(session)
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
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            // VISIBLE + 移出屏幕，避免 INVISIBLE 导致空白图
            visibility = View.VISIBLE
            translationX = 100_000f
            // 100%：1 CSS px 对应 density 物理像素（配合宽 = CSS_WIDTH * density）
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
                }, 280)
            }
        }

        val encoded = Base64.encodeToString(
            html.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        webView.loadData(encoded, "text/html; charset=utf-8", "base64")
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

    private fun buildHtml(session: GameSession): String {
        val settled = session.phase == SessionPhase.SETTLED
        val title = if (settled) "chipTrack 本局结算表" else "chipTrack 对局实时表"
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val summary = "拿取合计 ${session.totalTakenSum} · 退还合计 ${session.totalReturnedSum}"
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
            session.players.sortedBy { it.name }
        }

        val rows = players.joinToString("\n") { p ->
            if (settled) {
                val profit = p.profit
                val profitClass = when {
                    profit > 0 -> "pos"
                    profit < 0 -> "neg"
                    else -> "zero"
                }
                val profitText = if (profit > 0) "+$profit" else "$profit"
                """
                <tr>
                  <td class="name">${escape(p.name)}</td>
                  <td class="num">${p.totalTaken}</td>
                  <td class="num">${p.totalReturned}</td>
                  <td class="num $profitClass">$profitText</td>
                </tr>
                """.trimIndent()
            } else {
                """
                <tr>
                  <td class="name">${escape(p.name)}</td>
                  <td class="num">${p.totalTaken}</td>
                  <td class="num">${p.totalReturned}</td>
                </tr>
                """.trimIndent()
            }
        }

        val profitHeader = if (settled) "<th class=\"num\">盈亏</th>" else ""
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
    background: #F3F8FC;
    color: #121212;
    font-family: sans-serif;
  }
  .page { width: ${CSS_WIDTH}px; padding: 28px; }
  .card {
    background: #FFFFFF;
    border: 1px solid #DCEAF5;
    border-radius: 20px;
    overflow: hidden;
  }
  .hero {
    padding: 28px 28px 20px;
    background: #EAF5FC;
    border-bottom: 1px solid #E3EEF6;
  }
  .brand {
    font-size: 12px;
    letter-spacing: 0.08em;
    color: #2E7CB8;
    font-weight: 700;
    margin-bottom: 10px;
  }
  h1 {
    font-size: 28px;
    line-height: 1.25;
    color: #1E6FA8;
    font-weight: 700;
    margin-bottom: 12px;
  }
  .meta { font-size: 14px; line-height: 1.55; color: #5A6B78; }
  .meta .line { margin-top: 2px; }
  .status-live { color: #2E7CB8; font-weight: 700; }
  .status-ok { color: #1B7A3D; font-weight: 700; }
  .status-bad { color: #B3261E; font-weight: 700; }
  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  thead th {
    background: #E8F3FA;
    color: #5A6B78;
    font-size: 13px;
    font-weight: 700;
    text-align: left;
    padding: 14px 18px;
    border-bottom: 1px solid #DCEAF5;
  }
  thead th.num { text-align: right; }
  tbody td {
    padding: 16px 18px;
    font-size: 16px;
    border-bottom: 1px solid #EEF4F8;
    word-break: break-all;
  }
  tbody tr:nth-child(even) { background: #F7FBFE; }
  td.name { font-weight: 700; color: #121212; width: 34%; }
  td.num { text-align: right; }
  .pos { color: #1B7A3D; font-weight: 700; }
  .neg { color: #B3261E; font-weight: 700; }
  .zero { color: #5A6B78; font-weight: 700; }
  .footer {
    padding: 16px 22px 20px;
    overflow: hidden;
    border-top: 1px solid #EEF4F8;
    color: #5A6B78;
    font-size: 13px;
  }
  .foot-line { float: left; }
  .logo { float: right; font-weight: 700; color: #2E7CB8; }
</style>
</head>
<body>
  <div class="page">
    <div class="card">
      <div class="hero">
        <div class="brand">CHIPTRACK</div>
        <h1>$title</h1>
        <div class="meta">
          <div class="line">$time</div>
          <div class="line">$summary</div>
          <div class="line $statusClass">$status</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>玩家</th>
            <th class="num">拿取</th>
            <th class="num">退还</th>
            $profitHeader
          </tr>
        </thead>
        <tbody>
          $rows
        </tbody>
      </table>
      <div class="footer">
        <div class="foot-line">$profitTotal</div>
        <div class="logo">chipTrack</div>
      </div>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
