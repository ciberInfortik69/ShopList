
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
import androidx.compose.runtime.DisposableEffect
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
import cib.erpipa.shoplist.screen.ListScreen
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

private const val TAG = "ShopListApp"

/**
 * Actividad principal.
 *
 * Gestiona Firebase, la autenticación con Google
 * y la interfaz principal de la aplicación.
 */
class MainActivity : ComponentActivity() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialManager = CredentialManager.create(baseContext)
        analytics = Firebase.analytics
        auth = Firebase.auth

        enableEdgeToEdge()

        setContent {
            ShopListTheme {
                ShopListApp(
                    auth = auth,
                    onSignIn = { signInWithGoogle() },
                    onSignOut = { signOut() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val user = auth.currentUser
        if (user == null) {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateUI(auth.currentUser)
                }
            }
        } else {
            updateUI(user)
        }
    }

    /**
     * Inicia el proceso de autenticación mediante Google Credential Manager.
     */
    fun signInWithGoogle() {

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )

                handleSignIn(result.credential)

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "No se pudieron obtener las credenciales: ${e.localizedMessage}",
                    e
                )
            }
        }
    }

    /**
     * Comprueba y procesa la credencial de Google recibida.
     */
    private fun handleSignIn(credential: Credential) {

        if (
            credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {

            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(credential.data)

            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)

        } else {
            Log.w(TAG, "La credencial no es de tipo Google ID.")
        }
    }

    /**
     * Inicia sesión en Firebase utilizando el token de Google,
     * vinculando las listas creadas de forma anónima a la cuenta de Google.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isAnonymous) {
            // Vincular cuenta anónima existente con las credenciales de Google
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Cuenta anónima vinculada correctamente a Google.")
                        updateUI(auth.currentUser)
                    } else {
                        Log.w(TAG, "No se pudo vincular (posible cuenta existente), iniciando sesión normalmente", task.exception)
                        auth.signInWithCredential(credential)
                            .addOnCompleteListener(this) { signInTask ->
                                if (signInTask.isSuccessful) {
                                    updateUI(auth.currentUser)
                                } else {
                                    updateUI(null)
                                }
                            }
                    }
                }
        } else {
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Inicio de sesión correcto.")
                        updateUI(auth.currentUser)
                    } else {
                        Log.w(TAG, "Error al iniciar sesión.", task.exception)
                        updateUI(null)
                    }
                }
        }
    }

    /**
     * Cierra la sesión de Firebase y limpia las credenciales.
     */
    fun signOut() {

        auth.signOut()

        lifecycleScope.launch {
            try {

                val clearRequest = ClearCredentialStateRequest()

                credentialManager.clearCredentialState(clearRequest)

                updateUI(null)

            } catch (e: ClearCredentialException) {

                Log.e(
                    TAG,
                    "No se pudieron limpiar las credenciales: ${e.localizedMessage}",
                    e
                )
            }
        }
    }

    /**
     * Crea o actualiza el perfil del usuario en Firestore.
     */
    private fun updateUI(user: FirebaseUser?) {

        if (user != null) {

            val userData = hashMapOf(
                "email" to user.email,
                "displayName" to user.displayName,
                "photoUrl" to user.photoUrl?.toString()
            )

            Firebase.firestore
                .collection("users")
                .document(user.uid)
                .set(userData, SetOptions.merge())
                .addOnFailureListener { error ->

                    Log.w(
                        TAG,
                        "Error guardando el usuario en Firestore.",
                        error
                    )
                }
        }
    }
}

/**
 * Componente principal de la aplicación.
 *
 * Gestiona las pestañas principales y la navegación
 * hacia el contenido de cada lista.
 */
@Preview(showSystemUi = true)
@Composable
fun ShopListApp(
    auth: FirebaseAuth? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {

    // Pestaña seleccionada.
    var currentDestination by rememberSaveable {
        mutableStateOf(AppDestinations.HOME)
    }

    // ID de la lista abierta.
    var selectedListId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // Owner ID de la lista abierta.
    var selectedListOwnerId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // Usuario actualmente autenticado.
    var currentUser by remember {
        mutableStateOf(auth?.currentUser)
    }

    /*
     * Listener de autenticación.
     * Se elimina cuando el componente deja de existir.
     */
    DisposableEffect(auth) {

        if (auth == null) {
            onDispose { }
        } else {

            val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                currentUser = firebaseAuth.currentUser
            }

            auth.addAuthStateListener(authListener)

            onDispose {
                auth.removeAuthStateListener(authListener)
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {

            AppDestinations.entries.forEach { destination ->

                item(
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = destination.label
                        )
                    },
                    label = {
                        Text(destination.label)
                    },
                    selected = destination == currentDestination,
                    onClick = {
                        currentDestination = destination

                        // Al cambiar de pestaña volvemos a la navegación principal.
                        selectedListId = null
                    }
                )
            }
        }
    ) {

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->

            val screenModifier = Modifier.padding(innerPadding)

            when (currentDestination) {

                AppDestinations.HOME -> {

                    if (selectedListId == null) {

                        ShopListScreen(
                            modifier = screenModifier,
                            currentUser = currentUser,
                            onOpenList = { listId, ownerId ->
                                selectedListId = listId
                                selectedListOwnerId = ownerId
                            }
                        )

                    } else {

                        ListScreen(
                            listId = selectedListId!!,
                            ownerId = selectedListOwnerId ?: "",
                            currentUser = currentUser,
                            onBack = {
                                selectedListId = null
                                selectedListOwnerId = null
                            }
                        )
                    }
                }

                AppDestinations.FAVORITES -> {

                    FavoritesScreen(
                        modifier = screenModifier
                    )
                }

                AppDestinations.PROFILE -> {

                    ProfileScreen(
                        modifier = screenModifier,
                        currentUser = currentUser,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}

/**
 * Destinos de navegación de la aplicación.
 */
enum class AppDestinations(
    val label: String,
    val icon: Int
) {

    HOME("Home", R.drawable.ic_home),

    FAVORITES("Favorites", R.drawable.ic_favorite),

    PROFILE("Profile", R.drawable.ic_account_box)
}
