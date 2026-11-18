package cn.nebulae.timetable

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class Stop(val station: Int, val arrival: Int, val departure: Int)
data class Trip(val id: String, val destination: String, val stops: List<Stop>)
data class RailLine(
    val id: String,
    val name: String,
    val edition: String,
    val service: String,
    val source: String,
    val stations: List<String>,
    val ends: List<String>,
    val firstLast: List<List<String>>,
    val trips: List<Trip>,
    val branchAt: String
)
data class Arrival(val id: String, val arrival: Int, val departure: Int)

fun parseLine(value: JSONObject): RailLine {
    fun strings(array: JSONArray) = List(array.length()) { array.getString(it) }
    val stations = strings(value.getJSONArray("stations"))
    val ends = strings(value.getJSONArray("ends"))
    val table = value.getJSONArray("firstlast")
    require(stations.isNotEmpty() && stations.all { it.isNotBlank() }) { "车站不能为空" }
    require(ends.isNotEmpty() && ends.all { it in stations }) { "终点必须属于当前线路" }
    require(table.length() == stations.size) { "首末班表与车站数量不一致" }
    val firstLast = List(table.length()) { row ->
        val times = table.getJSONArray(row)
        List(times.length()) { index ->
            val time = if (times.isNull(index)) "" else times.getString(index)
            require(time.isEmpty() || Regex("\\d{2}:\\d{2}").matches(time) &&
                time.substringBefore(":").toInt() in 0..47 && time.substringAfter(":").toInt() in 0..59) { "首末班时间格式错误" }
            time
        }
    }
    val plans = value.getJSONArray("trips")
    val trips = List(plans.length()) { index ->
        val trip = plans.getJSONArray(index)
        val events = trip.getJSONArray(3)
        Trip(trip.getString(0), trip.getString(1), List(events.length()) { stopIndex ->
            val stop = events.getJSONArray(stopIndex)
            val station = stop.getInt(0)
            val arrival = stop.getInt(1)
            val departure = stop.getInt(2)
            require(station in stations.indices && arrival >= 0 && departure >= 0) { "停站记录无效" }
            Stop(station, arrival, departure)
        }).also { require(it.destination in ends) { "列车终点不在线路终点列表内" } }
    }
    return RailLine(
        value.getString("id").also { require(it.isNotBlank()) },
        value.getString("name").also { require(it.isNotBlank()) },
        value.optString("edition", "自定义时刻表"), value.optString("service"),
        value.optString("source"), stations, ends, firstLast, trips, value.optString("branchAt")
    )
}

fun serviceSeconds(hour: Int, minute: Int, second: Int = 0) =
    (if (hour < 4) hour + 24 else hour) * 3600 + minute * 60 + second

fun parseServiceTime(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val parts = value.split(":")
    if (parts.size != 2) return null
    return serviceSeconds(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
}

fun clockLabel(seconds: Int, includeSeconds: Boolean = false): String =
    if (includeSeconds) String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60)

fun nextArrivals(line: RailLine, station: String, destination: String, now: Int): List<Arrival> {
    val stationIndex = line.stations.indexOf(station)
    val direction = line.ends.indexOf(destination)
    if (stationIndex < 0 || direction < 0 || station == destination) return emptyList()
    val times = line.firstLast[stationIndex]
    val first = parseServiceTime(times.getOrNull(direction * 2))
    val last = parseServiceTime(times.getOrNull(direction * 2 + 1))
    val upper = if (first != null && last != null && last < first) last + 86400 else last
    return line.trips.asSequence().filter { it.destination == destination }.mapNotNull { trip ->
        val stop = trip.stops.firstOrNull { it.station == stationIndex } ?: return@mapNotNull null
        if (stop.departure < now || stop.departure - now > 5400 ||
            first != null && stop.departure < first - 85 ||
            upper != null && stop.departure > upper + 85) return@mapNotNull null
        Arrival(trip.id, stop.arrival, stop.departure)
    }.sortedWith(compareBy<Arrival> { it.arrival }.thenBy { it.departure }).take(2).toList()
}

