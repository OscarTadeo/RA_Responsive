/*===============================================================================
Copyright (c) 2022 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.native_sample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.schedule

import android.graphics.Color
import android.graphics.Outline
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.TimerTask
import android.util.TypedValue  // Add this import statement
import android.os.Handler


/**
 * Activity to demonstrate how to use Vuforia Image Target and Model Target features,
 * Video Background rendering and Vuforia lifecycle.
 */
class VuforiaActivity : AppCompatActivity(), GLSurfaceView.Renderer, SurfaceHolder.Callback {

    /**
     * The permissions that we need to run the Vuforia Activity
     * To use the SessionRecorder with audio add Manifest.permission.RECORD_AUDIO to this array
     * and to the App manifest file
     */
    val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    private var mPermissionsRequested = false;

    private lateinit var mGLView : GLSurfaceView

    private var mTarget = 0
    private var mProgressIndicatorLayout: RelativeLayout? = null

    private var mWidth = 0
    private var mHeight = 0
    private var mDWidth = 0
    private var mDHeight = 0

    private var mVuforiaStarted = false
    private var mSurfaceChanged = false

    private var mWindowDisplayRotation = Surface.ROTATION_0

    private var mGestureDetector : GestureDetectorCompat? = null

    // Native methods
    private external fun initAR(activity: Activity, assetManager: AssetManager, target: Int)
    private external fun deinitAR()

    private external fun startAR() : Boolean
    private external fun stopAR()

    external fun cameraPerformAutoFocus()
    external fun cameraRestoreAutoFocus()

    private external fun initRendering()
    private external fun setTextures(objetoWidth: Int, objetoHeight: Int, objetoBytes: ByteBuffer,
                                     diagramaWidth: Int, diagramaHeight: Int, diagramaBytes: ByteBuffer)
    private external fun deinitRendering()
    private external fun configureRendering(width: Int, height: Int, orientation: Int, rotation: Int) : Boolean
    private external fun renderFrame() : Boolean

    // Se envia el aumento dela variable X a la clase VuforiaWrapper.cpp
    private external fun incremCoordX(valor: Float)
    private external fun decremCoordX(valor: Float)

    private external fun incremCoordY(valor: Float)
    private external fun decremCoordY(valor: Float)

    private external fun incremCoordZ(valor: Float)
    private external fun decremCoordZ(valor: Float)

    private external fun incremRotX(valor: Float)

    // Declarar el texto.
    lateinit var traslacionTextView: TextView
    lateinit var rotacionTextView: TextView
    lateinit var escalaTextView: TextView

    // Declaración de botones de botones para interacción

    // X +
    private lateinit var vuforiaButton: Button
    var vuforiaButtonPressed = false  // Estado para saber si el estado del botón
    var vuforiaButtonDownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // X -
    private lateinit var vuforiaButton1: Button
    var vuforiaButton1Pressed = false  // Estado para saber si el estado del botón
    var vuforiaButton1DownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // Y +
    private lateinit var vuforiaButton2: Button
    var vuforiaButton2Pressed = false  // Estado para saber si el estado del botón
    var vuforiaButton2DownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // Y -
    private lateinit var vuforiaButton3: Button
    var vuforiaButton3Pressed = false  // Estado para saber si el estado del botón
    var vuforiaButton3DownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // Z +
    private lateinit var vuforiaButton4: Button
    var vuforiaButton4Pressed = false  // Estado para saber si el estado del botón
    var vuforiaButton4DownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // Z -
    private lateinit var vuforiaButton5: Button
    var vuforiaButton5Pressed = false  // Estado para saber si el estado del botón
    var vuforiaButton5DownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // rot X +
    private lateinit var vuforiaButton6: Button
    var vuforiaButton6Pressed = false  // Estado para saber si el estado del botón
    var vuforiaButton6DownStartTime = 0L  // Tiempo en que se presionó el botón por primera vez

    // Activity methods
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        mTarget = intent.getIntExtra("Target", 0)
        mVuforiaStarted = false
        mSurfaceChanged = true

