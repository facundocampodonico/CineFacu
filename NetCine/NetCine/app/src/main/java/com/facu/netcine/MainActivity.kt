package com.facu.netcine

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast

/**
 * Pantalla principal: muestra el catálogo del proveedor (que ya está pensado para el control
 * remoto) y, cuando elegís "Reproducir", abre el video en el reproductor nativo.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var loading: View
    private lateinit var errorView: View

    private val base = BuildConfig.BASE_URL.trimEnd('/')
    private val home = "$base/?index"
    private var lastBackPress = 0L
    private var lastPlayerOpen = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        loading = findViewById(R.id.loading)
        errorView = findViewById(R.id.error)

        findViewById<Button>(R.id.retry).setOnClickListener {
            errorView.visibility = View.GONE
            web.loadUrl(home)
            web.requestFocus()
        }

        CookieManager.getInstance().setAcceptCookie(true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.setBackgroundColor(0xFF262626.toInt())
        web.webChromeClient = WebChromeClient()
        web.webViewClient = Client()

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(home)
        }
        web.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) handleBack()
            return true
        }
        // La tecla Menú del control abre el menú lateral de la web.
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
            web.evaluateJavascript("try{document.getElementById('Openmenu').click()}catch(e){}", null)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBack() {
        if (errorView.visibility == View.VISIBLE) { finish(); return }
        // Si el menú lateral está abierto, Atrás lo cierra.
        web.evaluateJavascript(
            "(function(){var m=document.getElementById('Menu');" +
                "if(m&&m.style.display==='block'){m.style.display='none';" +
                "try{document.getElementById('Contenido').style.margin='75px 5px 20px 5px'}catch(e){}" +
                "try{document.getElementById('Openmenu').focus()}catch(e){}return 1}return 0})()"
        ) { closedMenu ->
            if (closedMenu == "1") return@evaluateJavascript
            when {
                web.canGoBack() -> web.goBack()
                SystemClock.elapsedRealtime() - lastBackPress < 2500 -> finish()
                else -> {
                    lastBackPress = SystemClock.elapsedRealtime()
                    Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isWatch(url: String) = url.contains("?watch=") || url.contains("&watch=")

    private fun openPlayer(url: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayerOpen < 1500) return
        lastPlayerOpen = now
        CookieManager.getInstance().flush()
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, url)
                .putExtra(PlayerActivity.EXTRA_UA, web.settings.userAgentString)
        )
    }

    private fun showError() {
        loading.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        findViewById<View>(R.id.retry).requestFocus()
    }

    private inner class Client : WebViewClient() {

        private fun route(url: String): Boolean {
            if (isWatch(url)) { openPlayer(url); return true }
            // Todo lo que no sea el catálogo (publicidad, links externos) se ignora.
            return !url.startsWith(base)
        }

        @Deprecated("API < 24")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = route(url)

        @TargetApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            if (request.isForMainFrame) route(request.url.toString()) else false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            // Red de seguridad por si alguna versión de WebView no avisó antes de navegar.
            if (isWatch(url)) {
                view.stopLoading()
                openPlayer(url)
                if (view.canGoBack()) view.goBack()
                return
            }
            loading.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            loading.visibility = View.GONE
            if (url.startsWith(base)) view.evaluateJavascript(TV_TWEAKS, null)
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showError()
        }

        @Deprecated("API < 23")
        override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
            showError()
        }
    }

    companion object {
        /** Retoques visuales para usar la web cómodamente desde el sillón. */
        private const val TV_TWEAKS = """
(function(){
  if (window.__netcine) return; window.__netcine = 1;
  var css = document.createElement('style');
  css.textContent =
    'a:focus,button:focus,input:focus{outline:4px solid #cd6a33 !important;outline-offset:2px;}' +
    'a[href*="downapp"]{display:none !important;}' +
    '::-webkit-scrollbar{display:none;}' +
    '.w3-actors-tooltip img{display:none !important;}';
  (document.head || document.documentElement).appendChild(css);

  // Que el elemento con foco siempre quede a la vista (la barra de arriba tapa 75px).
  document.addEventListener('focusin', function(e){
    var el = e.target; if (!el || !el.getBoundingClientRect) return;
    if (el.closest && el.closest('.w3-top')) return;
    var r = el.getBoundingClientRect(), h = window.innerHeight;
    if (r.top < 90 || r.bottom > h - 20) {
      var dy = r.top - (h - r.height) / 2;
      try { window.scrollBy({top: dy, behavior: 'smooth'}); } catch (_) { window.scrollBy(0, dy); }
    }
  }, true);

  // Arrancar con foco en el primer contenido, no en la nada.
  var a = document.activeElement;
  if (!a || a === document.body) {
    var first = document.querySelector('#Contenido a.w3-btn-play, #Contenido a.w3-items, #Contenido a.w3-episode, #Contenido a[href]')
             || document.getElementById('focusindex');
    if (first) first.focus();
  }
})();
"""
    }
}
