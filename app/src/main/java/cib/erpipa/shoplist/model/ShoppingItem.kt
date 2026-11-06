package cib.erpipa.shoplist.model

/**
 * Representa un producto dentro de una lista de la compra.
 *
 * @param name Nombre del producto.
 * @param bought Indica si el producto ya ha sido comprado.
 * @param important Indica si el producto está destacado como importante.
 * @param createdBy UID del usuario que añadió el producto.
 * @param createdAt Fecha de creación del producto.
 * @param updatedAt Fecha de la última modificación.
 */
data class ShoppingItem(
    val name: String = "",
    val bought: Boolean = false,
    val important: Boolean = false,
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
