package cn.nebulae.timetable

import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay
import java.util.Calendar

private val Brand = Color(0xFFD71935)
private val Ink = Color(0xFF23262B)
private val Muted = Color(0xFF6D7480)
private val Paper = Color(0xFFF5F6F8)
private val StrokeColor = Color(0xFFE9EBEF)
private val Green = Color(0xFF2AB66E)
private val Cyan = Color(0xFF12A6BA)
private val Labels = listOf("首页", "首末班", "到站提醒")
private val Icons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_schedule, R.drawable.ic_tab_arrivals)

private data class LineStyle(val short: String, val color: Color)
private fun style(line: RailLine): LineStyle = when (line.id) {
    "gangwan" -> LineStyle("港", Color(0xFFE73828))
    "xingdao" -> LineStyle("岛", Color(0xFF17C123))
    "jiangbei" -> LineStyle("江", Color(0xFF5758BB))
    "duhui" -> LineStyle("都", Color(0xFFF8A5C3))
    "zhongyang" -> LineStyle("中", Color(0xFF45AAF2))
    "shangyu" -> LineStyle("屿", Color(0xFF75C2C8))
    else -> LineStyle(line.id.take(1), Color(0xFF6F7A88))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent { RailApp() }
    }
}

@Composable
private fun Text(
    value: String, size: Int = 15, color: Color = Ink,
    bold: Boolean = false, modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Unspecified
) {
    BasicText(value, modifier, TextStyle(color = color, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        lineHeight = (size * 1.42).sp, textAlign = align))
}

@Composable
private fun GlassButton(
    label: String, backdrop: Backdrop, modifier: Modifier = Modifier,
    primary: Boolean = false, onClick: () -> Unit
) {
    LiquidButton(onClick, backdrop, modifier.heightIn(min = 44.dp),
        surfaceColor = if (primary) Brand.copy(alpha = .9f) else Color.White.copy(alpha = .65f),
        horizontalContentPadding = 12.dp) {
        Text(label, 13, if (primary) Color.White else Ink, bold = true)
    }
}

@Composable
private fun RailApp(model: RailViewModel = viewModel()) {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    val controlsBackdrop = rememberLayerBackdrop()
    val selectedTab = remember(model) { { model.tab } }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) model.importLine {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取文件")
        }
    }
    val onImport = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    BackHandler(model.page != "home") { model.back() }
    LaunchedEffect(model.message) {
        if (model.message != null) { delay(3500); model.dismissMessage() }
    }
    Box(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().clipToBounds()) {
        Box(Modifier.fillMaxSize().background(Paper).layerBackdrop(controlsBackdrop))
        if (model.loading || model.failure != null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(model.failure ?: "正在加载时刻表…")
                if (model.failure != null) GlassButton("重试", controlsBackdrop, onClick = model::load)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                val tablet = maxWidth >= 700.dp
                val content = Modifier.align(Alignment.TopCenter).widthIn(max = 1120.dp).fillMaxSize()
                Column(content) {
                    if (model.page != "home") {
                        Row(Modifier.fillMaxWidth().background(Color.White).heightIn(min = 64.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            LiquidButton(model::back, controlsBackdrop, Modifier.size(48.dp),
                                surfaceColor = Color.White.copy(alpha = .65f),
                                horizontalContentPadding = 0.dp) {
                                Image(painterResource(R.drawable.ic_back), "返回上一页", Modifier.size(24.dp))
                            }
                            Text(when (model.page) { "firstlast" -> "首末班车时刻表"; "choose" -> "请选择站点"; else -> "列车动态" },
                                19, bold = true, modifier = Modifier.weight(1f), align = TextAlign.Center)
                            Spacer(Modifier.width(48.dp))
                        }
                    }
                    when (model.page) {
                        "home" -> Home(model, controlsBackdrop, tablet, onImport)
                        "firstlast" -> FirstLast(model, onImport, tablet)
                        "choose" -> StationPicker(model, onImport, tablet)
                        else -> TrainPage(model, controlsBackdrop, tablet)
                    }
                }
            }
            LiquidBottomTabs(
                selectedTabIndex = selectedTab,
                onTabSelected = { model.navigate(listOf("home", "firstlast", "choose")[it]) },
                backdrop = backdrop,
                tabsCount = 3,
                containerHeight = 56.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 12.dp)
                    .widthIn(max = 360.dp)
            ) {
                Labels.forEachIndexed { index, label ->
                    LiquidBottomTab(onClick = { model.navigate(listOf("home", "firstlast", "choose")[index]) }) {
                        Image(painterResource(Icons[index]), null, Modifier.size(21.dp))
                        Text(label, 11)
                    }
                }
            }
        }
        model.message?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ink.copy(alpha = .94f)).padding(14.dp)) {
                Text(it, 13, Color.White)
            }
        }
    }
}

