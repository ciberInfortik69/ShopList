package cib.erpipa.shoplist.screen

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
data class ListEntry(
    val id: String,
    val name: String,
    val code: String = "",
    val isPublic: Boolean = false,
    val ownerId: String = ""
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

    var listToDelete by remember {
        mutableStateOf<ListEntry?>(null)
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
}

@Preview(showBackground = true)
@Composable
fun ShopListScreenPreview() {
    ShopListTheme {
        ShopListScreen()
    }
}
