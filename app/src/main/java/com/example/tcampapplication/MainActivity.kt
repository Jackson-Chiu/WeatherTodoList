package com.example.tcampapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tcampapplication.ui.theme.TcampApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TcampApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WeatherTodoApp()
                }
            }
        }
    }
}

// 数据模型
data class WeatherInfo(
    val city: String = "未知",
    val temperature: String = "--",
    val condition: String = "加载中",
    val conditionIcon: String = "",
    val humidity: String = "--",
    val windSpeed: String = "--",
    val feelsLike: String = "--"
)

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// 城市列表
val availableCities = listOf(
    "深圳", "北京", "上海", "广州", "杭州",
    "成都", "南京", "武汉", "西安", "厦门"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherTodoApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedCity by remember { mutableStateOf("深圳") }
    var showCityDialog by remember { mutableStateOf(false) }

    var weatherInfo by remember { mutableStateOf(WeatherInfo()) }
    var isLoadingWeather by remember { mutableStateOf(true) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var todoList by remember { mutableStateOf(loadTodoList(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newTodoText by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }

    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var editTodoText by remember { mutableStateOf("") }
    var editDueDate by remember { mutableStateOf<Long?>(null) }

    // 加载天气信息
    LaunchedEffect(selectedCity) {
        scope.launch {
            try {
                val weather = fetchWeatherData(selectedCity)
                weatherInfo = weather
                weatherError = null
                isLoadingWeather = false
            } catch (e: Exception) {
                weatherError = "网络错误: ${e.message}"
                weatherInfo = WeatherInfo(
                    city = selectedCity,
                    temperature = "28",
                    condition = "Partly cloudy",
                    humidity = "75",
                    windSpeed = "15",
                    feelsLike = "30"
                )
                isLoadingWeather = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = getWeatherGradient(weatherInfo.condition)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "今日计划",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 天气卡片
                item {
                    WeatherCard(
                        weatherInfo = weatherInfo,
                        isLoading = isLoadingWeather,
                        error = weatherError,
                        onRefresh = {
                            isLoadingWeather = true
                            weatherError = null
                            scope.launch {
                                try {
                                    val weather = fetchWeatherData(selectedCity)
                                    weatherInfo = weather
                                    weatherError = null
                                } catch (e: Exception) {
                                    weatherError = "刷新失败: ${e.message}"
                                }
                                isLoadingWeather = false
                            }
                        },
                        onCityClick = { showCityDialog = true }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "待办事项",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.9f)
                            ),
                            modifier = Modifier
                                .height((ButtonDefaults.MinHeight.value * 2 / 3).dp)
                                .wrapContentWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                            Spacer(Modifier.width(4.dp))
                            Text("添加")
                        }
                    }
                }

                item {
                    TodoListWindow(
                        todoList = todoList,
                        onToggle = { id ->
                            todoList = todoList.map {
                                if (it.id == id) it.copy(isCompleted = !it.isCompleted)
                                else it
                            }
                            saveTodoList(context, todoList)
                        },
                        onDelete = { id ->
                            todoList = todoList.filter { it.id != id }
                            saveTodoList(context, todoList)
                        },
                        onEdit = { id ->
                            val todoToEdit = todoList.find { it.id == id }
                            if (todoToEdit != null) {
                                editingTodo = todoToEdit
                                editTodoText = todoToEdit.text
                                editDueDate = todoToEdit.dueDate
                            }
                        }
                    )
                }
            }
        }

        // 城市选择对话框
        if (showCityDialog) {
            AlertDialog(
                onDismissRequest = { showCityDialog = false },
                title = { Text("选择城市") },
                text = {
                    LazyColumn(
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(availableCities) { city ->
                            ListItem(
                                headlineContent = { Text(city) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCity = city
                                        showCityDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCityDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        if (showAddDialog) {
            AddTodoDialog(
                newTodoText = newTodoText,
                selectedDueDate = selectedDueDate,
                onTextChange = { newTodoText = it },
                onDueDateChange = { selectedDueDate = it },
                onConfirm = {
                    if (newTodoText.isNotBlank()) {
                        todoList = todoList + TodoItem(
                            text = newTodoText.trim(),
                            dueDate = selectedDueDate
                        )
                        saveTodoList(context, todoList)
                        newTodoText = ""
                        selectedDueDate = null
                        showAddDialog = false
                    }
                },
                onDismiss = {
                    showAddDialog = false
                    newTodoText = ""
                    selectedDueDate = null
                }
            )
        }

        // 编辑待办事项对话框
        if (editingTodo != null) {
            EditTodoDialog(
                todoText = editTodoText,
                dueDate = editDueDate,
                onTextChange = { editTodoText = it },
                onDueDateChange = { editDueDate = it },
                onConfirm = {
                    if (editTodoText.isNotBlank()) {
                        todoList = todoList.map { todo ->
                            if (todo.id == editingTodo?.id) {
                                todo.copy(text = editTodoText.trim(), dueDate = editDueDate)
                            } else todo
                        }
                        saveTodoList(context, todoList)
                        editingTodo = null
                        editTodoText = ""
                        editDueDate = null
                    }
                },
                onDismiss = {
                    editingTodo = null
                    editTodoText = ""
                    editDueDate = null
                }
            )
        }
    }
}

@Composable
fun AddTodoDialog(
    newTodoText: String,
    selectedDueDate: Long?,
    onTextChange: (String) -> Unit,
    onDueDateChange: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加待办事项") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = newTodoText,
                    onValueChange = onTextChange,
                    label = { Text("输入待办内容") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (selectedDueDate != null) {
                            "截止日期: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(selectedDueDate))}"
                        } else {
                            "截止日期: 未设置"
                        },
                        fontSize = 14.sp
                    )

                    Row {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("设置")
                        }

                        if (selectedDueDate != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onDueDateChange(null) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("清除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = newTodoText.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showDatePicker) {
        val calendar = remember { Calendar.getInstance() }
        val datePicker = rememberDatePicker(
            initialDateMillis = selectedDueDate ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            context = context,
            onDateSelected = { year, month, day ->
                TimePickerDialog(
                    context = context,
                    onTimeSelected = { hour, minute ->
                        calendar.set(year, month, day, hour, minute)
                        onDueDateChange(calendar.timeInMillis)
                        showDatePicker = false
                    },
                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                    initialMinute = calendar.get(Calendar.MINUTE)
                ).show()
            },
            initialYear = calendar.get(Calendar.YEAR),
            initialMonth = calendar.get(Calendar.MONTH),
            initialDay = calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

@Composable
fun EditTodoDialog(
    todoText: String,
    dueDate: Long?,
    onTextChange: (String) -> Unit,
    onDueDateChange: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑待办事项") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = todoText,
                    onValueChange = onTextChange,
                    label = { Text("编辑待办内容") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (dueDate != null) {
                            "截止日期: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(dueDate))}"
                        } else {
                            "截止日期: 未设置"
                        },
                        fontSize = 14.sp
                    )

                    Row {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("设置")
                        }

                        if (dueDate != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onDueDateChange(null) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("清除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = todoText.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showDatePicker) {
        val calendar = remember { Calendar.getInstance() }
        val datePicker = rememberDatePicker(
            initialDateMillis = dueDate ?: System.currentTimeMillis()
        )

        // 日期+时间选择器
        DatePickerDialog(
            context = context,
            onDateSelected = { year, month, day ->
                TimePickerDialog(
                    context = context,
                    onTimeSelected = { hour, minute ->
                        calendar.set(year, month, day, hour, minute)
                        onDueDateChange(calendar.timeInMillis)
                        showDatePicker = false
                    },
                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                    initialMinute = calendar.get(Calendar.MINUTE)
                ).show()
            },
            initialYear = calendar.get(Calendar.YEAR),
            initialMonth = calendar.get(Calendar.MONTH),
            initialDay = calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

@Composable
fun WeatherCard(
    weatherInfo: WeatherInfo,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onCityClick: () -> Unit
) {
    val imageRes = when {
        weatherInfo.condition.contains("sunny", ignoreCase = true) ||
                weatherInfo.condition.contains("clear", ignoreCase = true) -> R.drawable.sunny_weather
        weatherInfo.condition.contains("rain", ignoreCase = true) ||
                weatherInfo.condition.contains("drizzle", ignoreCase = true) -> R.drawable.rainy_weather
        weatherInfo.condition.contains("cloud", ignoreCase = true) ||
                weatherInfo.condition.contains("overcast", ignoreCase = true) -> R.drawable.cloudy_weather
        weatherInfo.condition.contains("mist", ignoreCase = true) ||
                weatherInfo.condition.contains("fog", ignoreCase = true) -> R.drawable.mist_weather
        else -> R.drawable.default_weather
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("正在获取天气信息...", fontSize = 14.sp, color = Color.White)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.clickable { onCityClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = weatherInfo.city,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "选择城市",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = getCurrentTime(),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50)
                            )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = weatherInfo.temperature,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "°C",
                                fontSize = 24.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    getWeatherIcon(weatherInfo.condition),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    getChineseCondition(weatherInfo.condition),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "体感: ${weatherInfo.feelsLike}°C",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            "湿度: ${weatherInfo.humidity}%",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            "风速: ${weatherInfo.windSpeed} km/h",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    // 错误提示
                    if (error != null) {
                        Text(
                            text = error,
                            fontSize = 11.sp,
                            color = Color(0xFFFFB3B3),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodoItemCard(
    todo: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (todo.isCompleted)
                Color.White.copy(alpha = 0.6f)
            else
                Color.White.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.clickable(enabled = false) {
                    onToggle()
                }
            ) {
                Checkbox(
                    checked = todo.isCompleted,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.Gray,
                        uncheckedColor = Color.Gray.copy(alpha = 0.6f)
                    )
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.text,
                    style = if (todo.isCompleted) {
                        MaterialTheme.typography.bodyLarge.copy(
                            color = Color.Gray,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
                    }
                )

                if (todo.dueDate != null) {
                    val dueDateStr = SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(Date(todo.dueDate))
                    val isOverdue = todo.dueDate < System.currentTimeMillis() && !todo.isCompleted

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "截止时间",
                            modifier = Modifier.size(12.dp),
                            tint = if (isOverdue) Color.Red else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "截止: $dueDateStr",
                            fontSize = 12.sp,
                            color = if (isOverdue) Color.Red else Color.Gray,
                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.clickable(enabled = false) {}
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.Gray
                )
            }
        }
    }
}

fun getWeatherGradient(condition: String): Brush {
    return when {
        condition.contains("sunny", ignoreCase = true) ||
                condition.contains("clear", ignoreCase = true) ->
            Brush.verticalGradient(listOf(Color(0xFF4A90E2), Color(0xFF50C9C3)))
        condition.contains("rain", ignoreCase = true) ||
                condition.contains("drizzle", ignoreCase = true) ->
            Brush.verticalGradient(listOf(Color(0xFF5F7D8E), Color(0xFF8BA3B1)))
        condition.contains("cloud", ignoreCase = true) ||
                condition.contains("overcast", ignoreCase = true) ->
            Brush.verticalGradient(listOf(Color(0xFF7B92A8), Color(0xFFA8C0D8)))
        condition.contains("mist", ignoreCase = true) ||
                condition.contains("fog", ignoreCase = true) ->
            Brush.verticalGradient(listOf(Color(0xFF8C98A9), Color(0xFFB0BEC5)))
        else ->
            Brush.verticalGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2)))
    }
}

fun getWeatherIcon(condition: String) = when {
    condition.contains("sunny", ignoreCase = true) ||
            condition.contains("clear", ignoreCase = true) -> Icons.Default.WbSunny
    condition.contains("rain", ignoreCase = true) ||
            condition.contains("drizzle", ignoreCase = true) -> Icons.Default.BeachAccess
    condition.contains("cloud", ignoreCase = true) ||
            condition.contains("overcast", ignoreCase = true) -> Icons.Default.Cloud
    condition.contains("mist", ignoreCase = true) ||
            condition.contains("fog", ignoreCase = true) -> Icons.Default.BlurOn
    else -> Icons.Default.WbSunny
}

fun getChineseCondition(condition: String): String {
    return when {
        condition.contains("sunny", ignoreCase = true) -> "晴天"
        condition.contains("clear", ignoreCase = true) -> "晴朗"
        condition.contains("partly cloudy", ignoreCase = true) -> "多云"
        condition.contains("cloudy", ignoreCase = true) -> "阴天"
        condition.contains("overcast", ignoreCase = true) -> "阴天"
        condition.contains("rain", ignoreCase = true) -> "下雨"
        condition.contains("drizzle", ignoreCase = true) -> "小雨"
        condition.contains("mist", ignoreCase = true) -> "薄雾"
        condition.contains("fog", ignoreCase = true) -> "大雾"
        else -> condition
    }
}

fun getCurrentTime(): String {
    val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
    return sdf.format(Date())
}

// 网络请求
suspend fun fetchWeatherData(city: String = "深圳"): WeatherInfo = withContext(Dispatchers.IO) {
    try {
        val apiKey = "4ddc4ca070d34ea78da125612250912"
        val queryCity = city
        val url = "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=$queryCity&aqi=no"

        val response = URL(url).readText()
        val json = JSONObject(response)
        val locationInfo = json.getJSONObject("location")
        val currentWeather = json.getJSONObject("current")
        val weatherCondition = currentWeather.getJSONObject("condition")

        WeatherInfo(
            city = locationInfo.getString("name"),
            temperature = currentWeather.getDouble("temp_c").toInt().toString(),
            condition = weatherCondition.getString("text"),
            conditionIcon = weatherCondition.getString("icon"),
            humidity = currentWeather.getInt("humidity").toString(),
            windSpeed = currentWeather.getDouble("wind_kph").toInt().toString(),
            feelsLike = currentWeather.getDouble("feelslike_c").toInt().toString()
        )
    } catch (e: Exception) {
        e.printStackTrace()
        throw RuntimeException("获取天气数据失败: ${e.message}", e)
    }
}

// SharedPreferences 本地存储
fun saveTodoList(context: android.content.Context, list: List<TodoItem>) {
    val prefs = context.getSharedPreferences("todo_prefs", android.content.Context.MODE_PRIVATE)
    val json = org.json.JSONArray()
    list.forEach { todo ->
        val obj = org.json.JSONObject()
        obj.put("id", todo.id)
        obj.put("text", todo.text)
        obj.put("dueDate", todo.dueDate ?: JSONObject.NULL)
        obj.put("isCompleted", todo.isCompleted)
        obj.put("timestamp", todo.timestamp)
        json.put(obj)
    }
    prefs.edit().putString("todo_list", json.toString()).apply()
}

fun loadTodoList(context: android.content.Context): List<TodoItem> {
    val prefs = context.getSharedPreferences("todo_prefs", android.content.Context.MODE_PRIVATE)
    val jsonString = prefs.getString("todo_list", "[]") ?: "[]"
    val json = org.json.JSONArray(jsonString)
    val list = mutableListOf<TodoItem>()
    for (i in 0 until json.length()) {
        val obj = json.getJSONObject(i)

        val dueDate = try {
            if (!obj.isNull("dueDate")) obj.getLong("dueDate") else null
        } catch (e: Exception) {
            null
        }

        list.add(
            TodoItem(
                id = obj.getString("id"),
                text = obj.getString("text"),
                dueDate = dueDate,
                isCompleted = obj.getBoolean("isCompleted"),
                timestamp = obj.getLong("timestamp")
            )
        )
    }
    return list
}

// 日期/时间选择器
class DatePickerDialog(
    context: android.content.Context,
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int
) {
    private val dialog = android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            onDateSelected(year, month, day)
        },
        initialYear,
        initialMonth,
        initialDay
    )

    fun show() {
        dialog.show()
    }
}

class TimePickerDialog(
    context: android.content.Context,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    initialHour: Int,
    initialMinute: Int
) {
    private val dialog = android.app.TimePickerDialog(
        context,
        { _, hour, minute ->
            onTimeSelected(hour, minute)
        },
        initialHour,
        initialMinute,
        true
    )

    fun show() {
        dialog.show()
    }
}

@Composable
fun rememberDatePicker(initialDateMillis: Long): Calendar {
    val calendar = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialDateMillis
        }
    }
    return calendar
}

// TodoListWindow 组件
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoListWindow(
    todoList: List<TodoItem>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    // 排序逻辑：
    // 1. 未完成的优先
    // 2. 有截止日期的优先，且截止日期越近越靠前
    // 3. 无截止日期的按创建时间倒序（最新创建的在前）
    val sortedTodos = remember(todoList) {
        todoList.sortedWith(
            compareBy<TodoItem> { it.isCompleted }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenByDescending { it.timestamp }
        )
    }

    val listState = rememberLazyListState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8E8E8)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (sortedTodos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "暂无待办事项",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedTodos, key = { it.id }) { todo ->
                    TodoItemCard(
                        todo = todo,
                        onToggle = { onToggle(todo.id) },
                        onDelete = { onDelete(todo.id) },
                        onEdit = { onEdit(todo.id) }
                    )
                }
            }

            if (listState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f))
                            )
                        )
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "向下滑动查看更多",
                        modifier = Modifier.align(Alignment.Center),
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}