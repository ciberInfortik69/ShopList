package cib.erpipa.shoplist.model

/**
 * Modelo de datos que representa una lista de compras individual.
 *
 * @param id Identificador único numérico de la lista.
 * @param name Nombre o título dado a la lista.
 * @param products Lista de nombres de productos que contiene la lista.
 */
data class ShoppingList(
    val id: Int = 0,
    val name: String = "",
    val products: List<String> = emptyList()
)