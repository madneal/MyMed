package com.example.mymed

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val records = arrayListOf<MedicalRecord>()
    private val trendPoints = arrayListOf<TrendPoint>()

    private lateinit var summaryContainer: LinearLayout
    private lateinit var recordsContainer: LinearLayout
    private lateinit var metricContainer: LinearLayout
    private lateinit var trendListContainer: LinearLayout
    private lateinit var trendChartView: TrendChartView
    private var selectedAttachmentUri: Uri? = null
    private var attachmentLabel: TextView? = null
    private var selectedMetric = ""

    private val blue = Color.rgb(36, 107, 253)
    private val green = Color.rgb(29, 138, 109)
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val bg = Color.rgb(247, 250, 252)
    private val border = Color.rgb(226, 232, 240)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        if (selectedMetric.isEmpty() && trendPoints.isNotEmpty()) {
            selectedMetric = trendPoints.last().metric
        }
        buildScreen()
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_FILE || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        selectedAttachmentUri = uri
        val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: RuntimeException) {
            // Some providers grant only transient read access.
        }

        attachmentLabel?.apply {
            text = "已选择附件"
            setTextColor(green)
        }
    }

    private fun buildScreen() {
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scrollView.addView(page, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        page.addView(label("我的健康档案", 30, ink, Typeface.BOLD))
        page.addView(label("集中保存检查报告、CT、处方、笔记，并追踪指标趋势", 15, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(4), 0, dp(18))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(actions, matchWrap())

        actions.addView(primaryButton("添加资料").apply {
            setOnClickListener { showRecordDialog() }
        }, weightWrap(1))
        actions.addView(SpaceView(this, dp(10), 1))
        actions.addView(secondaryButton("添加趋势").apply {
            setOnClickListener { showTrendDialog() }
        }, weightWrap(1))

        summaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, dp(8))
        }
        page.addView(summaryContainer, matchWrap())

        addSectionTitle(page, "医疗资料")
        recordsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(recordsContainer, matchWrap())

        addSectionTitle(page, "趋势追踪")
        metricContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(metricContainer)
        }, matchWrap())

        trendChartView = TrendChartView(this)
        page.addView(trendChartView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(210)
        ).apply {
            setMargins(0, dp(12), 0, dp(10))
        })

        trendListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(trendListContainer, matchWrap())

        setContentView(scrollView)
    }

    private fun render() {
        renderSummary()
        renderRecords()
        renderMetrics()
        renderTrendList()
    }

    private fun renderSummary() {
        summaryContainer.removeAllViews()
        summaryContainer.addView(summaryCard("资料", records.size.toString()), weightWrap(1))
        summaryContainer.addView(SpaceView(this, dp(10), 1))
        summaryContainer.addView(summaryCard("趋势点", trendPoints.size.toString()), weightWrap(1))
    }

    private fun renderRecords() {
        recordsContainer.removeAllViews()
        if (records.isEmpty()) {
            recordsContainer.addView(emptyState("还没有医疗资料。可以添加检查报告、CT、化验单、处方或笔记。"))
            return
        }

        records.sortedByDescending { it.date }.forEach { record ->
            recordsContainer.addView(recordCard(record))
        }
    }

    private fun renderMetrics() {
        metricContainer.removeAllViews()
        val metrics = uniqueMetrics()
        if (metrics.isEmpty()) {
            selectedMetric = ""
            metricContainer.addView(emptyState("还没有趋势指标。"))
            trendChartView.setPoints(emptyList())
            return
        }

        if (selectedMetric.isEmpty() || selectedMetric !in metrics) {
            selectedMetric = metrics.first()
        }

        metrics.forEach { metric ->
            val chip = if (metric == selectedMetric) primaryButton(metric) else lightButton(metric)
            chip.isAllCaps = false
            chip.setOnClickListener {
                selectedMetric = metric
                renderMetrics()
                renderTrendList()
            }
            metricContainer.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ).apply {
                setMargins(0, 0, dp(8), 0)
            })
        }

        trendChartView.setPoints(pointsForMetric(selectedMetric))
    }

    private fun renderTrendList() {
        trendListContainer.removeAllViews()
        val points = pointsForMetric(selectedMetric)
        if (points.isEmpty()) {
            trendListContainer.addView(emptyState("添加数值后会显示趋势线。"))
            return
        }

        points.sortedByDescending { it.date }.forEach { point ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = cardBg(Color.WHITE)
            }

            row.addView(label(point.date, 14, muted, Typeface.NORMAL), weightWrap(1))
            val displayValue = buildString {
                append(point.valueText())
                if (point.unit.isNotEmpty()) append(" ").append(point.unit)
            }
            row.addView(label(displayValue, 18, ink, Typeface.BOLD).apply {
                gravity = Gravity.END
            }, weightWrap(1))

            trendListContainer.addView(row, matchWrap().apply {
                setMargins(0, 0, 0, dp(8))
            })
        }
    }

    private fun recordCard(record: MedicalRecord): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = cardBg(Color.WHITE)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        card.addView(top, matchWrap())
        top.addView(label(record.title, 18, ink, Typeface.BOLD), weightWrap(1))
        top.addView(label(record.type, 13, blue, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = pillBg(Color.rgb(232, 240, 255), blue)
        })

        val meta = if (record.provider.isBlank()) record.date else "${record.date} / ${record.provider}"
        card.addView(label(meta, 14, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(7), 0, 0)
        })

        if (record.notes.isNotEmpty()) {
            card.addView(label(record.notes, 15, ink, Typeface.NORMAL).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }

        if (record.uri.isNotEmpty()) {
            card.addView(lightButton("打开附件").apply {
                isAllCaps = false
                setOnClickListener { openAttachment(record.uri) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                setMargins(0, dp(12), 0, 0)
            })
        }

        card.layoutParams = matchWrap().apply { setMargins(0, 0, 0, dp(10)) }
        return card
    }

    private fun showRecordDialog() {
        selectedAttachmentUri = null
        attachmentLabel = null

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }

        val typeNames = listOf("检查报告", "CT", "化验单", "处方", "笔记", "其他")
        var selectedType = typeNames.first()
        val typeButtons = mutableListOf<Button>()
        val typeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun refreshTypeButtons() {
            typeButtons.forEach { button ->
                val selected = button.text.toString() == selectedType
                button.setTextColor(if (selected) Color.WHITE else blue)
                button.background = if (selected) buttonBg(blue) else pillBg(Color.WHITE, border)
            }
        }
        typeNames.forEach { typeName ->
            val button = lightButton(typeName).apply {
                isAllCaps = false
                minWidth = dp(76)
                setOnClickListener {
                    selectedType = typeName
                    refreshTypeButtons()
                }
            }
            typeButtons.add(button)
            typeRow.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ).apply {
                setMargins(0, 0, dp(8), 0)
            })
        }
        refreshTypeButtons()

        content.addView(fieldBlock("资料类型", HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(typeRow)
        }))

        val date = textInput("选择日期").apply {
            setText(today())
            inputType = InputType.TYPE_NULL
            isFocusable = false
            setOnClickListener { pickDate(this) }
        }
        var selectedProvider = ""
        val provider = selectorInput("请选择").apply {
            setOnClickListener {
                showProviderSelector(this) { selectedProvider = it }
            }
        }

        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        metaRow.addView(fieldBlock("日期", date), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        metaRow.addView(SpaceView(this, dp(10), 1))
        metaRow.addView(fieldBlock("医院或医生", provider), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        content.addView(metaRow)

        val notes = textInput("记录关键结论、复查建议或用药变化").apply {
            minLines = 3
            gravity = Gravity.TOP
        }
        content.addView(fieldBlock("备注", notes))

        val attachPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = cardBg(Color.WHITE)
        }
        val attachTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        attachTop.addView(label("附件", 16, ink, Typeface.BOLD), weightWrap(1))
        val attach = lightButton("选择文件").apply {
            isAllCaps = false
            minWidth = dp(96)
            setOnClickListener { pickAttachment() }
        }
        attachTop.addView(attach, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(40)
        ))
        attachPanel.addView(attachTop, matchWrap())
        attachmentLabel = label("未添加附件，可选择 PDF、图片或扫描件", 13, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(8), 0, 0)
        }
        attachPanel.addView(attachmentLabel)
        content.addView(attachPanel, matchWrap().apply {
            setMargins(0, dp(12), 0, 0)
        })

        val dialog = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(content) })
            .create()

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        footer.addView(lightButton("取消").apply {
            isAllCaps = false
            setOnClickListener { dialog.dismiss() }
        }, weightWrap(1))
        footer.addView(SpaceView(this, dp(10), 1))
        footer.addView(primaryButton("保存").apply {
            isAllCaps = false
            setOnClickListener {
                records.add(MedicalRecord(
                    title = recordTitle(selectedType, date.text.toString().trim(), selectedProvider),
                    type = selectedType,
                    date = date.text.toString().trim(),
                    provider = selectedProvider,
                    notes = notes.text.toString().trim(),
                    uri = selectedAttachmentUri?.toString().orEmpty()
                ))
                saveData()
                render()
                dialog.dismiss()
            }
        }, weightWrap(1))
        content.addView(footer, matchWrap())

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).roundToInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showTrendDialog() {
        val form = dialogForm()
        val metric = textInput("指标，例如：血糖").apply {
            if (selectedMetric.isNotEmpty()) setText(selectedMetric)
        }
        val date = textInput("日期").apply {
            setText(today())
            inputType = InputType.TYPE_NULL
            setOnClickListener { pickDate(this) }
        }
        val value = textInput("数值").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val unit = textInput("单位，例如：mmol/L")

        form.addView(metric)
        form.addView(date, inputParams())
        form.addView(value, inputParams())
        form.addView(unit, inputParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("添加趋势数值")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val metricName = metric.text.toString().trim()
                val rawValue = value.text.toString().trim()
                if (metricName.isEmpty()) {
                    metric.error = "必填"
                    return@setOnClickListener
                }
                if (rawValue.isEmpty()) {
                    value.error = "必填"
                    return@setOnClickListener
                }

                val parsedValue = rawValue.toDoubleOrNull()
                if (parsedValue == null) {
                    value.error = "请输入数字"
                    return@setOnClickListener
                }

                trendPoints.add(TrendPoint(
                    metric = metricName,
                    date = date.text.toString().trim(),
                    value = parsedValue,
                    unit = unit.text.toString().trim()
                ))
                selectedMetric = metricName
                saveData()
                render()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun pickAttachment() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_FILE)
    }

    private fun openAttachment(uriText: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriText)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "打开附件"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有可打开该附件的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProviderSelector(target: TextView, onSelected: (String) -> Unit) {
        val savedProviders = records
            .map { it.provider }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val defaultProviders = listOf("市人民医院", "社区卫生服务中心", "体检中心")
        val options = buildList {
            add("不选择")
            addAll(savedProviders)
            defaultProviders.forEach { provider ->
                if (provider !in savedProviders) add(provider)
            }
            add("手动输入")
        }

        AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (val selected = options[which]) {
                    "不选择" -> {
                        target.text = "请选择"
                        target.setTextColor(muted)
                        onSelected("")
                    }
                    "手动输入" -> showManualProviderInput(target, onSelected)
                    else -> {
                        target.text = selected
                        target.setTextColor(ink)
                        onSelected(selected)
                    }
                }
            }
            .show()
    }

    private fun showManualProviderInput(target: TextView, onSelected: (String) -> Unit) {
        val input = textInput("例如：市人民医院 / 李医生").apply {
            maxLines = 1
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), 0)
            addView(input, matchWrap())
        }

        AlertDialog.Builder(this)
            .setView(wrapper)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    target.text = value
                    target.setTextColor(ink)
                    onSelected(value)
                }
            }
            .show()
    }

    private fun recordTitle(type: String, date: String, provider: String): String =
        buildString {
            append(type)
            if (date.isNotBlank()) append(" · ").append(date)
            if (provider.isNotBlank()) append(" · ").append(provider)
        }

    private fun pickDate(target: EditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                target.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        records.clear()
        trendPoints.clear()
        try {
            val savedRecords = JSONArray(prefs.getString(KEY_RECORDS, "[]"))
            for (index in 0 until savedRecords.length()) {
                records.add(MedicalRecord.fromJson(savedRecords.getJSONObject(index)))
            }

            val savedTrends = JSONArray(prefs.getString(KEY_TRENDS, "[]"))
            for (index in 0 until savedTrends.length()) {
                trendPoints.add(TrendPoint.fromJson(savedTrends.getJSONObject(index)))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "无法读取已保存的数据", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveData() {
        val recordArray = JSONArray()
        records.forEach { recordArray.put(it.toJson()) }

        val trendArray = JSONArray()
        trendPoints.forEach { trendArray.put(it.toJson()) }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS, recordArray.toString())
            .putString(KEY_TRENDS, trendArray.toString())
            .apply()
    }

    private fun uniqueMetrics(): List<String> = trendPoints
        .map { it.metric }
        .distinct()
        .sorted()

    private fun pointsForMetric(metric: String): List<TrendPoint> = trendPoints
        .filter { it.metric == metric }
        .sortedBy { it.date }

    private fun addSectionTitle(page: LinearLayout, text: String) {
        page.addView(label(text, 20, ink, Typeface.BOLD).apply {
            setPadding(0, dp(20), 0, dp(10))
        })
    }

    private fun summaryCard(label: String, value: String): TextView =
        label("$value\n$label", 15, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = cardBg(Color.WHITE)
        }

    private fun emptyState(text: String): TextView =
        label(text, 15, muted, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = cardBg(Color.WHITE)
        }

    private fun fieldBlock(title: String, child: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(label(title, 13, muted, Typeface.BOLD).apply {
                setPadding(0, 0, 0, dp(6))
            })
            addView(child, matchWrap())
        }

    private fun dialogForm(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), 0)
        }

    private fun textInput(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            setTextColor(ink)
            setHintTextColor(muted)
            setSingleLine(false)
            background = inputBg()
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
        }

    private fun selectorInput(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(muted)
            background = inputBg()
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
        }

    private fun label(text: String, sp: Int, color: Int, style: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sp.toFloat()
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, style)
        }

    private fun primaryButton(text: String): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = buttonBg(blue)
        }

    private fun secondaryButton(text: String): Button =
        primaryButton(text).apply { background = buttonBg(green) }

    private fun lightButton(text: String): Button =
        Button(this).apply {
            this.text = text
            setTextColor(blue)
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = pillBg(Color.WHITE, border)
        }

    private fun cardBg(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), border)
        }

    private fun inputBg(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), border)
        }

    private fun buttonBg(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
        }

    private fun pillBg(color: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), strokeColor)
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun weightWrap(weight: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight.toFloat())

    private fun inputParams(): LinearLayout.LayoutParams =
        matchWrap().apply { setMargins(0, dp(10), 0, 0) }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private class SpaceView(context: Context, width: Int, height: Int) : View(context) {
        init {
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }

    private data class MedicalRecord(
        val title: String,
        val type: String,
        val date: String,
        val provider: String,
        val notes: String,
        val uri: String
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("title", title)
                .put("type", type)
                .put("date", date)
                .put("provider", provider)
                .put("notes", notes)
                .put("uri", uri)

        companion object {
            fun fromJson(item: JSONObject): MedicalRecord =
                MedicalRecord(
                    title = item.optString("title"),
                    type = item.optString("type"),
                    date = item.optString("date"),
                    provider = item.optString("provider"),
                    notes = item.optString("notes"),
                    uri = item.optString("uri")
                )
        }
    }

    private data class TrendPoint(
        val metric: String,
        val date: String,
        val value: Double,
        val unit: String
    ) {
        fun valueText(): String =
            if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

        fun toJson(): JSONObject =
            JSONObject()
                .put("metric", metric)
                .put("date", date)
                .put("value", value)
                .put("unit", unit)

        companion object {
            fun fromJson(item: JSONObject): TrendPoint =
                TrendPoint(
                    metric = item.optString("metric"),
                    date = item.optString("date"),
                    value = item.optDouble("value"),
                    unit = item.optString("unit")
                )
        }
    }

    private inner class TrendChartView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val points = arrayListOf<TrendPoint>()

        init {
            background = cardBg(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        fun setPoints(newPoints: List<TrendPoint>) {
            points.clear()
            points.addAll(newPoints)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left = dp(34)
            val right = width - dp(18)
            val top = dp(24)
            val bottom = height - dp(34)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = border
            canvas.drawLine(left.toFloat(), bottom.toFloat(), right.toFloat(), bottom.toFloat(), paint)
            canvas.drawLine(left.toFloat(), top.toFloat(), left.toFloat(), bottom.toFloat(), paint)

            if (points.isEmpty()) {
                paint.style = Paint.Style.FILL
                paint.textSize = dp(14).toFloat()
                paint.color = muted
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("暂无趋势数据", width / 2f, height / 2f, paint)
                return
            }

            var minValue = points.first().value
            var maxValue = points.first().value
            points.forEach {
                minValue = min(minValue, it.value)
                maxValue = max(maxValue, it.value)
            }
            if (minValue == maxValue) {
                minValue -= 1.0
                maxValue += 1.0
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    (left + right) / 2f
                } else {
                    left + (right - left) * (index / (points.size - 1).toFloat())
                }
                val y = (bottom - ((point.value - minValue) / (maxValue - minValue)) * (bottom - top)).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3).toFloat()
            paint.color = blue
            canvas.drawPath(path, paint)

            paint.style = Paint.Style.FILL
            paint.color = green
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    (left + right) / 2f
                } else {
                    left + (right - left) * (index / (points.size - 1).toFloat())
                }
                val y = (bottom - ((point.value - minValue) / (maxValue - minValue)) * (bottom - top)).toFloat()
                canvas.drawCircle(x, y, dp(4).toFloat(), paint)
            }

            paint.color = muted
            paint.textSize = dp(12).toFloat()
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(String.format(Locale.US, "%.1f", maxValue), dp(10).toFloat(), top + dp(4).toFloat(), paint)
            canvas.drawText(String.format(Locale.US, "%.1f", minValue), dp(10).toFloat(), bottom.toFloat(), paint)

            val first = points.first()
            val last = points.last()
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(first.date, left.toFloat(), height - dp(12).toFloat(), paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(last.date, right.toFloat(), height - dp(12).toFloat(), paint)
        }
    }

    private companion object {
        const val REQUEST_PICK_FILE = 401
        const val PREFS_NAME = "mymed_data"
        const val KEY_RECORDS = "records"
        const val KEY_TRENDS = "trends"
    }
}