@Composable
private fun Bubble(line: RailLine, small: Boolean = false) {
    val meta = style(line)
    Box(Modifier.size(if (small) 25.dp else 38.dp).clip(CircleShape).background(meta.color),
        contentAlignment = Alignment.Center) { Text(meta.short, if (small) 11 else 16, Color.White, true) }
}

@Composable
private fun Logo() {
    val context = LocalContext.current
    val bitmap = remember { runCatching { context.assets.open("logo.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
    if (bitmap != null) Image(bitmap, "星云轨道交通标识", Modifier.size(42.dp))
    else Image(painterResource(R.drawable.ic_train_foreground), "星云轨道交通标识",
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brand))
}

@Composable
private fun Home(model: RailViewModel, backdrop: Backdrop, tablet: Boolean, onImport: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (tablet) 2 else 1),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.background(Color.White).padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("星云轨道交通", 19, bold = true)
                    Text("XINGYUN RAIL TRANSIT", 9, Muted)
                }
                GlassButton("线路管理 ＋", backdrop, onClick = onImport)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFB70B28), Brand, Color(0xFFF26D65)))).heightIn(min = 222.dp).padding(horizontal = 24.dp, vertical = 26.dp)) {
                Column(Modifier.fillMaxWidth(.72f)) {
                    Text("智慧出行 · 星云同行", 12, Color(0xFFFFDCE3))
                    Text("轻松查询\n首末班与到站时刻", if (tablet) 34 else 29, Color.White, true, Modifier.padding(top = 13.dp))
                    Text("真实运营图数据 · 离线也能查", 12, Color(0xFFFFDCE3), modifier = Modifier.padding(top = 12.dp))
                }
                TrainIllustration(Modifier.align(Alignment.BottomEnd).offset(x = 31.dp, y = 12.dp).size(178.dp, 116.dp).rotate(-11f))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeAction("首末班车", "各线路各站首末班时刻", R.drawable.ic_tab_schedule, Brand,
                    Modifier.weight(1f)) { model.navigate("firstlast") }
                HomeAction("到站提醒", "查看各方向下两班车", R.drawable.ic_tab_arrivals, Cyan,
                    Modifier.weight(1f)) { model.navigate("choose") }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("已接入线路", 17, bold = true, modifier = Modifier.padding(18.dp, 4.dp, 18.dp, 12.dp)) }
        items(model.lines, key = { it.id }) { line ->
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Bubble(line)
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.name, 15, bold = true)
                        Text("已接入", 11, Green)
                    }
                    Text("${line.edition} · ${line.stations.size}站 · ${line.trips.size}个列车计划行程", 12, Muted, modifier = Modifier.padding(top = 5.dp))
                }
                Box(Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, StrokeColor, RoundedCornerShape(9.dp))
                    .clickable { model.chooseLine(line.id); model.navigate("firstlast") }.padding(10.dp)) { Text("查看", 12, Muted) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("继续接入更多线路", 13, bold = true)
                    Text("可以导入符合数据格式的 JSON 时刻表", 11, Muted)
                }
                GlassButton("导入线路", backdrop, primary = true, onClick = onImport)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Note("本应用按导入的运营时刻表计算，并非列车实时定位系统。\n" +
                if (model.lines.first().id == "sample") "当前内置合成示例数据，可导入自己的线路。"
                else "港湾线及江北线为双休日图，星岛线为假期图，都会线、中央线、上屿线为工作日图；请核对日期。")
        }
    }
}

