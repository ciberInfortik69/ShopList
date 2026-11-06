package cib.erpipa.shoplist.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "SearchScreen"

private data class JoinRequestEntry(
    val requestId: String,
    val listId: String,
    val listName: String,
    val ownerId: String,
    val userId: String,
    val userName: String,
    val userEmail: String
)

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    currentUser: FirebaseUser? = null,
    onOpenList: (listId: String, ownerId: String) -> Unit = { _, _ -> }
) {
    val isPreview = LocalInspectionMode.current
    val canUsePublicFeatures = currentUser != null && !currentUser.isAnonymous

    val userLists = remember { mutableStateListOf<ListEntry>() }

    // Buscador
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<ListEntry?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Solicitudes de acceso
    var showJoinRequestDialog by remember { mutableStateOf<ListEntry?>(null) }
    var isRequestingAccess by remember { mutableStateOf(false) }

    val pendingRequests = remember { mutableStateListOf<JoinRequestEntry>() }
    var showRequestsDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Cargar listas propias del usuario para saber cuáles son suyas
    DisposableEffect(currentUser, isPreview) {
        if (isPreview || currentUser == null) {
            userLists.clear()
            onDispose { }
        } else {
            val db = Firebase.firestore
            val uid = currentUser.uid
            val listener = db.collection("users")
                .document(uid)
                .collection("lists")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        userLists.clear()
                        snapshot.documents.forEach { doc ->
                            userLists.add(
                                ListEntry(
                                    id = doc.id,
                                    name = doc.getString("name") ?: "Lista sin nombre",
                                    code = doc.getString("code") ?: "",
                                    isPublic = doc.getBoolean("isPublic") ?: false,
                                    ownerId = uid
                                )
                            )
                        }
                    }
                }
            onDispose {
                listener.remove()
                userLists.clear()
            }
        }
    }

    // Escuchar solicitudes de acceso entrantes
    DisposableEffect(currentUser, userLists.size, isPreview) {
        if (isPreview || currentUser == null) {
            pendingRequests.clear()
            onDispose { }
        } else {
            val db = Firebase.firestore
            val uid = currentUser.uid
            val requestListeners = mutableMapOf<String, ListenerRegistration>()

            userLists.forEach { ownedList ->
                val listener = db.collection("users")
                    .document(uid)
                    .collection("lists")
                    .document(ownedList.id)
                    .collection("joinRequests")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
                            pendingRequests.removeAll { it.listId == ownedList.id }
                            snapshot.documents.forEach { doc ->
                                pendingRequests.add(
                                    JoinRequestEntry(
                                        requestId = doc.id,
                                        listId = ownedList.id,
                                        listName = ownedList.name,
                                        ownerId = uid,
                                        userId = doc.getString("userId") ?: doc.id,
                                        userName = doc.getString("userName") ?: "Usuario",
                                        userEmail = doc.getString("userEmail") ?: ""
                                    )
                                )
                            }
                        }
                    }
                requestListeners[ownedList.id] = listener
            }

            onDispose {
                requestListeners.values.forEach { it.remove() }
                requestListeners.clear()
            }
        }
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Search lists",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentUser == null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Inicia sesión con tu cuenta de Google para buscar y unirte a listas compartidas.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Buscar lista por código",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!canUsePublicFeatures) {
                        Text(
                            text = "Las cuentas anónimas no pueden buscar listas públicas. Inicia sesión con Google.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    searchError = null
                                },
                                placeholder = { Text("ej: miscompras#4569") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = !isSearching
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            Button(
                                onClick = {
                                    val codeToSearch = searchQuery.trim()
                                    if (codeToSearch.isNotBlank() && !isPreview) {
                                        isSearching = true
                                        searchError = null
                                        searchResult = null

                                        Firebase.firestore.collectionGroup("lists")
                                            .whereEqualTo("code", codeToSearch)
                                            .get()
                                            .addOnSuccessListener { snapshot ->
                                                isSearching = false
                                                val doc = snapshot.documents.firstOrNull()
                                                if (doc != null && doc.exists()) {
                                                    val ownerId = doc.getString("ownerId")
                                                        ?: doc.reference.parent.parent?.id
                                                        ?: ""

                                                    searchResult = ListEntry(
                                                        id = doc.id,
                                                        name = doc.getString("name") ?: "Lista",
                                                        code = doc.getString("code") ?: "",
                                                        isPublic = doc.getBoolean("isPublic") ?: false,
                                                        ownerId = ownerId
                                                    )
                                                } else {
                                                    searchError = "No se encontró ninguna lista con el código '$codeToSearch'."
                                                }
                                            }
                                            .addOnFailureListener { error ->
                                                isSearching = false
                                                searchError = "Error al buscar: ${error.localizedMessage}"
                                            }
                                    }
                                },
                                enabled = searchQuery.isNotBlank() && !isSearching
                            ) {
                                Text(if (isSearching) "..." else "Buscar")
                            }
                        }
                    }

                    searchError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }

                    searchResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        val isMyList = userLists.any { it.id == result.id }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (isMyList || result.isPublic) {
                                    onOpenList(result.id, result.ownerId)
                                } else {
                                    showJoinRequestDialog = result
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = result.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    if (result.code.isNotBlank()) {
                                        Text(text = "Código: ${result.code}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = if (result.isPublic) "Pública (Solo lectura para no miembros)" else "Privada (Requiere autorización)",
                                        fontSize = 12.sp,
                                        color = if (result.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(
                                    text = if (isMyList) "Abrir" else if (result.isPublic) "Ver" else "Solicitar",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (pendingRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showRequestsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Solicitudes de acceso pendientes (${pendingRequests.size})")
                }
            }
        }
    }

    // Diálogo para solicitar acceso
    showJoinRequestDialog?.let { targetList ->
        AlertDialog(
            onDismissRequest = { if (!isRequestingAccess) showJoinRequestDialog = null },
            title = { Text("Solicitar acceso") },
            text = { Text("La lista \"${targetList.name}\" es privada. ¿Deseas enviar una solicitud al propietario para poder acceder?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val user = currentUser
                        if (user != null && !isPreview) {
                            isRequestingAccess = true
                            val reqRef = Firebase.firestore
                                .collection("users")
                                .document(targetList.ownerId)
                                .collection("lists")
                                .document(targetList.id)
                                .collection("joinRequests")
                                .document(user.uid)

                            reqRef.set(
                                mapOf(
                                    "userId" to user.uid,
                                    "userName" to (user.displayName ?: user.email ?: "Usuario"),
                                    "userEmail" to (user.email ?: ""),
                                    "requestedAt" to System.currentTimeMillis()
                                )
                            )
                                .addOnSuccessListener {
                                    isRequestingAccess = false
                                    showJoinRequestDialog = null
                                }
                                .addOnFailureListener { error ->
                                    isRequestingAccess = false
                                    Log.e(TAG, "Error al enviar solicitud", error)
                                    errorMessage = "No se pudo enviar la solicitud."
                                }
                        }
                    },
                    enabled = !isRequestingAccess
                ) {
                    Text(if (isRequestingAccess) "Enviando..." else "Solicitar acceso")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinRequestDialog = null },
                    enabled = !isRequestingAccess
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para ver solicitudes pendientes
    if (showRequestsDialog) {
        AlertDialog(
            onDismissRequest = { showRequestsDialog = false },
            title = { Text("Solicitudes de acceso pendientes") },
            text = {
                Column {
                    if (pendingRequests.isEmpty()) {
                        Text("No hay solicitudes pendientes.")
                    } else {
                        pendingRequests.forEach { req ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${req.userName} (${req.userEmail})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Solicita acceso a la lista: ${req.listName}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (!isPreview) {
                                                    val db = Firebase.firestore
                                                    val batch = db.batch()
                                                    val timestamp = System.currentTimeMillis()

                                                    val memberRef = db.collection("users")
                                                        .document(req.ownerId)
                                                        .collection("lists")
                                                        .document(req.listId)
                                                        .collection("members")
                                                        .document(req.userId)
                                                    batch.set(memberRef, mapOf("role" to "editor", "joinedAt" to timestamp))

                                                    val membershipRef = db.collection("users")
                                                        .document(req.userId)
                                                        .collection("memberships")
                                                        .document(req.listId)
                                                    batch.set(membershipRef, mapOf("listId" to req.listId, "ownerId" to req.ownerId, "role" to "editor", "joinedAt" to timestamp))

                                                    val reqRef = db.collection("users")
                                                        .document(req.ownerId)
                                                        .collection("lists")
                                                        .document(req.listId)
                                                        .collection("joinRequests")
                                                        .document(req.userId)
                                                    batch.delete(reqRef)

                                                    batch.commit()
                                                }
                                            }
                                        ) {
                                            Text("Aceptar")
                                        }
                                        TextButton(
                                            onClick = {
                                                if (!isPreview) {
                                                    Firebase.firestore
                                                        .collection("users")
                                                        .document(req.ownerId)
                                                        .collection("lists")
                                                        .document(req.listId)
                                                        .collection("joinRequests")
                                                        .document(req.userId)
                                                        .delete()
                                                }
                                            }
                                        ) {
                                            Text("Rechazar", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRequestsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SearchScreenPreview() {
    ShopListTheme {
        SearchScreen()
    }
}
