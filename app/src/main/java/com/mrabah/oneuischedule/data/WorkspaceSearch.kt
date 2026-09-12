package com.mrabah.oneuischedule.data

internal data class WorkspaceResult(val id:String,val title:String,val detail:String,val section:String,val page:String="class")
internal object WorkspaceSearch {
    fun normalize(value:String):String=buildString {value.lowercase().forEach {c->append(when(c){in '٠'..'٩'->'0'+(c-'٠');'أ','إ','آ'->'ا';'ى'->'ي';else->c})}}.replace(Regex("[\u064B-\u065F\u0670\u0640]"),"").trim()
    fun filter(items:List<WorkspaceResult>,query:String):List<WorkspaceResult> {
        val words=normalize(query).split(Regex("\\s+")).filter{it.isNotBlank()}
        if(words.isEmpty())return emptyList()
        return items.filter{item->val haystack=normalize("${item.title} ${item.detail} ${item.section}");words.all{it in haystack}}.take(80)
    }
    fun all(c:android.content.Context,config:Config):List<WorkspaceResult> {
        val journal=ClassJournal.all(c)
        val sections=(config.sections+config.schoolSections+journal.map{it.section}).distinct()
        return sections.map{WorkspaceResult("class:$it","الفصل $it",config.progress[it]?.let{p->"${p.last} ${p.next}"}.orEmpty(),it)}+
            journal.map{WorkspaceResult("journal:${it.id}",it.title,"${it.unit} ${it.text} ${it.fileName}",it.section)}+
            sections.mapNotNull{section->com.mrabah.oneuischedule.widget.ClassNotes.get(c,section)?.let{WorkspaceResult("note:$section","آخر نقطة",it.text,section)}}+
            sections.flatMap{section->ClassShortcuts.forClass(c,section).map{WorkspaceResult("shortcut:${it.id}",it.title,if(it.kind=="link")it.target else "ملف أو مجلد",section,"shortcuts")}}
    }
}
