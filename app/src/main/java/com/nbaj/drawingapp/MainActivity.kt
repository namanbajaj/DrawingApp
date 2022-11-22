package com.nbaj.drawingapp

import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.get

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var currentPainColorList: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        // brush size
        drawingView = findViewById(R.id.drawingView)
        drawingView!!.setBrushSize(20f)

        val brushButton = findViewById<ImageButton>(R.id.ib_brush)
        brushButton.setOnClickListener {
            showBrushSizeDialog()
        }

        // brush color
        val colorsLinearLayout = findViewById<LinearLayout>(R.id.ll_brush_color)
        currentPainColorList = colorsLinearLayout[0] as ImageButton
        currentPainColorList!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

    }

    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallBtn = brushDialog.findViewById(R.id.ib_small_brush) as ImageButton
        smallBtn.setOnClickListener {
            drawingView!!.setBrushSize(10f)
            brushDialog.dismiss()
        }

        val mediumBtn = brushDialog.findViewById(R.id.ib_medium_brush) as ImageButton
        mediumBtn.setOnClickListener {
            drawingView!!.setBrushSize(20f)
            brushDialog.dismiss()
        }

        val largeBtn = brushDialog.findViewById(R.id.ib_large_brush) as ImageButton
        largeBtn.setOnClickListener {
            drawingView!!.setBrushSize(30f)
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        Toast.makeText(this, "Paint clicked", Toast.LENGTH_SHORT).show()
    }
}