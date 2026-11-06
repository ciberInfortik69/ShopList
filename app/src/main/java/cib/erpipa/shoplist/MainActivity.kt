
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
        updateUI(user)
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
     * sincronizando las listas locales ("local_user") con la cuenta de Google.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Inicio de sesión correcto con Google.")
                    val newGoogleUser = auth.currentUser
                    if (newGoogleUser != null) {
                        syncLocalListsToGoogle(newGoogleUser.uid) {
                            updateUI(newGoogleUser)
                        }
                    } else {
                        updateUI(null)
                    }
                } else {
                    Log.w(TAG, "Error al iniciar sesión con Google.", task.exception)
                    updateUI(null)
                }
            }
    }

    /**
     * Sincroniza las listas locales ("local_user") con la cuenta autenticada de Google:
     * - Sube o actualiza las listas creadas/modificadas en local.
     * - Elimina de la nube aquellas listas que fueron borradas en local mientras se estaba sin sesión.
     */
    private fun syncLocalListsToGoogle(googleUid: String, onComplete: () -> Unit) {
        if (googleUid.isBlank()) {
            onComplete()
            return
        }

        val db = Firebase.firestore
        val localListsRef = db.collection("users").document("local_user").collection("lists")
        val localMembershipsRef = db.collection("users").document("local_user").collection("memberships")
        val cloudListsRef = db.collection("users").document(googleUid).collection("lists")
        val cloudMembershipsRef = db.collection("users").document(googleUid).collection("memberships")

        localListsRef.get().addOnSuccessListener { localSnapshot ->
            cloudListsRef.get().addOnSuccessListener { cloudSnapshot ->
                val batch = db.batch()

                val localDocsMap = localSnapshot.documents.associateBy { it.id }

                // 1. Procesar listas locales (nuevas o actualizadas)
                localSnapshot.documents.forEach { doc ->
                    val listData = doc.data?.toMutableMap() ?: mutableMapOf()
                    listData["ownerId"] = googleUid

                    val newListRef = cloudListsRef.document(doc.id)
                    val newMembershipRef = cloudMembershipsRef.document(doc.id)

                    batch.set(newListRef, listData)
                    batch.set(newMembershipRef, mapOf(
                        "listId" to doc.id,
                        "ownerId" to googleUid,
                        "role" to "owner",
                        "joinedAt" to System.currentTimeMillis()
                    ))

                    // Eliminar copia local para que pasen a la nube del usuario
                    batch.delete(doc.reference)
                    batch.delete(localMembershipsRef.document(doc.id))
                }

                // 2. Procesar listas en la nube que ya no están en local (eliminadas en modo local)
                cloudSnapshot.documents.forEach { cloudDoc ->
                    if (!localDocsMap.containsKey(cloudDoc.id)) {
                        batch.delete(cloudDoc.reference)
                        batch.delete(cloudMembershipsRef.document(cloudDoc.id))
                    }
                }

                batch.commit().addOnCompleteListener {
                    onComplete()
                }
            }.addOnFailureListener {
                onComplete()
            }
        }.addOnFailureListener {
            onComplete()
        }
    }

    /**
     * Copia las listas de la nube de Google a local ("local_user") al cerrar sesión.
     */
    private fun copyCloudListsToLocal(googleUid: String, onComplete: () -> Unit) {
        if (googleUid.isBlank()) {
            onComplete()
            return
        }

        val db = Firebase.firestore
        db.collection("users").document(googleUid).collection("lists").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onComplete()
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    val listData = doc.data?.toMutableMap() ?: mutableMapOf()
                    listData["ownerId"] = "local_user"

                    val localListRef = db.collection("users").document("local_user").collection("lists").document(doc.id)
                    val localMembershipRef = db.collection("users").document("local_user").collection("memberships").document(doc.id)

                    batch.set(localListRef, listData)
                    batch.set(localMembershipRef, mapOf("listId" to doc.id, "ownerId" to "local_user", "role" to "owner", "joinedAt" to System.currentTimeMillis()))
                }

                batch.commit().addOnCompleteListener {
                    onComplete()
                }
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    /**
     * Cierra la sesión de Firebase y limpia las credenciales,
     * copiando previamente las listas a local ("local_user").
     */
    fun signOut() {
        val currentUser = auth.currentUser
        val googleUid = currentUser?.uid

        val doSignOut = {
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

        if (googleUid != null) {
            copyCloudListsToLocal(googleUid) {
                doSignOut()
            }
        } else {
            doSignOut()
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
