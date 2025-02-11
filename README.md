# **SafeVault** 📂🔐  
Aplicación de almacenamiento seguro de archivos con cifrado SSL y autenticación Firebase.  

## **📌 Descripción**  
SafeVault es una aplicación que permite a los usuarios **subir, descargar y gestionar archivos** de forma segura.  
- Los archivos se almacenan en una **base de datos MySQL** en un servidor local.  
- Se utiliza **SSL/TLS** para proteger la comunicación cliente-servidor.  
- **Firebase Authentication** se encarga del inicio de sesión seguro.  
- Los archivos pueden ser **privados o públicos**, permitiendo compartir documentos.  

---

## **🛠️ Tecnologías utilizadas**  
- **Android (Kotlin, XML, View Binding, Jetpack Navigation, RecyclerView)**  
- **Firebase Authentication** (para la autenticación de usuarios)  
- **Servidor en Kotlin con SSL/TLS**  
- **Base de datos MySQL**  
- **Corrutinas (Kotlin Coroutines) para procesos en segundo plano**  

---

## **🚀 Características**  
✅ **Inicio de sesión seguro con Firebase**  
✅ **Subida y almacenamiento de archivos en MySQL**  
✅ **Descarga de archivos desde la base de datos**  
✅ **Diferenciación entre archivos privados y públicos**  
✅ **Cifrado de comunicación con SSL/TLS**  
✅ **Interfaz en cuadrícula estilo Google Drive**  

---

## **💻 Uso**  
1. **Regístrate o inicia sesión** en la app con Firebase.  
2. **Sube archivos** desde tu dispositivo.  
3. **Elige si los archivos son privados o públicos**.  
4. **Descarga archivos** almacenados en la base de datos desde diferentes dispostivos.  
5. **Administra tus archivos** (cambiar nombre, eliminar, filtrar por tipo).

---

## **📂 Nota sobre el tamaño y seguridad del repositorio**  
Para optimizar el tamaño del repositorio y proteger información sensible, se han realizado las siguientes acciones:  

✅ **Se ha excluido el directorio `build/`** para reducir el tamaño del repositorio.  
✅ **Se ha eliminado el archivo `google-services.json`** por razones de seguridad, ya que contiene credenciales de Firebase.  

### **🔹 Configuración después de clonar el repositorio**  
Si clonas este repositorio y necesitas compilar el proyecto, sigue estos pasos:  

1. **Reconstruir el proyecto**  
   - Abre la carpeta Cliente2 en **Android Studio** y permite que **Gradle reconstruya los archivos** automáticamente.  
   - Si encuentras problemas, ejecuta el siguiente comando en la terminal del proyecto:  
     ```bash
     ./gradlew clean build
     ```
   - O desde **Android Studio**:  
     `Build > Clean Project` y luego `Build > Rebuild Project`.  

2. **Añadir Firebase (`google-services.json`)**  
   - Descarga tu archivo `google-services.json` desde la [consola de Firebase](https://console.firebase.google.com/).  
   - Colócalo en la ruta:  
     ```
     app/google-services.json
     ```
   - Sin este archivo, la autenticación con Firebase **no funcionará**.  

3. **Configuración del servidor MySQL**  
   - Asegúrate de que MySQL esté instalado y configurado.  
   - Modifica `DatabaseManager.kt` con las credenciales correctas de tu MySQL:  
     ```kotlin
     private const val URL = "jdbc:mysql://localhost:3306/PSP"
     private const val USER = "root"
     private const val PASSWORD = "tu_contraseña"
     ```
   - Importa la base de datos desde el archivo `database_schema.sql`.  

---

## **🚀 Cómo ejecutar el proyecto**  
🖥️ **Servidor** → Se ejecuta en **IntelliJ IDEA** (`ServidorSeguro.kt`).  
📱 **Cliente (App Android)** → Se abre y ejecuta en **Android Studio**.  
