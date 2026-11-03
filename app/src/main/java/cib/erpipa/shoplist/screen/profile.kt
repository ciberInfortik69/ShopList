package cib.erpipa.shoplist.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cib.erpipa.shoplist.ui.theme.ShopListTheme
import com.google.firebase.auth.FirebaseUser

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
    Column(modifier = modifier.padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Perfil de Usuario",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (currentUser != null) {
                    Text(
                        text = "Correo: ${currentUser.email ?: "Sin correo"}",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estado: Sesión iniciada",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Estado: No has iniciado sesión",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { if (currentUser == null) onSignIn() else onSignOut() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (currentUser == null) "Iniciar sesión con Google" else "Cerrar sesión")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ShopListTheme {
        ProfileScreen()
    }
}