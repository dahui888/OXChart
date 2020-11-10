package com.openxu.hkchart.line;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Scroller;

import com.openxu.cview.xmstock20201030.build.AxisMark;
import com.openxu.cview.xmstock20201030.build.Line;
import com.openxu.hkchart.BaseChart;
import com.openxu.hkchart.bar.Bar;
import com.openxu.hkchart.element.XAxisMark;
import com.openxu.hkchart.element.YAxisMark;
import com.openxu.utils.DensityUtil;
import com.openxu.utils.FontUtil;
import com.openxu.utils.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * autour : openXu
 * date : 2017/7/24 10:46
 * className : LineChart
 * version : 1.0
 * description : 曲线、折线图
 *
 */
public class LineChart extends BaseChart implements View.OnTouchListener {

    /**设置*/
    private List<List<LinePoint>> lineData;
    private YAxisMark yAxisMark;
    private XAxisMark xAxisMark;
    private boolean scaleAble = true;  //是否支持放大
    private boolean scrollAble = true;  //是否支持滚动
    private boolean showBegin = true;    //当数据超出一屏宽度时，实现最后的数据
    private int[] lineColor = new int[]{
            Color.parseColor("#f46763"),
            Color.parseColor("#3cd595"),
            Color.parseColor("#4d7bff"),
            Color.parseColor("#4d7bff")};
    /**计算*/
    private int pageShowNum;       //第一次页面总数据量
    private int maxPointNum;
    private float pointWidthMin;   //最初的每个点占据的宽度，最小缩放值
    private float pointWidthMax;   //最初的每个点占据的宽度，最大放大值
    private float pointWidth;      //每个点占据的宽度
    private float scrollXMax;      //最大滚动距离，是一个负值
    private float scrollx;         //当前滚动距离，默认从第一条数据绘制（scrollx==0），如果从最后一条数据绘制（scrollx==scrollXMax）

    protected GestureDetector mGestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    protected Scroller mScroller;

    public LineChart(Context context) {
        this(context, null);
    }

