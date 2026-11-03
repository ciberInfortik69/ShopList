package cib.erpipa.shoplist.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cib.erpipa.shoplist.model.ShoppingList
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Pantalla principal que muestra y permite crear listas de compras (guardando en Firestore).
 */
@Composable
fun ShopListScreen(
    modifier: Modifier = Modifier
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
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                            // Evento al pulsar en una lista
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
                            lists = lists + listName.trim() // Añade la nueva lista al listado local
                            showDialog = false // Cierra el diálogo

                            // Guarda la nueva lista en Firebase Firestore
                            val lista1 = ShoppingList(id = 1, name = listName.trim(), products = emptyList())
                            val db = Firebase.firestore
                            db.collection("lists").add(lista1)
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

@Preview(showBackground = true)
@Composable
fun ShopListScreenPreview() {
    ShopListTheme {
        ShopListScreen()
    }
}