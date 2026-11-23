package com.ngdstudio.mindimania.ui.legal

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.ui.components.RoyalBackground
import com.ngdstudio.mindimania.ui.components.ScreenTitleBar
import com.ngdstudio.mindimania.ui.theme.CM

@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val isPrivacy = doc == "privacy"
    val title = if (isPrivacy) stringResource(R.string.shell_privacy) else stringResource(R.string.shell_terms)
    // same pages as the website (assets/legal/ is uploaded as-is), shown from the copy built into the app
    val url = if (isPrivacy) "file:///android_asset/legal/privacy_policy.html" else "file:///android_asset/legal/terms_conditions.html"
    var loaded by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(350), label = "web")

    RoyalBackground(rays = false) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ScreenTitleBar(title, onBack)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(CM.Midnight)
                    .border(1.5.dp, Brush.verticalGradient(listOf(CM.Gold.copy(alpha = 0.7f), CM.GoldDeep.copy(alpha = 0.2f))), RoundedCornerShape(22.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(0xFF120634.toInt())
                            isVerticalScrollBarEnabled = false
                            overScrollMode = WebView.OVER_SCROLL_NEVER
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.domStorageEnabled = false
                            settings.builtInZoomControls = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url
                                    val scheme = u.scheme?.lowercase()
                                    return when {
                                        // phone number: open the dialler with it filled in (needs no call permission)
                                        scheme == "tel" -> {
                                            runCatching {
                                                view.context.startActivity(Intent(Intent.ACTION_DIAL, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                            true
                                        }
                                        scheme == "mailto" -> {
                                            runCatching {
                                                view.context.startActivity(Intent(Intent.ACTION_SENDTO, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                            true
                                        }
                                        scheme == "http" || scheme == "https" -> {
                                            runCatching {
                                                view.context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                            true
                                        }
                                        u.toString().startsWith("file:///android_asset/") -> false
                                        else -> true
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loaded = true
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    onRelease = { it.destroy() }
                )
            }
        }
    }
}
