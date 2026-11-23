package com.aibridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BridgeApp() }
    }
}

data class ChatMessage(val role: String, val text: String)

@Composable
fun BridgeApp() {
    val messages = remember { mutableStateListOf(ChatMessage("assistant", "AI Bridge ready. Configure your API endpoint and model in Settings to begin.")) }
    var input by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://openrouter.ai/api/v1") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF8B9CFF), background = Color(0xFF101116),
        surface = Color(0xFF1A1B23), secondary = Color(0xFF53D6B2)
    )) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101116)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("AI BRIDGE", fontSize = 22.sp, color = Color.White)
                    Text("Android agent client • 0.1.0", color = Color.Gray)
                }
                Button(onClick = { showSettings = !showSettings }) { Text("Settings") }
            }
            if (showSettings) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
                        Text("Keys are currently session-only. Don't share screenshots containing them.", color = Color.Gray)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (msg.role == "user") Color(0xFF29304A) else Color(0xFF202128))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (msg.role == "user") "YOU" else "AI", color = Color(0xFF9BA8FF), fontSize = 11.sp)
                            Text(msg.text, color = Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask the agent...") })
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    val prompt = input.trim()
                    input = ""
                    messages.add(ChatMessage("user", prompt))
                    busy = true
                    scope.launch {
                        val result = AiClient.chat(baseUrl, model, apiKey, messages.toList())
                        messages.add(ChatMessage("assistant", result))
                        busy = false
                    }
                }) { Text(if (busy) "..." else "Send") }
            }
        }
    }
}

object AiClient {
    suspend fun chat(baseUrl: String, model: String, apiKey: String, messages: List<ChatMessage>): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (model.isBlank() || apiKey.isBlank()) return@withContext "Open Settings and enter your model ID and API key."
                val client = okhttp3.OkHttpClient()
                val jsonMessages = org.json.JSONArray()
                jsonMessages.put(org.json.JSONObject().put("role", "system").put("content",
                    "You are AI Bridge, a helpful Android assistant. You can explain and plan tasks. Do not claim to have performed device actions unless a tool actually executed them."))
                messages.forEach { jsonMessages.put(org.json.JSONObject().put("role", it.role).put("content", it.text)) }
                val body = org.json.JSONObject().put("model", model).put("messages", jsonMessages)
                val request = okhttp3.Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext "API error ${response.code}: ${raw.take(500)}"
                    val root = org.json.JSONObject(raw)
                    root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
            } catch (e: Exception) {
                "Request failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
}