        // Create an OpenGL ES 3.0 context (also works for 3.1, 3.2)
        mGLView = GLSurfaceView(this)
        mGLView.holder.addCallback(this)
        mGLView.setEGLContextClientVersion(3)
        mGLView.setRenderer(this)
        addContentView(mGLView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Prevent screen from dimming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        makeFullScreen()

        // Setup and show a progress indicator
        mProgressIndicatorLayout = View.inflate(
            applicationContext,
            R.layout.progress_indicator, null
        ) as RelativeLayout

        addContentView(
            mProgressIndicatorLayout, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Hide the GLView until we are ready
        mGLView.visibility = View.GONE

        // If we have the needed runtime permissions we can start Vuforia initialization here
        // If we need to ask the user for runtime permissions we need to wait until we have them
        if (runtimePermissionsGranted()) {
            // Start Vuforia initialization in a coroutine
            GlobalScope.launch(Dispatchers.Unconfined) {
                initializeVuforia()
            }
        }

        // Inicializar el texto para la traslacion
        traslacionTextView = TextView(this)
        traslacionTextView.text = "TRASLACIÓN"
        traslacionTextView.setTextColor(Color.WHITE) // Ajustar el color según sea necesario
        traslacionTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 50f) // Ajustar el tamaño de fuente según sea necesario
        traslacionTextView.setBackgroundColor(Color.argb(50,255,255,255)) // Hacer que el fondo sea transparente

        traslacionTextView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        traslacionTextView.clipToOutline = true

        // Inicializar los botones de traslación
        vuforiaButton = Button(this)
        vuforiaButton.text = "X +"
        vuforiaButton.setTextColor(Color.RED)
        vuforiaButton.setBackgroundColor(Color.WHITE)

        // Redondeo de esquinas del botón
        vuforiaButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        vuforiaButton.clipToOutline = true

        vuforiaButton1 = Button(this)
        vuforiaButton1.text = "X -"
        vuforiaButton1.setTextColor(Color.RED)
        vuforiaButton1.setBackgroundColor(Color.WHITE)

        // Redondeo de esquinas del botón
        vuforiaButton1.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        vuforiaButton1.clipToOutline = true

        vuforiaButton2 = Button(this)
        vuforiaButton2.text = "Y +"
        vuforiaButton2.setTextColor(Color.GREEN)
        vuforiaButton2.setBackgroundColor(Color.WHITE)

        // Redondeo de esquinas del botón
        vuforiaButton2.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        vuforiaButton2.clipToOutline = true

        vuforiaButton3 = Button(this)
        vuforiaButton3.text = "Y -"
        vuforiaButton3.setTextColor(Color.GREEN)
        vuforiaButton3.setBackgroundColor(Color.WHITE)

        // Redondeo de esquinas del botón
        vuforiaButton3.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        vuforiaButton3.clipToOutline = true

        vuforiaButton4 = Button(this)
        vuforiaButton4.text = "Z +"
        vuforiaButton4.setTextColor(Color.BLUE)
        vuforiaButton4.setBackgroundColor(Color.WHITE)

        // Redondeo de esquinas del botón
        vuforiaButton4.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        vuforiaButton4.clipToOutline = true


        // Inicializar el texto para al rotacion
        rotacionTextView = TextView(this)
        rotacionTextView.text = "ROTACIÓN"
        rotacionTextView.setTextColor(Color.WHITE) // Ajustar el color según sea necesario
        rotacionTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 50f) // Ajustar el tamaño de fuente según sea necesario
        rotacionTextView.setBackgroundColor(Color.argb(50,255,255,255)) // Hacer que el fondo sea transparente

        // Inicializar los botones de rotación
        vuforiaButton6 = Button(this)
        vuforiaButton6.text = "X +"
        vuforiaButton6.setTextColor(Color.RED)
        vuforiaButton6.setBackgroundColor(Color.WHITE)

        // Redondeo de esquinas del botón
        vuforiaButton6.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        vuforiaButton6.clipToOutline = true

