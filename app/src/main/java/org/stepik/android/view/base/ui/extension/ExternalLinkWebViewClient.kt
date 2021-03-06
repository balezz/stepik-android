package org.stepik.android.view.base.ui.extension

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

open class ExternalLinkWebViewClient(
    private val context: Context
) : WebViewClient() {
    @Suppress("Deprecation")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        openExternalLink(Uri.parse(url))
        return true
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        openExternalLink(request.url)
        return true
    }

    private fun openExternalLink(uri: Uri) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}