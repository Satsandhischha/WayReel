package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.onGoogleSignInClicked
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.HowItWorksSheet
import com.example.ui.components.NavyGradientScaffoldBackground
import com.example.ui.components.UppercaseLabel
import com.example.ui.theme.AmberBright
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingSilent by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showHowItWorks by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSilent = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSilent = false
            },
            scope = scope
        )
    }

    NavyGradientScaffoldBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UppercaseLabel("WayReel")
                    IconButton(
                        onClick = { showHowItWorks = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("auth_how_it_works_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "How this works and its limits",
                            tint = TextSecondary
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.hero_map_journey_1795518948403),
                        contentDescription = "Amber route connecting mapped travel stops",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UppercaseLabel("Reels to mapped routes")
                        Text(
                            text = "Turn saved Reels into real day plans.",
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Paste a Reel link, caption, or screen recording. We pull every spot, pin it on an interactive map, and cluster your days by proximity.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }

                    GlassCard(elevated = true, contentPadding = PaddingValues(18.dp)) {
                        if (isCheckingSilent) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AmberBright,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Checking your account...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Text(
                                text = "Sync trips & keep offline copies",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Sign in with Google to save your itineraries and access them without signal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(14.dp))

                            if (authError != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ErrorCoral.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = authError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            AmberGradientButton(
                                text = if (isSigningIn) "Signing in..." else "Sign in with Google",
                                icon = if (isSigningIn) null else Icons.Outlined.AccountCircle,
                                enabled = !isSigningIn,
                                onClick = {
                                    isSigningIn = true
                                    authError = null
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            authError = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isSigningIn = false
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("google_sign_in_button")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showHowItWorks) {
        HowItWorksSheet(onDismiss = { showHowItWorks = false })
    }
}
