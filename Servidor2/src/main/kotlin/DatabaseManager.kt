import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

object DatabaseManager {
    private const val URL = "jdbc:mysql://localhost:3306/PSP"
    private const val USER = "root"
    private const val PASSWORD = "9181"

    var connection: Connection? = null

    init {
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD)
            println("✅ Conectado a MySQL correctamente!")
        } catch (e: SQLException) {
            e.printStackTrace()
            println("❌ Error al conectar a MySQL: ${e.message}")
        }
    }
}
