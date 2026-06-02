package com.codeint.objectboxkmp.sample

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.codeint.objectboxkmp.sample.shared.CommonCrudSample
import com.codeint.objectboxkmp.sample.shared.ObjectBoxKmpSampleAndroid
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var crudSample: CommonCrudSample
    private lateinit var outputView: TextView
    private lateinit var statusView: TextView

    private val ink = "#18212F".toColorInt()
    private val muted = "#667085".toColorInt()
    private val surface = "#F6F8FB".toColorInt()
    private val panel = "#FFFFFF".toColorInt()
    private val line = "#D9E0EA".toColorInt()
    private val primary = "#0E7A66".toColorInt()
    private val primaryDark = "#0A5D4E".toColorInt()
    private val danger = "#B42318".toColorInt()
    private val codeBg = "#111827".toColorInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ObjectBoxKmpSampleAndroid.init(this)
        crudSample = CommonCrudSample()

        outputView = TextView(this).apply {
            text = crudSample.currentState()
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor("#E5E7EB".toColorInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setLineSpacing(dp(2).toFloat(), 1f)
            background = rounded(codeBg, dp(8))
        }
        statusView = TextView(this).apply {
            text = "Flow observer active"
            textSize = 13f
            setTextColor(primaryDark)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded("#DDF7EE".toColorInt(), dp(999))
        }

        setContentView(createContentView())
        lifecycleScope.launch {
            crudSample.renderedStateFlow().collect { renderedState ->
                showResult(renderedState, "Flow update received")
            }
        }
    }

    private fun createContentView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(surface)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        content.addView(hero())
        content.addView(section("Core CRUD", listOf(
            Action("Create", "Insert one Note") { crudSample.create() },
            Action("Read", "Read selected") { crudSample.readSelected() },
            Action("Update", "Toggle + rename") { crudSample.updateSelected() },
            Action("Delete", "Remove selected", danger) { crudSample.deleteSelected() },
            Action("Create 3", "Batch insert") { crudSample.createThree() },
            Action("Clear All", "Reset sample", danger) { crudSample.clearAll() },
        )))
        content.addView(section("Queries", listOf(
            Action("Open Notes", "done = false") { crudSample.findOpenNotes() },
            Action("Search Titles", "contains + limit") { crudSample.searchTitles() },
            Action("Count Done", "aggregate count") { crudSample.countDoneNotes() },
            Action("Delete Done", "query remove", danger) { crudSample.deleteDoneNotes() },
        )))
        content.addView(section("Complex Query DSL", listOf(
            Action("Seed Data", "Realistic notes") { crudSample.seedComplexNotes() },
            Action("AND + Range", "filters + sort") { crudSample.complexAndRangeQuery() },
            Action("Grouped OR", "anyOf + limit") { crudSample.complexGroupedOrQuery() },
            Action("Complex Delete", "allOf remove", danger) { crudSample.complexDeleteQuery() },
        )))
        content.addView(outputPanel())

        return ScrollView(this).apply {
            isFillViewport = false
            setBackgroundColor(surface)
            addView(content)
        }
    }

    private fun hero(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(panel, dp(8), line)
            addView(TextView(context).apply {
                text = "ObjectBox KMP SDK"
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ink)
            })
            addView(TextView(context).apply {
                text = "CommonMain CRUD, query DSL, migrations, and Flow changes running through the shared SDK API."
                textSize = 15f
                setTextColor(muted)
                setPadding(0, dp(8), 0, dp(14))
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            addView(statusView)
            layoutParams = blockParams()
        }
    }

    private fun section(title: String, actions: List<Action>): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel, dp(8), line)
            layoutParams = blockParams()
        }
        wrapper.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            setPadding(0, 0, 0, dp(12))
        })
        wrapper.addView(GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            actions.forEach { action -> addView(actionButton(action)) }
        })
        return wrapper
    }

    private fun outputPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel, dp(8), line)
            layoutParams = blockParams()
            addView(TextView(context).apply {
                text = "Live Output"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ink)
                setPadding(0, 0, 0, dp(12))
            })
            addView(outputView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
    }

    private fun actionButton(action: Action): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(action.color, dp(8))
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(TextView(context).apply {
                text = action.title
                gravity = Gravity.CENTER
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor("#FFFFFF".toColorInt())
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = action.subtitle
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor("#FFFFFF".toColorInt())
                includeFontPadding = false
                setPadding(0, dp(5), 0, 0)
            })
            setOnClickListener { showResult(action.run(), action.title) }
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(86)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
    }

    private fun showResult(result: String, status: String) {
        outputView.text = result
        statusView.text = status
    }

    private fun blockParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(16)
        }
    }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int? = null,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class Action(
        val title: String,
        val subtitle: String,
        val color: Int = "#0E7A66".toColorInt(),
        val run: () -> String,
    )
}
