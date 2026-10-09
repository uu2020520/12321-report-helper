package com.report12321.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.report12321.app.R
import com.report12321.app.adapter.SpamItemAdapter
import com.report12321.app.model.SpamType
import com.report12321.app.service.AutoFillAccessibilityService
import com.report12321.app.util.AccessibilityPrompts
import com.report12321.app.util.ReportPrefs
import com.report12321.app.util.SpamDetector
import com.report12321.app.util.SystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面
 *
 * 功能：
 * 1. 展示骚扰短信和电话列表
 * 2. 支持多选批量举报
 * 3. 一键跳转12321并启动自动填表
 * 4. 权限管理和无障碍服务引导
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }

    private lateinit var rootLayout: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: LinearLayout
    private lateinit var textEmpty: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipSms: Chip
    private lateinit var chipCall: Chip
    private lateinit var multiSelectBar: LinearLayout
    private lateinit var textSelectedCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnCancelSelect: Button
    private lateinit var fabReport: FloatingActionButton

    private lateinit var adapter: SpamItemAdapter
    private lateinit var spamDetector: SpamDetector

    private var currentFilter = SpamType.SMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupChips()
        setupFab()
        setupMultiSelectBar()

        spamDetector = SpamDetector(this)

        // 检查权限
        if (hasAllPermissions()) {
            loadSpamData()
        } else {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.root_layout)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        recyclerView = findViewById(R.id.recycler_spam)
        progressBar = findViewById(R.id.progress_bar)
        emptyView = findViewById(R.id.empty_view)
        textEmpty = findViewById(R.id.text_empty)
        chipGroup = findViewById(R.id.chip_group)
        chipSms = findViewById(R.id.chip_sms)
        chipCall = findViewById(R.id.chip_call)
        multiSelectBar = findViewById(R.id.multi_select_bar)
        textSelectedCount = findViewById(R.id.text_selected_count)
        btnSelectAll = findViewById(R.id.btn_select_all)
        btnCancelSelect = findViewById(R.id.btn_cancel_select)
        fabReport = findViewById(R.id.fab_report)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // 状态栏适配：Toolbar 背景延伸到状态栏下方，并把状态栏涂成同色（深色底 + 浅色图标）
        SystemBarInsets.applyTopPadding(toolbar)
        SystemBarInsets.setStatusBarColor(window, this, R.color.primary, lightIcons = false)
        // 导航栏适配：避免底部悬浮按钮被手势导航条遮住
        SystemBarInsets.applyBottomPadding(rootLayout)
    }

    private fun setupRecyclerView() {
        adapter = SpamItemAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 单击进入举报详情
        adapter.onItemClickListener = { item, _ ->
            if (!adapter.isMultiSelectMode) {
                val intent = Intent(this, ReportActivity::class.java)
                intent.putExtra("spam_item", item)
                startActivity(intent)
            }
        }

        // 长按进入多选模式
        adapter.onItemLongClickListener = { _ ->
            showMultiSelectBar()
        }

        // 下拉刷新
        swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.design_default_color_primary
        )
        swipeRefresh.setOnRefreshListener {
            loadSpamData()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun setupChips() {
        chipSms.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                currentFilter = SpamType.SMS
                loadSpamData()
            }
        }
        chipCall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                currentFilter = SpamType.CALL
                loadSpamData()
            }
        }
    }

    private fun setupFab() {
        fabReport.setOnClickListener {
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请先选择要举报的骚扰信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 检查网络
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "请检查网络连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 选定无障碍通道但未开启服务时，先引导开启，避免静默失败
            if (!isSelectedChannelReady()) {
                return@setOnClickListener
            }

            // 批量举报：取第一个进入举报详情页
            val item = selectedItems[0]
            val intent = Intent(this, ReportActivity::class.java)
            intent.putExtra("spam_item", item)
            startActivity(intent)
        }
    }

    private fun setupMultiSelectBar() {
        btnSelectAll.setOnClickListener {
            adapter.selectAll()
            updateSelectedCount()
        }

        btnCancelSelect.setOnClickListener {
            adapter.exitMultiSelectMode()
            hideMultiSelectBar()
        }
    }

    private fun showMultiSelectBar() {
        multiSelectBar.visibility = View.VISIBLE
        fabReport.show()
        updateSelectedCount()
    }

    private fun hideMultiSelectBar() {
        multiSelectBar.visibility = View.GONE
        fabReport.hide()
    }

    private fun updateSelectedCount() {
        textSelectedCount.text = "已选择 ${adapter.getSelectedCount()} 项"
    }

    /**
     * 加载骚扰信息数据
     *
     * 用 lifecycleScope 替代原来的裸 Thread：查询跑在 IO 线程，
     * 且协程绑定 Activity 生命周期，页面销毁后回调不会再执行，避免崩溃和泄漏。
     */
    private fun loadSpamData() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                when (currentFilter) {
                    SpamType.SMS -> spamDetector.getSuspiciousSms()
                    SpamType.CALL -> spamDetector.getSuspiciousCalls()
                }
            }

            // 走到这里说明 Activity 仍然存活（销毁时协程会被取消，不会执行到这里）
            progressBar.visibility = View.GONE
            if (items.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                textEmpty.text = when (currentFilter) {
                    SpamType.SMS -> "未检测到可疑短信"
                    SpamType.CALL -> "未检测到可疑电话"
                }
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.updateData(items)
            }
        }
    }

    /**
     * 检查是否拥有所有必要权限
     */
    private fun hasAllPermissions(): Boolean {
        val smsPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val callLogPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        val notificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        return smsPermission && callLogPermission && notificationPermission
    }

    /**
     * 请求权限
     */
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            // 先显示说明
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.READ_SMS
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.READ_CALL_LOG
                )
            ) {
                AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("本应用需要读取短信、通话记录和通知权限，用于识别骚扰信息并在举报网页中提供复制助手。")
                    .setPositiveButton("确定") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            permissions.toTypedArray(),
                            REQUEST_PERMISSIONS
                        )
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    REQUEST_PERMISSIONS
                )
            }
        }
    }

    /**
     * 显示无障碍服务引导对话框
     */
    private fun showAccessibilityDialog() {
        AccessibilityPrompts.showEnableDialog(this)
    }

    /**
     * 当前选定的填表通道是否已经可用
     *
     * 内置网页通道永远可用；无障碍通道要求服务已开启，
     * 未开启时弹引导弹窗（此前这里没有判断，导致点了举报毫无反应）。
     */
    private fun isSelectedChannelReady(): Boolean {
        if (!ReportPrefs.useAccessibility(this)) {
            return true
        }
        if (AutoFillAccessibilityService.isRunning()) {
            return true
        }
        showAccessibilityDialog()
        return false
    }

    /**
     * 更新填表通道与无障碍服务状态（显示在 Toolbar 副标题）
     */
    private fun updateServiceStatus() {
        val channelName = if (ReportPrefs.useAccessibility(this)) "无障碍" else "内置网页"
        val serviceState = if (AutoFillAccessibilityService.isRunning()) "无障碍已开启" else "无障碍未开启"
        supportActionBar?.subtitle = "填表通道：$channelName · $serviceState"
    }

    /**
     * 检查网络是否可用
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_accessibility -> {
                startActivity(Intent(this, AccessibilityGuideActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                loadSpamData()
                Toast.makeText(this, "权限已授予，正在加载骚扰信息...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "部分权限未授予，功能可能受限", Toast.LENGTH_LONG).show()
            }
        }
    }
}
