package app.ember.tv.streaming

data class Offer(
    val serviceId: Int,
    val name: String,
    val type: String,
    val price: Double?,
    val format: String?,
    val webUrl: String?,
    val androidUrl: String?,
    val externalId: String? = null,
    val currency: String? = null,
    val expiresAt: Long? = null,
) {
    private fun cost(): String? = price?.takeIf { it.isFinite() && it >= 0 }?.let { amount ->
        if (currency == null) "$" + "%.2f".format(amount) else runCatching {
            java.text.NumberFormat.getCurrencyInstance().apply {
                currency = java.util.Currency.getInstance(this@Offer.currency)
            }.format(amount)
        }.getOrElse { "${this.currency} " + "%.2f".format(amount) }
    }
    val label: String
        get() = when (type) {
            "sub" -> "Subscription"
            "free" -> "Free"
            "tve" -> "With TV provider"
            "addon" -> "Channel add-on"
            "link" -> "From your addons"
            "rent" -> cost()?.let { "Rent $it" } ?: "Rent"
            "buy" -> cost()?.let { "Buy $it" } ?: "Buy"
            else -> type.replaceFirstChar { it.uppercase() }
        }

    /** Watchmode's format (SD, HD, 4K) as a quality label. Watchmode's HD covers 720p and 1080p. */
    val quality: String
        get() = when (format?.uppercase()) {
            "4K", "UHD" -> "4K"
            "HD" -> "HD"
            "QHD" -> "1440p"
            "SD" -> "SD"
            else -> ""
        }

    /** Subscription and free first, then TV provider, rent, buy. */
    val rank: Int get() = when (type) { "sub" -> 0; "free" -> 1; "tve" -> 2; "addon" -> 3; "rent" -> 4; else -> 5 }
}
