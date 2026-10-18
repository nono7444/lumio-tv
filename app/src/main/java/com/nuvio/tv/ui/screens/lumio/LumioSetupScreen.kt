@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.lumio

// Lumio : configuration au premier lancement, comme sur l'iPhone.
// 1. adresse du serveur Lumio (par défaut lumio.wawanono.lol), qui donne l'adresse publique de Jellyfin ;
// 2. connexion par code (Jellyfin Quick Connect) validé depuis l'appli Lumio du téléphone,
//    ou avec identifiant et mot de passe.

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.mediaserver.ServerException
import com.nuvio.tv.data.mediaserver.ServerFailure
import com.nuvio.tv.data.mediaserver.ServerRepository
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.ui.screens.account.InputField
import com.nuvio.tv.ui.screens.settings.SettingsDialogActionButton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

enum class LumioSetupStep { SERVER, CODE, PASSWORD }

data class LumioSetupState(
    val step: LumioSetupStep = LumioSetupStep.SERVER,
    val lumioUrl: String = "lumio.wawanono.lol",
    val jellyfinUrl: String = "",
    val code: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class LumioSetupViewModel @Inject constructor(
    private val repository: ServerRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val mutable = MutableStateFlow(LumioSetupState())
    val state: StateFlow<LumioSetupState> = mutable.asStateFlow()
    private var quickJob: Job? = null

    init {
        repository.ensureLoaded()
        LumioClient(context).serverUrlOverride?.let { saved ->
            mutable.value = mutable.value.copy(lumioUrl = saved.removePrefix("https://").removePrefix("http://"))
        }
    }

    private fun normalize(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http", ignoreCase = true)) u = "https://$u"
        return u
    }

    private val jellyfinProvider get() = repository.providers.first { it.id == "jellyfin" }

    /** Étape 1 : le serveur Lumio donne l'adresse publique de Jellyfin. */
    fun continueWithServer(lumio: String, manualJellyfin: String) {
        val base = normalize(lumio)
        mutable.value = mutable.value.copy(lumioUrl = lumio, busy = true, error = null)
        viewModelScope.launch {
            val jf = manualJellyfin.takeIf { it.isNotBlank() }?.let(::normalize) ?: runCatching {
                withContext(Dispatchers.IO) {
                    val conn = URL("$base/tv/setup").openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.inputStream.bufferedReader().use { JSONObject(it.readText()).optString("jellyfinUrl") }
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (jf == null) {
                mutable.value = mutable.value.copy(
                    busy = false,
                    error = "Le serveur Lumio n'a pas donné l'adresse de Jellyfin. Tape-la ci-dessous (ex. https://jellyfin.mondomaine.fr)."
                )
                return@launch
            }
            LumioClient(context).serverUrlOverride = base
            mutable.value = mutable.value.copy(jellyfinUrl = jf, busy = false)
            startQuickConnect()
        }
    }

    /** Étape 2 : code à valider depuis le téléphone (Lumio → Profil → Connecter une TV). */
    fun startQuickConnect() {
        quickJob?.cancel()
        mutable.value = mutable.value.copy(step = LumioSetupStep.CODE, code = null, error = null, busy = true)
        quickJob = viewModelScope.launch {
            try {
                repository.connectQuick(jellyfinProvider, mutable.value.jellyfinUrl) { code ->
                    mutable.value = mutable.value.copy(code = code, busy = false)
                }
                mutable.value = mutable.value.copy(done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServerException) {
                mutable.value = mutable.value.copy(
                    busy = false,
                    error = when {
                        e.failure == ServerFailure.UNREACHABLE || e.failure == ServerFailure.NOT_FOUND ->
                            "Jellyfin injoignable (${mutable.value.jellyfinUrl})."
                        mutable.value.code != null && e.failure == ServerFailure.AUTH_REQUIRED -> "Le code a expiré. Demande un nouveau code."
                        else -> "La connexion par code est désactivée sur Jellyfin (Tableau de bord → Général → Quick Connect). Utilise le mot de passe."
                    }
                )
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = "Connexion impossible. Utilise le mot de passe.")
            }
        }
    }

    fun usePassword() {
        quickJob?.cancel()
        mutable.value = mutable.value.copy(step = LumioSetupStep.PASSWORD, error = null, busy = false)
    }

    fun back() {
        quickJob?.cancel()
        mutable.value = mutable.value.copy(step = LumioSetupStep.SERVER, error = null, busy = false, code = null)
    }

    fun signIn(user: String, password: String) {
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                repository.connect(jellyfinProvider, mutable.value.jellyfinUrl, user.trim(), password)
                mutable.value = mutable.value.copy(done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServerException) {
                mutable.value = mutable.value.copy(
                    busy = false,
                    error = if (e.failure == ServerFailure.AUTH_REQUIRED) "Identifiant ou mot de passe incorrect." else "Jellyfin injoignable."
                )
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = "Connexion impossible.")
            }
        }
    }
}

private val Accent = Color(0xFF3D8BFF)
private val Dim = Color(0xFFA1A1A6)

@Composable
fun LumioSetupScreen(onDone: () -> Unit, viewModel: LumioSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF14264A), Color(0xFF000000)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(620.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.app_logo_wordmark),
                contentDescription = "Lumio",
                modifier = Modifier.height(70.dp)
            )
            when (state.step) {
                LumioSetupStep.SERVER -> ServerStep(state, viewModel)
                LumioSetupStep.CODE -> CodeStep(state, viewModel)
                LumioSetupStep.PASSWORD -> PasswordStep(state, viewModel)
            }
            state.error?.let {
                Text(it, color = Color(0xFFFF6B60), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ServerStep(state: LumioSetupState, vm: LumioSetupViewModel) {
    var lumio by remember { mutableStateOf(state.lumioUrl) }
    var jellyfin by remember { mutableStateOf("") }
    Text("Bienvenue sur Lumio TV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text(
        "Adresse du serveur Lumio (la même que dans l'appli du téléphone).",
        color = Dim, fontSize = 16.sp, textAlign = TextAlign.Center
    )
    InputField(
        value = lumio,
        onValueChange = { lumio = it },
        placeholder = "lumio.mondomaine.fr",
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Done,
        onImeAction = { vm.continueWithServer(lumio, jellyfin) },
        modifier = Modifier.fillMaxWidth()
    )
    if (state.error != null) {
        InputField(
            value = jellyfin,
            onValueChange = { jellyfin = it },
            placeholder = "Adresse de Jellyfin (facultatif)",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            onImeAction = { vm.continueWithServer(lumio, jellyfin) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    SettingsDialogActionButton(
        text = if (state.busy) "Connexion…" else "Continuer",
        onClick = { vm.continueWithServer(lumio, jellyfin) },
        primary = true,
        enabled = !state.busy && lumio.isNotBlank()
    )
}

@Composable
private fun CodeStep(state: LumioSetupState, vm: LumioSetupViewModel) {
    Text("Connecte la TV avec ton téléphone", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Box(
        modifier = Modifier
            .background(Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 40.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = state.code?.chunked(3)?.joinToString(" ") ?: "· · ·",
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp
        )
    }
    Text(
        "Sur ton iPhone ou Android : Lumio → Profil → « Connecter une TV », puis tape ce code.",
        color = Dim, fontSize = 17.sp, textAlign = TextAlign.Center
    )
    Text("La TV se connecte toute seule dès que le code est validé.", color = Accent, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsDialogActionButton(text = "Retour", onClick = vm::back)
        SettingsDialogActionButton(text = "Nouveau code", onClick = vm::startQuickConnect)
        SettingsDialogActionButton(text = "Utiliser un mot de passe", onClick = vm::usePassword, primary = true)
    }
}

@Composable
private fun PasswordStep(state: LumioSetupState, vm: LumioSetupViewModel) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text("Connexion à Jellyfin", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(state.jellyfinUrl, color = Dim, fontSize = 15.sp)
    InputField(
        value = user,
        onValueChange = { user = it },
        placeholder = "Nom d'utilisateur",
        imeAction = ImeAction.Next,
        modifier = Modifier.fillMaxWidth()
    )
    InputField(
        value = password,
        onValueChange = { password = it },
        placeholder = "Mot de passe",
        keyboardType = KeyboardType.Password,
        isPassword = true,
        imeAction = ImeAction.Done,
        onImeAction = { vm.signIn(user, password) },
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsDialogActionButton(text = "Code", onClick = vm::startQuickConnect)
        SettingsDialogActionButton(
            text = if (state.busy) "Connexion…" else "Se connecter",
            onClick = { vm.signIn(user, password) },
            primary = true,
            enabled = !state.busy && user.isNotBlank()
        )
    }
}
