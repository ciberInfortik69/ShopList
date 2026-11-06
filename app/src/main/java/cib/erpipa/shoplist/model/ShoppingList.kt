package cib.erpipa.shoplist.model

/**
 * Representa una lista de la compra compartida.
 *
 * @param name Nombre de la lista.
 * @param ownerId UID del usuario que creó la lista.
 * @param createdAt Fecha de creación de la lista.
 * @param updatedAt Fecha de la última modificación.
 */
data class ShoppingList(
    val name: String = "",
    val code: String = "",
    val isPublic: Boolean = false,
    val ownerId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)