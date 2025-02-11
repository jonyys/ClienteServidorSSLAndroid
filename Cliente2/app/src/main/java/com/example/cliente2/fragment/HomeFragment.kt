package com.example.cliente2.fragment

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cliente2.R
import com.example.cliente2.adapter.ArchivoAdapter
import com.example.cliente2.databinding.FragmentHomeBinding
import com.example.cliente2.model.Archivo
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var adapter: ArchivoAdapter
    private var listaArchivos = mutableListOf<Archivo>()

    private val serverHost = "172.20.10.8" // IP del servidor
    private val serverPort = 5556

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ArchivoAdapter(listaArchivos, { archivo ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                descargarArchivo(archivo)
            }
        }, { archivo ->
            mostrarDialogoArchivo(archivo) // Llamar al nuevo diálogo
        })

        binding.rvFiles.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvFiles.adapter = adapter

        obtenerArchivos()
    }

    /**
     * Muestra un diálogo con los detalles del archivo.
     */
    private fun mostrarDialogoArchivo(archivo: Archivo) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_detalle_archivo, null)

        // Asignar valores al diálogo
        val tvNombre = dialogView.findViewById<TextView>(R.id.tvDialogNombre)
        val tvPropietario = dialogView.findViewById<TextView>(R.id.tvDialogPropietario)
        val tvPublico = dialogView.findViewById<TextView>(R.id.tvDialogPublico)

        tvNombre.text = archivo.nombre
        tvPropietario.text = "Propietario: ${archivo.propietario}"
        tvPublico.text = if (archivo.isPublic) "Archivo Público" else "Archivo Privado"

        builder.setView(dialogView)
            .setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }



    /**
     * Envía la solicitud al servidor para cambiar el nombre del archivo.
     */
    private fun renombrarArchivo(archivo: Archivo, nuevoNombre: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sslContext = getSSLContext()
                val socketFactory = sslContext.socketFactory
                val socket = socketFactory.createSocket(serverHost, serverPort) as SSLSocket

                val outputStream = DataOutputStream(socket.getOutputStream())
                val inputStream = DataInputStream(socket.getInputStream())

                // Enviar comando "RENAME"
                outputStream.writeUTF("RENAME")
                outputStream.writeUTF(archivo.nombre)
                outputStream.writeUTF(nuevoNombre)
                outputStream.flush()

                // Leer respuesta del servidor
                val respuesta = inputStream.readUTF()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), respuesta, Toast.LENGTH_SHORT).show()
                    obtenerArchivos() // Refrescar la lista de archivos
                }

                socket.close()

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error al renombrar archivo: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al renombrar archivo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    /**
     * Solicita la lista de archivos al servidor y actualiza la UI.
     */
    private fun obtenerArchivos() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val uid = user?.uid ?: return@launch

                val sslContext = getSSLContext()
                val socketFactory = sslContext.socketFactory
                val socket = socketFactory.createSocket(serverHost, serverPort) as SSLSocket

                val outputStream = DataOutputStream(socket.getOutputStream())
                val inputStream = DataInputStream(socket.getInputStream())

                // Enviar comando "LIST"
                outputStream.writeUTF("LIST")
                outputStream.writeUTF(uid) // Enviar UID para filtrar archivos
                outputStream.flush()

                // Leer la lista de archivos
                val archivosRecibidos = inputStream.readUTF()
                if (archivosRecibidos.isNotEmpty()) {
                    val archivos = archivosRecibidos.split(";").map { data ->
                        val partes = data.split(",")
                        Archivo(
                            nombre = partes[0],
                            isPublic = partes[1] == "1",
                            propietario = partes[2],
                            fileType = partes[3]
                        )
                    }
                    listaArchivos.clear()
                    listaArchivos.addAll(archivos)
                }

                socket.close()

                requireActivity().runOnUiThread {
                    adapter.actualizarLista(listaArchivos)
                }

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error al obtener archivos: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al obtener archivos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Filtra la lista de archivos según la selección del usuario.
     */
    private fun filtrarArchivos(filtro: Int) {
        val archivosFiltrados = when (filtro) {
            0 -> listaArchivos // Todos
            1 -> listaArchivos.filter { !it.isPublic } // Privados
            2 -> listaArchivos.filter { it.isPublic } // Públicos
            else -> listaArchivos
        }
        adapter.actualizarLista(archivosFiltrados)
    }

    /**
     * Solicita la descarga de un archivo al servidor.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun descargarArchivo(archivo: Archivo) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sslContext = getSSLContext()
                val socketFactory = sslContext.socketFactory
                val socket = socketFactory.createSocket(serverHost, serverPort) as SSLSocket

                val outputStream = DataOutputStream(socket.getOutputStream())
                val inputStream = DataInputStream(socket.getInputStream())

                // Enviar comando "DOWNLOAD"
                outputStream.writeUTF("DOWNLOAD")
                outputStream.writeUTF(archivo.nombre)
                outputStream.flush()

                // Recibir tamaño del archivo
                val fileSize = inputStream.readInt()
                if (fileSize == 0) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "❌ Archivo no encontrado", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Recibir los datos del archivo
                val fileData = ByteArray(fileSize)
                inputStream.readFully(fileData)

                // Guardar el archivo en el dispositivo
                guardarArchivoEnDispositivo(archivo.nombre, fileData)

                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "✅ Descarga completada!", Toast.LENGTH_SHORT).show()
                    Log.d("HomeFragment", "📥 Recibido archivo: ${archivo.nombre} - Tamaño: $fileSize bytes")
                }

                socket.close()

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error al descargar archivo: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al descargar archivo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Guarda el archivo descargado en la carpeta de descargas del dispositivo.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun guardarArchivoEnDispositivo(nombre: String, fileData: ByteArray) {
        try {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombre)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SafeVault/")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(fileData)
                }
                Log.d("HomeFragment", "📂 Archivo guardado en Downloads/SafeVault/$nombre")
            } else {
                Log.e("HomeFragment", "❌ Error al guardar archivo")
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error al guardar archivo: ${e.message}")
        }
    }


    /**
     * Configura SSLContext con el certificado personalizado desde res/raw/
     */
    private fun getSSLContext(): SSLContext {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val certInputStream = resources.openRawResource(R.raw.my_certificate)
            val ca = cf.generateCertificate(certInputStream)
            certInputStream.close()

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("ca", ca)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            sslContext

        } catch (e: Exception) {
            throw RuntimeException("Error configurando SSL", e)
        }
    }
}
