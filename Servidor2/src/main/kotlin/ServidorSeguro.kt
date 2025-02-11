import java.io.*
import java.net.ServerSocket
import java.security.KeyStore
import javax.net.ssl.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object ServidorSeguro {
    private const val PUERTO = 5556
    private const val KEYSTORE_PATH = "AlmacenSrv2"
    private const val KEYSTORE_PASSWORD = "1234567"
    private const val KEY_PASSWORD = "1234567"

    @JvmStatic
    fun main(args: Array<String>) {
        val sslContext = configurarSSL()
        val serverSocketFactory = sslContext.serverSocketFactory
        val servidorSSL = serverSocketFactory.createServerSocket(PUERTO) as SSLServerSocket

        println("✅ Servidor seguro iniciado en el puerto $PUERTO")

        while (true) {
            val cliente = servidorSSL.accept() as SSLSocket
            Thread { manejarCliente(cliente) }.start()
        }
    }

    /**
     * Configura el contexto SSL con el certificado del servidor.
     */
    private fun configurarSSL(): SSLContext {
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(FileInputStream(KEYSTORE_PATH), KEYSTORE_PASSWORD.toCharArray())

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, KEY_PASSWORD.toCharArray())

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        return sslContext
    }

    /**
     * Maneja la comunicación con un cliente.
     */
    private fun manejarCliente(cliente: SSLSocket) {
        try {
            val input = DataInputStream(cliente.inputStream)
            val output = DataOutputStream(cliente.outputStream)

            val comando = input.readUTF()
            println("📩 Comando recibido: $comando")

            when (comando) {
                "UPLOAD" -> recibirArchivo(input, output)
                "LIST" -> enviarListaArchivos(input, output)
                "DOWNLOAD" -> enviarArchivo(input, output)
                "RENAME" -> renombrarArchivo(input, output)
                else -> println("❌ Comando desconocido")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cliente.close()
        }
    }

    /**
     * Renombra un archivo en la base de datos.
     */
    private fun renombrarArchivo(input: DataInputStream, output: DataOutputStream) {
        try {
            val nombreActual = input.readUTF()
            val nuevoNombre = input.readUTF()

            println("✏️ Renombrando archivo: $nombreActual -> $nuevoNombre")

            val query = "UPDATE files SET filename = ? WHERE filename = ?"
            val stmt = DatabaseManager.connection?.prepareStatement(query)
            stmt?.setString(1, nuevoNombre)
            stmt?.setString(2, nombreActual)
            val filasAfectadas = stmt?.executeUpdate()

            if (filasAfectadas != null && filasAfectadas > 0) {
                output.writeUTF("✅ Archivo renombrado correctamente")
                println("📂 Archivo renombrado en MySQL: $nombreActual -> $nuevoNombre")
            } else {
                output.writeUTF("❌ Error al renombrar archivo")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Recibe un archivo desde el cliente y lo guarda en la base de datos.
     */
    private fun recibirArchivo(input: DataInputStream, output: DataOutputStream) {
        try {
            val firebaseUid = input.readUTF()
            println("🔍 Buscando usuario con UID: $firebaseUid")

            // Buscar el ID del usuario en la base de datos
            val queryUser = "SELECT id FROM users WHERE firebase_uid = ?"
            val stmtUser = DatabaseManager.connection?.prepareStatement(queryUser)
            stmtUser?.setString(1, firebaseUid)
            val rsUser = stmtUser?.executeQuery()

            if (rsUser == null || !rsUser.next()) {
                println("❌ Error: Usuario no encontrado con UID $firebaseUid")
                output.writeUTF("❌ Error: Usuario no encontrado")
                return
            }

            val ownerId = rsUser.getInt("id")

            val filename = input.readUTF()
            val isPublic = input.readBoolean()

            // Determinar el tipo de archivo antes de leerlo
            val fileType = determinarTipoArchivo(filename)

            // Leer el archivo en un buffer
            val fileBuffer = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var bytesRead: Int

            while (true) {
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                fileBuffer.write(buffer, 0, bytesRead)
            }

            val fileData = fileBuffer.toByteArray()

            // Insertar en MySQL con el nuevo campo file_type
            val queryInsert = "INSERT INTO files (filename, owner_id, is_public, file_type, file_data) VALUES (?, ?, ?, ?, ?)"
            val stmtInsert = DatabaseManager.connection?.prepareStatement(queryInsert)
            stmtInsert?.setString(1, filename)
            stmtInsert?.setInt(2, ownerId)
            stmtInsert?.setBoolean(3, isPublic)
            stmtInsert?.setString(4, fileType) // Guardar tipo de archivo
            stmtInsert?.setBytes(5, fileData)
            val filasAfectadas = stmtInsert?.executeUpdate()

            if (filasAfectadas != null && filasAfectadas > 0) {
                output.writeUTF("✅ Archivo subido correctamente")
                println("📂 Archivo guardado en MySQL: $filename (Propietario ID: $ownerId, Público: $isPublic, Tipo: $fileType)")
            } else {
                output.writeUTF("❌ Error al subir archivo")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Determina el tipo de archivo basado en la extensión del nombre.
     */
    private fun determinarTipoArchivo(nombreArchivo: String): String {
        return when {
            nombreArchivo.endsWith(".jpg", true) || nombreArchivo.endsWith(".png", true) || nombreArchivo.endsWith(".jpeg", true) -> "image"
            nombreArchivo.endsWith(".pdf", true) -> "pdf"
            nombreArchivo.endsWith(".mp4", true) -> "video"
            nombreArchivo.endsWith(".doc", true) || nombreArchivo.endsWith(".docx", true) -> "document"
            nombreArchivo.endsWith(".zip", true) || nombreArchivo.endsWith(".rar", true) -> "compressed"
            else -> "unknown"
        }
    }


    /**
     * Envía la lista de archivos disponibles para el usuario autenticado.
     */
    private fun enviarListaArchivos(input: DataInputStream, output: DataOutputStream) {
        try {
            val firebaseUid = input.readUTF()
            println("🔍 Buscando archivos para UID: $firebaseUid")

            val queryUser = "SELECT id FROM users WHERE firebase_uid = ?"
            val stmtUser = DatabaseManager.connection?.prepareStatement(queryUser)
            stmtUser?.setString(1, firebaseUid)
            val rsUser = stmtUser?.executeQuery()

            if (rsUser == null || !rsUser.next()) {
                output.writeUTF("ERROR: Usuario no encontrado")
                return
            }

            val ownerId = rsUser.getInt("id")

            // obtenemos el tipo de archivo (file_type)
            val queryFiles = "SELECT filename, is_public, owner_id, file_type FROM files WHERE owner_id = ? OR is_public = 1"
            val stmtFiles = DatabaseManager.connection?.prepareStatement(queryFiles)
            stmtFiles?.setInt(1, ownerId)
            val rsFiles = stmtFiles?.executeQuery()

            val archivos = mutableListOf<String>()
            while (rsFiles?.next() == true) {
                val nombre = rsFiles.getString("filename")
                val isPublic = rsFiles.getBoolean("is_public")
                val propietario = if (rsFiles.getInt("owner_id") == ownerId) "Tú" else "Otro usuario"
                val fileType = rsFiles.getString("file_type") ?: "unknown" // Si es null, ponemos "unknown"

                archivos.add("$nombre,${if (isPublic) "1" else "0"},$propietario,$fileType")
            }

            val respuesta = if (archivos.isNotEmpty()) archivos.joinToString(";") else "EMPTY"
            output.writeUTF(respuesta)
            println("✅ Lista de archivos enviada: $respuesta")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Envía un archivo solicitado al cliente.
     */
    private fun enviarArchivo(input: DataInputStream, output: DataOutputStream) {
        try {
            val filename = input.readUTF()
            println("⬇️ Solicitando archivo: $filename")

            // Buscar el archivo en la base de datos
            val query = "SELECT file_data FROM files WHERE filename = ?"
            val stmt = DatabaseManager.connection?.prepareStatement(query)
            stmt?.setString(1, filename)
            val rs = stmt?.executeQuery()

            if (rs?.next() == true) {
                val fileData = rs.getBytes("file_data")

                // Enviar el tamaño del archivo primero
                output.writeInt(fileData.size)
                output.flush()

                // Enviar el archivo
                output.write(fileData)
                output.flush()

                println("📤 Archivo enviado con éxito: $filename")
            } else {
                output.writeInt(0) // Indica que el archivo no se encontró
                output.flush()
                println("❌ Archivo no encontrado en la base de datos")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
