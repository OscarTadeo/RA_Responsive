/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.native_sample

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.Button
import android.widget.Toast


/**
 * The MainActivity presents a simple choice for the user to select Image Targets or Model Targets.
 */
class MainActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Establece el layout de la actividad
        setContentView(R.layout.activity_main)


        // Encuentra el botón en el layout
        val btnCamera = findViewById<Button>(R.id.btnCamera)
        btnCamera.setOnClickListener {
            // Aquí se ejecuta el código cuando se hace clic en el botón
            // Puedes agregar aquí la lógica para activar o desactivar la cámara AR
            // Por ejemplo, mostrar un mensaje o iniciar la cámara
            Toast.makeText(applicationContext, "Botón de cámara presionado", Toast.LENGTH_SHORT).show()
            // Lógica adicional para activar/desactivar la cámara AR
        }
    }


    fun goToActivity(view: View) {
        if (view.id == btn_image_target.id) {

            val intent = Intent(
                this@MainActivity,
                VuforiaActivity::class.java
            )
            if (view.id == btn_image_target.id) {
                intent.putExtra("Target", VuforiaActivity.getImageTargetId())
            }
            startActivity(intent)
        }
    }


    companion object {

        // Used to load the 'VuforiaSample' library on application startup.
        init {
            System.loadLibrary("VuforiaSample")
        }
    }
}
