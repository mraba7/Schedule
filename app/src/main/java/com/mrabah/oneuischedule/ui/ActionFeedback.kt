package com.mrabah.oneuischedule.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mrabah.oneuischedule.data.DataVault
import kotlinx.coroutines.delay
import org.json.JSONObject

internal object RecentAction {
    fun mergeUndo(before:JSONObject,after:JSONObject,current:JSONObject):JSONObject {
        val merged=JSONObject(current.toString())
        val a=before.getJSONObject("stores");val b=after.getJSONObject("stores");val result=merged.getJSONObject("stores")
        DataVault.stores.forEach {name->
            val old=a.getJSONObject(name);val next=b.getJSONObject(name);val live=result.getJSONObject(name)
            (old.keys().asSequence().toSet()+next.keys().asSequence().toSet()).forEach {key->
                if(old.opt(key)?.toString()!=next.opt(key)?.toString()) {
                    require(live.opt(key)?.toString()==next.opt(key)?.toString()){"تغيّرت البيانات؛ استخدم سجل الاسترجاع"}
                    if(old.has(key))live.put(key,old.get(key)) else live.remove(key)
                }
            }
        }
        return merged
    }
}

@Composable
internal fun ActionFeedback() {
    val c=LocalContext.current;val view=LocalView.current
    var action by remember{mutableStateOf<Pair<JSONObject,JSONObject>?>(null)}
    var message by remember{mutableStateOf("")}
    var serial by remember{mutableIntStateOf(0)}
    var suppress by remember{mutableStateOf(false)}
    DisposableEffect(c) {
        val handler=Handler(Looper.getMainLooper())
        var baseline=DataVault.snapshot(c)
        val task=Runnable {
            val next=DataVault.snapshot(c)
            if(baseline.getJSONObject("stores").toString()!=next.getJSONObject("stores").toString() && !suppress) {
                action=baseline to next;message="تم الحفظ ✓";serial++
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            }
            baseline=next;suppress=false
        }
        val prefs=DataVault.stores.map{c.getSharedPreferences(it,Context.MODE_PRIVATE)}
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->handler.removeCallbacks(task);handler.postDelayed(task,180)}
        prefs.forEach{it.registerOnSharedPreferenceChangeListener(listener)}
        onDispose{handler.removeCallbacks(task);prefs.forEach{it.unregisterOnSharedPreferenceChangeListener(listener)}}
    }
    LaunchedEffect(serial){if(serial>0){delay(6000);message="";action=null}}
    Box(Modifier.fillMaxSize().zIndex(5f),contentAlignment=Alignment.BottomCenter) {
        androidx.compose.animation.AnimatedVisibility(message.isNotEmpty()) {
            Snackbar(Modifier.padding(12.dp),action={if(action!=null)TextButton(onClick={
                val pair=action ?: return@TextButton
                runCatching{val merged=RecentAction.mergeUndo(pair.first,pair.second,DataVault.snapshot(c));suppress=true;DataVault.restore(c,merged)}
                    .onSuccess{message="تم التراجع ✓";action=null;serial++}
                    .onFailure{message=it.message ?: "تعذر التراجع";action=null;serial++}
            }){Text("تراجع")}}){Text(message)}
        }
    }
}