@Composable
private fun HomeAction(label: String, caption: String, icon: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White)
        .clickable(onClick = onClick).heightIn(min = 142.dp).padding(12.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(icon), null, Modifier.size(49.dp).clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = .12f)).padding(12.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent))
            Text(label, 16, bold = true, modifier = Modifier.padding(top = 12.dp))
            Text(caption, 11, Muted, modifier = Modifier.padding(top = 5.dp), align = TextAlign.Center)
        }
    }
}

@Composable
private fun Note(value: String) { Text(value, 11, Muted, modifier = Modifier.padding(18.dp, 10.dp)) }

@Composable
private fun LinesBar(model: RailViewModel, onImport: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("全部 ⌄", 12, Muted)
        model.lines.forEach { line ->
            val selected = model.lineId == line.id
            Box(Modifier.clip(CircleShape).background(if (selected) Brand else Paper)
                .clickable { model.chooseLine(line.id) }.padding(horizontal = 15.dp, vertical = 10.dp)) {
                Text(line.name, 13, if (selected) Color.White else Muted, selected)
            }
        }
        Box(Modifier.clip(CircleShape).border(1.dp, StrokeColor, CircleShape).clickable(onClick = onImport).padding(12.dp)) { Text("＋添加线路", 13, Muted) }
    }
}

@Composable
private fun FirstLast(model: RailViewModel, onImport: () -> Unit, tablet: Boolean) {
    Column {
        LinesBar(model, onImport)
        BasicTextField(
            value = model.search, onValueChange = model::query, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = Ink),
            modifier = Modifier.padding(16.dp, 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp),
            decorationBox = { field -> Box { if (model.search.isEmpty()) Text("搜索站名，例如 清水湾", 14, Muted); field() } }
        )
        Text("${model.line.name} · ${model.line.edition}　｜　各终点分别列示", 12, Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp))
        val stations = model.line.stations.withIndex().filter { model.search.trim().isEmpty() || it.value.contains(model.search.trim()) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (tablet) 2 else 1),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (stations.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Note("没有找到匹配车站") }
            items(stations, key = { it.index }) { station -> StationCard(model.line, station.index) }
            item(span = { GridItemSpan(maxLineSpan) }) { Note("首末班时间依据各线路文件中的首末班表；源表未单列的终点方向显示 --:--，不编造时刻。\n“--:--”表示该站无相应方向服务。") }
        }
    }
}

@Composable
private fun StationCard(line: RailLine, index: Int) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp)) {
        Text(line.stations[index], 18, bold = true)
        if (line.stations[index] == line.branchAt) Text("主支线接轨站", 11, Muted)
        line.ends.forEachIndexed { direction, end ->
            Row(Modifier.fillMaxWidth().padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("开往 ${end} 方向", 12, modifier = Modifier.weight(1f))
                TimeBadge("首", line.firstLast[index].getOrNull(direction * 2), Green)
                TimeBadge("末", line.firstLast[index].getOrNull(direction * 2 + 1), Brand)
            }
        }
    }
}

@Composable
private fun TimeBadge(label: String, value: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(19.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(label, 10, Color.White) }
        Text(value?.takeIf { it.isNotEmpty() } ?: "--:--", 13, if (value.isNullOrBlank()) Color(0xFF98A0AC) else Ink)
    }
}

