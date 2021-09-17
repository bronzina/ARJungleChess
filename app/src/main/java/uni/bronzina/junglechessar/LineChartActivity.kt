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

class LineChartActivity : AppCompatActivity() {


    private lateinit var lineChart: LineChart
    private var scoreList: ArrayList<Score> = ArrayList()
    private var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inside)

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
        val entries: ArrayList<Entry> = ArrayList()
        var sum = 0f

        //Populate with data acquired in MainActivity
        for (i in scoreList.indices) {
            val score = scoreList[i]
            sum += score.time.toFloat()
            entries.add(Entry(i.toFloat(), score.time.toFloat()))
        }

        //Calculate average lag
        val average = sum / scoreList.size

        val lineDataSet = LineDataSet(entries, "")
        lineDataSet.color = Color.BLUE
        lineDataSet.setCircleColor(Color.BLUE)

        val data = LineData(lineDataSet)
        lineChart.data = data

        val ll1 = LimitLine(average, "Average delay: " + average.toString())
        ll1.lineWidth = 2f
        ll1.disableDashedLine()
        ll1.labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        ll1.textSize = 10f
        ll1.lineColor = Color.RED

        val leftAxis: YAxis = lineChart.axisLeft
        leftAxis.removeAllLimitLines() // reset all limit lines to avoid overlapping lines
        leftAxis.addLimitLine(ll1)

        val legend = lineChart.legend
        legend.isEnabled = true
        legend.form = Legend.LegendForm.CIRCLE
        legend.formSize = 10f // set the size of the legend forms/shapes
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.xEntrySpace = 5f // set the space between the legend entries on the x-axis
        legend.yEntrySpace = 5f // set the space between the legend entries on the y-axis

        val l1 = LegendEntry("Single delays", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.BLUE)
        val l2 = LegendEntry("Average delay", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.RED)

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