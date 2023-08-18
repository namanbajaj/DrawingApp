package com.nbaj.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var drawPath : CustomPath? = null
    private var canvasBitmap : Bitmap? = null
    private var drawPaint : Paint? = null
    private var canvasPaint : Paint? = null
    private var brushSize : Float = 0.toFloat()
    private var color : Int = Color.BLACK
    private var canvas : Canvas? = null
    private val paths = ArrayList<CustomPath>()
    private val undoPaths = ArrayList<CustomPath>()

    init {
        setUpDrawing()
    }

    fun undo() {
        if (paths.size > 0) {
            undoPaths.add(paths.removeAt(paths.size - 1))
            invalidate()
        }
    }

    fun redo() {
        if (undoPaths.size > 0) {
            paths.add(undoPaths.removeAt(undoPaths.size - 1))
            invalidate()
        }
    }

    fun clear() {
        paths.clear()
        undoPaths.clear()
        invalidate()
    }

    private fun setUpDrawing() {
        drawPath = CustomPath(color, brushSize)
        drawPaint = Paint()
        drawPaint!!.color = color
        drawPaint!!.style = Paint.Style.STROKE
        drawPaint!!.strokeJoin = Paint.Join.ROUND
        drawPaint!!.strokeCap = Paint.Cap.ROUND
        canvasPaint = Paint(Paint.DITHER_FLAG)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap!!, 0f, 0f, canvasPaint)

        for(path in paths) {
            drawPaint!!.strokeWidth = path.brushThickness
            drawPaint!!.color = path.color
            canvas.drawPath(path, drawPaint!!)
        }

        if(!drawPath!!.isEmpty){
            drawPaint!!.strokeWidth = drawPath!!.brushThickness
            drawPaint!!.color = drawPath!!.color
            canvas.drawPath(drawPath!!, drawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event!!.x
        val touchY = event!!.y

        undoPaths.clear()

        when(event.action){
            MotionEvent.ACTION_DOWN -> {
                drawPath!!.color = color
                drawPath!!.brushThickness = brushSize

                drawPath!!.reset()
                drawPath!!.moveTo(touchX, touchY)
            }
            MotionEvent.ACTION_MOVE -> {
                drawPath!!.lineTo(touchX, touchY)
            }
            MotionEvent.ACTION_UP -> {
                paths.add(drawPath!!)
                drawPath = CustomPath(color, brushSize)
            }
            else -> return false
        }
        invalidate()

        return true
    }

    fun setBrushSize(newSize : Float){
        brushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newSize, resources.displayMetrics)
        drawPaint!!.strokeWidth = brushSize
    }

    fun setBrushColor(newColor : String){
        color = Color.parseColor(newColor)
        drawPaint!!.color = color
    }

    internal inner class CustomPath(var color: Int, var brushThickness: Float) : Path() {
    }

}