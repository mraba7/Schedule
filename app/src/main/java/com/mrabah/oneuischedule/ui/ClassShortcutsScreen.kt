package com.mrabah.oneuischedule.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import kotlinx.coroutines.*

@Composable
internal fun ClassShortcutsScreen(section:String) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var revision by remember{mutableStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var addingLink by rememberSaveable{mutableStateOf(false)}
    var title by rememberSaveable{mutableStateOf("")}
    var link by rememberSaveable{mutableStateOf("")}
    var editing by remember{mutableStateOf<ClassShortcut?>(null)}
    var deleting by remember{mutableStateOf<ClassShortcut?>(null)}
    DisposableEffect(Unit){val prefs=c.getSharedPreferences("app_preferences",0);val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};prefs.registerOnSharedPreferenceChangeListener(l);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(l)}}
    val entries=remember(revision,section){ClassShortcuts.forClass(c,section)}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch {
        busy=true
        try {val attached=withContext(Dispatchers.IO){ClassJournal.attach(c,uri)};ClassShortcuts.save(c,ClassShortcut(section=section,title=attached.second.take(80),kind="file",target=attached.first,mime=attached.third));message="تم حفظ الملف للفصل"}
        catch(e:Exception){message=e.message ?: "تعذر إضافة الملف"} finally{busy=false}
    }}
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null) {
        runCatching {c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);ClassShortcuts.save(c,ClassShortcut(section=section,title="مجلد الفصل",kind="folder",target=uri.toString()))}.onSuccess{message="تمت إضافة المجلد؛ يمكنك تغيير اسمه"}.onFailure{message="تعذر الوصول للمجلد؛ اختر مجلدًا آخر"}
    }}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("اختصارات الفصل $section",style=MaterialTheme.typography.headlineSmall);Text("ملفاتك وروابطك في مكان واحد، وتصل إليها من زر اختصارات الفصل في الودجت.")}
        if(section.isBlank())item{Text("اختر فصلًا من صفحة الفصول أولًا.")}
        else {
            item {Button(onClick={addingLink=true;title="";link=""},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("إضافة رابط")}
                OutlinedButton(onClick={filePicker.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("إضافة ملف · حتى 10 ميجابايت")}
                OutlinedButton(onClick={folderPicker.launch(null)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("اختيار مجلد")}}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(message.isNotBlank())item{Text(message,color=MaterialTheme.colorScheme.primary)}
            if(entries.isEmpty())item{Text("أضف مثلًا: عرض الدرس، مجلد أوراق العمل، أو رابط منصة الفصل.")}
            entries.forEach {s->item(key=s.id) {Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(s.title,style=MaterialTheme.typography.titleMedium)
                Text(when(s.kind){"file"->"ملف محفوظ داخل التطبيق";"folder"->"مجلد على هذا الجهاز";else->s.target},style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick={runCatching{c.startActivity(ClassShortcuts.intent(c,s))}.onFailure{message=if(it is android.content.ActivityNotFoundException)"لا يوجد تطبيق لفتح هذا النوع" else it.message ?: "تعذر فتح الاختصار"}}){Text("فتح")}
                    TextButton(onClick={editing=s;title=s.title;link=s.target}){Text("تعديل")}
                    TextButton(onClick={deleting=s}){Text("حذف")}
                }
            }}}}
        }
    }
    if(addingLink || editing!=null)EditorPage(onDismissRequest={addingLink=false;editing=null},title={Text(if(editing==null)"إضافة رابط" else "تعديل الاختصار")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(title,{title=it.take(80)},label={Text("اسم الاختصار")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(addingLink || editing?.kind=="link")OutlinedTextField(link,{link=it.take(2048)},label={Text("الرابط https://")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Uri),isError=link.isNotBlank() && !ClassShortcuts.validLink(link))
    }},confirmButton={Button(enabled=title.isNotBlank() && (!(addingLink || editing?.kind=="link") || ClassShortcuts.validLink(link)),onClick={val old=editing;ClassShortcuts.save(c,old?.copy(title=title.trim(),target=if(old.kind=="link")link.trim() else old.target) ?: ClassShortcut(section=section,title=title.trim(),kind="link",target=link.trim()));addingLink=false;editing=null}){Text("حفظ")}},dismissButton={TextButton(onClick={addingLink=false;editing=null}){Text("إلغاء")}})
    deleting?.let{s->AlertDialog(onDismissRequest={deleting=null},title={Text("حذف الاختصار؟")},text={Text(s.title)},confirmButton={TextButton(onClick={ClassShortcuts.remove(c,s);deleting=null}){Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}){Text("إلغاء")}})}
}
