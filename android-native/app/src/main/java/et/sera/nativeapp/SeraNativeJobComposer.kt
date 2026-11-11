package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val nativeWorkTypes = listOf("Full-time", "Part-time", "Contract", "Internship")
private val jobTeal = Color(0xFF087E83)
private val jobInk = Color(0xFF18383D)

private data class JobDraft(
    val title: String,
    val company: String,
    val location: String,
    val workType: String,
    val salaryMin: Int,
    val salaryMax: Int,
    val description: String,
    val requirements: String
)

/** The server authenticates the bearer and validates the job again before RLS insertion. */
private fun publishNativeJob(token: String, job: JobDraft): Pair<String?, String> {
    var connection: HttpURLConnection? = null
    return try {
        val json = JSONObject()
            .put("title", job.title.trim())
            .put("company", job.company.trim())
            .put("location", job.location.trim())
            .put("work_type", job.workType)
            .put("salary_min", job.salaryMin)
            .put("salary_max", job.salaryMax)
            .put("description", job.description.trim())
            .put("requirements", job.requirements.trim())
            .put("posting_acknowledgement", true)
        connection = (URL("https://sera.hisabtech.com/api/native/jobs").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        val result = runCatching { JSONObject(response) }.getOrNull()
        if (status in 200..299 && result?.optBoolean("success") == true) {
            result.optString("job_id").takeIf { it.isNotBlank() } to ""
        } else {
            null to (result?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "Unable to publish this opportunity. Please retry.")
        }
    } catch (_: Exception) {
        null to "Network unavailable. Your job details are still here."
    } finally {
        connection?.disconnect()
    }
}

/** Full native publishing sheet; no browser redirect and no pretend-success states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeraNativeJobComposer(
    token: String,
    onDismiss: () -> Unit,
    onPublished: (String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var workType by rememberSaveable { mutableStateOf("Full-time") }
    var salaryMin by rememberSaveable { mutableStateOf("0") }
    var salaryMax by rememberSaveable { mutableStateOf("0") }
    var description by rememberSaveable { mutableStateOf("") }
    var requirements by rememberSaveable { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val minSalary = salaryMin.toIntOrNull()
    val maxSalary = salaryMax.toIntOrNull()
    val salaryValid = minSalary != null && maxSalary != null &&
        minSalary >= 0 && maxSalary >= minSalary
    val valid = title.trim().length in 3..160 &&
        company.trim().length in 2..160 &&
        location.trim().length in 2..160 &&
        description.trim().length in 40..12000 &&
        requirements.trim().length in 10..8000 &&
        salaryValid && acknowledged && workType in nativeWorkTypes
    val edited = title.isNotBlank() || company.isNotBlank() || location.isNotBlank() ||
        description.isNotBlank() || requirements.isNotBlank() || acknowledged
    fun requestClose() {
        if (sending) return
        if (edited) showDiscard = true else onDismiss()
    }

    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false },
        title = { Text("Discard your job draft?") },
        text = { Text("The information you entered will be lost if you close this editor.") },
        confirmButton = {
            TextButton(onClick = { showDiscard = false; onDismiss() }) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
        },
        shape = RoundedCornerShape(24.dp)
    )

    SeraSignatureSheet(onDismissRequest = { requestClose() }) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp)
                .imePadding().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Publish an opportunity", style = MaterialTheme.typography.titleLarge,
                        color = jobInk, fontWeight = FontWeight.Bold)
                    Text("A clear role brings the right people together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF687E81))
                }
                TextButton(onClick = { requestClose() }, enabled = !sending) { Text("Close") }
            }
            HorizontalDivider(color = Color(0xFFE2EEEC))
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Text("ROLE DETAILS", style = MaterialTheme.typography.labelMedium,
                    color = jobTeal, fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { if (it.length <= 160) title = it },
                    label = { Text("Job title *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp))
                OutlinedTextField(company, { if (it.length <= 160) company = it },
                    label = { Text("Organization *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp))
                OutlinedTextField(location, { if (it.length <= 160) location = it },
                    label = { Text("City or remote region *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = RoundedCornerShape(17.dp)) {
                        Text("Work type", modifier = Modifier.weight(1f), color = jobInk)
                        Text(workType, color = jobTeal, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text("⌄", color = jobTeal)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        nativeWorkTypes.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { workType = option; expanded = false },
                                leadingIcon = if (workType == option) {
                                    { Text("✓", color = jobTeal) }
                                } else null
                            )
                        }
                    }
                }
                Text("COMPENSATION", style = MaterialTheme.typography.labelMedium,
                    color = jobTeal, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(salaryMin, { if (it.length <= 10 && it.all(Char::isDigit)) salaryMin = it },
                        label = { Text("Minimum ETB") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, isError = minSalary == null,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(17.dp))
                    OutlinedTextField(salaryMax, { if (it.length <= 10 && it.all(Char::isDigit)) salaryMax = it },
                        label = { Text("Maximum ETB") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, isError = !salaryValid,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(17.dp))
                }
                Text("Use 0 and 0 when compensation is not specified.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF687E81))
                HorizontalDivider(color = Color(0xFFE2EEEC))
                Text("ABOUT THE OPPORTUNITY", style = MaterialTheme.typography.labelMedium,
                    color = jobTeal, fontWeight = FontWeight.Bold)
                OutlinedTextField(description, { if (it.length <= 12000) description = it },
                    label = { Text("Responsibilities and description *") },
                    modifier = Modifier.fillMaxWidth(), minLines = 5,
                    shape = RoundedCornerShape(17.dp),
                    supportingText = { Text("${description.length} / 12,000") })
                OutlinedTextField(requirements, { if (it.length <= 8000) requirements = it },
                    label = { Text("Experience and requirements *") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    shape = RoundedCornerShape(17.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it },
                        colors = CheckboxDefaults.colors(checkedColor = jobTeal))
                    Text("I am authorized to post this role. The details will be public. I will never request payments or sensitive financial information from applicants.",
                        modifier = Modifier.padding(top = 11.dp),
                        style = MaterialTheme.typography.bodySmall, color = jobInk)
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(color = Color(0xFFE2EEEC))
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { requestClose() }, enabled = !sending,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        if (!valid || sending || minSalary == null || maxSalary == null) return@Button
                        sending = true
                        error = ""
                        scope.launch {
                            val (id, reason) = withContext(Dispatchers.IO) {
                                publishNativeJob(token, JobDraft(title, company, location, workType,
                                    minSalary, maxSalary, description, requirements))
                            }
                            sending = false
                            if (id != null) onPublished(id) else error = reason
                        }
                    },
                    enabled = valid && !sending,
                    modifier = Modifier.weight(1.6f).heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = jobTeal),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Publishing…")
                    } else Text("Publish job")
                }
            }
        }
    }
}