@Composable
private fun StationPicker(model: RailViewModel, onImport: () -> Unit, tablet: Boolean) {
    Row(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(Modifier.width(if (tablet) 160.dp else 110.dp).fillMaxHeight().background(Paper),
            contentPadding = PaddingValues(bottom = 88.dp)) {
            items(model.lines, key = { it.id }) { line ->
                Box(Modifier.fillMaxWidth().background(if (model.lineId == line.id) Color.White else Paper)
                    .clickable { model.chooseLine(line.id) }.padding(vertical = 23.dp, horizontal = 12.dp)) {
                    Text(line.name, 14, if (model.lineId == line.id) Brand else Muted, model.lineId == line.id)
                }
            }
            item { Box(Modifier.clickable(onClick = onImport).padding(14.dp)) { Text("＋ 添加线路", 12, Muted) } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { Note("${model.line.name} · 选择车站查看到发情况") }
            items(model.line.stations, key = { it }) { station ->
                Row(Modifier.fillMaxWidth().clickable { model.chooseStation(station) }.padding(start = 20.dp, end = 12.dp)
                    .heightIn(min = 71.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(station, 16, bold = true, modifier = Modifier.weight(1f))
                    model.lines.filter { station in it.stations }.sortedBy { if (it.id == model.lineId) 0 else 1 }.forEach { Bubble(it, true) }
                    if (station == model.line.branchAt || model.line.id == "gangwan" && station in listOf("徐州路", "昕北")) {
                        Box(Modifier.size(25.dp).clip(CircleShape).background(Color(0xFFD79B20)), contentAlignment = Alignment.Center) { Text("支", 11, Color.White) }
                    }
                }
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(StrokeColor))
            }
            item { Note("点击站名可查看开往各终点的下两班列车。\n换乘站右侧显示可换乘线路的颜色圆圈，并在列车动态页联动显示其他线路到站信息。") }
        }
    }
}

private data class Direction(val line: RailLine, val destination: String)

@Composable
private fun TrainPage(model: RailViewModel, backdrop: Backdrop, tablet: Boolean) {
    val context = LocalContext.current
    var selectedTime by rememberSaveable { mutableStateOf(-1) }
    val directions = remember(model.lines, model.lineId, model.station) {
        model.lines.filter { model.station in it.stations }.sortedBy { if (it.id == model.lineId) 0 else 1 }
            .flatMap { line ->
                val index = line.stations.indexOf(model.station)
                line.ends.filter { end -> end != model.station && line.trips.any { trip -> trip.destination == end && trip.stops.any { it.station == index } } }
                    .map { Direction(line, it) }
            }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (tablet) 2 else 1),
        contentPadding = PaddingValues(bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(if (tablet) 180.dp else 152.dp).background(Brush.linearGradient(listOf(Color(0xFFB6DAE6), Color(0xFFE8F4F7), Color(0xFF80B4CB)))), contentAlignment = Alignment.Center) {
                TrainIllustration(Modifier.align(Alignment.BottomEnd).widthIn(max = 600.dp).fillMaxWidth(.9f).height(140.dp), true)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.background(Color.White).padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前站点　›", 12, Muted)
                    Text("${model.line.name} · ${model.line.edition}", 11, Muted, modifier = Modifier.weight(1f), align = TextAlign.End)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.station, 27, bold = true, modifier = Modifier.weight(1f))
                    GlassButton("⟳ 刷新", backdrop, onClick = model::refresh)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(15.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (model.simulated != null) "模拟查询时间" else "当前手机时间", 12, Muted, modifier = Modifier.weight(1f))
                    Text(clockLabel(model.now) + if (model.simulated != null) "　⏱ 预览" else "　● 实时钟", 16, bold = true)
                }
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).heightIn(min = 46.dp).border(1.dp, StrokeColor, RoundedCornerShape(8.dp))
                        .clickable {
                            val time = if (selectedTime >= 0) selectedTime else model.now
                            TimePickerDialog(context, { _, h, m -> selectedTime = serviceSeconds(h, m) },
                                time / 3600 % 24, time / 60 % 60, true).show()
                        }.padding(12.dp)) {
                        Text(clockLabel(if (selectedTime >= 0) selectedTime else model.now), 15)
                    }
                    GlassButton("查询时刻", backdrop, primary = true) {
                        val time = if (selectedTime >= 0) selectedTime else model.now
                        model.time(time / 3600 % 24, time / 60 % 60)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Paper).clickable { selectedTime = -1; model.realtime() }.padding(12.dp)) { Text("当前时间", 12) }
                }
                Text(serviceNotice(model), 11, Color(0xFF987020), modifier = Modifier.padding(top = 12.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("各线路各终点下两班车", 16, bold = true, modifier = Modifier.weight(1f))
                Box(Modifier.clickable { model.navigate("choose") }.padding(10.dp)) { Text("切换车站 ›", 12, Muted) }
            }
        }
        val transfers = model.lines.filter { it.id != model.lineId && model.station in it.stations }
        if (transfers.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("当前站可换乘：" + transfers.joinToString("　") { it.name }, 12, Muted,
                modifier = Modifier.padding(16.dp, 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFEAF4FA)).padding(12.dp))
        }
        if (directions.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Note("该站暂无可选方向，请返回选择其他车站。") }
        items(directions, key = { it.line.id + "/" + it.destination }) { direction ->
            DirectionCard(direction, model)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Note("到站时间及发车时间精确到秒。依据运行图停站记录筛选计划班次；换乘站同步显示同名换乘线路到站信息。部分首班、空载及特殊短交路应以正式运行图为准。新接入的 PDF 未列明每一列车次号，因此显示“计划列次”。\n夜间 00:xx 按前一运营日末班衔接处理。")
        }
    }
}

