package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BudgetPlan
import com.example.data.ReceiptAnalysisResult
import com.example.data.Transaction
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen navigation state
    var currentTab by remember { mutableStateOf(0) } // 0: Ringkasan, 1: Transaksi & Scan, 2: Analisis & Setelan

    // Collect States
    val month by viewModel.selectedMonth.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val period by viewModel.selectedPeriod.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val budgetPlans by viewModel.budgetPlans.collectAsState()
    
    // Scan States
    val isScanning by viewModel.isScanning.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val userApiKey by viewModel.userApiKey.collectAsState()

    // Compute metrics
    val incomeActual = transactions.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val savingsActual = transactions.filter { it.type == "Tabungan" }.sumOf { it.amount }
    val billsActual = transactions.filter { it.type == "Tagihan" && it.isChecked }.sumOf { it.amount }
    val expensesActual = transactions.filter { it.type == "Pengeluaran" }.sumOf { it.amount }

    val incomePlanned = budgetPlans.filter { it.category == "Pemasukan" }.sumOf { it.plannedAmount }
    val savingsPlanned = budgetPlans.filter { it.category == "Tabungan" }.sumOf { it.plannedAmount }
    val billsPlanned = budgetPlans.filter { it.category == "Tagihan" }.sumOf { it.plannedAmount }
    val expensesPlanned = budgetPlans.filter { it.category == "Pengeluaran" }.sumOf { it.plannedAmount }

    val totalOutflowActual = savingsActual + billsActual + expensesActual
    val totalOutflowPlanned = savingsPlanned + billsPlanned + expensesPlanned

    val remainingBudgetActual = incomeActual - totalOutflowActual
    val remainingBudgetPlanned = incomePlanned - totalOutflowPlanned

    val remainingBudgetPercentage = if (incomeActual > 0) {
        (remainingBudgetActual / incomeActual) * 100
    } else {
        0.0
    }

    // Modal dialog states
    var showAddTxDialog by remember { mutableStateOf(false) }
    var showAddPlanDialog by remember { mutableStateOf(false) }
    var showDeletePeriodDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        bottomBar = {
            // Elegant glowing bottom bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, CharcoalCard, RoundedCornerShape(24.dp)),
                color = CharcoalCard.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(72.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard", tint = if (currentTab == 0) PremiumOrange else MutedText) },
                        label = { Text("MacoSheet", color = if (currentTab == 0) PremiumOrange else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PremiumOrange.copy(alpha = 0.15f))
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Scan", tint = if (currentTab == 1) PremiumOrange else MutedText) },
                        label = { Text("Scan / Transaksi", color = if (currentTab == 1) PremiumOrange else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PremiumOrange.copy(alpha = 0.15f))
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Analisis", tint = if (currentTab == 2) PremiumOrange else MutedText) },
                        label = { Text("Analisis & Setelan", color = if (currentTab == 2) PremiumOrange else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PremiumOrange.copy(alpha = 0.15f))
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab != 2) {
                FloatingActionButton(
                    onClick = { showAddTxDialog = true },
                    containerColor = PremiumOrange,
                    contentColor = SoftWhite,
                    shape = CircleShape,
                     modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Transaksi")
                }
            }
        },
        containerColor = DeepDarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Header: Maco Title + Period Selectors
            FinanceHeader(
                month = month,
                year = year,
                onMonthChange = { viewModel.setMonth(it) },
                onYearChange = { viewModel.setYear(it) },
                onResetClick = { showDeletePeriodDialog = true }
            )

            HorizontalDivider(color = DarkBorder, thickness = 1.dp)

            when (currentTab) {
                0 -> DashboardTab(
                    period = period,
                    transactions = transactions,
                    budgetPlans = budgetPlans,
                    incomeActual = incomeActual,
                    savingsActual = savingsActual,
                    billsActual = billsActual,
                    expensesActual = expensesActual,
                    incomePlanned = incomePlanned,
                    savingsPlanned = savingsPlanned,
                    billsPlanned = billsPlanned,
                    expensesPlanned = expensesPlanned,
                    remainingBudgetActual = remainingBudgetActual,
                    remainingBudgetPercentage = remainingBudgetPercentage,
                    onToggleCheck = { tx -> viewModel.updateTransaction(tx.copy(isChecked = !tx.isChecked)) },
                    onDeleteTx = { tx -> viewModel.deleteTransaction(tx.id) },
                    onAddPlanClick = { showAddPlanDialog = true }
                )
                1 -> TransactionAndScanTab(
                    transactions = transactions,
                    isScanning = isScanning,
                    scanResult = scanResult,
                    userApiKey = userApiKey,
                    onScanClick = { bytes -> viewModel.scanReceiptImage(bytes) },
                    onConfirmScan = { name, amt, cat, dt -> viewModel.confirmReceiptTransaction(name, amt, cat, dt) },
                    onClearScan = { viewModel.clearScanResult() },
                    onDeleteTx = { tx -> viewModel.deleteTransaction(tx.id) }
                )
                2 -> AnalysisAndSettingsTab(
                    incomeActual = incomeActual,
                    savingsActual = savingsActual,
                    billsActual = billsActual,
                    expensesActual = expensesActual,
                    incomePlanned = incomePlanned,
                    savingsPlanned = savingsPlanned,
                    billsPlanned = billsPlanned,
                    expensesPlanned = expensesPlanned,
                    transactions = transactions,
                    userApiKey = userApiKey,
                    onSaveApiKey = { viewModel.saveUserApiKey(it) }
                )
            }
        }
    }

    // Modal dialogs implementation details
    if (showAddTxDialog) {
        AddTransactionDialog(
            budgetPlans = budgetPlans,
            onDismiss = { showAddTxDialog = false },
            onSave = { name, amount, type, categoryName, isChecked, notes ->
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDateStr = formatter.format(Date())
                viewModel.insertTransaction(
                    Transaction(
                        timestamp = System.currentTimeMillis(),
                        dateString = currentDateStr,
                        monthYear = period,
                        type = type,
                        categoryName = categoryName,
                        name = name,
                        amount = amount,
                        isChecked = isChecked,
                        notes = notes
                    )
                )
                showAddTxDialog = false
            }
        )
    }

    if (showAddPlanDialog) {
        AddPlanDialog(
            onDismiss = { showAddPlanDialog = false },
            onSave = { name, amount, category, notes ->
                viewModel.addBudgetPlan(
                    BudgetPlan(
                        monthYear = period,
                        category = category,
                        name = name,
                        plannedAmount = amount,
                        notes = notes
                    )
                )
                showAddPlanDialog = false
            }
        )
    }

    if (showDeletePeriodDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePeriodDialog = false },
            containerColor = CharcoalSurface,
            title = { Text("Risest Data periode?", color = SoftWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin mereset data rencana anggaran dan transaksi untuk periode $period kembali ke setelan standard Spreadsheet MACO?", color = MutedText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreDefaultSpreadsheet()
                        showDeletePeriodDialog = false
                        Toast.makeText(context, "Data $period berhasil direset!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PremiumOrange)
                ) {
                    Text("Ok, Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePeriodDialog = false }) {
                    Text("Batal", color = MutedText)
                }
            }
        )
    }
}

