package com.example.banglehealth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var estado by mutableStateOf("Desconectado")
    private var respuesta by mutableStateOf("")

    private lateinit var bangle: BangleConnection

    private val permisos = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val pedirPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) conectar() else estado = "Faltan permisos de Bluetooth"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bangle = BangleConnection(
            this,
            onStatus = { estado = it },
            onResponse = { respuesta = it }
        )
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PantallaConexion(estado, respuesta, onConectar = ::pulsarConectar)
                }
            }
        }
    }

    private fun pulsarConectar() {
        val faltan = permisos.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (faltan) pedirPermisos.launch(permisos) else conectar()
    }

    private fun conectar() {
        respuesta = ""
        bangle.connect()
    }

    override fun onDestroy() {
        bangle.disconnect()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
fun PantallaConexion(estado: String, respuesta: String, onConectar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BangleHealth", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("Estado: $estado", style = MaterialTheme.typography.titleMedium)
        if (respuesta.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(respuesta, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConectar) { Text("Conectar") }
    }
}
