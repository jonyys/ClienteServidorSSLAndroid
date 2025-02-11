package com.example.cliente2.fragment

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.cliente2.R
import com.example.cliente2.databinding.FragmentUploadBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*

class UploadFragment : Fragment() {

    private lateinit var binding: FragmentUploadBinding
    private var fileUri: Uri? = null
    private var fileName: String? = null
    private val serverHost = "172.20.10.8"  // IP del servidor
    private val serverPort = 5556

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectFile.setOnClickListener {
            seleccionarArchivo()
        }

        binding.btnUpload.setOnClickListener {
            if (fileUri != null && fileName != null) {
                mostrarDialogoCambioNombre()
            } else {
                Toast.makeText(requireContext(), "Por favor, selecciona un archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun seleccionarArchivo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, REQUEST_FILE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_PICK && resultCode == Activity.RESULT_OK) {
            fileUri = data?.data
            fileName = obtenerNombreArchivo(fileUri!!)
            binding.tvFileName.text = fileName ?: "Archivo seleccionado"
        }
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        return uri.lastPathSegment?.split("/")?.last() ?: "archivo_desconocido"
    }

    /**
     * Muestra un `AlertDialog` para cambiar el nombre del archivo antes de subirlo.
     */
    private fun mostrarDialogoCambioNombre() {
        val input = EditText(requireContext())
        input.hint = "Nuevo nombre del archivo"
        input.setText(fileName?.substringBeforeLast(".")) // Muestra el nombre sin la extensión

        AlertDialog.Builder(requireContext())
            .setTitle("Cambiar nombre del archivo")
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ ->
                val nuevoNombre = input.text.toString().trim()
                val extension = fileName?.substringAfterLast(".", "")

                // Si el usuario deja el campo vacío, usamos el nombre original
                val nombreFinal = if (nuevoNombre.isNotEmpty()) {
                    if (!extension.isNullOrEmpty()) "$nuevoNombre.$extension" else nuevoNombre
                } else {
                    fileName!!
                }

                subirArchivo(fileUri!!, nombreFinal, binding.switchPublic.isChecked)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Sube el archivo al servidor con el nombre final elegido por el usuario.
     */
    private fun subirArchivo(uri: Uri, nombreArchivo: String, isPublic: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sslContext = getSSLContext()
                val socketFactory = sslContext.socketFactory
                val socket = socketFactory.createSocket(serverHost, serverPort) as SSLSocket

                val outputStream = DataOutputStream(socket.getOutputStream())

                // Enviar comando "UPLOAD"
                outputStream.writeUTF("UPLOAD")
                outputStream.flush()

                // Obtener el UID del usuario autenticado en Firebase
                val user = FirebaseAuth.getInstance().currentUser
                val uid = user?.uid

                if (uid == null) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Enviar UID del usuario
                outputStream.writeUTF(uid)
                outputStream.flush()

                // Enviar nombre del archivo (nombre final)
                outputStream.writeUTF(nombreArchivo)
                outputStream.flush()

                // Enviar si es público o privado
                outputStream.writeBoolean(isPublic)
                outputStream.flush()

                // Enviar contenido del archivo
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val buffer = ByteArray(4096)
                var bytesRead: Int

                inputStream?.use { stream ->
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }

                outputStream.flush()
                socket.shutdownOutput()

                // Recibir respuesta del servidor
                val inputStreamResponse = DataInputStream(socket.getInputStream())
                val response = inputStreamResponse.readUTF()

                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), response, Toast.LENGTH_SHORT).show()
                    binding.tvFileName.text = "Ningún archivo seleccionado"
                    fileUri = null
                    fileName = null
                }

                outputStream.close()
                inputStreamResponse.close()
                socket.close()

            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al subir archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("UploadFragment", "Error al subir archivo", e)
            }
        }
    }

    /**
     * Configura el contexto SSL para la conexión segura con el servidor.
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

    companion object {
        private const val REQUEST_FILE_PICK = 1001
    }
}