        // Inicializar el texto para la Escala
        escalaTextView = TextView(this)
        escalaTextView.text = "ESCALA"
        escalaTextView.setTextColor(Color.WHITE) // Ajustar el color según sea necesario
        escalaTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 50f) // Ajustar el tamaño de fuente según sea necesario
        escalaTextView.setBackgroundColor(Color.argb(50,255,255,255)) // Hacer que el fondo sea transparente

        escalaTextView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f) // Set corner radius to 10dp
            }
        }
        escalaTextView.clipToOutline = true

        // Ocultar el boton
//        traslacionTextView.visibility = View.GONE
        vuforiaButton.visibility = View.GONE
        vuforiaButton1.visibility = View.GONE
        vuforiaButton2.visibility = View.GONE
        vuforiaButton3.visibility = View.GONE
        vuforiaButton4.visibility = View.GONE
        vuforiaButton6.visibility = View.GONE
        rotacionTextView.visibility = View.GONE
        escalaTextView.visibility = View.GONE



        // Obtener el ancho y alto de la vista principal (considera usar ViewTreeObserver)
        val viewTreeObserver = traslacionTextView.viewTreeObserver
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val parentWidth = (traslacionTextView.parent as ViewGroup).measuredWidth
                val parentHeight = (traslacionTextView.parent as ViewGroup).measuredHeight

                /*val buttonWidth = vuforiaButton.width
                val buttonHeight = vuforiaButton.height*/

                // Coordenadas para posicionar el texto "TRASLACIÓN" en la parte superior izquierda
                traslacionTextView.x = 50.0f
                traslacionTextView.y = (0.0f)

                vuforiaButton.setX(traslacionTextView.x)
                vuforiaButton.setY(traslacionTextView.y + 100.0f)

                vuforiaButton1.setX(traslacionTextView.x)
                vuforiaButton1.setY(vuforiaButton.y + 150.0f)

                vuforiaButton2.setX(traslacionTextView.x)
                vuforiaButton2.setY(vuforiaButton1.y + 185.0f)

                vuforiaButton3.setX(traslacionTextView.x)
                vuforiaButton3.setY(vuforiaButton2.y + 150.0f)

                vuforiaButton4.setX(traslacionTextView.x)
                vuforiaButton4.setY(vuforiaButton3.y + 185.0f)


                // Coordenadas para posicionar el texto "ROTACION" en la parte superior derecha
                rotacionTextView.x = parentWidth.toFloat() - traslacionTextView.width
                rotacionTextView.y = 0.0f

                vuforiaButton6.setX(rotacionTextView.x)
                vuforiaButton6.setY(100.0f)

                // Coordenadas para posicionar el texto "ESCALA" en la inferior

                escalaTextView.x = parentWidth / 2f - escalaTextView.width / 2f
                escalaTextView.y = parentHeight.toFloat() - escalaTextView.height


                // Posiciona el botón en el centro de la pantalla
               /* vuforiaButton.setX(parentWidth / 2f - buttonWidth / 2f)
                vuforiaButton.setY(parentHeight / 2f - buttonHeight / 2f)*/

                /*vuforiaButton.setX(parentWidth.toFloat() - buttonWidth.toFloat())
                vuforiaButton.setY(100.0f)*/

                // Hasta abajo
                // vuforiaButton.setY(parentHeight.toFloat() - buttonHeight.toFloat())

//                traslacionTextView.x = vuforiaButton.x + vuforiaButton.width / 2 - traslacionTextView.width / 2