    public LineChart(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LineChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void init(Context context, AttributeSet attrs, int defStyleAttr) {
        setOnTouchListener(this);
        mGestureDetector = new GestureDetector(getContext(), new MyOnGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new MyOnScaleGestureListener());
        mScroller = new Scroller(context);
//        showAnim = false;
    }

    /***********************************1. setting👇**********************************/
    public void setYAxisMark(YAxisMark yAxisMark) {
        this.yAxisMark = yAxisMark;
    }
    public void setXAxisMark(XAxisMark xAxisMark) {
        this.xAxisMark = xAxisMark;
    }
    public void setShowBegin(boolean showBegin) {
        this.showBegin = showBegin;
    }
    public void setPageShowNum(int pageShowNum) {
        this.pageShowNum = pageShowNum;
    }
    public void setScaleAble(boolean scaleAble) {
        this.scaleAble = scaleAble;
        if(scaleAble)   //支持缩放的一定支持滚动
            this.scrollAble = true;
    }
    public void setScrollAble(boolean scrollAble) {
        this.scrollAble = scrollAble;
    }

    public void setData(List<List<LinePoint>> lineData) {
        Log.w(TAG, "设置数据，总共"+lineData.size()+"条线，每条线"+lineData.get(0).size()+"个点");
        this.lineData = lineData;
        if(showAnim)
            chartAnimStarted = false;
        calculate();
        setLoading(false);
    }
    /***********************************1. setting👆**********************************/

    /***********************************2. 计算👇**********************************/
    private void calculate() {
        paintText.setTextSize(xAxisMark.textSize);
        xAxisMark.textHeight = (int)FontUtil.getFontHeight(paintText);
        xAxisMark.textLead = (int)FontUtil.getFontLeading(paintText);
        //确定图表最下放绘制位置
        rectChart.bottom = getMeasuredHeight() - getPaddingBottom() - xAxisMark.textHeight - xAxisMark.textSpace;
        xAxisMark.drawPointY = rectChart.bottom + xAxisMark.textSpace + xAxisMark.textLead;
        calculateYMark();
        paintText.setTextSize(yAxisMark.textSize);
        yAxisMark.textHeight = FontUtil.getFontHeight(paintText);
        yAxisMark.textLead = FontUtil.getFontLeading(paintText);
        String maxLable = yAxisMark.getMarkText(yAxisMark.cal_mark_max);
        rectChart.left =  (int)(getPaddingLeft() + yAxisMark.textSpace + FontUtil.getFontlength(paintText, maxLable));
        rectChart.top = rectChart.top + yAxisMark.textHeight/2;

        for(List<LinePoint> list :lineData)
            maxPointNum = Math.max(maxPointNum, list.size());
        //没有设置展示数据量，则默认为全部展示
        if(pageShowNum<=0){
            pageShowNum = maxPointNum;
        }
        Log.w(TAG, "计算pageShowNum="+pageShowNum);
        pointWidthMin = rectChart.width() / (pageShowNum-1);
        pointWidth = pointWidthMin;
        pointWidthMax = rectChart.width() / (xAxisMark.lableNum-1);
        //数据没有展示完，说明可以滚动
        if(pageShowNum<maxPointNum)
            scrollXMax = -(pointWidth*(maxPointNum-1) - rectChart.width());      //最大滚动距离，是一个负值
        scrollx = showBegin?0:scrollXMax;

        caculateXMark();
        /**计算点坐标*/
      /*  for (int i = 0; i < lineData.size(); i++) {
            List<LinePoint> linePoints = lineData.get(i);
            for (int j = 0; j < linePoints.size(); j++) {
                if (linePoints.get(j).getValuey() == null)
                    continue;
                linePoints.get(j).setPoint(new PointF(
                        rectChart.left + j * pointWidth,
                        rectChart.bottom - (rectChart.bottom - rectChart.top) /
                                (yAxisMark.cal_mark_max - yAxisMark.cal_mark_min) * (linePoints.get(j).getValuey() - yAxisMark.cal_mark_min)
                ));
            }
        }*/

        Log.w(TAG, "计算scrollXMax="+scrollXMax+"   scrollx="+scrollx);
    }
    public List<String> xlables = new ArrayList<>();

    private void caculateXMark(){
        xlables.clear();
        if(xAxisMark.lables!=null){
            xlables.addAll(Arrays.asList(xAxisMark.lables));
            return;
        }
//        float markSpace = (-scrollXMax+rectChart.width())/(xAxisMark.lableNum-1);
        float markSpace = rectChart.width()/(xAxisMark.lableNum-1);
        //每隔多少展示一个标签
        int indexSpace = (int)(markSpace/pointWidth);
        indexSpace = Math.max(indexSpace, 1);
//        List<List<LinePoint>> lineData;
        for(int i =0; i< lineData.get(0).size(); i++){
            if(i%indexSpace==0)
                xlables.add(lineData.get(0).get(i).getValuex());
        }
        Log.w(TAG, "矩形区域需要展示"+xAxisMark.lableNum+"个标签，单个标签间距"+markSpace+"  每隔"+indexSpace+"个数据展示一个:"+xlables.size()+"   "+xlables);
    }

    private void calculateYMark() {
        float redundance = 1.01f;  //y轴最大和最小值冗余
        yAxisMark.cal_mark_max =  Float.MIN_VALUE;    //Y轴刻度最大值
        yAxisMark.cal_mark_min =  Float.MAX_VALUE;    //Y轴刻度最小值
        for(List<LinePoint> linePoints : lineData){
            for(LinePoint point : linePoints){
                yAxisMark.cal_mark_max = Math.max(yAxisMark.cal_mark_max, point.getValuey());
                yAxisMark.cal_mark_min = Math.min(yAxisMark.cal_mark_min, point.getValuey());
            }
        }
        LogUtil.i(TAG, "Y轴真实cal_mark_min="+yAxisMark.cal_mark_min+"  cal_mark_max="+yAxisMark.cal_mark_max);
        if(yAxisMark.markType == YAxisMark.MarkType.Integer){
            int min = 0;
            int max = (int)yAxisMark.cal_mark_max;
            int mark = (max-min)/(yAxisMark.lableNum - 1)+((max-min)%(yAxisMark.lableNum - 1)>0?1:0);
            int first = (Integer.parseInt((mark + "").substring(0, 1)) + 1);
            LogUtil.i(TAG, "mark="+mark+"  first="+first);

            if ((mark + "").length() == 1) {
                //YMARK = 1、2、5、10
                mark = (mark == 3 || mark == 4 || mark == 6 ||
                        mark == 7 || mark == 8 || mark == 9) ?
                        ((mark == 3 || mark == 4) ? 5 : 10)
                        : mark;
            } else if ((mark + "").length() == 2) {
                mark = first * 10;
            } else if ((mark + "").length() == 3) {
                mark = first * 100;
            } else if ((mark + "").length() == 4) {
                mark = first * 1000;
            } else if ((mark + "").length() == 5) {
                mark = first * 10000;
            } else if ((mark + "").length() == 6) {
                mark = first * 100000;
            }
            yAxisMark.cal_mark_min = 0;
            yAxisMark.cal_mark_max = mark * (yAxisMark.lableNum - 1);
            yAxisMark.cal_mark = mark;
        }else {   //Float   //Percent
            yAxisMark.cal_mark_max *= redundance;
            yAxisMark.cal_mark_min /= redundance;
            yAxisMark.cal_mark = (yAxisMark.cal_mark_max-yAxisMark.cal_mark_min)/(yAxisMark.lableNum - 1);
        }
        LogUtil.i(TAG, "  cal_mark_min="+yAxisMark.cal_mark_min+"   cal_mark_max="+yAxisMark.cal_mark_max+"  yAxisMark.cal_mark="+yAxisMark.cal_mark);
    }
    /***********************************2. 计算👆**********************************/

    /**********************************3. 测量和绘制👇***********************************/
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    public void drawChart(Canvas canvas) {
        long startTime =System.currentTimeMillis();
        float yMarkSpace = (rectChart.bottom - rectChart.top) / (yAxisMark.lableNum - 1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(yAxisMark.lineColor);
        paint.setColor(yAxisMark.lineColor);
        paintEffect.setStyle(Paint.Style.STROKE);
        paintEffect.setStrokeWidth(yAxisMark.lineWidth);
        paintEffect.setColor(yAxisMark.lineColor);
        paintText.setTextSize(yAxisMark.textSize);
        paintText.setColor(yAxisMark.textColor);
//        canvas.drawLine(rectChart.left, rectChart.top, rectChart.left, rectChart.bottom, paint);
        PathEffect effects = new DashPathEffect(new float[]{15, 6, 15, 6}, 0);
        paintEffect.setPathEffect(effects);
        for (int i = 0; i < yAxisMark.lableNum; i++) {
            /**绘制横向线*/
            canvas.drawLine(rectChart.left, rectChart.bottom - yMarkSpace * i,
                    rectChart.right, rectChart.bottom - yMarkSpace * i, paint);
            /**绘制y刻度*/
            String text = yAxisMark.getMarkText(yAxisMark.cal_mark_min + i * yAxisMark.cal_mark);
            canvas.drawText(text,
                    rectChart.left - yAxisMark.textSpace - FontUtil.getFontlength(paintText, text),
                    rectChart.bottom - yMarkSpace * i - yAxisMark.textHeight / 2 + yAxisMark.textLead, paintText);
        }

        /**绘制x轴刻度*/
//        if(xAxisMark.lables!=null){
//            //绘制固定的
//            drawFixedXLable(canvas);
//            lables = xAxisMark.lables;
//        }else{
//            drawXLable(canvas);
//        }

        /**绘制折线*/
        paint.setStyle(Paint.Style.STROKE);
        paintText.setTextSize(xAxisMark.textSize);
        paintText.setColor(xAxisMark.textColor);
        Path path = new Path();
        PointF lastPoint = new PointF();
        PointF currentPoint = new PointF();
        int startIndex = (int)(-scrollx/pointWidth);
        int endIndex = (int)((-scrollx+rectChart.width())/pointWidth+1);
        endIndex = Math.min(endIndex, maxPointNum-1);
//        Log.w(TAG, "绘制索引："+startIndex+" 至  "+endIndex+"   scrollx="+scrollx);
        int restorePath = canvas.save();
        for (int i = 0; i < lineData.size(); i++) {
            path.reset();
            List<LinePoint> linePoints = lineData.get(i);
            for(int j = startIndex; j<=endIndex; j++){
//            for (int j = 0; j < linePoints.size(); j++) {
                if(j>startIndex+(endIndex - startIndex)*chartAnimValue)
                    break;
                if (linePoints.get(j).getValuey() == null)
                    continue;
                currentPoint.x = scrollx + rectChart.left + j * pointWidth;
                currentPoint.y = rectChart.bottom - (rectChart.bottom - rectChart.top) /
                        (yAxisMark.cal_mark_max - yAxisMark.cal_mark_min) * (linePoints.get(j).getValuey() - yAxisMark.cal_mark_min);
                if (path.isEmpty()) {
                    path.moveTo(currentPoint.x, currentPoint.y);
                } else {
                    path.lineTo(currentPoint.x, currentPoint.y);
                }
                if(j % 50 == 0){
                    canvas.drawCircle(currentPoint.x, currentPoint.y, 10, paint);
                }
                if(i==0 && xlables.contains(linePoints.get(j).getValuex())){
                    int restoreText = canvas.save();
                    canvas.clipRect(new RectF(rectChart.left, rectChart.top, rectChart.right,
                            rectChart.bottom + xAxisMark.textSpace + xAxisMark.textLead));   //裁剪画布，只绘制rectChart的范围
//                    Log.v(TAG, "绘制x轴刻度"+linePoints.get(j).getValuex());
                    float x;
                    if(j==0){
                        x = currentPoint.x;
                    }else if(j == maxPointNum-1){
                        x = currentPoint.x - FontUtil.getFontlength(paintText, linePoints.get(j).getValuex());
                    }else {
                        x = currentPoint.x - FontUtil.getFontlength(paintText, linePoints.get(j).getValuex()) / 2;
                    }
                    canvas.drawText(linePoints.get(j).getValuex(), x,
                            rectChart.bottom + xAxisMark.textSpace + xAxisMark.textLead, paintText);
                    canvas.restoreToCount(restoreText);
                }
            }
            canvas.clipRect(rectChart);   //裁剪画布，只绘制rectChart的范围
            paint.setColor(lineColor[i]);
            canvas.drawPath(path, paint);
            //恢复到裁切之前的画布
            canvas.restoreToCount(restorePath);
        }
//        Log.w(TAG, "绘制一次需要："+(System.currentTimeMillis() - startTime)+ " ms");
    }

    /**绘制 XAxisMark.lables 设置的固定x刻度，*/
    private void drawFixedXLable(Canvas canvas){
        float oneWidth = (-scrollXMax+rectChart.width())/(xAxisMark.lables.length-1);
        Log.w(TAG, "最大滚动："+scrollXMax+ "  图表宽度"+rectChart.width()+"  lable数量"+xAxisMark.lables.length+"   单个跨度："+oneWidth);
        paintText.setTextSize(xAxisMark.textSize);
        paintText.setColor(xAxisMark.textColor);
        float x ;
        int restoreCount = canvas.save();
        canvas.clipRect(new RectF(rectChart.left, rectChart.bottom, rectChart.right, rectChart.bottom+ xAxisMark.textSpace+ xAxisMark.textHeight));
        for(int i = 0; i< xAxisMark.lables.length; i++){
            String text = xAxisMark.lables[i];
            if(i==0){
                x = scrollx + rectChart.left + i * oneWidth;
            }else if(i == xAxisMark.lables.length-1){
                x = scrollx + rectChart.left + i * oneWidth - FontUtil.getFontlength(paintText, text);
            }else {
                x = scrollx + rectChart.left + i * oneWidth - FontUtil.getFontlength(paintText, text) / 2;
            }
            canvas.drawText(text, x,
                    rectChart.bottom + xAxisMark.textSpace + xAxisMark.textLead, paintText);
        }
        canvas.restoreToCount(restoreCount);
    }

    /**********************************3. 测量和绘制👆***********************************/

    /**************************4. 事件👇******************************/
    protected float mDownX, mDownY;

    /**
     * 重写dispatchTouchEvent，并调用requestDisallowInterceptTouchEvent申请父控件不要拦截事件，将事件处理权交给图表
     *
     * 这对图表来说是非常重要的，比如图表放在ScrollerView里面时，如果不调用requestDisallowInterceptTouchEvent(true)，
     * 图表接受的事件将由ScrollerView决定，一旦ScrollerView发现竖直滚动则会拦截事件，导致图表不能再接受到事件
     *
     * 此处首先申请父控件不要拦截事件，所有事件都将传到图表中，由图表决定自己是否处理事件，如果不需要处理（竖直方向滑动距离大于水平方向）则让父控件处理
     * 需要注意的是一旦放弃处理，剩下的事件将不会被收到
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if(scrollAble){
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getX();
                    mDownY = event.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);//ACTION_DOWN的时候，赶紧把事件hold住
                    break;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(event.getY()-mDownY) > Math.abs(event.getX() - mDownX)*1.5) {
                        //竖直滑动的距离大于水平的时候，将事件还给父控件
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    break;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(scaleAble) {
            scaleGestureDetector.onTouchEvent(event);
            mGestureDetector.onTouchEvent(event);
        }else if(scrollAble) {
            mGestureDetector.onTouchEvent(event);
        }
        return true;
    }
    class MyOnGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            if(!mScroller.isFinished())
                mScroller.forceFinished(true);
            return true;   //事件被消费，下次才能继续收到事件
        }
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if(!scaleing) {
                scrollx -= distanceX;    //distanceX左正右负
                scrollx = Math.min(scrollx, 0);
                scrollx = Math.max(scrollXMax, scrollx);
//                Log.d(TAG, "滚动：" + distanceX + "   " + scrollx);
                postInvalidate();
            }
            return false;
        }
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //          LogUtil.e(TAG,"onFling------------>velocityX="+velocityX+"    velocityY="+velocityY);
            /**
             * 从当前位置scrollx开始滚动，
             * 最小值为scrollXMax -- 滚动到最后
             * 最大值为0 -- 滚动到开始
             */
            mScroller.fling((int)scrollx, 0,
                    (int)velocityX, 0,
                    (int)scrollXMax, 0,
                    0, 0
            );
            return false;
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if(scrollAble) {
            if (mScroller.isFinished())
                return;
            if (mScroller.computeScrollOffset()) {
                Log.d(TAG, "滚动后计算：" + mScroller.getCurrX());
                scrollx = mScroller.getCurrX();
                postInvalidate();
            }
        }
    }

    boolean scaleing = false;
    class MyOnScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {
        private float focusIndex;
        private float beginScrollx;
        private float beginPointWidth;
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            pointWidth *= detector.getScaleFactor();
            //缩放范围约束
            pointWidth = Math.min(pointWidth, pointWidthMax);
            pointWidth = Math.max(pointWidth, pointWidthMin);
            //重新计算最大偏移量
            scrollXMax = -(pointWidth*(maxPointNum-1) - rectChart.width());      //最大滚动距离，是一个负值
            //计算当前偏移量
//            Log.i(TAG, "当前偏移："+scrollx+"    缩放中心数据索引 = " +index);
            //为了保证焦点对应的点位置不变，是使用公式： beginScrollx + rectChart.left + focusIndex*beginPointWidth = scrollx + rectChart.left + focusIndex*pointWidth
            scrollx = beginScrollx + focusIndex*(beginPointWidth - pointWidth);
            scrollx = Math.min(scrollx, 0);
            scrollx = Math.max(scrollXMax, scrollx);
            caculateXMark();
//            Log.i(TAG, "缩放后偏移："+scrollx);
            postInvalidate();
            return true;
        }
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            focusIndex = (int)((-scrollx + (detector.getFocusX()-rectChart.left))/pointWidth);
            beginScrollx = scrollx;
            beginPointWidth = pointWidth;
            Log.i(TAG, "缩放开始了，焦点索引为"+ focusIndex);   // 缩放因子
            scaleing = true;
            return true;
        }
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            scaleing = false;
        }
    }

    /**************************4. 事件👆******************************/

}
