package com.shadhin.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.engine.ErrorPageMapper
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.ShadhinTheme

/**
 * Composable representation of ErrorPage for Compose UI Previews.
 */
@Composable
fun ErrorPageView(
    failingUrl: String,
    details: ErrorPageMapper.ErrorDetails,
    showHttpFallback: Boolean = false,
    onRetry: () -> Unit = {},
    onContinueHttp: () -> Unit = {},
    onGoToStartPage: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Warning",
                    tint = AccentOrange,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = details.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = failingUrl,
                    color = AccentOrange,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = details.description,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Retry", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }

                if (showHttpFallback) {
                    OutlinedButton(
                        onClick = onContinueHttp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Continue with HTTP (not secure)", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                OutlinedButton(
                    onClick = onGoToStartPage,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Go to start page", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Preview(name = "ErrorPage - Dark Warm")
@Composable
fun PreviewErrorPageDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        ErrorPageView(
            failingUrl = "https://untrusted-site.com",
            details = ErrorPageMapper.ErrorDetails(
                title = "Secure Connection Blocked",
                description = "Shadhin Browser cancelled the connection because the SSL certificate could not be securely verified.",
                isHttpsDowngradeCandidate = true
            ),
            showHttpFallback = true
        )
    }
}

@Preview(name = "ErrorPage - Light Cream")
@Composable
fun PreviewErrorPageLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        ErrorPageView(
            failingUrl = "https://slow-server.org",
            details = ErrorPageMapper.ErrorDetails(
                title = "Connection Timed Out",
                description = "The server took too long to respond (ERR_CONNECTION_TIMED_OUT). Check your network connection.",
                isHttpsDowngradeCandidate = false
            ),
            showHttpFallback = false
        )
    }
}