/*                rotacionTextView.x = (0.0f)
                rotacionTextView.y = (0.0f)*/





                /*vuforiaButton1.setX(parentWidth.toFloat() - buttonWidth.toFloat())
                vuforiaButton1.setY(vuforiaButton.y + vuforiaButton.height + 10.0f)*/



                // Eliminar el listener después de la primera medición
                vuforiaButton.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // Registrar un nuevo listener para la siguiente rotación
                vuforiaButton.viewTreeObserver.addOnGlobalLayoutListener(this)


            }
        })


        vuforiaButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vuforiaButtonPressed = true
                    // Empieza a contar la cuenta de los segundos
                    vuforiaButtonDownStartTime = System.currentTimeMillis()

                    // Cambio de color a gris al ser presionado
                    vuforiaButton.setBackgroundColor(Color.GRAY)
                    incremCoordX(0.01f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vuforiaButtonPressed = false
                    vuforiaButton.setBackgroundColor(Color.WHITE)  // Revert color on release
                    true
                }
                else -> false
            }
        }


        vuforiaButton1.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vuforiaButton1Pressed = true
                    // Empieza a contar la cuenta de los segundos
                    vuforiaButton1DownStartTime = System.currentTimeMillis()

                    // Cambio de color a gris al ser presionado
                    vuforiaButton1.setBackgroundColor(Color.GRAY)
                    decremCoordX(0.01f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vuforiaButton1Pressed = false
                    // Cambia color a dejar de estar presioando
                    vuforiaButton1.setBackgroundColor(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        vuforiaButton2.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vuforiaButton2Pressed = true
                    // Empieza a contar la cuenta de los segundos
                    vuforiaButton2DownStartTime = System.currentTimeMillis()

                    // Cambio de color a gris al ser presionado
                    vuforiaButton2.setBackgroundColor(Color.GRAY)
                    incremCoordY(0.01f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vuforiaButton2Pressed = false
                    // Cambia color a dejar de estar presioando
                    vuforiaButton2.setBackgroundColor(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        vuforiaButton3.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vuforiaButton3Pressed = true
                    // Empieza a contar la cuenta de los segundos
                    vuforiaButton3DownStartTime = System.currentTimeMillis()

                    // Cambio de color a gris al ser presionado
                    vuforiaButton3.setBackgroundColor(Color.GRAY)
                    decremCoordY(0.01f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vuforiaButton3Pressed = false
                    // Cambia color a dejar de estar presioando
                    vuforiaButton3.setBackgroundColor(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        vuforiaButton4.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vuforiaButton4Pressed = true
                    // Empieza a contar la cuenta de los segundos
                    vuforiaButton4DownStartTime = System.currentTimeMillis()

                    // Cambio de color a gris al ser presionado
                    vuforiaButton4.setBackgroundColor(Color.GRAY)
                    incremCoordZ(0.01f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vuforiaButton4Pressed = false
                    // Cambia color a dejar de estar presioando
                    vuforiaButton4.setBackgroundColor(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        vuforiaButton6.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Cambio de color a gris al ser presionado
                    vuforiaButton6.setBackgroundColor(Color.GRAY)
                    incremRotX(10.0f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Change button color back to red on release or cancel
                    vuforiaButton6.setBackgroundColor(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        // Temporizador para gestionar el estado de los botones e incrementar el conteo
        val timer = Timer()
        val timerTask = object : TimerTask() {
            override fun run() {
                if (vuforiaButtonPressed) {
                    val currentTime = System.currentTimeMillis()

                    // Se comprueba si han pasado 2 segundos desde que se presionó el botón por primera vez
                    if (currentTime - vuforiaButtonDownStartTime >= 500) {
                        // Comienza el incremento cada medio segundo
                        incremCoordX(0.01f)
                    }
                }

                if (vuforiaButton1Pressed) {
                    val currentTime = System.currentTimeMillis()

                    // Se comprueba si han pasado 2 segundos desde que se presionó el botón por primera vez
                    if (currentTime - vuforiaButton1DownStartTime >= 500) {
                        // Comienza el incremento cada medio segundo
                        decremCoordX(0.01f)
                    }
                }

                if (vuforiaButton2Pressed) {
                    val currentTime = System.currentTimeMillis()

                    // Se comprueba si han pasado 2 segundos desde que se presionó el botón por primera vez
                    if (currentTime - vuforiaButton2DownStartTime >= 500) {
                        // Comienza el incremento cada medio segundo
                        incremCoordY(0.01f)
                    }
                }

                if (vuforiaButton3Pressed) {
                    val currentTime = System.currentTimeMillis()

                    // Se comprueba si han pasado 2 segundos desde que se presionó el botón por primera vez
                    if (currentTime - vuforiaButton3DownStartTime >= 500) {
                        // Comienza el incremento cada medio segundo
                        decremCoordY(0.01f)
                    }
                }

                if (vuforiaButton4Pressed) {
                    val currentTime = System.currentTimeMillis()

                    // Se comprueba si han pasado 2 segundos desde que se presionó el botón por primera vez
                    if (currentTime - vuforiaButton4DownStartTime >= 500) {
                        // Comienza el incremento cada medio segundo
                        incremCoordZ(0.01f)
                    }
                }


            }
        }

        // Inicie la tarea del temporizador para que se ejecute cada 100 milisegundos
        timer.scheduleAtFixedRate(timerTask, 0, 100)


        // Agregar el texto traslacion
        addContentView(traslacionTextView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el botón a la jerarquía de vistas sobre GLSurfaceView
        addContentView(vuforiaButton, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el botón a la jerarquía de vistas sobre GLSurfaceView
        addContentView(vuforiaButton1, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el botón a la jerarquía de vistas sobre GLSurfaceView
        addContentView(vuforiaButton2, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el botón a la jerarquía de vistas sobre GLSurfaceView
        addContentView(vuforiaButton3, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el botón a la jerarquía de vistas sobre GLSurfaceView
        addContentView(vuforiaButton4, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el texto rotacion
        addContentView(rotacionTextView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el botón a la jerarquía de vistas sobre GLSurfaceView
        addContentView(vuforiaButton6, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Agregar el texto escala
        addContentView(escalaTextView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        mGestureDetector = GestureDetectorCompat(this, GestureListener())

    }



    override fun onPause() {
        stopAR()
        super.onPause()
    }


    override fun onResume() {
        super.onResume()

        makeFullScreen()

        /*if (runtimePermissionsGranted()) {
            if (!mPermissionsRequested && mVuforiaStarted) {
                GlobalScope.launch(Dispatchers.Unconfined) {
                    startAR()
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 0)
            mPermissionsRequested = true
        }*/

        if (runtimePermissionsGranted()) {
            if (!mPermissionsRequested && mVuforiaStarted) {
                GlobalScope.launch(Dispatchers.Unconfined) {

                    // Se reinicia AR despues de minimizar
                    startAR()
                    initializeVuforia()
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 0)
            mPermissionsRequested = true
        }

    }


    override fun onBackPressed() {
        // Hide the GLView while we clean up
        mGLView.visibility = View.INVISIBLE
        // Stop Vuforia Engine and call parent to navigate back
        stopAR()
        mVuforiaStarted = false
        deinitAR()
        super.onBackPressed()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button.
            NavUtils.navigateUpFromSameTask(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mPermissionsRequested = false

        if (permissions.isEmpty()) {
            // Permissions request was cancelled
            Toast.makeText(this, "The permission request was cancelled. You must grant Camera permission to access AR features of this application.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (runtimePermissionsGranted()) {
            // Start Vuforia initialization in a coroutine
            GlobalScope.launch(Dispatchers.Unconfined) {
                initializeVuforia()
            }
        } else {
            Toast.makeText(this, "You must grant Camera permission to access AR features of this application.", Toast.LENGTH_LONG).show()
            finish()
        }
    }


    // Overrider onTouchEvent to connect it to our GestureListener
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        mGestureDetector?.onTouchEvent(event)
        return super.onTouchEvent(event)


    }


    /// Custom GestureListener to capture single and double tap
    inner class GestureListener : SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Calls the Autofocus Native Method
            cameraPerformAutoFocus()

            // After triggering a focus event wait 2 seconds
            // before restoring continuous autofocus
            Timer("RestoreAutoFocus", false).schedule(2000) {
                cameraRestoreAutoFocus()
            }

            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onBackPressed()
            return true
        }
    }


    private fun makeFullScreen() {
        // Make the Activity completely full screen
        (View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION).also { mGLView.systemUiVisibility = it }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }


    private suspend fun initializeVuforia() {
        return withContext(Dispatchers.Default) {
            initAR(this@VuforiaActivity, this@VuforiaActivity.assets, mTarget)
        }
    }


    private fun runtimePermissionsGranted(): Boolean {
        var result = true
        for (permission in REQUIRED_PERMISSIONS) {
            result = result && ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        return result
    }


    @Suppress("unused")
    private fun presentError(message: String) {
        val builder: AlertDialog.Builder = this.let {
            AlertDialog.Builder(it)
        }

        builder.setMessage(message)
        builder.setTitle(R.string.error_dialog_title)
        builder.setPositiveButton(R.string.ok
        ) { _, _ ->
            stopAR()
            deinitAR()
            this@VuforiaActivity.finish()
        }

        // This is called from another coroutine not on the Main thread
        // Showing the UI needs to be on the main thread
        GlobalScope.launch(Dispatchers.Main) {
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }


    @Suppress("unused")
    private fun initDone() {
        /*mVuforiaStarted = startAR()
        if (!mVuforiaStarted) {
            Log.e("VuforiaSample", "Failed to start AR")
        }
        // Show the GLView
        GlobalScope.launch(Dispatchers.Main) {
            mGLView.visibility = View.VISIBLE
        }*/

        mVuforiaStarted = startAR()
        if (!mVuforiaStarted) {
            Log.e("VuforiaSample", "Failed to start AR")
        }

        // Mostrar el GLView
        GlobalScope.launch(Dispatchers.Main) {
            mGLView.visibility = View.VISIBLE

            // Mostrar el botón después de la carga de Vuforia
            vuforiaButton.visibility = View.VISIBLE
            vuforiaButton1.visibility = View.VISIBLE
            vuforiaButton2.visibility = View.VISIBLE
            vuforiaButton3.visibility = View.VISIBLE
            vuforiaButton4.visibility = View.VISIBLE
            vuforiaButton6.visibility = View.VISIBLE
            rotacionTextView.visibility = View.VISIBLE
            escalaTextView.visibility = View.VISIBLE
        }

    }



    // GLSurfaceView.Renderer methods
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        initRendering()
    }


    override fun onDrawFrame(unused: GL10) {
        if (mVuforiaStarted) {

            if (mSurfaceChanged || mWindowDisplayRotation != windowManager.defaultDisplay.rotation) {
                mSurfaceChanged = false
                mWindowDisplayRotation = windowManager.defaultDisplay.rotation

                // Pass rendering parameters to Vuforia Engine
                configureRendering(mWidth, mHeight, resources.configuration.orientation, mWindowDisplayRotation)
                configureRendering(mDWidth, mDHeight, resources.configuration.orientation, mWindowDisplayRotation)
            }

            // OpenGL rendering of Video Background and augmentations is implemented in native code
            val didRender = renderFrame()
            if (didRender && mProgressIndicatorLayout?.visibility != View.GONE) {
                GlobalScope.launch(Dispatchers.Main) {
                    mProgressIndicatorLayout?.visibility = View.GONE
                }
            }
        }
    }


    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int/*,dwidth: Int, dheight: Int*/) {
        // Store values for later use
        mWidth = width
        mHeight = height

        // Re-load textures in case they got destroyed
        val objetoTexture = Texture.loadTextureFromApk("cubo.png", assets)
        val diagramaTexture = Texture.loadTextureFromApk("cohete.jpg", assets)

        if (objetoTexture != null && diagramaTexture != null) {
            setTextures(
                objetoTexture.width, objetoTexture.height, objetoTexture.data!!,
                diagramaTexture.width, diagramaTexture.height, diagramaTexture.data!!
            )
        } else {
            Log.e("VuforiaSample", "Failed to load objeto texturas")
        }
        mSurfaceChanged = true
    }


    // SurfaceHolder.Callback
    override fun surfaceCreated(var1: SurfaceHolder) {}


    override fun surfaceChanged(var1: SurfaceHolder, var2: Int, var3: Int, var4: Int) {}


    override fun surfaceDestroyed(var1: SurfaceHolder) {
        deinitRendering()
    }


    companion object {
        external fun getImageTargetId() : Int
//        external fun getModelTargetId() : Int
    }
}


