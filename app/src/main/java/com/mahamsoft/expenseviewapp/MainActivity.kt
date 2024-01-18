package com.mahamsoft.expenseviewapp

import android.Manifest
import android.content.pm.PackageManager
import android.icu.text.NumberFormat
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.mahamsoft.expenseviewapp.ui.theme.ExpenseViewAppTheme
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var creditKeywords = listOf<String>()
    private var debitKeywords = listOf<String>()

    companion object {
        private const val SMS_PERMISSION_CODE = 101
    }

    private var isLoading = mutableStateOf(true)
    private var smsMessages = listOf<SmsMessage>()
    private var allSmsMessages = listOf<SmsMessage>()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var totalCredit = mutableStateOf(0.0)
    private var totalDebit = mutableStateOf(0.0)

    private var showDialog = mutableStateOf(false)
    private val timePeriodOptions = listOf("Month", "Year", "Overall")
    private var selectedTimePeriod = mutableStateOf("Month")

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var keywordViewModel: KeywordViewModel

    // Handle the permissions result
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSmsMessages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS),
                SMS_PERMISSION_CODE
            )
        }

        databaseHelper = DatabaseHelper(this)
        keywordViewModel = ViewModelProvider(
            this,
            KeywordViewModelFactory(databaseHelper)
        ).get(KeywordViewModel::class.java)

        loadKeywordsFromDatabase()

        keywordViewModel.message.observe(this, Observer { msg ->
            if (msg.isNotEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        })

        setContent {
            ExpenseViewAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        if (isLoading.value) {
                            LoadingScreen()
                        } else {
                            BottomTabs(
                                smsMessages,
                                keywordViewModel,
                                onRefresh = { loadSmsMessages() })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadSmsMessages()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel() // Cancel any running coroutines when the activity is destroyed
    }

    private fun loadKeywordsFromDatabase() {
        val db = databaseHelper.readableDatabase

        coroutineScope.launch {
            val keywords = withContext(Dispatchers.IO) {
                databaseHelper.getAllKeywords(db)
            }

            creditKeywords = keywords.filter { it.type == "Credit" }.map { it.keyword }
            debitKeywords = keywords.filter { it.type == "Debit" }.map { it.keyword }
        }
    }

    private fun extractAmount(smsText: String): Double {
        val regexPattern =
            "(Amount\\s+(of\\s+)?(PKR\\s+|Rs\\.?\\s+)?\\d+(,\\d{3})*(\\.\\d+)?)|(PKR\\s+\\d+(,\\d{3})*(\\.\\d+)?)|(Rs\\.?\\s+\\d+(,\\d{3})*(\\.\\d+)?)|\\d+(,\\d+)*(\\.\\d+)? amount".toRegex(
                RegexOption.IGNORE_CASE
            )

        val matchResult = regexPattern.find(smsText)

        val numericPart = matchResult?.value
            ?.replace("[^\\d.]".toRegex(), "")  // Remove everything except digits and dot
            ?.trim('.')                         // Trim leading and trailing dots

        //Log.d("SmsReader", (("Amount: " + numericPart?.toDoubleOrNull()) ?: 0.0).toString())

        return numericPart?.toDoubleOrNull() ?: 0.0
    }

    private fun loadSmsMessages() {
        coroutineScope.launch {
            isLoading.value = true // Show loading

            withContext(Dispatchers.IO) {
                if (allSmsMessages.isEmpty()) {
                    val smsReader = SmsReader(this@MainActivity)
                    allSmsMessages = smsReader.readAllSms()
                }

                filterAndCalculateTotals()
            }

            isLoading.value = false // Hide loading
        }
    }

    private fun filterAndCalculateTotals() {
        coroutineScope.launch {
            var creditSum = 0.0
            var debitSum = 0.0

            withContext(Dispatchers.Main) {
                isLoading.value = true // Show loading

                // Filter messages based on the selected time period
                val filteredMessages = when (selectedTimePeriod.value) {
                    "Month" -> filterMessagesByMonth(allSmsMessages)
                    "Year" -> filterMessagesByYear(allSmsMessages)
                    else -> allSmsMessages // "Overall"
                }

                smsMessages = filteredMessages.mapNotNull { sms ->
                    val isCredit = creditKeywords.any { keyword ->
                        sms.text.contains(keyword, ignoreCase = true)
                    }

                    val isDebit = debitKeywords.any { keyword ->
                        sms.text.contains(keyword, ignoreCase = true)
                    }

                    if (isCredit || isDebit) {
                        val amount = extractAmount(sms.text)
                        if (isCredit) creditSum += amount
                        if (isDebit) debitSum += amount
                        SmsMessage(sms.date, sms.text, iscredit = isCredit, amount = amount)
                    } else {
                        null
                    }
                }

                totalCredit.value = creditSum.roundToDecimalPlaces(0)
                totalDebit.value = debitSum.roundToDecimalPlaces(0)
                isLoading.value = false // Hide loading
            }
        }
    }

    fun Double.roundToDecimalPlaces(places: Int): Double {
        return BigDecimal(this).setScale(places, RoundingMode.HALF_UP).toDouble()
    }

    @Composable
    fun LoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Loading...", style = MaterialTheme.typography.titleLarge)
        }
    }

    @Composable
    fun SelectTimePeriodDialog() {
        if (showDialog.value) {
            Dialog(onDismissRequest = { showDialog.value = false }) {
                Surface(
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        timePeriodOptions.forEach { option ->
                            Text(
                                text = option,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTimePeriod.value = option
                                        showDialog.value = false
                                        //onRefresh()
                                    }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun filterMessagesByMonth(messages: List<SmsMessage>): List<SmsMessage> {
        val currentMonthYear = SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date())
        return messages.filter {
            val smsMonthYear = it.date.substring(3)  // Extract MM/yyyy part from dd/MM/yyyy
            smsMonthYear == currentMonthYear
        }
    }

    private fun filterMessagesByYear(messages: List<SmsMessage>): List<SmsMessage> {
        val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        return messages.filter {
            val smsYear = it.date.takeLast(4)  // Extract yyyy part from dd/MM/yyyy
            smsYear == currentYear
        }
    }

    @Composable
    fun BottomTabs(
        smsMessages: List<SmsMessage>,
        keywordViewModel: KeywordViewModel,
        onRefresh: () -> Unit
    ) {
        val tabs = listOf("Home", "Keywords")
        val selectedTabIndex = remember { mutableStateOf(0) }
        val bottomTabHeight = 56.dp

        val showDialogKeyword = remember { mutableStateOf(false) }
        val keyword = remember { mutableStateOf("") }
        val type = remember { mutableStateOf("Credit") }

        val showDialogEdit = remember { mutableStateOf(false) }
        val editingKeyword = remember { mutableStateOf(Keyword()) }

        val showDialogDelete = remember { mutableStateOf(false) }
        val deletingKeywordId = remember { mutableStateOf(0) }

        // LaunchedEffect to filter messages when selectedTimePeriod changes
        LaunchedEffect(selectedTimePeriod.value) {
            filterAndCalculateTotals()
        }

        Box(modifier = Modifier.fillMaxSize()) {

            TabRow(
                selectedTabIndex = selectedTabIndex.value,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex.value]),
                        color = Color.Blue // Custom color for the tab indicator
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                containerColor = Color(0xFFE7E7E7), // Custom container color for the TabRow
                contentColor = Color(0xFFE7E7E7) // Custom content color for unselected tabs
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex.value == index,
                        onClick = { selectedTabIndex.value = index },
                        selectedContentColor = Color.Black,
                        unselectedContentColor = Color.DarkGray
                    )
                }
            }

            if (selectedTabIndex.value == 0) {

                Column {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF006400))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White // Set the text color to white
                        )
                    }

                    // Button to select time period
                    Button(
                        onClick = { showDialog.value = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(text = "Select Time Period: ${selectedTimePeriod.value}")
                    }

                    // Custom dialog for selecting time period
                    SelectTimePeriodDialog()

                    // New Row for Total Credit and Total Debit boxes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 20.dp)
                    ) {
                        // Total Credit box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF006400))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isLoading.value) "Loading..." else "Credit: ${
                                    formatAmount(
                                        totalCredit.value
                                    )
                                }",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Total Debit box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFC20000))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isLoading.value) "Loading..." else "Debit: ${
                                    formatAmount(
                                        totalDebit.value
                                    )
                                }",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.padding(
                            bottom = bottomTabHeight,
                            start = 10.dp,
                            end = 10.dp
                        )
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE0E0E0))
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Date",
                                    modifier = Modifier.wrapContentWidth(),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Text",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Amount",
                                    modifier = Modifier.wrapContentWidth(),
                                    textAlign = TextAlign.Right,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            //Spacer(modifier = Modifier.height(16.dp))
                        }

                        items(smsMessages) { sms ->
                            val isExpanded = remember { mutableStateOf(false) }
                            val smsBody =
                                if (isExpanded.value) sms.text else sms.text.take(50) + if (sms.text.length > 50) "..." else ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .background(
                                        if (sms.iscredit) Color(0xFFDAFFDB) else Color(
                                            0xFFFFDBD9
                                        )
                                    )
                            ) {
                                Text(
                                    text = sms.date,
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .padding(8.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = smsBody,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp)
                                        .clickable { isExpanded.value = !isExpanded.value }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatAmount(sms.amount),
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .padding(8.dp),
                                    textAlign = TextAlign.Right
                                )

                            }

                            //Divider(color = Color.LightGray, thickness = 1.dp)
                        }
                    }
                }

                // Floating Refresh Button
                /*
                FloatingActionButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp, end = 16.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                */

            } else if (selectedTabIndex.value == 1) {

                // Floating Button to Add Keyword
                FloatingActionButton(
                    onClick = {
                        showDialogKeyword.value = true
                        keyword.value = ""
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp, end = 16.dp)
                        .zIndex(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Keyword")
                }

                // Dialog for Adding Keyword
                if (showDialogKeyword.value) {
                    Dialog(onDismissRequest = { showDialogKeyword.value = false }) {
                        Surface(
                            modifier = Modifier.padding(16.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                TextField(
                                    value = keyword.value,
                                    onValueChange = { keyword.value = it },
                                    label = { Text("Keyword") }
                                )

                                Row(Modifier.padding(top = 16.dp)) {
                                    RadioButton(
                                        selected = type.value == "Credit",
                                        onClick = { type.value = "Credit" }
                                    )
                                    Text(
                                        "Credit",
                                        modifier = Modifier.clickable { type.value = "Credit" })
                                    Spacer(Modifier.width(16.dp))
                                    RadioButton(
                                        selected = type.value == "Debit",
                                        onClick = { type.value = "Debit" }
                                    )
                                    Text(
                                        "Debit",
                                        modifier = Modifier.clickable { type.value = "Debit" })
                                }

                                Button(
                                    onClick = {
                                        // Handle form submission by adding a keyword
                                        val newKeyword =
                                            Keyword(keyword = keyword.value, type = type.value)
                                        keywordViewModel.addKeyword(newKeyword)
                                        showDialogKeyword.value = false
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                Column {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF006400))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Keywords",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White // Set the text color to white
                        )
                    }

                    // Display Keywords List
                    val keywordsList =
                        keywordViewModel.keywordsFlow.collectAsState(initial = listOf()).value

                    LazyColumn(
                        modifier = Modifier.padding(
                            bottom = bottomTabHeight
                        )
                    ) {

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE0E0E0))
                            ) {
                                Text(
                                    text = "Keyword",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp, horizontal = 20.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Action",
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .padding(vertical = 8.dp, horizontal = 20.dp),
                                    textAlign = TextAlign.Right,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            //Spacer(modifier = Modifier.height(16.dp))
                        }

                        items(keywordsList) { keyword ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp, horizontal = 10.dp)
                                    .background(
                                        if (keyword.type == "Credit") Color(0xFFDAFFDB) else Color(
                                            0xFFFFDBD9
                                        )
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = keyword.keyword,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 20.dp, horizontal = 10.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Button(onClick = {
                                    editingKeyword.value = keyword
                                    showDialogEdit.value = true
                                }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(Modifier.width(5.dp))

                                Button(onClick = {
                                    deletingKeywordId.value = keyword.id
                                    showDialogDelete.value = true
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                }

                // Edit Keyword Dialog
                if (showDialogEdit.value) {

                    val (selectedType, onTypeSelected) = remember(editingKeyword.value) {
                        mutableStateOf(editingKeyword.value.type)
                    }

                    Dialog(onDismissRequest = { showDialogEdit.value = false }) {
                        Surface(
                            modifier = Modifier.padding(16.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                TextField(
                                    value = editingKeyword.value.keyword,
                                    onValueChange = {
                                        editingKeyword.value =
                                            editingKeyword.value.copy(keyword = it)
                                    },
                                    label = { Text("Keyword") }
                                )

                                val types = listOf("Credit", "Debit")
                                Column {
                                    types.forEach { text ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = text == selectedType,
                                                onClick = {
                                                    onTypeSelected(text)
                                                    editingKeyword.value =
                                                        editingKeyword.value.copy(type = text)
                                                }
                                            )
                                            Text(
                                                text = text,
                                                modifier = Modifier.clickable {
                                                    onTypeSelected(text)
                                                    editingKeyword.value =
                                                        editingKeyword.value.copy(type = text)
                                                }
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        keywordViewModel.updateKeyword(editingKeyword.value)
                                        showDialogEdit.value = false
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("Update")
                                }
                            }
                        }
                    }
                }

                // Delete Keyword Confirmation Dialog
                if (showDialogDelete.value) {
                    AlertDialog(
                        onDismissRequest = { showDialogDelete.value = false },
                        title = { Text("Confirm Delete") },
                        text = { Text("Are you sure you want to delete this keyword?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    keywordViewModel.deleteKeyword(deletingKeywordId.value)
                                    showDialogDelete.value = false
                                }
                            ) {
                                Text("Confirm")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showDialogDelete.value = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

            }
        }
    }

    private fun formatAmount(amount: Double): String {
        val numberFormat = NumberFormat.getNumberInstance()
        return numberFormat.format(amount)
    }

}
