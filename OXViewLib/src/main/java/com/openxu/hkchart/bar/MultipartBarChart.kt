package com.openxu.hkchart.bar

import android.content.Context
import android.graphics.*
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.widget.Scroller
import com.openxu.hkchart.BaseChart
import com.openxu.hkchart.config.DisplayScheme
import com.openxu.hkchart.config.MultipartBarConfig
import com.openxu.hkchart.element.MarkType
import com.openxu.hkchart.element.XAxisMark
import com.openxu.hkchart.element.YAxisMark
import com.openxu.utils.FontUtil
import com.openxu.utils.LogUtil
import java.util.regex.Pattern

/**
 * Author: openXu
 * Time: 2021/5/9 12:00
 * class: MultipartBarChart
 * Description:
 */
class MultipartBarChart : BaseChart, View.OnTouchListener {

    constructor(context: Context) :this(context, null)
    constructor(context: Context, attrs: AttributeSet?) :this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int):super(context, attrs, defStyle){
        mGestureDetector = GestureDetector(getContext(), MyOnGestureListener())
        mScaleGestureDetector = ScaleGestureDetector(context, MyOnScaleGestureListener())
        mScroller = Scroller(context)
        setOnTouchListener(this)
    }

    lateinit var displayConfig : MultipartBarConfig
    lateinit var yAxisMark: YAxisMark
    lateinit var xAxisMark: XAxisMark
    private val barColor = intArrayOf(
            Color.parseColor("#f46763"),
            Color.parseColor("#3cd595"),
            Color.parseColor("#4d7bff")) //柱颜色


    private var _datas = mutableListOf<MultipartBarData>()
    var dataTotalCount : Int = -1

    fun setDatas(datas : List<MultipartBarData>){
        _datas.clear()
        _datas.addAll(datas)
        if(dataTotalCount<0)
            dataTotalCount = datas.size
        initial()
        if (showAnim) chartAnimStarted = false
        setLoading(false)
    }

    /**
     * 初步计算，当设置数据 & size发生变化时调用
     */
    private var barWidth : Float = 0f  //柱宽度
    private var barSpace : Float = 0f  //柱间的间距
    private var oneDataWidth : Float = 0f
    private var allDataWidth : Float = 0f
    private var startPointx : Float = 0f  //第一条数据绘制的x坐标
    private fun initial(){
        if(_datas.isNullOrEmpty() || rectChart==null)
            return
        if(!this::displayConfig.isInitialized)
            throw RuntimeException("---------请设置初始显示方案")
        if(!this::xAxisMark.isInitialized || !this::yAxisMark.isInitialized)
            throw RuntimeException("---------请设置x or y坐标")
        /**计算表体矩形rectChart*/
        paintText.textSize = xAxisMark.textSize.toFloat()
        xAxisMark.textHeight = FontUtil.getFontHeight(paintText)
        xAxisMark.textLead = FontUtil.getFontLeading(paintText)
        //确定图表最下放绘制位置
        rectChart.bottom -= (xAxisMark.textHeight + xAxisMark.textSpace)
        xAxisMark.drawPointY = rectChart.bottom + xAxisMark.textSpace + xAxisMark.textLead
        LogUtil.e(TAG, "--------------设置数据后第一次计算所有数据y轴刻度，以确定图标左侧位置")
        calculateYMark(true)
        paintText.textSize = yAxisMark.textSize.toFloat()
        yAxisMark.textHeight = FontUtil.getFontHeight(paintText)
        yAxisMark.textLead = FontUtil.getFontLeading(paintText)
        val maxLable: String = yAxisMark.getMarkText(yAxisMark.cal_mark_max)
        val minLable: String = yAxisMark.getMarkText(yAxisMark.cal_mark_min)
        val maxYWidth = FontUtil.getFontlength(paintText, if(maxLable.length>minLable.length) maxLable else minLable)
        rectChart.left = rectChart.left + yAxisMark.textSpace + maxYWidth
        LogUtil.w(TAG, "原始顶部：${rectChart.top}  单位高度${if (TextUtils.isEmpty(yAxisMark.unit)) 0f else (yAxisMark.textHeight + yAxisMark.textSpace)}   y一半：${yAxisMark.textHeight / 2}")
        rectChart.top = rectChart.top + yAxisMark.textHeight / 2 + (if (TextUtils.isEmpty(yAxisMark.unit)) 0f else (yAxisMark.textHeight + yAxisMark.textSpace))
        LogUtil.v(TAG, "确定表格矩形 $rectChart  宽度 ${rectChart.width()}  高度${rectChart.height()}")
        /**重新计算柱子宽度 和 间距*/
        LogUtil.e(TAG, "--------------根据显示配置和数据，计算柱子宽度和间距")
        //根据设置的柱子宽度和间距，计算所有数据宽度
        allDataWidth = dataTotalCount * displayConfig.barWidth + (dataTotalCount+1) * displayConfig.barSpace
        barWidth = displayConfig.barWidth
        barSpace = displayConfig.barSpace
        when(displayConfig.displayScheme){
            DisplayScheme.SHOW_ALL->{  //全部显示
                if(allDataWidth > rectChart.width()){  //超出时，重新计算barWidth
//                    barWidth * dataTotalCount + barWidth*displayConfig.spacingRatio*(dataTotalCount+1) = rectChart.width()
                    barWidth = rectChart.width()/(dataTotalCount + displayConfig.spacingRatio*(dataTotalCount+1))
                    barSpace = barWidth * displayConfig.spacingRatio
                    LogUtil.w(TAG, "全部展示时宽度超过，重新计算柱子宽度$barWidth  间距 $barSpace")
                }
            }
            DisplayScheme.SHOW_BEGIN->{}//从第一条数据开始展示，柱子宽度就是设置的宽度
            DisplayScheme.SHOW_END->{}  //从最后一条数据开始展示，柱子宽度就是设置的宽度
        }
        LogUtil.v(TAG, "确定柱子宽度 $barWidth  间距 $barSpace")
        /**确定第一条数据的绘制x坐标   计算滚动最大值*/
        startPointx = rectChart.left
        oneDataWidth = barWidth + barSpace
        allDataWidth = dataTotalCount * barWidth + (dataTotalCount+1) * barSpace
        if(allDataWidth < rectChart.width())  //数据不能填充时，居中展示
            startPointx = (rectChart.width()-allDataWidth)/2

        scrollx = 0f
        scrollXMax = 0f
        scalex = 1f
        if(allDataWidth>rectChart.width()){
            scrollXMax = rectChart.width() -allDataWidth //最大滚动距离，是一个负值
        }
        when(displayConfig.displayScheme){
            DisplayScheme.SHOW_ALL->{ }//全部显示
            DisplayScheme.SHOW_BEGIN->{
                scrollx = 0f
            }
            DisplayScheme.SHOW_END->{
                if(allDataWidth > rectChart.width())
                    startPointx = (rectChart.width() - allDataWidth)/2
                scrollx = scrollXMax
            }
        }
        LogUtil.v(TAG, "单个柱子+间距 $oneDataWidth  所有数据宽度 $allDataWidth")
    }

    private var startIndex = 0
    private var endIndex = 0
    /**计算当前缩放、移动状态下，需要绘制的数据的起始和结束索引*/
    private fun caculateIndex(){
        //预算需要绘制的组的开始和结尾index，避免不必要的计算浪费性能
        val scaleOneWidth = oneDataWidth*scalex
        if(allDataWidth*scalex<=rectChart.width()){
            startIndex = 0
            endIndex = _datas.size-1
        }else{
            startIndex = (-scrollx / scaleOneWidth).toInt()
            endIndex = ((-scrollx + rectChart.width()) / scaleOneWidth).toInt() - 1
//            LogUtil.w(TAG, "总宽度：${-scrollx + rectChart.width()}  当前状态下一个柱子及间隙宽度$scaleOneWidth   最后一条数据索引取整$endIndex")
            val nextVisible = (-scrollx + rectChart.width()) % scaleOneWidth>=barSpace*scalex
            endIndex += if(nextVisible)1 else 0
//            LogUtil.w(TAG, "取余：${(-scrollx + rectChart.width()) % scaleOneWidth}  柱子宽度${barSpace*scalex}   是否可见$nextVisible   结束索引$endIndex")
            endIndex = endIndex.coerceAtMost(_datas.size - 1)
        }
    }
    /**y值累加*/
    private fun getTotalValuey(data : MultipartBarData) : Float{
        var valuey = 0f
        for(v in data.valuey)
            valuey+=v
        return valuey
    }

    /**获取startIndex~endIndex的数据最大最小值，并根据需要显示几个y刻度计算出递增值*/
    private fun calculateYMark(all:Boolean) {
        val redundance = 1.1f //y轴最大和最小值冗余
        yAxisMark.cal_mark_max = -Float.MAX_VALUE //Y轴刻度最大值
        yAxisMark.cal_mark_min = Float.MAX_VALUE  //Y轴刻度最小值
        var startIdx = 0
        var endIdx = _datas.size-1
        if(!all){
            caculateIndex()
            startIdx = startIndex
            endIdx = endIndex
        }
        for(index in startIdx..endIdx){
            val valuey = getTotalValuey(_datas[index])
            yAxisMark.cal_mark_max = Math.max(yAxisMark.cal_mark_max, valuey)
            yAxisMark.cal_mark_min = Math.min(yAxisMark.cal_mark_min, valuey)
        }
        LogUtil.w(TAG, "$startIdx ~ $endIdx 真实最小最大值：" + yAxisMark.cal_mark_min + "  " + yAxisMark.cal_mark_max)
        //只有一个点的时候
        if (yAxisMark.cal_mark_min == yAxisMark.cal_mark_max) {
            when {
                yAxisMark.cal_mark_min > 0 -> {
                    yAxisMark.cal_mark_min = 0f
                }
                yAxisMark.cal_mark_min == 0f -> {
                    yAxisMark.cal_mark_max = 1f
                }
                yAxisMark.cal_mark_min < 0 -> {
                    yAxisMark.cal_mark_max = 0f
                }
            }
        }
        if (yAxisMark.markType == MarkType.Integer) {
            val min = if (yAxisMark.cal_mark_min > 0) 0 else yAxisMark.cal_mark_min.toInt()
            val max = yAxisMark.cal_mark_max.toInt()
            var mark = (max - min) / (yAxisMark.lableNum - 1) + if ((max - min) % (yAxisMark.lableNum - 1) > 0) 1 else 0
            mark = if (mark == 0) 1 else mark //最大值和最小值都为0的情况
            val first = (mark.toString() + "").substring(0, 1).toInt() + 1
            if ((mark.toString() + "").length == 1) {
                //YMARK = 1、2、5、10
                mark = if (mark == 3 || mark == 4 || mark == 6 || mark == 7 || mark == 8 || mark == 9) if (mark == 3 || mark == 4) 5 else 10 else mark
            } else if ((mark.toString() + "").length == 2) {
                mark = first * 10
            } else if ((mark.toString() + "").length == 3) {
                mark = first * 100
            } else if ((mark.toString() + "").length == 4) {
                mark = first * 1000
            } else if ((mark.toString() + "").length == 5) {
                mark = first * 10000
            } else if ((mark.toString() + "").length == 6) {
                mark = first * 100000
            }
            yAxisMark.cal_mark_min = 0f
            yAxisMark.cal_mark_max = mark * (yAxisMark.lableNum - 1).toFloat()
            yAxisMark.cal_mark = mark.toFloat()
        } else {   //Float   //Percent
            yAxisMark.cal_mark_max = if (yAxisMark.cal_mark_max < 0) yAxisMark.cal_mark_max / redundance else yAxisMark.cal_mark_max * redundance
//            yAxisMark.cal_mark_min = if (yAxisMark.cal_mark_min < 0) yAxisMark.cal_mark_min * redundance else yAxisMark.cal_mark_min / redundance

            yAxisMark.cal_mark_min = 0f

            yAxisMark.cal_mark = (yAxisMark.cal_mark_max - yAxisMark.cal_mark_min) / (yAxisMark.lableNum - 1)
        }
        //小数点位
        if (yAxisMark.digits == 0) {
            val mark = (yAxisMark.cal_mark_max - yAxisMark.cal_mark_min) / (yAxisMark.lableNum - 1)
            if (mark < 1) {
                val pattern = "[1-9]"
                val p = Pattern.compile(pattern)
                val m = p.matcher(mark.toString() + "") // 获取 matcher 对象
                m.find()
                val index = m.start()
                yAxisMark.digits = index - 1
                LogUtil.w(TAG, mark.toString() + "第一个大于0的数字位置：" + index + "   保留小数位数：" + yAxisMark.digits)
            }
        }
//        LogUtil.w(TAG, "最终最小最大值：" + yAxisMark.cal_mark_min + "  " + yAxisMark.cal_mark_max + "   " + yAxisMark.cal_mark)
    }

    /**根据startIndex~endIndex计算x标签间隔数量*/
    //从当前绘制的第一条数据开始，每隔多少展示一个x标签
    private var xindexSpace: Int = 0
    private fun caculateXMark() {
        caculateIndex()
        paintText.textSize = xAxisMark.textSize.toFloat()
        //计算当前显示的数据的x轴文字长度最大值
        var xTextMaxLength = 0f
        for(index in startIndex..endIndex){
            xTextMaxLength = xTextMaxLength.coerceAtLeast(FontUtil.getFontlength(paintText, _datas[index].valuex))
        }
        var xNumber = (rectChart.width() / xTextMaxLength).toInt()
        val dataNumber = endIndex - startIndex + 1
        LogUtil.e(TAG, "绘制的数据条数${endIndex-startIndex+1}  X轴文字最长长度$xTextMaxLength   理论最多可显示$xNumber 个")
        xNumber = Math.min(xNumber, xAxisMark.lableNum)
        when(xNumber){
            1->xindexSpace = endIndex - startIndex + 10   //只显示第一个
            2->xindexSpace = endIndex - startIndex   //显示第一个和最后一个
            3->{   //取中点
                when(dataNumber % 2){
                    0->xindexSpace = (dataNumber-1)/2   //数据条数为偶数 变为奇数取中点
                    1->xindexSpace = dataNumber/2   //数据条数为奇数取中点
                }
            }
            else->{
                xindexSpace = when(dataNumber%xNumber){
                    0-> dataNumber/xNumber        //数据条数 整除 lable数 时，取除数
                    else-> dataNumber/xNumber + 1 //不能整除时 +1
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initial()
    }


    override fun drawChart(canvas: Canvas?) {
        LogUtil.e(TAG, "-----------开始绘制，当前缩放系数$scalex  偏移量$scrollx")
        if(_datas.isNullOrEmpty())
            return
        //预算需要绘制的组的开始和结尾index，避免不必要的计算浪费性能
//        caculateIndex()
        //计算Y轴刻度值
        calculateYMark(false)
        //计算x轴刻度值
        caculateXMark()

        val yMarkSpace = (rectChart.bottom - rectChart.top) / (yAxisMark.lableNum - 1)
        paintEffect.style = Paint.Style.STROKE
        paintEffect.strokeWidth = yAxisMark.lineWidth.toFloat()
        paintEffect.color = yAxisMark.lineColor
        paintText.textSize = yAxisMark.textSize.toFloat()
        paintText.color = yAxisMark.textColor
//        canvas.drawLine(rectChart.left, rectChart.top, rectChart.left, rectChart.bottom, paint);
        //        canvas.drawLine(rectChart.left, rectChart.top, rectChart.left, rectChart.bottom, paint);
        val effects: PathEffect = DashPathEffect(floatArrayOf(15f, 6f, 15f, 6f), 0f)
        paintEffect.pathEffect = effects
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = yAxisMark.lineWidth.toFloat()
        paint.color = yAxisMark.lineColor
        for (i in 0..yAxisMark.lableNum) {
            /**绘制横向线 */
            canvas!!.drawLine(rectChart.left, rectChart.bottom - yMarkSpace * i,
                    rectChart.right, rectChart.bottom - yMarkSpace * i, paint)
            /**绘制y刻度 */
            val text = yAxisMark.getMarkText(yAxisMark.cal_mark_min + i * yAxisMark.cal_mark)
            canvas!!.drawText(text,
                    rectChart.left - yAxisMark.textSpace - FontUtil.getFontlength(paintText, text),
                    rectChart.bottom - yMarkSpace * i - yAxisMark.textHeight / 2 + yAxisMark.textLead, paintText)
        }
        //绘制Y轴单位
        if (!TextUtils.isEmpty(yAxisMark.unit)) {
            canvas!!.drawText(yAxisMark.unit,
                    rectChart.left - yAxisMark.textSpace - FontUtil.getFontlength(paintText, yAxisMark.unit),
                    //y = 图表顶部 - 单位文字距离 - 单位文字高度 + 最上方y刻度高度/2
                    rectChart.top - yAxisMark.textSpace - yAxisMark.textHeight * 3 / 2 + yAxisMark.textLead, paintText)
        }

        val rect = RectF()
        paint.style = Paint.Style.FILL

        for(index:Int in startIndex..endIndex){
            //计算柱体x,y坐标
            if(allDataWidth*scalex<=rectChart.width()){
                rect.left = rectChart.left + (rectChart.width() - allDataWidth*scalex)/2 +
                        index * oneDataWidth*scalex + barSpace*scalex
//                LogUtil.v(TAG, "数据不够填充表，居中显示，当前数据x坐标 ${rect.left}")
            }else{
                rect.left = scrollx + rectChart.left + index * oneDataWidth*scalex + barSpace*scalex
//                LogUtil.v(TAG, "数据超过表，当前数据x坐标 ${rect.left}")
            }
            rect.right = rect.left + barWidth*scalex
            rect.bottom = rectChart.bottom
            rect.top = rectChart.bottom
//            LogUtil.d(TAG, "$i 绘制："+_datas[i].valuey)
            /**绘制柱状 */
            //过滤掉绘制区域外的柱
            var barLayer:Int? = null
            if(index == startIndex || index == endIndex){
                /**
                 * Canvas有两种坐标系：
                 * 1. Canvas自己的坐标系：(0,0,canvas.width,canvas.height)，它是固定不变的
                 * 2. 绘图坐标系：用于绘制，通过Matrix让Canvas平移translate，旋转rotate，缩放scale 等时实际上操作的是绘图坐标系
                 * 由于绘图坐标系中Matrix的改变是不可逆的，所以产生了状态栈和Layer栈，它们分别运用于save方法和saveLayer方法，使得绘图坐标系恢复到保存时的状态
                 * 1. 状态栈：save()、restore()保存和还原变换操作Matrix以及Clip剪裁，也可以restoretoCount()直接还原到对应栈的保存状态
                 * 2. Layer栈:saveLayer()时会新建一个透明图层（离屏Bitmap-离屏缓冲），并且将saveLayer之前的一些Canvas操作延续过来，
                 *            后续的绘图操作都在新建的layer上面进行，当调用restore或者restoreToCount时更新到对应的图层和画布上
                 */
                barLayer = canvas?.saveLayer(rectChart.left, rectChart.top, rectChart.right,
                        rectChart.bottom + xAxisMark.textSpace + xAxisMark.textHeight
                        , paint, Canvas.ALL_SAVE_FLAG)
            }
            for(vindex : Int in _datas[index].valuey.indices){
                paint.color = barColor[vindex]
                if (_datas[index].valuey[vindex] != null) {
                    val vh = rectChart.height() / (yAxisMark.cal_mark_max - yAxisMark.cal_mark_min) *
                            (_datas[index].valuey[vindex] - yAxisMark.cal_mark_min) * chartAnimValue
                    rect.top -= vh
                    canvas?.drawRect(rect, paint)
                    rect.bottom = rect.top
                }
            }
            if(barLayer!=null)
                canvas?.restoreToCount(barLayer)//还原画布，将柱子更新到画布上
            /**绘制x坐标 */
            //测试：绘制索引
//            canvas?.drawText("$i", rect.left + (barWidth*scalex) / 2 - FontUtil.getFontlength(paintText, "$i") / 2, xAxisMark.drawPointY, paintText)
            //从第一条数据开始每隔xindexSpace绘制一个x刻度
            if((index - startIndex) % xindexSpace == 0){
                val x = rect.left + (barWidth*scalex) / 2 - FontUtil.getFontlength(paintText, _datas[index].valuex) / 2
                //过滤掉超出图表范围的x值绘制，通常是第一条和最后一条
                if(x < paddingLeft || x+FontUtil.getFontlength(paintText, _datas[index].valuex) > measuredWidth - paddingRight)
                    continue
                canvas?.drawText(_datas[index].valuex, x,xAxisMark.drawPointY, paintText)
            }
        }


    }

    /**********************************3. 测量和绘制👆 */


    /**************************4. 事件👇******************************/
    private var mDownX = 0f
    private var mDownY = 0f
    private var mGestureDetector : GestureDetector
    private var mScroller : Scroller
    private var scrollXMax = 0f //最大滚动距离，是一个负值
    private var scrollx = 0f    //当前滚动距离，默认从第一条数据绘制（scrollx==0），如果从最后一条数据绘制（scrollx==scrollXMax）
    private var mScaleGestureDetector : ScaleGestureDetector
    private var scalex = 1f    //x方向缩放系数

    /**
     * 重写dispatchTouchEvent，并调用requestDisallowInterceptTouchEvent申请父控件不要拦截事件，将事件处理权交给图表
     *
     * 这对图表来说是非常重要的，比如图表放在ScrollerView里面时，如果不调用requestDisallowInterceptTouchEvent(true)，
     * 图表接受的事件将由ScrollerView决定，一旦ScrollerView发现竖直滚动则会拦截事件，导致图表不能再接受到事件
     *
     * 此处首先申请父控件不要拦截事件，所有事件都将传到图表中，由图表决定自己是否处理事件，如果不需要处理（竖直方向滑动距离大于水平方向）则让父控件处理
     * 需要注意的是一旦放弃处理，剩下的事件将不会被收到
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = event.x
                mDownY = event.y
                parent.requestDisallowInterceptTouchEvent(true) //ACTION_DOWN的时候，赶紧把事件hold住
            }
            MotionEvent.ACTION_MOVE -> if (Math.abs(event.y - mDownY) > Math.abs(event.x - mDownX)) {
                //竖直滑动的距离大于水平的时候，将事件还给父控件
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        mScaleGestureDetector.onTouchEvent(event)
        mGestureDetector.onTouchEvent(event)
        return true
    }


    inner class MyOnGestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            if (!mScroller.isFinished) mScroller.forceFinished(true)
            return true //事件被消费，下次才能继续收到事件
        }

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleing) {
                scrollx -= distanceX //distanceX左正右负
                scrollx = Math.min(scrollx, 0f)
                scrollx = Math.max(scrollXMax, scrollx)
                Log.d(TAG, "---------滚动："+distanceX+"   "+scrollx);
                postInvalidate()
            }
            return false
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            //          LogUtil.e(TAG,"onFling------------>velocityX="+velocityX+"    velocityY="+velocityY);
            /**
             * 从当前位置scrollx开始滚动，
             * 最小值为scrollXMax -- 滚动到最后
             * 最大值为0 -- 滚动到开始
             */
            mScroller.fling(scrollx.toInt(), 0,
                    velocityX.toInt(), 0,
                    scrollXMax.toInt(), 0,
                    0, 0
            )
            return false
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (mScroller.isFinished) return
        if (mScroller.computeScrollOffset()) {
//            Log.d(TAG, "滚动后计算："+mScroller.getCurrX());
            scrollx = mScroller.currX.toFloat()
            invalidate()
        }
    }
    var scaleing = false
    inner class MyOnScaleGestureListener : OnScaleGestureListener {
        private var focusIndex = 0
        private var beginScrollx = 0f
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scalex *= detector.scaleFactor
            LogUtil.e(TAG, "--------------------当前缩放值$scalex  缩放${detector.scaleFactor}   缩放之后${scalex*detector.scaleFactor}")
            //缩放范围约束
            scalex = scalex.coerceAtMost(2f)
            scalex = scalex.coerceAtLeast(1f)
            LogUtil.e(TAG, "--------------------最终值$scalex ")
            //重新计算最大偏移量
            if(allDataWidth * scalex > rectChart.width()){
                scrollXMax = rectChart.width() - allDataWidth * scalex
                //为了保证焦点对应的点位置不变，是使用公式： beginScrollx + rectChart.left + focusIndex*beginPointWidth = scrollx + rectChart.left + focusIndex*pointWidth
                scrollx = beginScrollx + focusIndex * (oneDataWidth - oneDataWidth*scalex)
                scrollx = Math.min(scrollx, 0f)
                scrollx = Math.max(scrollXMax, scrollx)
                Log.i(TAG, "缩放后偏移："+scrollx);
            }else{
                scrollXMax = 0f  //数据不能填充时，居中展示
                scrollx = 0f
            }
            postInvalidate()
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            val width = -scrollx + (detector.focusX - rectChart.left)
            val zs = (width / (oneDataWidth*scalex)).toInt()
            val ys = width % (oneDataWidth*scalex)
            focusIndex = zs + if(ys>(barWidth/2+barSpace)*scalex)1 else 0
            beginScrollx = scrollx
            Log.i(TAG, "缩放开始了，焦点索引为$focusIndex") // 缩放因子
            scaleing = true
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaleing = false
        }
    }

    /**************************4. 事件👆******************************/
}

