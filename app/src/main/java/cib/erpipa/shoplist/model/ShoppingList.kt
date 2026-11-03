package cib.erpipa.shoplist.model

/**
 * Modelo de datos que representa una lista de compras individual.
 *
 * @param name Nombre o título dado a la lista.
 * @param products Lista de nombres de productos que contiene la lista.
 */
data class ShoppingList(
    val name: String = "",
    val products: List<String> = emptyList()
)