private fun serviceNotice(model: RailViewModel): String {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val weekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
    return when {
        model.simulated != null -> "当前为模拟时间预览，不代表手机现在的时间。"
        model.line.service == "holiday" -> "当前选择假期版时刻表，请确认运营日采用该版本。以下为计划时刻，非现场实时定位。"
        model.line.service == "weekend" && !weekend -> "今天不是双休日：此线路的双休日时刻仅供预览。"
        model.line.service == "weekday" && weekend -> "今天是双休日：此线路当前仅有工作日运行图，不能作为今日班次依据。"
        model.line.id == "shangyu" -> "上屿线所用 PDF 为 Draft 草案，计划可能调整；不属于现场实时列车信息。"
        else -> "按运行图推算，不代表实际晚点或信号系统实时位置。"
    }
}

@Composable
private fun DirectionCard(direction: Direction, model: RailViewModel) {
    val line = direction.line
    val arrivals = remember(line, model.station, direction.destination, model.now) {
        nextArrivals(line, model.station, direction.destination, model.now)
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(27.dp).clip(CircleShape).background(style(line).color), contentAlignment = Alignment.Center) {
                Text(arrow(line, direction.destination, model.station), 17, Color.White, true)
            }
            Column(Modifier.weight(1f)) {
                Text((if (line.id != model.lineId) line.name + " · " else "") + "开往 " + direction.destination, 15, bold = true)
                Text(if (line.id == model.lineId) "本线下两班车" else "换乘线下两班车", 11, Muted)
            }
        }
        Column(Modifier.padding(top = 9.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)) {
            if (arrivals.isEmpty()) Text(
                if (model.now >= 22 * 3600) "本时段已收班或没有后续班次" else "当前时段没有可查询的载客班次",
                13, Muted, modifier = Modifier.padding(18.dp))
            arrivals.forEachIndexed { index, arrival ->
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("第${index + 1}班", 15, bold = true)
                        Text(if (index == 0) "最近一班" else "后续一班", 11, Muted)
                        Text((if (arrival.id == "未标注") "计划列次（原图未标车次号）" else "车次 " + arrival.id) +
                            if (arrival.arrival == arrival.departure) " · 始发或通过" else " · 停站", 11, Muted, modifier = Modifier.padding(top = 5.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(clockLabel(arrival.arrival) + " 到", 20, bold = true)
                        Text(clockLabel(arrival.departure) + " 发", 12, Muted)
                        val seconds = arrival.arrival - model.now
                        val remaining = when {
                            arrival.arrival <= model.now && arrival.departure >= model.now -> "当前正在停站"
                            seconds < 0 -> "列车已到站"
                            seconds < 60 -> "${seconds} 秒后到站"
                            else -> "还有 ${seconds / 60} 分" + if (seconds % 60 > 0) " ${seconds % 60} 秒" else ""
                        }
                        Text(remaining, 12, if (seconds < 180) Brand else Green, true, Modifier.padding(top = 5.dp))
                    }
                }
                if (index != arrivals.lastIndex) Spacer(Modifier.fillMaxWidth().height(1.dp).background(StrokeColor))
            }
        }
    }
}
