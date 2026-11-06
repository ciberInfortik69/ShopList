package cib.erpipa.shoplist.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cib.erpipa.shoplist.model.ShoppingItem
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "ListScreen"

/**
 * Representa un producto junto con su índice dentro del array products.
 */
private data class ShoppingItemEntry(
    val index: Int,
    val item: ShoppingItem
)

/**
 * Pantalla que muestra y permite gestionar los productos almacenados en el array products de una lista en users/{ownerId}/lists/{listId}.
 */
@Composable
fun ListScreen(
    listId: String,
    ownerId: String = "",
    currentUser: FirebaseUser?,
    onBack: () -> Unit = {}
) {
    val shoppingItems = remember(listId) {
        mutableStateListOf<ShoppingItemEntry>()
    }

    var rawProductsList by remember {
        mutableStateOf<List<Map<String, Any>>>(emptyList())
    }

    var listName by remember {
        mutableStateOf("Lista de la compra")
    }

    var newItemName by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var isAdding by remember {
        mutableStateOf(false)
    }

    var isMember by remember {
        mutableStateOf(false)
    }

    var isOwner by remember {
        mutableStateOf(false)
    }

    var isPublicList by remember {
        mutableStateOf(false)
    }

    val isPreview = LocalInspectionMode.current
    val isGoogleUser = currentUser != null && !currentUser.isAnonymous

    val canEdit = if (!isGoogleUser) {
        true
    } else {
        isOwner || isMember
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    /*
     * Escucha los cambios del documento de la lista en users/{ownerId}/lists/{listId}
     */
    DisposableEffect(listId, ownerId, currentUser, isPreview) {

        if (isPreview || listId.isBlank()) {
            shoppingItems.clear()
            onDispose { }
        } else {
            val db = Firebase.firestore
            val currentUid = currentUser?.uid ?: ""
            val targetOwnerId = if (ownerId.isNotBlank()) ownerId else currentUid

            val listReference = db.collection("users")
                .document(targetOwnerId)
                .collection("lists")
                .document(listId)

            var memberListener: ListenerRegistration? = null
            if (currentUid.isNotBlank() && currentUid != targetOwnerId) {
                memberListener = listReference
                    .collection("members")
                    .document(currentUid)
                    .addSnapshotListener { memDoc, _ ->
                        isMember = memDoc != null && memDoc.exists()
                    }
            } else {
                isOwner = true
            }

            val listListener = listReference.addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e(TAG, "Error al obtener la lista", error)
                    errorMessage = "No se ha podido cargar la lista."
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    listName = snapshot.getString("name") ?: "Lista de la compra"
                    isPublicList = snapshot.getBoolean("isPublic") ?: false
                    val docOwnerId = snapshot.getString("ownerId") ?: targetOwnerId
                    if (currentUser?.uid == docOwnerId) {
                        isOwner = true
                    }

                    @Suppress("UNCHECKED_CAST")
                    val productsRaw = snapshot.get("products") as? List<Map<String, Any>> ?: emptyList()
                    rawProductsList = productsRaw

                    shoppingItems.clear()

                    productsRaw.forEachIndexed { idx, map ->
                        val item = ShoppingItem(
                            name = map["name"] as? String ?: "",
                            bought = map["bought"] as? Boolean ?: false,
                            important = map["important"] as? Boolean ?: false,
                            createdBy = map["createdBy"] as? String ?: "",
                            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
                        )

                        shoppingItems.add(
                            ShoppingItemEntry(
                                index = idx,
                                item = item
                            )
                        )
                    }
                }
            }

            onDispose {
                memberListener?.remove()
                listListener.remove()
                shoppingItems.clear()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // Cabecera y botón para volver.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text("Volver")
            }

            Text(
                text = listName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = canEdit) {
                        renameInput = listName
                        showRenameDialog = true
                    }
                    .padding(4.dp)
            )
        }

        if (!canEdit && isPublicList) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Modo solo lectura (Lista pública)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Campo para introducir productos.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newItemName,
                onValueChange = {
                    newItemName = it
                },
                modifier = Modifier.weight(1f),
                label = {
                    Text("Nuevo producto")
                },
                singleLine = true,
                enabled = canEdit && !isAdding
            )

            Spacer(modifier = Modifier.padding(4.dp))

            Button(
                onClick = {
                    val name = newItemName.trim()

                    if (name.isNotBlank() && !isAdding && canEdit) {

                        isAdding = true
                        errorMessage = null

                        val timestamp = System.currentTimeMillis()
                        val creatorUid = currentUser?.uid ?: "local_user"
                        val targetOwnerId = if (ownerId.isNotBlank()) ownerId else creatorUid

                        val newProductMap = mapOf(
                            "name" to name,
                            "bought" to false,
                            "important" to false,
                            "createdBy" to creatorUid,
                            "createdAt" to timestamp,
                            "updatedAt" to timestamp
                        )

                        Firebase.firestore
                            .collection("users")
                            .document(targetOwnerId)
                            .collection("lists")
                            .document(listId)
                            .update("products", FieldValue.arrayUnion(newProductMap))
                            .addOnSuccessListener {
                                newItemName = ""
                                isAdding = false
                            }
                            .addOnFailureListener { error ->
                                Log.e(TAG, "Error al añadir el producto", error)
                                errorMessage = "No se ha podido añadir el producto."
                                isAdding = false
                            }
                    }
                },
                enabled = newItemName.isNotBlank() && canEdit && !isAdding
            ) {
                Text("Añadir")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Listado de productos.
        if (shoppingItems.isEmpty()) {
            Text("Todavía no hay productos en esta lista.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = shoppingItems,
                    key = { "${it.index}_${it.item.name}" }
                ) { entry ->

                    val idx = entry.index
                    val item = entry.item
                    val goldColor = Color(0xFFFFB300)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = if (item.important) BorderStroke(1.5.dp, goldColor) else null,
                        colors = if (item.bought) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        } else if (item.important) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Checkbox(
                                    checked = item.bought,
                                    onCheckedChange = { checked ->
                                        val targetOwnerId = if (ownerId.isNotBlank()) ownerId else (currentUser?.uid ?: "local_user")
                                        if (idx in rawProductsList.indices && targetOwnerId.isNotBlank()) {
                                            val updatedList = rawProductsList.mapIndexed { i, map ->
                                                if (i == idx) {
                                                    map.toMutableMap().apply {
                                                        put("bought", checked)
                                                        put("updatedAt", System.currentTimeMillis())
                                                    }
                                                } else map
                                            }

                                            Firebase.firestore
                                                .collection("users")
                                                .document(targetOwnerId)
                                                .collection("lists")
                                                .document(listId)
                                                .update("products", updatedList)
                                                .addOnFailureListener { error ->
                                                    Log.e(TAG, "Error al actualizar el producto", error)
                                                    errorMessage = "No se ha podido actualizar el producto."
                                                }
                                        }
                                    },
                                    enabled = canEdit
                                )

                                if (item.important) {
                                    Text(
                                        text = "★ ",
                                        color = goldColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                Text(
                                    text = item.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (item.important) FontWeight.Bold else FontWeight.Medium,
                                    textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (item.bought) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {

                                TextButton(
                                    onClick = {
                                        val targetOwnerId = if (ownerId.isNotBlank()) ownerId else (currentUser?.uid ?: "local_user")
                                        if (idx in rawProductsList.indices && targetOwnerId.isNotBlank()) {
                                            val updatedList = rawProductsList.mapIndexed { i, map ->
                                                if (i == idx) {
                                                    map.toMutableMap().apply {
                                                        put("important", !item.important)
                                                        put("updatedAt", System.currentTimeMillis())
                                                    }
                                                } else map
                                            }

                                            Firebase.firestore
                                                .collection("users")
                                                .document(targetOwnerId)
                                                .collection("lists")
                                                .document(listId)
                                                .update("products", updatedList)
                                                .addOnFailureListener { error ->
                                                    Log.e(TAG, "Error al marcar importante", error)
                                                    errorMessage = "No se ha podido modificar el producto."
                                                }
                                        }
                                    },
                                    enabled = canEdit
                                ) {
                                    Text(
                                        text = if (item.important) "★ Importante" else "☆ Marcar importante",
                                        color = if (item.important) goldColor else MaterialTheme.colorScheme.primary,
                                        fontWeight = if (item.important) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        val targetOwnerId = if (ownerId.isNotBlank()) ownerId else (currentUser?.uid ?: "local_user")
                                        if (idx in rawProductsList.indices && targetOwnerId.isNotBlank()) {
                                            val updatedList = rawProductsList.filterIndexed { i, _ -> i != idx }

                                            Firebase.firestore
                                                .collection("users")
                                                .document(targetOwnerId)
                                                .collection("lists")
                                                .document(listId)
                                                .update("products", updatedList)
                                                .addOnFailureListener { error ->
                                                    Log.e(TAG, "Error al eliminar el producto", error)
                                                    errorMessage = "No se ha podido eliminar el producto."
                                                }
                                        }
                                    },
                                    enabled = canEdit
                                ) {
                                    Text(
                                        text = "Eliminar",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar lista") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank() && !isPreview) {
                            val currentUid = currentUser?.uid ?: "local_user"
                            val targetOwnerId = if (ownerId.isNotBlank()) ownerId else currentUid
                            Firebase.firestore
                                .collection("users")
                                .document(targetOwnerId)
                                .collection("lists")
                                .document(listId)
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
                        showRenameDialog = false
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ListScreenPreview() {
    ShopListTheme {
        ListScreen(
            listId = "preview",
            currentUser = null
        )
    }
}
