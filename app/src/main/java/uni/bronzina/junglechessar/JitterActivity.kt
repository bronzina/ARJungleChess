package uni.bronzina.junglechessar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlin.math.pow
import kotlin.math.sqrt

class JitterActivity : AppCompatActivity() {


    private lateinit var lineChart: LineChart
    private var scoreList: ArrayList<Score> = ArrayList()
    private var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jitter)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        toolbar!!.visibility = View.VISIBLE
        toolbar!!.title = "AR Jungle Chess"

        val intent = intent
        val args = intent.getBundleExtra("BUNDLE")
        scoreList = args.getParcelableArrayList("SCORE")

        lineChart = findViewById(R.id.lineChart)

        initLineChart()

        setDataToLineChart()

    }

    private fun initLineChart() {

        //Hide grid lines
        lineChart.axisLeft.setDrawGridLines(false)
        val xAxis: XAxis = lineChart.xAxis
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(true)

        //Remove right y-axis
        lineChart.axisRight.isEnabled = false

        //Remove legend
        lineChart.legend.isEnabled = true


        //Remove description label
        lineChart.description.isEnabled = false


        //Add animation
        lineChart.animateX(1000, Easing.EaseInSine)

        //To draw label on xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = MyAxisFormatter()
        xAxis.setDrawLabels(true)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = 0f

    }


    inner class MyAxisFormatter : IndexAxisValueFormatter() {

        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            val index = value.toInt()
            return if (index < scoreList.size) {
                scoreList[index].index.toString()
            } else {
                ""
            }
        }
    }

    private fun setDataToLineChart() {
        //Now draw line chart with dynamic data
        var sum = 0f

        //Populate with data acquired in MainActivity
        for (i in scoreList.indices) {
            val score = scoreList[i]
            sum += score.time.toFloat()
        }

        //Calculate average lag
        val average = sum / scoreList.size

        //Calculate variance (jitter)
        val jitters: ArrayList<Float> = ArrayList()

        for (i in scoreList.indices){
            val score = scoreList[i]
            var jitter = score.time.toFloat() - average
            jitter = jitter.pow(2)
            jitters.add(jitter)
        }

        val entries2: ArrayList<Entry> = ArrayList()
        var sumJitters = 0f

        for(j in jitters.indices){
            sumJitters += jitters[j]
            entries2.add(Entry(j.toFloat(), sqrt(jitters[j])))
        }

        val lineDataSet = LineDataSet(entries2, "")
        lineDataSet.color = Color.GRAY
        lineDataSet.setCircleColor(Color.GRAY)

        val data = LineData(lineDataSet)
        lineChart.data = data

        val squaredVariance = sumJitters/(scoreList.size-1)
        val variance = sqrt(squaredVariance)

        val ll2 = LimitLine(variance, "Average jitter: " + variance.toString())
        ll2.lineWidth = 2f
        ll2.disableDashedLine()
        ll2.labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        ll2.textSize = 10f
        ll2.lineColor = Color.GREEN

        val leftAxis: YAxis = lineChart.axisLeft
        leftAxis.removeAllLimitLines() // reset all limit lines to avoid overlapping lines
        leftAxis.addLimitLine(ll2)


        val legend = lineChart.legend
        legend.isEnabled = true
        legend.form = Legend.LegendForm.CIRCLE
        legend.formSize = 10f // set the size of the legend forms/shapes
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.xEntrySpace = 5f // set the space between the legend entries on the x-axis
        legend.yEntrySpace = 5f // set the space between the legend entries on the y-axis

        val l1 = LegendEntry("Single jitters", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.GRAY)
        //val l2 = LegendEntry("Average delay", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.RED)
        val l2 = LegendEntry("Average jitter", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.GREEN)

        legend.setCustom(arrayOf(l1, l2))

        lineChart.invalidate()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_logout) {
            //logout()
            return true
        }
        else if(id == R.id.action_inside){
            val intent = Intent(this, LineChartActivity::class.java)
            val args = Bundle()
            args.putParcelableArrayList("SCORE", scoreList);
            intent.putExtra("BUNDLE", args)
            startActivity(intent)
        }
        else if(id == R.id.action_jitter){
            val intent = Intent(this, JitterActivity::class.java)
            val args = Bundle()
            args.putParcelableArrayList("SCORE", scoreList);
            intent.putExtra("BUNDLE", args)
            startActivity(intent)
        }
        return super.onOptionsItemSelected(item)
    }

}