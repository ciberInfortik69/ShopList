package cib.erpipa.shoplist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.lifecycleScope
import cib.erpipa.shoplist.screen.FavoritesScreen
import cib.erpipa.shoplist.screen.ProfileScreen
import cib.erpipa.shoplist.screen.ShopListScreen
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

// Etiqueta (TAG) utilizada para filtrar los mensajes de depuración en el Logcat de Android Studio
private const val TAG = "ShopListApp"

// =============================================================================
// SECCIÓN 1: ACTIVIDAD PRINCIPAL Y AUTENTICACIÓN (MainActivity)
// =============================================================================

/**
 * MainActivity es el punto de entrada principal de la aplicación en Android.
 * Se encarga de inicializar los servicios de Firebase y Credential Manager,
 * manejar el flujo de inicio/cierre de sesión con Google y lanzar la interfaz gráfica (Compose).
 */
class MainActivity : ComponentActivity() {

    // Variables privadas para gestionar servicios de Firebase y autenticación de Android
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    /**
     * Método que se ejecuta al crearse la actividad por primera vez.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de servicios del sistema y Firebase
        credentialManager = CredentialManager.create(baseContext)
        analytics = Firebase.analytics
        auth = Firebase.auth

        // Permite que el contenido se extienda debajo de las barras del sistema (pantalla completa)
        enableEdgeToEdge()

        // Establece la interfaz gráfica definida en Jetpack Compose
        setContent {
            ShopListTheme {
                // Llama al componente principal de la aplicación pasando la sesión y las callbacks de autenticación
                ShopListApp(
                    auth = auth,
                    onSignIn = { signInWithGoogle() },
                    onSignOut = { signOut() }
                )
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

        // Asíncronamente (en una co-rutina), solicita la cuenta al usuario
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )
                // Procesa la credencial obtenida
                handleSignIn(result.credential)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudieron obtener las credenciales: ${e.localizedMessage}", e)
            }
        }
    }

    /**
     * Procesa la credencial recibida de Credential Manager y extrae el token de Google
     */
    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            // Autentifica en Firebase usando el token obtenido de Google
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(TAG, "¡La credencial no es de tipo Google ID!")
        }
    }

    /**
     * Autentifica la cuenta en Firebase Auth usando el ID Token recibido de Google
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
     * Método auxiliar para registrar/actualizar la información del usuario en Firestore
     */
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val userData = hashMapOf(
                "email" to user.email,
                "displayName" to user.displayName,
                "photoUrl" to user.photoUrl?.toString()
            )
            // Crea o actualiza el documento del usuario en la colección "users"
            Firebase.firestore.collection("users")
                .document(user.uid)
                .set(userData, SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error guardando usuario en Firestore", e)
                }
        }
    }
}

// =============================================================================
// SECCIÓN 2: ESTRUCTURA Y NAVEGACIÓN PRINCIPAL (ShopListApp)
// =============================================================================

/**
 * Componente principal que define la estructura y navegación general de la aplicación.
 *
 * @param auth Instancia opcional de FirebaseAuth para escuchar cambios de sesión.
 * @param onSignIn Función lambda que se ejecuta para iniciar sesión.
 * @param onSignOut Función lambda que se ejecuta para cerrar sesión.
 */
@Preview(showSystemUi = true)
@Composable
fun ShopListApp(
    auth: FirebaseAuth? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    // Guarda el destino de navegación seleccionado actualmente (Home, Favorites, Profile)
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    // Estado reactivo que rastrea y guarda la sesión activa del usuario
    var currentUser by remember { mutableStateOf(auth?.currentUser) }

    // Escuchador que detecta cuando el usuario inicia o cierra sesión en Firebase
    LaunchedEffect(auth) {
        auth?.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
    }

    // Estructura contenedora que muestra la barra o menú de navegación lateral/inferior
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // Recorre los destinos definidos en la enumeración AppDestinations
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
        // Marco de la pantalla que ajusta los márgenes automáticos (insets)
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val screenModifier = Modifier.padding(innerPadding)

            // Renderiza la pantalla seleccionada según el destino actual
            when (currentDestination) {
                AppDestinations.HOME -> ShopListScreen(
                    modifier = screenModifier,
                    currentUser = currentUser
                )
                AppDestinations.FAVORITES -> FavoritesScreen(
                    modifier = screenModifier
                )
                AppDestinations.PROFILE -> ProfileScreen(
                    modifier = screenModifier,
                    currentUser = currentUser,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

// =============================================================================
// SECCIÓN 3: MODELOS DE NAVEGACIÓN Y DESTINOS
// =============================================================================

/**
 * Enum que define los tres destinos o pestañas principales de la barra de navegación:
 * Home (Inicio), Favorites (Favoritos) y Profile (Perfil).
 *
 * @param label Texto visible en la barra de navegación.
 * @param icon Recurso de imagen/icono asociado a la pestaña.
 */
enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}