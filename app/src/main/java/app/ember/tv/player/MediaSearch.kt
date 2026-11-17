package app.ember.tv.player

internal object MediaSearch {
    fun matches(title:CharSequence?,query:String?):Boolean {
        val text=query?.trim().orEmpty()
        return text.isEmpty()||text.length<=160&&title?.contains(text,ignoreCase=true)==true
    }
}
