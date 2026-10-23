package cib.erpipa.shoplist

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle.Companion.Italic
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var analytics: FirebaseAnalytics
    // Instancia de Firebase Auth para gestionar la sesión del usuario
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos Analytics y Firebase Authentication
        analytics = Firebase.analytics
        auth = Firebase.auth

        enableEdgeToEdge()
        setContent {
            ShopListTheme {
                // Pasamos la instancia de auth a la app de Jetpack Compose
                ShopListApp(auth = auth)
            }
        }
    }

    /**
     * Método del ciclo de vida que se ejecuta cuando la app pasa a primer plano.
     */
    override fun onStart() {
        super.onStart()
        // Verificar si el usuario ya tiene una sesión activa al abrir la app
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    /**
     * Lanza la interfaz nativa de Credential Manager de Android para iniciar sesión con Google
     */
    fun signInWithGoogle() {
        // Configura las opciones para solicitar la cuenta de Google
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        // Construye la petición de credenciales
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        // Asíncronamente (en una corrutina), solicita la cuenta al usuario
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )
                // Procesa la credencial obtenida
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "No se pudieron obtener las credenciales: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Procesa la credencial recibida de Credential Manager y extrae el token de Google
     */
    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            // Autentica en Firebase usando el token obtenido de Google
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(TAG, "¡La credencial no es de Google ID!")
        }
    }

    /**
     * Autentica la cuenta en Firebase Auth usando el ID Token recibido de Google
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:éxito")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    Log.w(TAG, "signInWithCredential:fallo", task.exception)
                    updateUI(null)
                }
            }
    }

    /**
     * Cierra la sesión activa en Firebase y limpia el estado de las credenciales de Google
     */
    fun signOut() {
        auth.signOut()

        lifecycleScope.launch {
            try {
                val clearRequest = ClearCredentialStateRequest()
                credentialManager.clearCredentialState(clearRequest)
                updateUI(null)
            } catch (e: ClearCredentialException) {
                Log.e(TAG, "No se pudieron limpiar las credenciales: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Método auxiliar para actualizar la interfaz si fuera necesario tras un cambio de usuario
     */
    private fun updateUI(user: FirebaseUser?) {
        // Método para actualizar la UI cuando cambie el estado del usuario si fuese necesario
    }
}

@PreviewScreenSizes
@Composable
fun ShopListApp(auth: FirebaseAuth? = null) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    // Estado reactivo que mantiene la información del usuario logueado en tiempo real
    var currentUser by remember { mutableStateOf(auth?.currentUser) }

    // Listener que detecta automáticamente si el usuario inicia o cierra sesión en Firebase
    LaunchedEffect(auth) {
        auth?.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {

                // =========================================================================
                // SECCIÓN DINÁMICA DE AUTENTICACIÓN Y ESTADO DE SESIÓN
                // =========================================================================
                if (currentUser != null) {
                    // --- CASO 1: USUARIO CON SESIÓN INICIADA (MOSTRAR NOMBRE Y BOTÓN CERRAR SESIÓN) ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Hola, ${currentUser?.displayName ?: "Usuario"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            currentUser?.email?.let { email ->
                                Text(
                                    text = email,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Botón para cerrar sesión en Firebase Auth
                        OutlinedButton(
                            onClick = {
                                auth?.signOut()
                                currentUser = null
                                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Cerrar sesión")
                        }
                    }
                } else {
                    // --- CASO 2: USUARIO SIN SESIÓN (MOSTRAR BOTÓN DE INICIAR SESIÓN CON GOOGLE) ---
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    // 1. Configuración de la opción de inicio de sesión con Google
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setServerClientId("242221971105-ekp51thsmg1sqq83q182p5tjengot89m.apps.googleusercontent.com")
                                        .setFilterByAuthorizedAccounts(false)
                                        .build()

                                    // 2. Creación de la solicitud para Credential Manager de AndroidX
                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    // 3. Mostramos la interfaz emergente nativa para seleccionar la cuenta
                                    val result = credentialManager.getCredential(
                                        context = context,
                                        request = request
                                    )

                                    // 4. Autenticación en Firebase con el token recibido
                                    if (auth != null) {
                                        handleSignInResult(
                                            result = result,
                                            auth = auth,
                                            onSuccess = { user ->
                                                currentUser = user
                                                Toast.makeText(context, "¡Bienvenido, ${user.displayName ?: ""}!", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { errorMsg ->
                                                Log.e("ShopListApp", "Error al autenticar con Firebase: $errorMsg")
                                                Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                } catch (e: GetCredentialException) {
                                    Log.e("ShopListApp", "Error en Credential Manager: ${e.message}", e)
                                } catch (e: Exception) {
                                    Log.e("ShopListApp", "Error inesperado: ${e.message}", e)
                                }
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Iniciar sesión con Google")
                    }
                }

                ShopListScreen()
            }
        }
    }
}

/**
 * Función auxiliar para autenticar en Firebase Authentication usando la credencial obtenida de Google.
 */
private fun handleSignInResult(
    result: GetCredentialResponse,
    auth: FirebaseAuth,
    onSuccess: (FirebaseUser) -> Unit,
    onError: (String) -> Unit
) {
    val credential = result.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        // Extraemos el Token ID enviado por Google
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken

        // Creamos la credencial de Firebase a partir del ID Token de Google
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        // Autenticamos en Firebase Auth
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        Log.d("ShopListApp", "Autenticación exitosa en Firebase para: ${user.email}")
                        onSuccess(user)
                    } else {
                        onError("No se pudo obtener la información del usuario")
                    }
                } else {
                    val errorMsg = task.exception?.message ?: "Falló la autenticación en Firebase"
                    Log.e("ShopListApp", "Error en signInWithCredential: $errorMsg")
                    onError(errorMsg)
                }
            }
    } else {
        onError("Tipo de credencial no válido para Google")
    }
}

@Composable
fun ShopListScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Lista de listas - Valentin",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontStyle = Italic
        )

        Text(
            text = "List of the buying",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )

    }
}

// =============================================================================
// SECCIÓN 4: MODELOS DE NAVEGACIÓN Y DESTINOS
// =============================================================================

/**
 * Enum que define los tres destinos o pestañas principales de la barra de navegación:
 * Home (Inicio), Favorites (Favoritos) y Profile (Perfil).
 *
 * @param label Texto visible en la barra de navegación.
 * @param icon Recurso de imagen/icono asociado a la pestaña.dff
 */
enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShopListTheme {
        ShopListScreen()
    }
}