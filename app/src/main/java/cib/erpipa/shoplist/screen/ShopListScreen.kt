package cib.erpipa.shoplist.screen

import android.content.Context
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
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

private const val TAG = "ShopListScreen"

/**
 * Representa una lista que pertenece o ha sido compartida con el usuario.
 */
private data class ListEntry(
    val id: String,
    val name: String,
    val code: String = "",
    val isPublic: Boolean = false,
    val ownerId: String = ""
)

/**
 * Representa una solicitud de acceso enviada por un usuario a una lista privada.
 */
private data class JoinRequestEntry(
    val requestId: String,
    val listId: String,
    val listName: String,
    val ownerId: String,
    val userId: String,
    val userName: String,
    val userEmail: String
)

/**
 * Pantalla principal que muestra las listas del usuario asociadas a la ruta users/{uid}/lists.
 */
@Composable
fun ShopListScreen(
    modifier: Modifier = Modifier,
    currentUser: FirebaseUser? = null,
    onOpenList: (listId: String, ownerId: String) -> Unit = { _, _ -> }
) {
    val isPreview = LocalInspectionMode.current

    // Listas disponibles para el usuario.
    val lists = remember {
        mutableStateListOf<ListEntry>()
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    var listName by remember {
        mutableStateOf("")
    }

    var isPublicChecked by remember {
        mutableStateOf(false)
    }

    var isCreating by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var listToRename by remember {
        mutableStateOf<ListEntry?>(null)
    }

    var renameText by remember {
        mutableStateOf("")
    }

    var listToDelete by remember {
        mutableStateOf<ListEntry?>(null)
    }

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

    /*
     * Escucha las solicitudes de acceso entrantes a las listas del usuario en users/{uid}/lists/{listId}/joinRequests
     */
    DisposableEffect(currentUser, lists.size, isPreview) {
        if (isPreview || currentUser == null) {
            pendingRequests.clear()
            onDispose { }
        } else {
            val db = Firebase.firestore
            val uid = currentUser.uid
            val requestListeners = mutableMapOf<String, ListenerRegistration>()

            val ownedLists = lists.filter { it.ownerId == uid }

            ownedLists.forEach { ownedList ->
                val listener = db.collection("users")
                    .document(uid)
                    .collection("lists")
                    .document(ownedList.id)
                    .collection("joinRequests")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error al escuchar solicitudes de acceso para ${ownedList.id}", error)
                            return@addSnapshotListener
                        }
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

    /*
     * Escucha las listas propias del usuario en users/{uid}/lists
     * y las membresías en users/{uid}/memberships para listas compartidas.
     */
    DisposableEffect(currentUser, isPreview) {

        if (isPreview) {
            lists.clear()

            onDispose { }
        } else {
            val db = Firebase.firestore
            val uid = currentUser?.uid ?: "local_user"

            val listListeners = mutableMapOf<String, ListenerRegistration>()

            // 1. Escuchar directamente las listas del propio usuario en users/{uid}/lists
            val userListsListener = db.collection("users")
                .document(uid)
                .collection("lists")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        Log.e(TAG, "Error al obtener las listas del usuario", error)
                        errorMessage = "No se han podido cargar tus listas."
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        // Conservamos únicamente las listas que NO pertenecen al usuario actual (compartidas por otros)
                        val sharedLists = lists.filter { it.ownerId != uid && it.ownerId != "local_user" }
                        lists.clear()
                        lists.addAll(sharedLists)

                        snapshot.documents.forEach { doc ->
                            lists.add(
                                ListEntry(
                                    id = doc.id,
                                    name = doc.getString("name") ?: "Lista sin nombre",
                                    code = doc.getString("code") ?: "",
                                    isPublic = doc.getBoolean("isPublic") ?: false,
                                    ownerId = doc.getString("ownerId") ?: uid
                                )
                            )
                        }
                        val distinctLists = lists.distinctBy { it.name.lowercase().trim() }
                        lists.clear()
                        lists.addAll(distinctLists)
                        lists.sortBy { it.name.lowercase() }
                    }
                }

            // 2. Escuchar membresías compartidas por otros usuarios en users/{uid}/memberships
            val membershipListener = db.collection("users")
                .document(uid)
                .collection("memberships")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        Log.e(TAG, "Error al obtener las membresías compartidas", error)
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    val sharedMemberships = snapshot.documents.mapNotNull { doc ->
                        val ownerId = doc.getString("ownerId") ?: ""
                        if (ownerId.isNotBlank() && ownerId != uid) {
                            Pair(doc.id, ownerId)
                        } else null
                    }.toSet()

                    val activeKeys = sharedMemberships.map { it.first }.toSet()
                    val removedKeys = listListeners.keys - activeKeys

                    removedKeys.forEach { key ->
                        listListeners.remove(key)?.remove()
                        lists.removeAll { it.id == key }
                    }

                    sharedMemberships.forEach { (listId, ownerId) ->
                        if (!listListeners.containsKey(listId)) {
                            val listener = db.collection("users")
                                .document(ownerId)
                                .collection("lists")
                                .document(listId)
                                .addSnapshotListener { listSnapshot, listError ->

                                    if (listError != null || listSnapshot == null || !listSnapshot.exists()) {
                                        lists.removeAll { it.id == listId }
                                        return@addSnapshotListener
                                    }

                                    val entry = ListEntry(
                                        id = listSnapshot.id,
                                        name = listSnapshot.getString("name") ?: "Lista sin nombre",
                                        code = listSnapshot.getString("code") ?: "",
                                        isPublic = listSnapshot.getBoolean("isPublic") ?: false,
                                        ownerId = ownerId
                                    )

                                    lists.removeAll { it.id == listId }
                                    lists.add(entry)
                                    val distinctLists = lists.distinctBy { it.name.lowercase().trim() }
                                    lists.clear()
                                    lists.addAll(distinctLists)
                                    lists.sortBy { it.name.lowercase() }
                                }

                            listListeners[listId] = listener
                        }
                    }
                }

            onDispose {
                userListsListener.remove()
                membershipListener.remove()

                listListeners.values.forEach { listener ->
                    listener.remove()
                }

                listListeners.clear()
                lists.clear()
            }
        }
    }

    val canUsePublicFeatures = currentUser != null && !currentUser.isAnonymous

    // Interfaz principal.
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
                    text = "List of lists",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buscador de listas por código
        if (currentUser != null) {
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
                            text = "Inicia sesión con tu cuenta de Google para buscar y unirte a listas compartidas.",
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
                        val isMyList = lists.any { it.id == result.id }
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
                Spacer(modifier = Modifier.height(12.dp))
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

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Button(
                    onClick = {
                        listName = ""
                        isPublicChecked = false
                        errorMessage = null
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating && !isPreview
                ) {
                    Text("Crear nueva lista")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (lists.isEmpty()) {
                    Text("Todavía no tienes ninguna lista.")
                }

                lists.forEach { list ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            onOpenList(list.id, list.ownerId)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = list.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (list.code.isNotBlank()) {
                                    Text(
                                        text = "Código: ${list.code} • ${if (list.isPublic) "Pública" else "Privada"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    renameText = list.name
                                    listToRename = list
                                }
                            ) {
                                Text("Editar")
                            }

                            TextButton(
                                onClick = {
                                    listToDelete = list
                                }
                            ) {
                                Text(
                                    text = "Eliminar",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // Diálogo para crear una lista.
    if (showDialog) {

        AlertDialog(
            onDismissRequest = {
                if (!isCreating) {
                    showDialog = false
                }
            },

            title = {
                Text("Nueva lista")
            },

            text = {
                Column {
                    OutlinedTextField(
                        value = listName,
                        onValueChange = {
                            listName = it
                        },
                        label = {
                            Text("Nombre de la lista")
                        },
                        singleLine = true,
                        enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isPublicChecked && canUsePublicFeatures,
                            onCheckedChange = { isPublicChecked = it },
                            enabled = !isCreating && canUsePublicFeatures
                        )
                        Text(
                            text = "Hacer pública (solo lectura para no miembros)",
                            fontSize = 14.sp,
                            color = if (canUsePublicFeatures) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!canUsePublicFeatures) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Inicia sesión con tu cuenta de Google para crear listas públicas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {

                        val trimmedName = listName.trim()

                        if (trimmedName.isNotBlank() && !isCreating && !isPreview) {

                            isCreating = true
                            errorMessage = null

                            val db = Firebase.firestore
                            val uid = currentUser?.uid ?: "local_user"
                            val timestamp = System.currentTimeMillis()

                            val cleanName = trimmedName.lowercase().replace("\\s+".toRegex(), "")
                            val randomCode = (1000..9999).random()
                            val generatedCode = "$cleanName#$randomCode"
                            val finalIsPublic = isPublicChecked && canUsePublicFeatures

                            // Documento principal de la lista dentro de users/{uid}/lists
                            val listReference = db.collection("users")
                                .document(uid)
                                .collection("lists")
                                .document()

                            // Referencia de la membresía propia del usuario.
                            val membershipReference = db.collection("users")
                                .document(uid)
                                .collection("memberships")
                                .document(listReference.id)

                            val batch = db.batch()

                            batch.set(
                                listReference,
                                mapOf(
                                    "name" to trimmedName,
                                    "code" to generatedCode,
                                    "isPublic" to finalIsPublic,
                                    "ownerId" to uid,
                                    "createdAt" to timestamp,
                                    "updatedAt" to timestamp,
                                    "products" to emptyList<Map<String, Any>>()
                                )
                            )

                            batch.set(
                                membershipReference,
                                mapOf(
                                    "listId" to listReference.id,
                                    "ownerId" to uid,
                                    "role" to "owner",
                                    "joinedAt" to timestamp
                                )
                            )

                            batch.commit()
                                .addOnSuccessListener {
                                    showDialog = false
                                    listName = ""
                                    isPublicChecked = false
                                    isCreating = false
                                }
                                .addOnFailureListener { error ->
                                    Log.e(TAG, "Error al crear la lista", error)
                                    errorMessage = "No se ha podido crear la lista: ${error.localizedMessage}"
                                    isCreating = false
                                }
                        }
                    },
                    enabled = listName.isNotBlank() &&
                            !isCreating &&
                            !isPreview
                ) {
                    Text(if (isCreating) "Creando..." else "Crear")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isCreating) {
                            showDialog = false
                        }
                    },
                    enabled = !isCreating
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para renombrar una lista.
    listToRename?.let { targetList ->
        AlertDialog(
            onDismissRequest = { listToRename = null },
            title = { Text("Renombrar lista") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotBlank()) {
                            if (!isPreview) {
                                val uid = currentUser?.uid ?: "local_user"
                                Firebase.firestore.collection("users")
                                    .document(uid)
                                    .collection("lists")
                                    .document(targetList.id)
                                    .update(
                                        mapOf(
                                            "name" to trimmed,
                                            "updatedAt" to System.currentTimeMillis()
                                        )
                                    )
                                    .addOnFailureListener { error ->
                                        Log.e(TAG, "Error al renombrar la lista", error)
                                        errorMessage = "No se pudo renombrar la lista."
                                    }
                            }
                            listToRename = null
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { listToRename = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para eliminar una lista.
    listToDelete?.let { targetList ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Estás seguro de que deseas eliminar la lista \"${targetList.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isPreview) {
                            val db = Firebase.firestore
                            val uid = currentUser?.uid ?: "local_user"
                            val batch = db.batch()

                            val membershipRef = db.collection("users")
                                .document(uid)
                                .collection("memberships")
                                .document(targetList.id)
                            batch.delete(membershipRef)

                            val listRef = db.collection("users")
                                .document(uid)
                                .collection("lists")
                                .document(targetList.id)
                            batch.delete(listRef)

                            batch.commit()
                                .addOnFailureListener { error ->
                                    Log.e(TAG, "Error al eliminar la lista", error)
                                    errorMessage = "No se pudo eliminar la lista."
                                }
                        }
                        listToDelete = null
                    }
                ) {
                    Text(
                        text = "Eliminar",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para solicitar acceso a una lista privada.
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

    // Diálogo para ver solicitudes de acceso pendientes (para el creador/propietario)
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
fun ShopListScreenPreview() {
    ShopListTheme {
        ShopListScreen()
    }
}
