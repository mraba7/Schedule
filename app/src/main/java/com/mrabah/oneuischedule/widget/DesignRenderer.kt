package com.mrabah.oneuischedule.widget

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.mrabah.oneuischedule.R

/** Dynamic Android Canvas rendering, not a screenshot background.
 * All seven compositions use one 360x360 design space, with proportional
 * letterboxing on non-square hosts. Text uses bundled fonts on every launcher.
 */
internal class DesignRenderer(context: Context) {
    private val regular = ResourcesCompat.getFont(context, R.font.plex_arabic_regular) ?: Typeface.DEFAULT
    private val bold = ResourcesCompat.getFont(context, R.font.plex_arabic_bold) ?: Typeface.DEFAULT_BOLD
    private lateinit var c: Canvas
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var d: DesignDay
    private var prepared = false
    private var preparation = ""
    private fun col(hex: String) = Color.parseColor(hex)
    private val ink = "#141A1A"

    fun render(design: Design, day: DesignDay, width: Int = 720, height: Int = 720,
               task: String = "تحديد التجهيز", done: Boolean = false): Bitmap {
        d = day; preparation = task; prepared = done
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap)
        val scale = minOf(width, height) / 360f
        c.translate((width - 360 * scale) / 2, (height - 360 * scale) / 2)
        c.scale(scale, scale)
        if(day.focus==null) {
            empty(design)
            return bitmap
        }
        when (design) {
            Design.TICKET -> ticket()
            Design.ORBIT -> orbit()
            Design.EDITORIAL -> editorial()
            Design.BENTO -> bento()
            Design.ROUTE -> route()
            Design.SPORT -> sport()
            Design.BLUEPRINT -> blueprint()
        }
        return bitmap
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float, fill: String, r: Float = 14f, stroke: String? = null) {
        p.style = Paint.Style.FILL; p.color = col(fill)
        c.drawRoundRect(RectF(x,y,x+w,y+h),r,r,p)
        if (stroke != null) {
            p.style = Paint.Style.STROKE; p.strokeWidth = .7f; p.color = col(stroke)
            c.drawRoundRect(RectF(x,y,x+w,y+h),r,r,p); p.style = Paint.Style.FILL
        }
    }
    private fun line(x: Float,y: Float,x2: Float,y2: Float,color: String,width: Float=.7f) {
        p.color=col(color); p.strokeWidth=width; p.style=Paint.Style.STROKE; p.strokeCap=Paint.Cap.ROUND
        c.drawLine(x,y,x2,y2,p); p.style=Paint.Style.FILL
    }
    private fun circle(x: Float,y: Float,r: Float,color: String,outline: Boolean=false,width: Float=1f) {
        p.color=col(color); p.style=if(outline) Paint.Style.STROKE else Paint.Style.FILL; p.strokeWidth=width
        c.drawCircle(x,y,r,p); p.style=Paint.Style.FILL
    }
    /** Android shapes Arabic and isolates LTR clocks/sections. Every label is
     * fitted to its allocated box, so long lesson names never collide. */
    private fun text(value: String,x: Float,y: Float,w: Float,h: Float,size: Float,color: String=ink,
                     strong: Boolean=false,align: String="right",minSize: Float=size) {
        var fs=size
        val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color=col(color); typeface=if(strong) bold else regular }
        tp.textSize=fs
        while (tp.measureText(value)>w && fs>minSize) { fs-=.5f; tp.textSize=fs }
        val alignment=when(align) { "center"->Layout.Alignment.ALIGN_CENTER; "left"->Layout.Alignment.ALIGN_NORMAL; else->Layout.Alignment.ALIGN_OPPOSITE }
        // Fixed paragraph LTR gives ALIGN_OPPOSITE a physical right edge, while
        // Unicode's bidi algorithm still shapes each Arabic run correctly.
        val display=if(value.any{it in '\u0600'..'\u06FF'}) "\u2067$value\u2069" else value
        val layout=StaticLayout.Builder.obtain(display,0,display.length,tp,w.toInt().coerceAtLeast(1))
            .setAlignment(alignment).setTextDirection(TextDirectionHeuristics.LTR)
            .setIncludePad(false).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.clipRect(x,y,x+w,y+h); c.translate(x,y+(h-layout.height)/2f); layout.draw(c); c.restore()
    }
    private fun clock(value: String,x:Float,y:Float,w:Float,h:Float,size:Float,color:String=ink,strong:Boolean=true) =
        text(if(value.any{it in '\u0600'..'\u06FF'}) value else "\u2066$value\u2069",x,y,w,h,size,color,strong,"center",size.coerceAtMost(12f))
    private fun section(value:String,x:Float,y:Float,w:Float,h:Float,size:Float,color:String=ink) =
        clock(value,x,y,w,h,size,color)
    private fun icon(type:String,x:Float,y:Float,color:String,s:Float=18f) {
        c.save();c.translate(x,y);c.scale(s/20,s/20)
        when(type) {
            "clock" -> { circle(10f,10f,8f,color,true,1.4f);line(10f,5f,10f,10f,color,1.4f);line(10f,10f,14f,12f,color,1.4f) }
            "coffee" -> { line(3f,8f,15f,8f,color,1.4f);line(3f,8f,4f,16f,color,1.4f);line(4f,16f,13f,16f,color,1.4f);line(13f,16f,15f,8f,color,1.4f);line(2f,19f,17f,19f,color,1.4f);circle(16f,11f,3f,color,true);line(6f,1f,5f,5f,color);line(10f,1f,9f,5f,color) }
            "note" -> { rect(3f,2f,14f,16f,"#00000000",2f,color);line(6f,7f,14f,7f,color);line(6f,11f,12f,11f,color) }
            "calendar" -> { rect(2f,4f,16f,14f,"#00000000",2f,color);line(2f,8f,18f,8f,color);line(6f,1f,6f,5f,color,1.4f);line(14f,1f,14f,5f,color,1.4f) }
            "plus" -> { line(10f,3f,10f,17f,color,1.6f);line(3f,10f,17f,10f,color,1.6f) }
            "exit" -> { line(4f,2f,15f,2f,color,1.4f);line(15f,2f,15f,18f,color,1.4f);line(4f,18f,15f,18f,color,1.4f);line(2f,10f,11f,10f,color,1.4f);line(7f,6f,11f,10f,color,1.4f);line(7f,14f,11f,10f,color,1.4f) }
        }; c.restore()
    }
    private fun task(y:Float,fill:String,fg:String,w:Float=328f) {
        rect(16f,y,w,34f,fill,10f)
        text("تجهيز الحصة",150f,y+1f,166f,15f,12f,fg,true)
        text(preparation,68f,y+16f,248f,15f,10f,fg)
        circle(37f,y+17f,7f,fg,true)
        if(prepared) {line(33f,y+17f,36f,y+20f,fg,1.6f);line(36f,y+20f,42f,y+13f,fg,1.6f)}
    }
    private fun header(title:String,number:String,fg:String,muted:String,badge:String) {
        rect(16f,15f,33f,33f,badge,8f)
        clock(number,17f,16f,31f,31f,17f,fg)
        text(title,58f,13f,286f,25f,22f,fg,true)
        text("${d.day} · ${d.date}",60f,38f,284f,19f,12f,muted)
    }
    private fun futureCells(y:Float,fg:String,muted:String) {
        d.upcoming.take(3).forEachIndexed { i,s ->
            val x=236f-i*108f
            section(DesignDay.section(s),x,y,108f,27f,22f,fg)
            clock(DesignDay.clock(s.bell.start),x,y+27,108f,21f,13f,muted,false)
            if(i<2) line(x,y+6,x,y+42,muted,.35f)
        }
        if(d.upcoming.isEmpty()) text("هذه آخر حصة",22f,y,316f,44f,14f,muted,align="center")
    }

    private fun empty(design:Design) {
        val (bg,fg)=when(design){
            Design.TICKET->"#F7F5EC" to "#103F32"
            Design.ORBIT->"#0B1220" to "#76DBFF"
            Design.EDITORIAL->"#FFFFFF" to "#202124"
            Design.BENTO->"#EEF0F4" to "#275CF5"
            Design.ROUTE->"#14171D" to "#FFC15A"
            Design.SPORT->"#080808" to "#D6FF42"
            Design.BLUEPRINT->"#E9F2FC" to "#174BA0"
        }
        rect(0f,0f,360f,360f,bg,24f)
        text(design.title,24f,24f,312f,34f,24f,fg,true)
        icon("calendar",157f,114f,fg,46f)
        text(d.ui.holiday?.label ?: "لا توجد حصص",28f,185f,304f,40f,27f,fg,true,"center")
        text("افتح التطبيق لمراجعة جدولك",28f,235f,304f,28f,15f,fg,align="center")
    }

    private fun ticket() {
        val green="#103F32"; val muted="#6A7069"
        rect(0f,0f,360f,360f,"#F7F5EC",24f)
        text("يومك الدراسي",134f,14f,210f,28f,23f,green,true)
        text("${d.day} · ${d.date}",112f,40f,232f,18f,11f,green)
        icon("calendar",18f,21f,green,20f)
        text("${d.ui.remaining} حصص",44f,18f,70f,29f,15f,green,true,"center")
        rect(16f,69f,328f,115f,"#F7F5EC",13f,"#CCD0C5")
        rect(157f,69f,187f,115f,green,13f)
        for(y in 80..174 step 5) circle(157f,y.toFloat(),1.6f,"#F7F5EC")
        text(d.status,172f,76f,156f,20f,12f,"#B5DCC2",true)
        section(d.section,174f,100f,155f,49f,43f,"#F7F5EC")
        text("${d.period} · ${d.subject}",171f,151f,161f,23f,12f,"#F7F5EC",true)
        text(if(d.ui.live!=null) "تنتهي الساعة" else "تبدأ الساعة",24f,80f,124f,21f,12f,green,align="center")
        clock(d.focus?.let { DesignDay.clock(if(d.ui.live!=null)it.bell.end else it.bell.start) } ?: "—",24f,104f,124f,44f,32f,green)
        text(if(d.minutes!=null) "${d.minutes} ${d.countLabel}" else d.period,23f,152f,124f,22f,11f,"#BC591F",true,"center")
        rect(16f,189f,328f,47f,"#F7F5EC",12f,"#D7D7CB")
        line(180f,198f,180f,228f,"#D0D0C5")
        text(d.freeLabel,201f,192f,126f,18f,12f,green);clock(d.freeValue,208f,211f,116f,21f,17f,green)
        text("نهاية الدوام",33f,192f,130f,18f,12f,green);clock(d.finish,39f,211f,116f,21f,17f,green)
        text("بعدها",269f,237f,67f,15f,11f,green,true)
        futureCells(247f,green,muted)
        task(296f,"#E8DCC6",green)
        icon("note",297f,338f,green,15f)
        text("ملاحظة",225f,334f,66f,23f,12f,green)
        text("الجدول",28f,334f,133f,23f,12f,green)
        // The whole face opens this lesson; the task hit area toggles completion.
    }
    private fun orbit() {
        val fg="#F4F7FF";val blue="#76DBFF";val muted="#A4B8D2"
        rect(0f,0f,360f,360f,"#0B1220",24f)
        header("جدولي", "01",fg,muted,"#17293C")
        text(d.day,218f,62f,120f,20f,14f,fg,true)
        circle(78f,132f,49f,"#243D57",true,7f)
        if(d.ui.live!=null) {
            p.color=col(if(d.urgent) "#FF966C" else blue);p.style=Paint.Style.STROKE;p.strokeWidth=7f;p.strokeCap=Paint.Cap.ROUND
            c.drawArc(RectF(29f,83f,127f,181f),-90f,360*(1-d.ui.progress),false,p);p.style=Paint.Style.FILL
        }
        clock(d.countText,33f,107f,91f,49f,if(d.minutes!=null)38f else 23f,fg)
        text(d.countLabel,30f,150f,97f,23f,9f,muted,align="center")
        line(151f,85f,151f,189f,"#2D4159")
        section(d.section,169f,88f,166f,67f,61f,blue)
        text("${d.status} · ${d.focus?.period ?: "—"}",164f,155f,172f,20f,12f,fg,true)
        clock(d.range,165f,176f,171f,21f,15f,fg)
        rect(16f,211f,328f,51f,"#18283D",14f,"#294056")
        icon("clock",29f,224f,blue,22f)
        val next=d.upcoming.firstOrNull()
        text(if(next==null) "آخر حصة" else "التالي",265f,214f,64f,18f,12f,muted)
        section(next?.let(DesignDay::section) ?: "—",196f,233f,132f,23f,23f,fg)
        clock(next?.let { DesignDay.clock(it.bell.start) } ?: d.finish,68f,225f,117f,27f,21f,fg)
        rect(16f,269f,159f,51f,"#18283D",13f);rect(183f,269f,161f,51f,"#18283D",13f)
        text(d.freeLabel,26f,273f,137f,18f,12f,muted);clock(d.freeValue,30f,291f,126f,26f,19f,fg)
        text("نهاية الدوام",193f,273f,139f,18f,12f,muted);clock(d.finish,201f,291f,126f,26f,19f,fg)
        rect(16f,328f,328f,24f,"#16354C",9f);icon("plus",27f,331f,blue,17f)
        text("إضافة ملاحظة",52f,328f,274f,24f,13f,blue,true)
    }
    private fun editorial() {
        val fg="#202124";val muted="#66686B";val red="#D84C3E"
        rect(0f,0f,360f,360f,"#FFFFFF",22f)
        rect(17f,17f,34f,34f,"#FFFFFF",2f,red);clock("02",18f,18f,32f,32f,18f,red)
        text(d.day,66f,15f,276f,57f,41f,fg,true,minSize=24f)
        text(d.date,150f,70f,192f,21f,15f,muted)
        rect(226f,102f,89f,22f,red,8f)
        text(if(d.ui.live!=null)"الآن" else "القادمة",227f,102f,87f,22f,14f,"#FFFFFF",true,"center")
        section(d.section,190f,129f,152f,70f,64f,fg)
        clock(d.range,184f,199f,161f,24f,15f,muted,false)
        line(176f,104f,176f,221f,"#CFD0D1")
        clock(d.countText,20f,118f,143f,78f,if(d.minutes!=null)66f else 29f,fg)
        text(d.countLabel,20f,196f,143f,28f,14f,muted,align="center")
        d.upcoming.take(3).forEachIndexed { i,s ->
            val y=234f+30*i;line(17f,y,342f,y,"#D9DADD")
            section(DesignDay.section(s),215f,y+2,127f,27f,23f,fg)
            clock(DesignDay.clock(s.bell.start),17f,y+2,113f,27f,23f,fg,false)
        }
        if(d.upcoming.isEmpty()) text("هذه آخر حصة",25f,250f,310f,45f,17f,muted,align="center")
        line(17f,337f,96f,337f,red,2f)
        text("ينتهي دوامك ${d.finish}",113f,326f,230f,27f,14f,fg,true)
    }
    private fun bento() {
        val fg="#121925";val muted="#586578"
        rect(0f,0f,360f,360f,"#EEF0F4",24f)
        header("يومك الدراسي","03",fg,muted,"#DEE2E9")
        rect(146f,67f,198f,175f,"#275CF5",17f)
        text(d.status,158f,78f,171f,28f,17f,"#FFFFFF",true)
        section(d.section,159f,112f,171f,70f,64f,"#FFFFFF")
        text(d.subject,158f,183f,172f,26f,20f,"#FFFFFF",true,"center")
        clock(d.range,156f,211f,177f,23f,16f,"#DBE7FF",false)
        rect(16f,67f,122f,84f,"#FFE790",17f)
        clock(d.countText,23f,73f,108f,48f,if(d.minutes!=null)42f else 24f,fg)
        text(d.countLabel,22f,123f,110f,20f,11f,fg,true,"center")
        rect(16f,159f,122f,83f,"#FFD8C8",17f)
        icon("coffee",28f,172f,"#624334",21f)
        text(d.freeLabel,54f,173f,74f,23f,15f,"#624334",true)
        clock(d.freeValue,25f,204f,106f,29f,22f,"#624334",false)
        rect(16f,250f,328f,68f,"#FFFFFF",17f)
        text("الحصص القادمة",197f,251f,133f,18f,12f,fg,true)
        futureCells(268f,fg,muted)
        task(320f,"#FFFFFF",fg)
    }
    private fun route() {
        val fg="#F4F4F5";val muted="#B8B9C2";val amber="#FFC15A"
        rect(0f,0f,360f,360f,"#14171D",24f)
        header("مسار يومك","04",fg,muted,"#292D34")
        line(328f,72f,328f,337f,"#686D75",1f)
        rect(16f,77f,297f,65f,"#30281D",12f,"#72572D")
        circle(328f,98f,6f,amber)
        text(if(d.ui.live!=null)"الآن" else "القادمة",237f,87f,64f,30f,21f,amber,true)
        section(d.section,94f,82f,128f,31f,27f,fg)
        clock(d.range,95f,115f,137f,19f,14f,muted,false)
        rect(27f,88f,57f,43f,amber,8f)
        clock(d.countText,29f,87f,53f,29f,if(d.minutes!=null)26f else 12f,"#10141A")
        text(if(d.minutes!=null)"دقيقة" else "البداية",30f,114f,51f,15f,10f,"#10141A",true,"center")
        data class Stop(val title:String,val time:String,val order:Int)
        val stops=d.upcoming.map { Stop(DesignDay.section(it),DesignDay.clock(it.bell.start),it.period*10) }.toMutableList()
        if(d.breakRange!=null && (d.focus?.period ?: 8)<=3) stops.add(Stop("الفسحة",d.breakTime,35))
        stops.add(Stop("نهاية الدوام",d.finish,999))
        val visible=stops.sortedBy { it.order }.take(6)
        val step=184f/visible.size.coerceAtLeast(1)
        visible.forEachIndexed { i,s ->
            val y=150f+i*step
            circle(328f,y+16f,5f,fg,true,1.2f)
            text(s.title,63f,y,232f,24f,if(s.order==999)16f else 21f,fg,true)
            clock(s.time,213f,y+23f,82f,17f,13f,muted,false)
        }
    }
    private fun sport() {
        val fg="#F8F8F6";val lime="#D6FF42";val muted="#B0B3BB"
        rect(0f,0f,360f,360f,"#080808",24f)
        header("وقت التركيز","05",fg,muted,"#252A12")
        line(17f,64f,343f,64f,"#44464B")
        circle(246f,83f,4f,if(d.urgent)"#FF966C" else lime)
        text(d.status,102f,71f,137f,24f,15f,fg,true,"center")
        clock(d.countText,26f,94f,308f,91f,if(d.minutes!=null)91f else 54f,fg)
        text(d.countLabel,46f,190f,268f,27f,20f,fg,true,"center")
        rect(16f,218f,328f,42f,lime,12f)
        text("الفصل",286f,225f,47f,26f,17f,"#0E1407",true)
        section(d.section,200f,222f,83f,34f,28f,"#0E1407")
        line(180f,225f,180f,253f,"#809934")
        clock(d.range,25f,221f,146f,22f,17f,"#0E1407")
        text(d.period,28f,243f,135f,15f,10f,"#384321")
        val next=d.upcoming.firstOrNull()
        text("التالي",220f,265f,109f,18f,12f,muted)
        section(next?.let(DesignDay::section) ?: "—",209f,283f,127f,25f,27f,fg)
        clock(next?.let { DesignDay.clock(it.bell.start) } ?: "—",212f,309f,118f,16f,12f,muted,false)
        line(180f,270f,180f,321f,"#44464B")
        text("نهاية الدوام",29f,265f,126f,20f,12f,muted)
        clock(d.finish,27f,285f,132f,35f,31f,fg)
        rect(16f,332f,328f,22f,"#202125",8f)
        icon("note",28f,335f,fg,16f)
        text("ملاحظة الحصة",61f,332f,267f,22f,12f,fg,true)
    }
    private fun blueprint() {
        val fg="#174BA0";val muted="#4776A7";val grid="#D4E6F8"
        rect(0f,0f,360f,360f,"#E9F2FC",18f,"#99C7FC")
        c.save(); val clip=Path().apply{ addRoundRect(RectF(1f,1f,359f,359f),18f,18f,Path.Direction.CW) };c.clipPath(clip)
        for(v in 20..340 step 20){line(v.toFloat(),1f,v.toFloat(),359f,grid,.3f);line(1f,v.toFloat(),359f,v.toFloat(),grid,.3f)};c.restore()
        header("مخطط اليوم","06",fg,muted,"#DBEAFB")
        rect(16f,77f,328f,81f,"#EFF7FF",11f,fg)
        circle(324f,92f,4f,"#EB7465")
        text(if(d.ui.live!=null)"الآن" else "القادمة",247f,82f,68f,20f,14f,"#CD5147",true)
        section(d.section,243f,102f,89f,36f,33f,fg)
        text(d.period,244f,138f,89f,15f,10f,fg,true)
        line(237f,93f,237f,143f,"#AFCCEF")
        clock(d.range,84f,103f,146f,30f,18f,fg)
        rect(26f,88f,50f,58f,"#E9F2FC",8f,fg)
        clock(d.countText,28f,92f,46f,33f,if(d.minutes!=null)29f else 12f,fg)
        text(if(d.minutes!=null)"دقيقة" else "البداية",29f,125f,44f,16f,10f,fg,true,"center")
        rect(16f,170f,328f,106f,"#F3F8FE",8f,"#BEDAF8")
        rect(16f,170f,328f,25f,"#DCEBFB",8f)
        listOf("البداية","الفصل","الحصة").forEachIndexed{i,s->text(s,19f+108*i,171f,104f,23f,13f,fg,true,"center")}
        line(125f,170f,125f,276f,"#BEDAF8");line(235f,170f,235f,276f,"#BEDAF8")
        d.upcoming.take(3).forEachIndexed { i,s ->
            val y=196f+26*i
            clock(DesignDay.clock(s.bell.start),19f,y,104f,25f,16f,fg,false)
            section(DesignDay.section(s),127f,y,106f,25f,16f,fg)
            clock(s.period.toString(),237f,y,104f,25f,16f,fg,false)
            line(16f,y+26,344f,y+26,"#BEDAF8")
        }
        task(286f,"#DBEAFB",fg)
        text("الفسحة ${d.breakTime}",192f,330f,137f,22f,13f,fg,true)
        text("النهاية ${d.finish}",29f,330f,137f,22f,13f,fg,true)
    }
}