fun arrow(line: RailLine, destination: String, station: String): String {
    val down = when (line.id) {
        "gangwan" -> destination == "新自由之丘"
        "xingdao" -> destination in listOf("昌原路", "铁山路")
        "jiangbei" -> destination == "红沙坪"
        "duhui" -> destination == "北堡公园"
        "zhongyang" -> destination in listOf("星辰湾", "下关大道")
        "shangyu" -> destination == "源深路"
        else -> line.stations.indexOf(destination) > line.stations.indexOf(station)
    }
    return if (down) "↓" else "↑"
}

class RailViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    var lines by mutableStateOf<List<RailLine>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var page by mutableStateOf(saved.get<String>("page") ?: "home")
        private set
    var lineId by mutableStateOf(saved.get<String>("line") ?: "")
        private set
    var station by mutableStateOf(saved.get<String>("station") ?: "北大桥")
        private set
    var search by mutableStateOf(saved.get<String>("search") ?: "")
        private set
    var simulated by mutableStateOf(saved.get<Int>("time"))
        private set
    var now by mutableStateOf(0)
        private set
    private val importMutex = Mutex()
    private val preferences = application.getSharedPreferences("rail_lines_native", 0)
    private var imported = JSONArray()

    val line get() = lines.first { it.id == lineId }
    val tab get() = when (page) { "home" -> 0; "firstlast" -> 1; else -> 2 }

    init {
        load()
        viewModelScope.launch {
            while (true) {
                updateClock()
                delay(1000)
            }
        }
    }

    fun load() {
        loading = true
        failure = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bundled = getApplication<Application>().assets.open("lines.json").bufferedReader().use {
                        val json = JSONArray(it.readText().removePrefix("\uFEFF"))
                        List(json.length()) { index -> parseLine(json.getJSONObject(index)) }
                    }
                    val custom = JSONArray(preferences.getString("lines", "[]"))
                    val extras = List(custom.length()) { parseLine(custom.getJSONObject(it)) }
                    imported = custom
                    (bundled + extras).distinctBy { it.id }
                }
            }.onSuccess { loaded ->
                lines = loaded
                if (lines.none { it.id == lineId }) lineId = lines.first().id
                if (station !in line.stations) station = line.stations.first()
                loading = false
            }.onFailure { error ->
                failure = error.message ?: "无法读取时刻表"
                loading = false
            }
        }
    }

    fun navigate(value: String) { page = value; save() }
    fun back() = navigate(if (page == "train") "choose" else "home")
    fun chooseLine(id: String) {
        lineId = id
        search = ""
        if (station !in line.stations) station = line.stations.first()
        save()
    }
    fun chooseStation(value: String) {
        station = value
        simulated = null
        updateClock()
        navigate("train")
    }
    fun query(value: String) { search = value; save() }
    fun time(hour: Int, minute: Int) { simulated = serviceSeconds(hour, minute); updateClock(); save() }
    fun realtime() { simulated = null; updateClock(); save() }
    fun refresh() { updateClock(); message = "已按时刻表刷新查询结果" }
    fun dismissMessage() { message = null }
    private fun updateClock() {
        val calendar = Calendar.getInstance()
        now = simulated ?: serviceSeconds(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
    }
    private fun save() {
        saved["page"] = page
        saved["line"] = lineId
        saved["station"] = station
        saved["search"] = search
        saved["time"] = simulated
    }

    fun importLine(read: () -> String) {
        viewModelScope.launch {
            importMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val raw = JSONObject(read().removePrefix("\uFEFF"))
                        val parsed = parseLine(raw)
                        require(lines.none { it.id == parsed.id }) { "线路标识已存在" }
                        val next = JSONArray(imported.toString()).put(raw)
                        check(preferences.edit().putString("lines", next.toString()).commit()) { "无法保存线路数据" }
                        imported = next
                        parsed
                    }
                }.onSuccess { added ->
                    lines = lines + added
                    chooseLine(added.id)
                    station = added.stations.first()
                    navigate("firstlast")
                    message = "已导入" + added.name
                }.onFailure { message = "导入失败：" + (it.message ?: "文件无法读取") }
            }
        }
    }
}
