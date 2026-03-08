package blinker.go.data.extension

import android.webkit.WebView

object ExtensionInjector {

    private val API_SHIM = buildString {
        append("(function(){")
        append("if(window.__blinkerExtInit)return;")
        append("window.__blinkerExtInit=true;")
        append("var _s={};")
        append("if(typeof chrome==='undefined')window.chrome={};")
        append("if(!chrome.storage)chrome.storage={};")
        append("if(!chrome.storage.local)chrome.storage.local={")
        append("get:function(k,c){var r={};")
        append("if(typeof k==='string')k=[k];")
        append("if(Array.isArray(k))k.forEach(function(x){")
        append("if(_s[x]!==undefined)r[x]=_s[x];});")
        append("else if(k===null)r=Object.assign({},_s);")
        append("if(c)c(r);return Promise.resolve(r);},")
        append("set:function(i,c){Object.assign(_s,i);")
        append("if(c)c();return Promise.resolve();},")
        append("remove:function(k,c){if(typeof k==='string')k=[k];")
        append("k.forEach(function(x){delete _s[x];});")
        append("if(c)c();return Promise.resolve();}};")
        append("if(!chrome.storage.sync)chrome.storage.sync=chrome.storage.local;")
        append("if(!chrome.runtime)chrome.runtime={};")
        append("if(!chrome.runtime.sendMessage)chrome.runtime.sendMessage=")
        append("function(m,c){if(c)c();return Promise.resolve();};")
        append("if(!chrome.runtime.onMessage)chrome.runtime.onMessage={")
        append("addListener:function(){},removeListener:function(){},")
        append("hasListener:function(){return false;}};")
        append("if(!chrome.runtime.getURL)chrome.runtime.getURL=function(p){return p;};")
        append("if(!chrome.runtime.id)chrome.runtime.id='blinker-ext';")
        append("if(!chrome.runtime.getManifest)chrome.runtime.getManifest=function(){return {};};")
        append("if(!chrome.runtime.onInstalled)chrome.runtime.onInstalled={addListener:function(c){c({reason:'install'});}};")
        append("if(!chrome.i18n)chrome.i18n={getMessage:function(m){return m;}};")
        append("if(!chrome.tabs)chrome.tabs={query:function(q,c){if(c)c([]);return Promise.resolve([]);}};")
        append("if(!chrome.extension)chrome.extension={getURL:function(p){return p;}};")
        append("if(typeof browser==='undefined')window.browser=chrome;")
        append("})();")
    }

    /**
     * Inject at document_start (called from onPageStarted)
     */
    fun injectAtStart(webView: WebView, url: String, manager: ExtensionManager) {
        val matches = manager.getContentScriptsForUrl(url)
        val startScripts = matches.filter { (_, cs) ->
            cs.runAt == "document_start"
        }
        if (startScripts.isEmpty()) return

        webView.evaluateJavascript(API_SHIM, null)

        startScripts.forEach { (ext, cs) ->
            cs.css.forEach { path ->
                manager.readFile(ext.id, path)?.let { css ->
                    injectCss(webView, css)
                }
            }
            cs.js.forEach { path ->
                manager.readFile(ext.id, path)?.let { js ->
                    injectJs(webView, js)
                }
            }
        }
    }

    /**
     * Inject at document_end and document_idle (called from onPageFinished)
     */
    fun inject(webView: WebView, url: String, manager: ExtensionManager) {
        val matches = manager.getContentScriptsForUrl(url)
        val scripts = matches.filter { (_, cs) ->
            cs.runAt != "document_start"
        }
        if (scripts.isEmpty()) return

        webView.evaluateJavascript(API_SHIM, null)

        scripts.forEach { (ext, cs) ->
            cs.css.forEach { path ->
                manager.readFile(ext.id, path)?.let { css ->
                    injectCss(webView, css)
                }
            }
            cs.js.forEach { path ->
                manager.readFile(ext.id, path)?.let { js ->
                    injectJs(webView, js)
                }
            }
        }
    }

    /**
     * Inject API shim only (for options pages)
     */
    fun injectApiShim(webView: WebView) {
        webView.evaluateJavascript(API_SHIM, null)
    }

    private fun injectCss(webView: WebView, css: String) {
        val escaped = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        val script = "(function(){var s=document.createElement('style');" +
            "s.textContent='" + escaped + "';" +
            "(document.head||document.documentElement).appendChild(s);})()"
        webView.evaluateJavascript(script, null)
    }

    private fun injectJs(webView: WebView, js: String) {
        val wrapped = "(function(){try{\n$js\n}catch(e){console.error('Blinker ext error:',e);}})()"
        webView.evaluateJavascript(wrapped, null)
    }
}
