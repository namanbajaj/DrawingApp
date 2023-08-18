package com.nbaj.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
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
import com.coderz.f1.customdialogs.colordialog.ColorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var currentPaintColor: ImageButton? = null

    var progressDialog: Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground: ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
                Toast.makeText(
                    this,
                    "Long press icon to return to white background",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                val (permission, granted) = it
                if (!granted) {
//                        Toast.makeText(this, "$permission denied", Toast.LENGTH_SHORT).show()
                    if (permission == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            this@MainActivity,
                            "You need to grant permission to read external storage to load images",
                            Toast.LENGTH_SHORT
                        ).show()
                        allGranted = false
                        finish()
                    }
                }
            }
            if (allGranted) {
                val pickIntent = Intent(
                    Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                )
                openGalleryLauncher.launch(pickIntent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
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
        currentPaintColor!!.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallet_pressed
            )
        )

        // custom color picker
        val customColorButton = findViewById<ImageButton>(R.id.customcolorbtn)
        var initial = Color.RED
        customColorButton.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val cd = ColorDialog(this@MainActivity, object :
                ColorDialog.DialogResponseListener {
                override fun onOkClicked(color: Int) {
                    val hexColor = String.format("#%06X", 0xFFFFFF and color)
                    drawingView?.setBrushColor(hexColor)
                    customColorButton.setImageDrawable(
                        ContextCompat.getDrawable(
                            this@MainActivity,
                            R.drawable.pallet_pressed
                        )
                    )
                    currentPaintColor?.setImageDrawable(
                        ContextCompat.getDrawable(
                            this@MainActivity,
                            R.drawable.pallet_normal
                        )
                    )
                    currentPaintColor = customColorButton
                    initial = color
                    this@MainActivity.drawingView!!.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }

                override fun onCancelClicked() {
                    this@MainActivity.drawingView!!.performHapticFeedback(HapticFeedbackConstants.REJECT)
                }

            })
            cd.title = "Choose a color"
            cd.tabIndex = ColorDialog.TabIndex.RGB
            cd.initialColor = initial
            cd.margins = 8
            cd.backgroundColor = Color.WHITE
            cd.textColor = Color.BLACK
            cd.showDialog()
        }

        // eraser
        val eraseButton = findViewById<ImageButton>(R.id.eraserbtn)
        eraseButton.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            drawingView?.setBrushColor("#FFFFFF")
            eraseButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this@MainActivity,
                    R.drawable.pallet_pressed
                )
            )
            currentPaintColor?.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.pallet_normal))
            currentPaintColor = eraseButton
        }

        // permissions
        val ibGallery = findViewById<ImageButton>(R.id.ib_image)
        ibGallery.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            requestStoragePermissions()
        }
        ibGallery.setOnLongClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val imageBackground: ImageView = findViewById(R.id.iv_background)
            val whiteImage = getDrawable(R.drawable.white)
            imageBackground.setImageDrawable(whiteImage)
            true
        }

        // undo button
        val ibUndo = findViewById<ImageButton>(R.id.ib_undo)
        ibUndo.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            drawingView?.undo()
        }

        // redo button
        val ibRedo = findViewById<ImageButton>(R.id.ib_redo)
        ibRedo.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            drawingView?.redo()
        }

        // save button
        val ibSave = findViewById<ImageButton>(R.id.ib_save)
        ibSave.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showProgressDialog()
            lifecycleScope.launch {
                val flDrawingView = findViewById<FrameLayout>(R.id.fl_view_container)
                saveDrawing(getBitmapFromView(flDrawingView))
            }
        }

        // clear canvas
        val ibClear = findViewById<ImageButton>(R.id.ib_clear)
        ibClear.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // create pop up to ask for confirmation
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Clear Canvas")
            builder.setMessage("Are you sure you want to clear the canvas?")
            builder.setPositiveButton("Yes") { dialog, which ->
                this@MainActivity.drawingView!!.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                drawingView?.clear()
            }
            builder.setNegativeButton("No") { dialog, which ->
                this@MainActivity.drawingView!!.performHapticFeedback(HapticFeedbackConstants.REJECT)
                dialog.dismiss()
            }
            builder.show()
        }

        // privacy policy
        val privacypolicy = findViewById<TextView>(R.id.privacy_policy)
        privacypolicy.setOnClickListener {
            this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(android.content.Intent.ACTION_VIEW)
            intent.data =
                Uri.parse("https://sites.google.com/view/drawing-app-policy-privacy/home  ")
            startActivity(intent)
        }
    }

    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")

        val okbutton = brushDialog.findViewById(R.id.btn_set_brush_size) as Button
        okbutton.setOnClickListener {
            val seekBar = brushDialog.findViewById(R.id.seekBarBrushSize) as SeekBar
            val size = seekBar.progress
            drawingView!!.setBrushSize(size.toFloat())
            brushDialog.dismiss()
        }

        val cancelbtn = brushDialog.findViewById(R.id.btn_cancel) as Button
        cancelbtn.setOnClickListener {
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View) {
//        Toast.makeText(this, "Paint clicked", Toast.LENGTH_SHORT).show()
        this.drawingView!!.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (view !== currentPaintColor) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            if (colorTag != "colorpicker") {
                drawingView?.setBrushColor(colorTag)
                imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
                currentPaintColor?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_normal))
                currentPaintColor = view
            }
        }
    }

    private fun requestStoragePermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationalDialog(
                "Drawing App",
                "You need to grant permission to read external storage to load images"
            )
        } else {
            requestPermission.launch(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun showRationalDialog(title: String, message: String) {
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
        if (bgDrawable != null) {
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
                    val values = ContentValues()
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    values.put(
                        MediaStore.Images.Media.DATE_ADDED,
                        System.currentTimeMillis() / 1000
                    );
                    values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DrawingApp")
                    values.put(MediaStore.Images.Media.IS_PENDING, true)

                    val uri: Uri? = this@MainActivity.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    if (uri != null) {
                        val outputStream = this@MainActivity.contentResolver.openOutputStream(uri)
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream!!.close()

                        values.put(MediaStore.Images.Media.IS_PENDING, false)
                        this@MainActivity.contentResolver.update(uri, values, null, null)
                        result = uri.toString()
                    }

                    runOnUiThread {
                        cancelProgressDialog()
                        if (!result.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "Drawing saved successfully to gallery",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
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

                    val f =
                        File(externalCacheDir?.absoluteFile.toString() + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".jpg")

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

    private fun shareImage(result: String) {
        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null) { path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

}