// FORMAT RUPIAH EXCELLENCE HELPER
fun formatRupiah(value: Double): String {
    val formatter = java.text.NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return try {
        val result = formatter.format(value)
        // clean up formatting from standard ID locale
        result.replace("Rp", "Rp ").replace(",00", "")
    } catch (e: Exception) {
        "Rp %.0f".format(value)
    }
}

@Composable
fun FinanceHeader(
    month: String,
    year: Int,
    onMonthChange: (String) -> Unit,
    onYearChange: (Int) -> Unit,
    onResetClick: () -> Unit
) {
    var showMonthMenu by remember { mutableStateOf(false) }
    var showYearMenu by remember { mutableStateOf(false) }

    val monthsList = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
    val yearsList = listOf(2025, 2026, 2027, 2028)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CharcoalSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo and title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(PremiumOrange, NeonOrangeAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "MACO",
                    color = SoftWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    "Money Control",
                    color = PremiumOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Dropdowns for Period Budget matching Spreadsheet layout
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Button(
                    onClick = { showMonthMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(month, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PremiumOrange, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showMonthMenu,
                    onDismissRequest = { showMonthMenu = false },
                    modifier = Modifier.background(CharcoalCard)
                ) {
                    monthsList.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, color = SoftWhite, fontSize = 12.sp) },
                            onClick = {
                                onMonthChange(m.take(3)) // store as e.g. "Mei" (3 letters match spreadsheet)
                                showMonthMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box {
                Button(
                    onClick = { showYearMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(year.toString(), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PremiumOrange, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showYearMenu,
                    onDismissRequest = { showYearMenu = false },
                    modifier = Modifier.background(CharcoalCard)
                ) {
                    yearsList.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y.toString(), color = SoftWhite, fontSize = 12.sp) },
                            onClick = {
                                onYearChange(y)
                                showYearMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Reset spreadsheet button
            IconButton(
                onClick = onResetClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(CharcoalCard, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Spreadsheet", tint = PremiumOrange, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ==================== TAB 0: DASHBOARD TAB ====================
@Composable
fun DashboardTab(
    period: String,
    transactions: List<Transaction>,
    budgetPlans: List<BudgetPlan>,
    incomeActual: Double,
    savingsActual: Double,
    billsActual: Double,
    expensesActual: Double,
    incomePlanned: Double,
    savingsPlanned: Double,
    billsPlanned: Double,
    expensesPlanned: Double,
    remainingBudgetActual: Double,
    remainingBudgetPercentage: Double,
    onToggleCheck: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit,
    onAddPlanClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Sisa Anggaran Hero
        item {
            SisaAnggaranHero(
                actualAmount = remainingBudgetActual,
                percent = remainingBudgetPercentage
            )
        }

        // 1.5 Fallback In-App Weekend Alert Notice
        item {
            FinansialHealthAlertCard(remainingBudget = remainingBudgetActual)
        }

        // 2. Cashflow Sheet Summary Table (Plan vs Actual)
        item {
            CashflowTableCard(
                incomeAct = incomeActual,
                incomePl = incomePlanned,
                savingsAct = savingsActual,
                savingsPl = savingsPlanned,
                billsAct = billsActual,
                billsPl = billsPlanned,
                expensesAct = expensesActual,
                expensesPl = expensesPlanned,
                onAddPlan = onAddPlanClick
            )
        }

        // 3. Tabungan Detail Sheet Card
        item {
            TabunganSheetCard(
                plans = budgetPlans.filter { it.category == "Tabungan" },
                transactions = transactions.filter { it.type == "Tabungan" },
                onDeleteTx = onDeleteTx
            )
        }

        // 4. Tagihan Detail Sheet Card
        item {
            TagihanSheetCard(
                plans = budgetPlans.filter { it.category == "Tagihan" },
                transactions = transactions.filter { it.type == "Tagihan" },
                onToggleCollect = onToggleCheck,
                onDeleteTx = onDeleteTx
            )
        }

        // 5. Biaya Hidup Checklist Card (Spreadsheet style)
        item {
            LivingExpensesChecklistCard(
                plans = budgetPlans.filter { it.category == "Pengeluaran" },
                transactions = transactions.filter { it.type == "Pengeluaran" },
                onToggleCheck = onToggleCheck,
                onDeleteTx = onDeleteTx
            )
        }
    }
}

@Composable
fun SisaAnggaranHero(actualAmount: Double, percent: Double) {
    val isNegative = actualAmount < 0
    val gradientColors = if (isNegative) {
        listOf(CrimsonRed.copy(alpha = 0.2f), CharcoalCard)
    } else {
        listOf(PremiumOrange.copy(alpha = 0.15f), CharcoalCard)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(gradientColors),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, if (isNegative) CrimsonRed.copy(alpha = 0.5f) else PremiumOrange.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "SISA ANGGARAN AKTUAL",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                formatRupiah(actualAmount),
                color = if (isNegative) CrimsonRed else SoftWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // Status Pills
            Surface(
                color = if (isNegative) CrimsonRed.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isNegative) CrimsonRed else EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "%.2f%% dari pemasukan".format(percent),
                        color = if (isNegative) SoftRed else SoftGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FinansialHealthAlertCard(remainingBudget: Double) {
    val context = LocalContext.current
    val isNegative = remainingBudget < 0
    val alertTitle: String
    val alertDesc: String
    val alertColor: Color
    val alertIcon = if (isNegative) Icons.Default.Warning else if (remainingBudget < 500000.0) Icons.Default.Info else Icons.Default.CheckCircle

    if (isNegative) {
        alertTitle = "⚠️ Evaluasi Anggaran MACO"
        alertDesc = "Kesehatan keuangan Anda saat ini defisit ${formatRupiah(kotlin.math.abs(remainingBudget))}. Pertimbangkan untuk mengurangi pengeluaran sekunder/hiburan demi kestabilan anggaran."
    } else if (remainingBudget < 500000.0) {
        alertTitle = "👀 Peringatan: Anggaran Menipis!"
        alertDesc = "Sisa anggaran periode saat ini kurang dari Rp500.000 (tersisa ${formatRupiah(remainingBudget)}). Harap lebih berhati-hati dan prioritaskan kebutuhan wajib terlebih dahulu."
    } else {
        alertTitle = "✨ Weekend Insight: Keuangan Sehat!"
        alertDesc = "Luar biasa! Sisa anggaran Anda aman sebesar ${formatRupiah(remainingBudget)}. Pola pencatatan Anda rapi dan berada di jalur yang tepat!"
    }

    val alertColorVal = if (isNegative) SoftRed else if (remainingBudget < 500000.0) SoftYellow else SoftGreen

    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, alertColorVal.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Toast.makeText(context, "Asisten Pengingat Finansial In-App Aktif!", Toast.LENGTH_SHORT).show()
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(alertColorVal.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = alertIcon,
                    contentDescription = null,
                    tint = alertColorVal,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alertTitle,
                        color = SoftWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    
                    Text(
                        text = "Weekend Insight",
                        color = PremiumOrange,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alertDesc,
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                Surface(
                    color = CharcoalCard,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mode Alternatif Aktif: Memantau langsung dari dashboard aplikasi.",
                            color = MutedText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CashflowTableCard(
    incomeAct: Double, incomePl: Double,
    savingsAct: Double, savingsPl: Double,
    billsAct: Double, billsPl: Double,
    expensesAct: Double, expensesPl: Double,
    onAddPlan: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp, 16.dp).background(PremiumOrange))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cashflow Summary", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                
                IconButton(onClick = onAddPlan, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add Budget Target", tint = PremiumOrange, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))

            // Spreadsheet headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCard, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text("Klasifikasi", modifier = Modifier.weight(1.2f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Rencana (Plan)", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Aktual", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // CashflowRows
            CashflowRow("Pemasukan", incomePl, incomeAct, SoftGreen)
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            CashflowRow("Tabungan", savingsPl, savingsAct, SoftYellow)
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            CashflowRow("Pengeluaran (Living)", expensesPl, expensesAct, NeonOrangeAccent)
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            CashflowRow("Tagihan Bulanan", billsPl, billsAct, SoftBlue)

            HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Total flows rows
            val totalPl = incomePl - (savingsPl + billsPl + expensesPl)
            val totalAct = incomeAct - (savingsAct + billsAct + expensesAct)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Margin Bersih", modifier = Modifier.weight(1.2f), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(formatRupiah(totalPl), modifier = Modifier.weight(1f), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text(formatRupiah(totalAct), modifier = Modifier.weight(1f), color = if (totalAct >= 0) SoftGreen else SoftRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun CashflowRow(title: String, plan: Double, actual: Double, highlightColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(highlightColor))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(formatRupiah(plan), modifier = Modifier.weight(1f), color = SoftWhite.copy(alpha = 0.85f), fontSize = 11.sp, textAlign = TextAlign.End)
        Text(formatRupiah(actual), modifier = Modifier.weight(1f), color = highlightColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun TabunganSheetCard(
    plans: List<BudgetPlan>,
    transactions: List<Transaction>,
    onDeleteTx: (Transaction) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 16.dp).background(SoftYellow))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Alokasi Tabungan", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                val act = transactions.sumOf { it.amount }
                val pl = plans.sumOf { it.plannedAmount }
                val percent = if (pl > 0) (act / pl) * 100 else 0.0
                Surface(
                    color = SoftYellow.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        "%.1f%% terealisasi".format(percent),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = SoftYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spreadsheet headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCard, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text("Nama Rekening", modifier = Modifier.weight(1.5f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Rencana", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Aktual", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (plans.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada rencana tabungan", color = MutedText, fontSize = 12.sp)
                }
            } else {
                plans.forEach { plan ->
                    val actualFromTx = transactions.filter { it.categoryName == plan.name }.sumOf { it.amount }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(plan.name, color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatRupiah(plan.plannedAmount), color = SoftWhite.copy(alpha = 0.85f), fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        
                        // Click to show transaction list modifier
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            Text(formatRupiah(actualFromTx), color = SoftYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// BILLS DETAILED SHEET (Like Spreadsheet Bills Matrix)
@Composable
fun TagihanSheetCard(
    plans: List<BudgetPlan>,
    transactions: List<Transaction>,
    onToggleCollect: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 16.dp).background(SoftBlue))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tagihan & Cicilan", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                val paidCount = transactions.filter { it.isChecked }.size
                val totalCount = plans.size
                Surface(
                    color = SoftBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "$paidCount / $totalCount Lunas",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = SoftBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spreadsheet headers in indonesian
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCard, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓", modifier = Modifier.width(28.dp), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Tagihan", modifier = Modifier.weight(1.3f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Jumlah", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Sisa", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (plans.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada daftar tagihan", color = MutedText, fontSize = 12.sp)
                }
            } else {
                plans.forEach { plan ->
                    // Find actual transaction for this bill. If it already exists, toggle. If not, can generate on checked.
                    val billingTx = transactions.firstOrNull { it.categoryName == plan.name }
                    val isChecked = billingTx?.isChecked ?: false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Interactive Checkbox
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { _ ->
                                if (billingTx != null) {
                                    onToggleCollect(billingTx)
                                } else {
                                    // Generate a physical checked transaction
                                    onToggleCollect(
                                        Transaction(
                                            timestamp = System.currentTimeMillis(),
                                            dateString = "2026-05-28", // default or match month
                                            monthYear = plan.monthYear,
                                            type = "Tagihan",
                                            categoryName = plan.name,
                                            name = "Bayar ${plan.name}",
                                            amount = plan.plannedAmount,
                                            isChecked = true,
                                            notes = plan.notes
                                        )
                                    )
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = SoftBlue,
                                uncheckedColor = DarkBorder,
                                checkmarkColor = CharcoalSurface
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(plan.name, color = if (isChecked) MutedText else SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatRupiah(plan.plannedAmount), color = if (isChecked) MutedText else SoftWhite, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(plan.notes.ifEmpty { "Rp0" }, color = if (isChecked) MutedText else SoftBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// BIAYA HIDUP CHECKLIST COMPONENT
@Composable
fun LivingExpensesChecklistCard(
    plans: List<BudgetPlan>,
    transactions: List<Transaction>,
    onToggleCheck: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 16.dp).background(NeonOrangeAccent))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Biaya Hidup Checklist", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                val paidCount = transactions.filter { it.isChecked }.size
                val totalCount = plans.size
                Surface(
                    color = NeonOrangeAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "$paidCount / $totalCount Terbayar",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = NeonOrangeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spreadsheet headers in Indonesian
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCard, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓", modifier = Modifier.width(28.dp), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Komponen Biaya", modifier = Modifier.weight(1.5f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Anggaran", modifier = Modifier.weight(1f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Aktual Pengeluaran", modifier = Modifier.weight(1.2f), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (plans.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada target biaya hidup", color = MutedText, fontSize = 12.sp)
                }
            } else {
                plans.forEach { plan ->
                    // Get all transactions logged under this exact category name
                    val linkedTxs = transactions.filter { it.categoryName == plan.name }
                    val actualSum = linkedTxs.sumOf { it.amount }
                    val isChecked = linkedTxs.any { it.isChecked }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { _ ->
                                if (linkedTxs.isNotEmpty()) {
                                    // Toggle all linked transactions
                                    linkedTxs.forEach { onToggleCheck(it) }
                                } else {
                                    // Generate actual transaction for this category
                                    onToggleCheck(
                                        Transaction(
                                            timestamp = System.currentTimeMillis(),
                                            dateString = "2026-05-28",
                                            monthYear = plan.monthYear,
                                            type = "Pengeluaran",
                                            categoryName = plan.name,
                                            name = "Bayar ${plan.name}",
                                            amount = plan.plannedAmount,
                                            isChecked = true
                                        )
                                    )
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = NeonOrangeAccent,
                                uncheckedColor = DarkBorder,
                                checkmarkColor = CharcoalSurface
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(plan.name, color = if (isChecked) MutedText else SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatRupiah(plan.plannedAmount), color = if (isChecked) MutedText else SoftWhite.copy(alpha = 0.85f), fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(formatRupiah(actualSum), color = if (isChecked) MutedText else NeonOrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ==================== TAB 1: TRANSACTION & SCAN TAB ====================
@Composable
fun TransactionAndScanTab(
    transactions: List<Transaction>,
    isScanning: Boolean,
    scanResult: ReceiptAnalysisResult?,
    userApiKey: String,
    onScanClick: (ByteArray) -> Unit,
    onConfirmScan: (String, Double, String, String) -> Unit,
    onClearScan: () -> Unit,
    onDeleteTx: (Transaction) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Photo pick launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    onScanClick(bytes)
                } else {
                    Toast.makeText(context, "Gagal mengolah file gambar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Receipt Scan Hero Action Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PremiumOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, contentDescription = "AI Scanner", tint = PremiumOrange, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "AI Scan Nota Otomatis",
                        color = SoftWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.dashPathEffectModifier().height(4.dp))
                    Text(
                        "Unggah atau foto kuitansi/nota belanja Anda. Gemini AI akan menganalisis nama barang, jumlah uang, dan mengelompokkan kategori pengeluaran secara real-time ke dalam MacoSheet!",
                        color = MutedText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isScanning) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PremiumOrange, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("MACO sedang menganalisis nota Anda via Gemini AI...", color = PremiumOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Picker for Real Receipt Camera/Gallery
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pilih Foto Nota", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Predefined Mock Samples for visual feedback or test evaluation in emulator
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Uji Coba Cepat dengan Transaksi Contoh:", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MockReceiptChip("🍔 Nasi Padang", 55000.0, "Makan", onConfirmScan)
                        MockReceiptChip("🛍️ Supermarket", 185000.0, "Belanja", onConfirmScan)
                        MockReceiptChip("🛵 Servis Vespa", 250000.0, "Service", onConfirmScan)
                    }
                }
            }
        }

        // Animated scan result success card
        if (scanResult != null) {
            item {
                AnimatedVisibility(
                    visible = scanResult != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    when (scanResult) {
                        is ReceiptAnalysisResult.Success -> {
                            var editedName by remember(scanResult) { mutableStateOf(scanResult.itemName) }
                            var editedAmount by remember(scanResult) { mutableStateOf(scanResult.amount.toString()) }
                            var editedCategory by remember(scanResult) { mutableStateOf(scanResult.category) }
                            var editedDate by remember(scanResult) { mutableStateOf(scanResult.date) }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, SoftGreen.copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = "Success", tint = SoftGreen, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Nota Berhasil Diekstrak!", color = SoftGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        label = { Text("Deskripsi Pengeluaran") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PremiumOrange, 
                                            unfocusedBorderColor = DarkBorder,
                                            focusedLabelColor = PremiumOrange,
                                            focusedTextColor = SoftWhite,
                                            unfocusedTextColor = SoftWhite
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = editedAmount,
                                        onValueChange = { editedAmount = it },
                                        label = { Text("Nominal (Rp)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PremiumOrange, 
                                            unfocusedBorderColor = DarkBorder,
                                            focusedLabelColor = PremiumOrange,
                                            focusedTextColor = SoftWhite,
                                            unfocusedTextColor = SoftWhite
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = editedCategory,
                                        onValueChange = { editedCategory = it },
                                        label = { Text("Kategori") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PremiumOrange, 
                                            unfocusedBorderColor = DarkBorder,
                                            focusedLabelColor = PremiumOrange,
                                            focusedTextColor = SoftWhite,
                                            unfocusedTextColor = SoftWhite
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = onClearScan,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedText)
                                        ) {
                                            Text("Batal")
                                        }
                                        Button(
                                            onClick = {
                                                val amt = editedAmount.toDoubleOrNull() ?: 0.0
                                                onConfirmScan(editedName, amt, editedCategory, editedDate)
                                                Toast.makeText(context, "Berhasil ditambahkan ke MacoSheet!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1.5f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = SoftGreen, contentColor = CharcoalSurface)
                                        ) {
                                            Text("Selesai & Masukkan", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        is ReceiptAnalysisResult.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = "Error", tint = SoftRed)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gagal Menganalisis Nota", color = SoftRed, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(scanResult.message, color = SoftWhite, fontSize = 11.sp, lineHeight = 16.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TextButton(onClick = onClearScan, colors = ButtonDefaults.textButtonColors(contentColor = PremiumOrange)) {
                                        Text("Tutup")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Historic Transaction Logs Title
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(modifier = Modifier.size(4.dp, 16.dp).background(PremiumOrange))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Daftar Semua Transaksional", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Logs items
        if (transactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada pencatatan transaksi untuk periode ini.", color = MutedText, fontSize = 12.sp)
                }
            }
        } else {
            items(transactions) { tx ->
                TransactionListItem(transaction = tx, onDelete = { onDeleteTx(tx) })
            }
        }
    }
}

@Composable
fun MockReceiptChip(label: String, amount: Double, category: String, onConfirm: (String, Double, String, String) -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .clickable {
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayStr = formatter.format(Date())
                onConfirm(label, amount, category, todayStr)
                Toast.makeText(context, "Simulasi: Sukses menambahkan $label sebesar ${formatRupiah(amount)}", Toast.LENGTH_SHORT).show()
            },
        color = CharcoalCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, DarkBorder)
    ) {
        PaddingValues(horizontal = 8.dp, vertical = 6.dp).let {
            Text(
                text = "$label\n${formatRupiah(amount)}",
                color = SoftWhite,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun TransactionListItem(transaction: Transaction, onDelete: () -> Unit) {
    val tx = transaction
    val highlightColor = when (tx.type) {
        "Pemasukan" -> SoftGreen
        "Tabungan" -> SoftYellow
        "Tagihan" -> SoftBlue
        else -> NeonOrangeAccent
    }

    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(highlightColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (tx.type) {
                    "Pemasukan" -> Icons.Default.Check
                    "Tabungan" -> Icons.Default.Star
                    "Tagihan" -> Icons.Default.Notifications
                    else -> Icons.Default.ShoppingCart
                }
                Icon(icon, contentDescription = null, tint = highlightColor, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(tx.name, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.categoryName, color = highlightColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("•", color = MutedText, fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tx.dateString, color = MutedText, fontSize = 10.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (tx.type == "Pemasukan") "+" else "-"}${formatRupiah(tx.amount)}",
                    color = if (tx.type == "Pemasukan") SoftGreen else SoftWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).padding(top = 2.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = CrimsonRed.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ==================== TAB 2: ANALYSIS & SETTINGS TAB ====================
@Composable
fun AnalysisAndSettingsTab(
    incomeActual: Double,
    savingsActual: Double,
    billsActual: Double,
    expensesActual: Double,
    incomePlanned: Double,
    savingsPlanned: Double,
    billsPlanned: Double,
    expensesPlanned: Double,
    transactions: List<Transaction>,
    userApiKey: String,
    onSaveApiKey: (String) -> Unit
) {
    val context = LocalContext.current
    var inputKey by remember { mutableStateOf(userApiKey) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. DONUT CHART FOR SPENDING BREAKDOWN ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(modifier = Modifier.size(4.dp, 16.dp).background(PremiumOrange))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Penggunaan Uang (Aktual)", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val totalOut = savingsActual + billsActual + expensesActual
                    
                    if (totalOut == 0.0) {
                        Box(modifier = Modifier.height(160.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada alokasi pengeluaran aktual.", color = MutedText, fontSize = 11.sp)
                        }
                    } else {
                        val savingsPct = if (totalOut > 0) (savingsActual / totalOut).toFloat() else 0f
                        val billsPct = if (totalOut > 0) (billsActual / totalOut).toFloat() else 0f
                        val expensesPct = if (totalOut > 0) (expensesActual / totalOut).toFloat() else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Donut Ring Canvas
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                                Canvas(modifier = Modifier.size(120.dp)) {
                                    var currentAngle = 270f
                                    
                                    // Slices
                                    val slices = listOf(
                                        Pair(savingsPct, SoftYellow),
                                        Pair(billsPct, SoftBlue),
                                        Pair(expensesPct, NeonOrangeAccent)
                                    )

                                    slices.forEach { (pct, color) ->
                                        if (pct > 0) {
                                            val sweep = pct * 360f
                                            drawArc(
                                                color = color,
                                                startAngle = currentAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                            currentAngle += sweep
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TOTAL", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(formatRupiah(totalOut).replace("Rp ", ""), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            // Legends
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChartLegendItem(color = SoftYellow, "Tabungan: %.1f%%".format(savingsPct * 100))
                                ChartLegendItem(color = SoftBlue, "Tagihan: %.1f%%".format(billsPct * 100))
                                ChartLegendItem(color = NeonOrangeAccent, "Belanja & Hidup: %.1f%%".format(expensesPct * 100))
                            }
                        }
                    }
                }
            }
        }

        // --- 2. CATEGORY LIVING EXPENSES BREAKDOWN ---
        item {
            val expenseTxs = transactions.filter { it.type == "Pengeluaran" }
            val categoriesGrouped = expenseTxs.groupBy { it.categoryName }.mapValues { entry ->
                entry.value.sumOf { it.amount }
            }.toList().sortedByDescending { it.second }

            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(4.dp, 16.dp).background(PremiumOrange))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Peringkat Kategori Pengeluaran", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (categoriesGrouped.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada rincian belanja", color = MutedText, fontSize = 11.sp)
                        }
                    } else {
                        val maxCategoryAmt = categoriesGrouped.firstOrNull()?.second ?: 1.0
                        categoriesGrouped.forEach { (catName, amt) ->
                            val fillRatio = (amt / maxCategoryAmt).toFloat()
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(catName, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(formatRupiah(amt), color = PremiumOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Horizontal colored visual progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(CharcoalCard)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fillRatio)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(PremiumOrange, NeonOrangeAccent)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. WEEKEND BUDGET ALERTS SYSTEM ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = PremiumOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notifikasi Mingguan Akhir Pekan", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sistem alarm berkala yang berjalan di latar belakang untuk secara otomatis mengevaluasi cashflow bulanan Anda setiap akhir pekan (Sabtu pukul 20:00) dan mengingatkan sisa saldo anggaran Anda.",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                NotificationHelper.showReminderNotification(
                                    context,
                                    "✨ Simulasikan MACO Budget Alert",
                                    "Status keuangan kamu saat ini: sisa budget Rp%,.0f. Tetap bijak mengelola pengeluaran!".format(incomeActual - (savingsActual + billsActual + expensesActual)).replace(",", ".")
                                )
                                Toast.makeText(context, "Berhasil mengirim simulasi notifikasi!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Uji Simulasi Notif", fontSize = 11.sp, color = PremiumOrange, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                NotificationHelper.scheduleWeeklyReminder(context)
                                Toast.makeText(context, "Alarm akhir pekan berhasil di-schedule!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nyalakan Alarm", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 4. SECURE GEMINI SETTINGS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = PremiumOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pengaturan Kunci API Gemini", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Masukkan Kunci API Gemini Anda di bawah ini secara instan di perangkat lokal Anda demi privasi dan keamanan tingkat lanjut. Konfigurasi ini disimpan dengan aman di SharedPreferences.",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        placeholder = { Text("AI Studio Gemini API Key...", color = MutedText, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedBorderColor = PremiumOrange,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = PremiumOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            onSaveApiKey(inputKey)
                            Toast.makeText(context, "API Key Gemini tersimpan secara lokal!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Simpan Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChartLegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// Dialog Add Transaction Detail
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    budgetPlans: List<BudgetPlan>,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Pengeluaran") }
    var categoryName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val types = listOf("Pemasukan", "Tabungan", "Tagihan", "Pengeluaran")
    var showTypeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        title = { Text("Tambah Transaksi Baru", color = SoftWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Dropdown untuk Type
                Box {
                    Button(
                        onClick = { showTypeMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Klasifikasi: $type", color = SoftWhite)
                    }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false },
                        modifier = Modifier.background(CharcoalCard)
                    ) {
                        types.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t, color = SoftWhite) },
                                onClick = {
                                    type = t
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deskripsi / Keterangan") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Nominal (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Kategori (Contoh: Makan, Wifi, dll.)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Tambahan (Opsional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    onSave(name, amt, type, categoryName, type == "Tagihan", notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange)
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MutedText)
            }
        }
    )
}

// Dialog Add Plan Detail
@Composable
fun AddPlanDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pengeluaran") }
    var notes by remember { mutableStateOf("") }

    val categories = listOf("Pemasukan", "Tabungan", "Tagihan", "Pengeluaran")
    var showCatMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        title = { Text("Tambah Target Anggaran (Plan)", color = SoftWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    Button(
                        onClick = { showCatMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Klasifikasi Rencana: $category", color = SoftWhite)
                    }
                    DropdownMenu(
                        expanded = showCatMenu,
                        onDismissRequest = { showCatMenu = false },
                        modifier = Modifier.background(CharcoalCard)
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, color = SoftWhite) },
                                onClick = {
                                    category = c
                                    showCatMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Rencana Anggaran") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Target Anggaran (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Batas Sisa / Memo (Opsional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = PremiumOrange, unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PremiumOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    onSave(name, amt, category, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumOrange)
            ) {
                Text("Simpan Rencana", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MutedText)
            }
        }
    )
}

// Fallback dash line generator since Material 3 height dash canvas doesn't map directly
fun Modifier.dashPathEffectModifier(): Modifier = drawBehind {
    val strokeWidth = 1.dp.toPx()
    val dashWidth = 8.dp.toPx()
    val gapWidth = 6.dp.toPx()
    
    var currentX = 0f
    while (currentX < size.width) {
        drawLine(
            color = DarkBorder,
            start = androidx.compose.ui.geometry.Offset(currentX, 0f),
            end = androidx.compose.ui.geometry.Offset(currentX + dashWidth, 0f),
            strokeWidth = strokeWidth
        )
        currentX += dashWidth + gapWidth
    }
}
