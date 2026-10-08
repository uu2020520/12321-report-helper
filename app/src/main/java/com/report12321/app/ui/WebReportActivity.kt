package com.report12321.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.report12321.app.R
import com.report12321.app.service.AutoFillAccessibilityService
import com.report12321.app.util.ReportHelperNotifier
import com.report12321.app.util.SystemBarInsets
import org.json.JSONObject
import org.json.JSONTokener

class WebReportActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val assistRunnable = Runnable { runJsAssist() }
    private var assistAttempt = 0
    private var assistFinished = false

    private val phoneNumber by lazy { intent.getStringExtra(EXTRA_PHONE).orEmpty() }
    private val smsContent by lazy { intent.getStringExtra(EXTRA_CONTENT).orEmpty() }
    private val receiveTime by lazy { intent.getStringExtra(EXTRA_TIME).orEmpty() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_report)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        // 系统栏适配：顶部让 Toolbar 延伸到状态栏下方，底部避开手势导航条
        SystemBarInsets.applyTopPadding(toolbar)
        SystemBarInsets.applyBottomPadding(findViewById(R.id.root_layout))
        SystemBarInsets.setStatusBarColor(window, this, R.color.primary, lightIcons = false)

        progressBar = findViewById(R.id.progress_bar)
        webView = findViewById(R.id.web_view)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                scheduleJsAssist()
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(AutoFillAccessibilityService.URL_12321_REPORT)
        } else {
            webView.restoreState(savedInstanceState)
        }

        Toast.makeText(this, "页面打开后会自动尝试选择垃圾短信入口并填入举报信息", Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView.removeCallbacks(assistRunnable)
        ReportHelperNotifier.dismiss(this)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    /**
     * 开始（或重新开始）JS 填表尝试
     *
     * 页面每次加载完成都会调用，因此这里重置计数与状态。
     */
    private fun scheduleJsAssist() {
        assistAttempt = 0
        assistFinished = false
        webView.removeCallbacks(assistRunnable)
        webView.postDelayed(assistRunnable, ASSIST_FIRST_DELAY_MS)
    }

    private fun runJsAssist() {
        if (assistFinished || isFinishing || isDestroyed) return

        val script = buildAssistScript(
            phone = phoneNumber,
            content = smsContent,
            time = receiveTime
        )

        webView.evaluateJavascript(script) { raw ->
            val state = AssistState.parse(raw)
            Log.d(TAG, "JS 填表尝试 ${assistAttempt + 1}: $state")
            if (state != null && state.phone && state.content) {
                finishAssist(state)
            } else {
                retryOrFinish(state)
            }
        }
    }

    private fun retryOrFinish(state: AssistState?) {
        if (assistFinished) return
        assistAttempt++
        if (assistAttempt >= MAX_ASSIST_ATTEMPTS) {
            finishAssist(state)
        } else {
            webView.postDelayed(assistRunnable, ASSIST_RETRY_DELAY_MS)
        }
    }

    private fun finishAssist(state: AssistState?) {
        if (assistFinished) return
        assistFinished = true
        webView.removeCallbacks(assistRunnable)
        Toast.makeText(this, buildAssistMessage(state), Toast.LENGTH_LONG).show()
    }

    private fun buildAssistMessage(state: AssistState?): String {
        if (state == null) {
            return "未能读取填写页状态，请用侧边助手或通知栏复制内容手动填写"
        }
        return when {
            state.phone && state.content ->
                if (state.time) "已自动填入号码、短信内容和接收时间，请补充手机号与验证码后提交"
                else "已自动填入号码和短信内容，请补充手机号、接收时间和验证码后提交"
            state.phone -> "已填入发送方号码，请手动粘贴短信内容"
            state.content -> "已填入短信内容，请手动填写发送方号码"
            else -> "未能自动识别填写页，请用侧边助手或通知栏复制内容手动填写"
        }
    }

    /**
     * JS 填表结果
     */
    private data class AssistState(
        val phone: Boolean,
        val content: Boolean,
        val time: Boolean,
        val href: String
    ) {
        companion object {
            fun parse(raw: String?): AssistState? {
                if (raw.isNullOrBlank() || raw == "null") return null
                return try {
                    // evaluateJavascript 返回的是 JSON 编码后的值。
                    // 脚本用 return JSON.stringify(...) 返回的是字符串，
                    // 所以这里拿到的是带引号的 JSON 字符串，需要再解开一层。
                    val unwrapped = JSONTokener(raw).nextValue()
                    val obj = when (unwrapped) {
                        is JSONObject -> unwrapped
                        is String -> JSONObject(unwrapped)
                        else -> return null
                    }
                    AssistState(
                        phone = obj.optBoolean("phoneFilled", false),
                        content = obj.optBoolean("contentFilled", false),
                        time = obj.optBoolean("timeFilled", false),
                        href = obj.optString("href", "")
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "解析填表结果失败: $raw", e)
                    null
                }
            }
        }
    }

    private fun buildAssistScript(phone: String, content: String, time: String): String {
        val phoneJson = JSONObject.quote(phone)
        val contentJson = JSONObject.quote(content)
        val timeJson = JSONObject.quote(time)

        return """
            (function() {
              const data = {
                phone: $phoneJson,
                content: $contentJson,
                time: $timeJson
              };

              function visible(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
              }

              function textOf(el) {
                return ((el && (el.innerText || el.textContent || el.value || el.placeholder || el.getAttribute('aria-label'))) || '').trim();
              }

              function allElements() {
                return Array.from(document.querySelectorAll('button,a,input,textarea,select,div,span,p,li'));
              }

              function clickText(candidates, reject) {
                const nodes = allElements().filter(visible);
                for (const wanted of candidates) {
                  const matched = nodes
                    .filter(el => textOf(el).includes(wanted))
                    .filter(el => !reject || !reject(textOf(el)));
                  matched.sort((a, b) => {
                    const ac = ['BUTTON', 'A'].includes(a.tagName) ? 1 : 0;
                    const bc = ['BUTTON', 'A'].includes(b.tagName) ? 1 : 0;
                    return bc - ac;
                  });
                  const target = matched[0];
                  if (target) {
                    target.click();
                    return true;
                  }
                }
                return false;
              }

              function setValue(el, value) {
                if (!el || value == null || value === '') return false;
                el.focus && el.focus();
                const proto = el.tagName === 'TEXTAREA'
                  ? window.HTMLTextAreaElement.prototype
                  : window.HTMLInputElement.prototype;
                const descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                if (descriptor && descriptor.set) {
                  descriptor.set.call(el, value);
                } else {
                  el.value = value;
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                el.dispatchEvent(new Event('blur', { bubbles: true }));
                return true;
              }

              function formControls() {
                return Array.from(document.querySelectorAll('input,textarea')).filter(visible);
              }

              function findNearbyControl(labelKeywords) {
                const labels = allElements().filter(visible).filter(el => {
                  const t = textOf(el);
                  return labelKeywords.some(k => t.includes(k));
                });
                for (const label of labels) {
                  let parent = label;
                  for (let depth = 0; parent && depth < 5; depth++, parent = parent.parentElement) {
                    const controls = Array.from(parent.querySelectorAll('input,textarea')).filter(visible);
                    const empty = controls.find(c => !c.value || c.value === c.placeholder);
                    if (empty) return empty;
                    if (controls[0]) return controls[0];
                  }
                  const rect = label.getBoundingClientRect();
                  const controls = formControls().filter(c => {
                    const cr = c.getBoundingClientRect();
                    return cr.top >= rect.top - 8 && cr.top <= rect.bottom + 90;
                  });
                  if (controls[0]) return controls[0];
                }
                return null;
              }

              function findByPlaceholder(keywords) {
                return formControls().find(el => {
                  const p = (el.placeholder || el.getAttribute('aria-label') || '').trim();
                  return keywords.some(k => p.includes(k));
                });
              }

              function fillField(labelKeywords, placeholderKeywords, value) {
                let control = findByPlaceholder(placeholderKeywords);
                if (!control) control = findNearbyControl(labelKeywords);
                return setValue(control, value);
              }

              function fillTime(value) {
                if (!value) return false;
                const normalized = value.replace(/-/g, '/').slice(0, 16);
                const control = findByPlaceholder(['请选择', '时间']) ||
                  findNearbyControl(['短信接收时间', '接收时间', '收到时间']);
                return setValue(control, normalized);
              }

              clickText(['投诉非应邀商业短信', '垃圾短信'], function(t) { return t.includes('违法') || t.includes('举报指南'); });
              clickText(['我同意'], function(t) { return t.includes('不同意'); });

              const phoneFilled = fillField(
                ['发送方号码', '发送垃圾短信的号码', '垃圾短信的号码'],
                ['发送垃圾短信的号码', '发送方号码'],
                data.phone
              );
              const contentFilled = fillField(
                ['长按粘贴短信内容', '短信内容', '投诉内容', '举报内容'],
                ['短信内容', '内容至少15个字', '长按粘贴短信内容'],
                data.content
              );
              const timeFilled = fillTime(data.time);

              return JSON.stringify({ phoneFilled, contentFilled, timeFilled, href: location.href });
            })();
        """.trimIndent()
    }

    companion object {
        private const val TAG = "WebReportActivity"

        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_TIME = "extra_time"

        private const val MAX_ASSIST_ATTEMPTS = 6
        private const val ASSIST_FIRST_DELAY_MS = 600L
        private const val ASSIST_RETRY_DELAY_MS = 1500L
    }
}
