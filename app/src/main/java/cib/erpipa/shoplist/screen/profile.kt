package cib.erpipa.shoplist.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de perfil de usuario e inicio/cierre de sesión.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    currentUser: FirebaseUser? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    // Estado para guardar el número de listas creadas obtenidas de Firestore
    var listCount by remember { mutableIntStateOf(0) }

    // Escuchador en tiempo real de la colección de listas en Firestore
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val db = Firebase.firestore
            db.collection("lists").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    listCount = snapshot.size()
                }
            }
        } else {
            listCount = 0
        }
    }

    Column(modifier = modifier.padding(16.dp)) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentUser != null) {
                    // Avatar con la primera letra del nombre o email
                    val initial = (currentUser.displayName?.take(1)
                        ?: currentUser.email?.take(1)
                        ?: "U").uppercase()

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Nombre del usuario
                    Text(
                        text = currentUser.displayName ?: "Usuario de Google",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Email del usuario
                    Text(
                        text = currentUser.email ?: "Sin correo registrado",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Información detallada de la cuenta
                    ProfileInfoRow(
                        label = "Estado de cuenta",
                        value = "Conectado con Google"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Contador de listas creadas desde Firestore
                    ProfileInfoRow(
                        label = "Listas creadas",
                        value = "$listCount"
                    )

                    // Fecha de registro de la cuenta si está disponible
                    currentUser.metadata?.creationTimestamp?.let { timestamp ->
                        if (timestamp > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
                            ProfileInfoRow(
                                label = "Miembro desde",
                                value = dateStr
                            )
                        }
                    }

                } else {
                    // Estado cuando no hay sesión iniciada
                    Text(
                        text = "Perfil de Usuario",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Inicia sesión con tu cuenta de Google para sincronizar tus listas de compras en todos tus dispositivos.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón de Inicio o Cierre de Sesión
        Button(
            onClick = { if (currentUser == null) onSignIn() else onSignOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = if (currentUser != null) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                text = if (currentUser == null) "Iniciar sesión con Google" else "Cerrar sesión",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ShopListTheme {
        ProfileScreen()
    }
}