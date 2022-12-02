package com.nbaj.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.defaults.colorpicker.ColorPickerPopup
import top.defaults.colorpicker.ColorPickerPopup.ColorPickerObserver
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var currentPaintColor: ImageButton? = null

    var progressDialog: Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageBackground: ImageView = findViewById(R.id.iv_background)
            imageBackground.setImageURI(result.data?.data)
            Toast.makeText(this, "Long press icon to return to white background", Toast.LENGTH_SHORT).show()
        }
    }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissions ->
                permissions.entries.forEach {
                    val (permission, granted) = it
                    if (granted) {
//                        Toast.makeText(this, "$permission granted", Toast.LENGTH_SHORT).show()
                        val pickIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGalleryLauncher.launch(pickIntent)

                    } else {
//                        Toast.makeText(this, "$permission denied", Toast.LENGTH_SHORT).show()
                        if(permission == Manifest.permission.READ_EXTERNAL_STORAGE) {
                            Toast.makeText(this@MainActivity,
                                "You need to grant permission to read external storage to load images",
                                Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
        }

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
        currentPaintColor = colorsLinearLayout[0] as ImageButton
        currentPaintColor!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

        // custom color picker
        val customColorButton = findViewById<ImageButton>(R.id.customcolorbtn)
        customColorButton.setOnClickListener {
                v -> ColorPickerPopup.Builder(this@MainActivity)
                .initialColor(Color.RED) // initial color
                .enableBrightness(true) // enable color brightness
                .enableAlpha(true) // enable color alpha
                .okTitle("Choose")
                .cancelTitle("Cancel")
                .showIndicator(true) // this is the small box
                .showValue(true) // this is the value which
                .build()
                .show(v, object : ColorPickerObserver() {
                        override fun onColorPicked(color: Int) {
                            val hexColor = String.format("#%06X", 0xFFFFFF and color)
                            drawingView?.setBrushColor(hexColor)
                            customColorButton.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.pallet_pressed))
                            currentPaintColor?.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.pallet_normal))
                            currentPaintColor = customColorButton
                        }
                    })
            Toast.makeText(this, "Click top right of popup to select color", Toast.LENGTH_SHORT).show()

        }

        // permissions
        val ibGallery = findViewById<ImageButton>(R.id.ib_image)
        ibGallery.setOnClickListener {
            requestStoragePermissions()
        }
        ibGallery.setOnLongClickListener {
            val imageBackground: ImageView = findViewById(R.id.iv_background)
            val whiteImage = getDrawable(R.drawable.white)
            imageBackground.setImageDrawable(whiteImage)
            true
        }

        // undo button
        val ibUndo = findViewById<ImageButton>(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.undo()
        }

        // redo button
        val ibRedo = findViewById<ImageButton>(R.id.ib_redo)
        ibRedo.setOnClickListener {
            drawingView?.redo()
        }

        // save button
        val ibSave = findViewById<ImageButton>(R.id.ib_save)
        ibSave.setOnClickListener {
            showProgressDialog()
            lifecycleScope.launch{
                val flDrawingView = findViewById<FrameLayout>(R.id.fl_view_container)
                saveDrawing(getBitmapFromView(flDrawingView))
            }
        }

        // share button
        val ibShare = findViewById<ImageButton>(R.id.ib_share)
        ibShare.setOnClickListener {
            showProgressDialog()
            lifecycleScope.launch{
                val flDrawingView = findViewById<FrameLayout>(R.id.fl_view_container)
                shareDrawing(getBitmapFromView(flDrawingView))
            }
        }

        // privacy policy
        val privacypolicy = findViewById<TextView>(R.id.privacy_policy)
        privacypolicy.setOnClickListener{
            val intent = Intent(android.content.Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://sites.google.com/view/drawing-app-policy-privacy/home  ")
            startActivity(intent)
        }
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
//        Toast.makeText(this, "Paint clicked", Toast.LENGTH_SHORT).show()
        if(view !== currentPaintColor) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            if (colorTag != "colorpicker"){
                drawingView?.setBrushColor(colorTag)
                imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
                currentPaintColor?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_normal))
                currentPaintColor = view
            }
        }
    }

    private fun requestStoragePermissions() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationalDialog("Drawing App","You need to grant permission to read external storage to load images")
        } else {
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun showRationalDialog(title: String, message: String){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, which ->
                try {
                    dialog.dismiss()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }.show()
    }

    private fun getBitmapFromView(v: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = v.background
        if(bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        v.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveDrawing(bitmap: Bitmap): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (bitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString() + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".jpg")

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    runOnUiThread {
                        cancelProgressDialog()
                        if (!result.isEmpty()) {
                            Toast.makeText(this@MainActivity, "File saved successfully :$result", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity,"Something went wrong while saving the file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog() {
        progressDialog = Dialog(this@MainActivity)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        progressDialog?.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        progressDialog?.show()
    }

    private fun cancelProgressDialog() {
        if (progressDialog != null) {
            progressDialog?.dismiss()
            progressDialog = null
        }
    }

    private suspend fun shareDrawing(bitmap: Bitmap): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (bitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString() + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".jpg")

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    runOnUiThread {
                        cancelProgressDialog()
                        if (!result.isEmpty()) {
                            shareImage(result)
                        } else {
                        }
                    }

                    val file = File(result)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null) { path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

}