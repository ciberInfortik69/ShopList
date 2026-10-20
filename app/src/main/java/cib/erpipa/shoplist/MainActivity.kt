package cib.erpipa.shoplist

import android.R.attr.text
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontStyle.Companion.Italic
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
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

    /**
     * Modelo de datos que representa una lista de compras individual.
     * @param id Identificador único numérico de la lista.
     * @param name Nombre o título dado a la lista.
     * @param products Lista de nombres de productos que contiene la lista.
     */
    data class ShoppingList(
        val id: Int,
        val name: String,
        val products: List<String> = emptyList()
    )

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
            Log.w(TAG, "¡La credencial no es de tipo Google ID!")
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
            // Muestra la pantalla de listas de compras aplicando el margen superior/inferior
            ShopListScreen(
                modifier = Modifier.padding(innerPadding),
                currentUser = currentUser,
                onSignIn = onSignIn,
                onSignOut = onSignOut
            )
        }
    }
}

// =============================================================================
// SECCIÓN 3: PANTALLAS Y COMPONENTES VISUALES (UI)
// =============================================================================

/**
 * Pantalla que muestra el listado de listas creadas, permite crear una nueva lista mediante un diálogo
 * e incluye el botón para iniciar o cerrar sesión.
 */
@Composable
fun ShopListScreen(
    modifier: Modifier = Modifier,
    currentUser: FirebaseUser? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    // Estado que almacena la lista de nombres de las listas creadas por el usuario
    var lists by remember {
        mutableStateOf(listOf<String>())
    }

    // Estado booleano para controlar si se muestra o se oculta el cuadro de diálogo
    var showDialog by remember {
        mutableStateOf(false)
    }

    // Estado que guarda el texto tipeado por el usuario en el cuadro de diálogo
    var listName by remember {
        mutableStateOf("")
    }

    // Columna principal que organiza todos los elementos verticalmente
    Column(modifier = modifier.padding(16.dp)) {

        // Tarjeta con el título principal de la pantalla
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "List of lists",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    fontStyle = Italic
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta de subtítulo "Listas creadas:"
        Card(modifier = Modifier.fillMaxWidth()){

            Text(text =" Listas creadas:",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontStyle = Italic
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        // Tarjeta contenedora para el botón "Crear nueva lista" y las listas agregadas
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                // Botón para abrir el cuadro de diálogo de creación de lista
                Button(
                    onClick = {
                        listName = "" // Limpia el texto previo
                        showDialog = true // Muestra el cuadro de diálogo
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear nueva lista")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Muestra dinámicamente cada una de las listas agregadas por el usuario
                lists.forEach { name ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            // Aquí se implementará el evento al tocar una lista
                        }
                    ) {
                        Text(
                            text = name,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón para Iniciar o Cerrar sesión dependiendo del estado del usuario
        Button(
            onClick = { if (currentUser == null) onSignIn() else onSignOut() }
        ) {
            Text(if (currentUser == null) "Iniciar sesión" else "Cerrar sesión")
        }
    }

    // Cuadro de diálogo emergente que se muestra únicamente cuando showDialog es true
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false // Cierra el diálogo al tocar fuera de él
            },
            title = {
                Text("Nueva lista")
            },
            text = {
                // Campo de texto donde el usuario escribe el nombre de la lista
                OutlinedTextField(
                    value = listName,
                    onValueChange = {
                        listName = it
                    },
                    label = {
                        Text("Nombre de la lista")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                // Botón para guardar la nueva lista escrita
                TextButton(
                    onClick = {
                        if (listName.isNotBlank()) {
                            lists = lists + listName.trim() // Añade la nueva lista al listado
                            showDialog = false // Cierra el diálogo
                        }
                    },
                    enabled = listName.isNotBlank() // Se habilita únicamente si el texto no está vacío
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                // Botón para cancelar la acción y cerrar el diálogo
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

}


/**
 * Previsualización sin datos reales para el diseñador de Android Studio
 */
@Preview(showBackground = true)
@Composable
fun ShopListScreenPreview() {
    ShopListTheme {
        ShopListScreen()
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