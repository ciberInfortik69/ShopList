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
import androidx.compose.ui.text.font.FontStyle.Companion.Italic
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cib.erpipa.shoplist.ui.theme.ShopListTheme

/**
 * Pantalla principal que muestra y permite crear listas de compras.
 */
@Composable
fun ShopListScreen(
    modifier: Modifier = Modifier
) {
    var lists by remember {
        mutableStateOf(listOf<String>())
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    var listName by remember {
        mutableStateOf("")
    }

    Column(modifier = modifier.padding(16.dp)) {

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

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = " Listas creadas:",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontStyle = Italic
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Button(
                    onClick = {
                        listName = ""
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear nunytyeva lista")
                }

                Spacer(modifier = Modifier.height(16.dp))

                lists.forEach { name ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            // Evento al tocar una lista
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text("Nueva lista")
            },
            text = {
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
                TextButton(
                    onClick = {
                        if (listName.isNotBlank()) {
                            lists = lists + listName.trim()
                            showDialog = false
                        }
                    },
                    enabled = listName.isNotBlank()
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
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