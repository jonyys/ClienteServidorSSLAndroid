package com.example.cliente2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cliente2.R
import com.example.cliente2.model.Archivo

class ArchivoAdapter(
    private var archivos: List<Archivo>,
    private val onDownloadClick: (Archivo) -> Unit,
    private val onLongClick: (Archivo) -> Unit
) : RecyclerView.Adapter<ArchivoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgFileType: ImageView = view.findViewById(R.id.imgFileType)
        val tvNombreArchivo: TextView = view.findViewById(R.id.tvNombreArchivo)
        val tvPropietario: TextView = view.findViewById(R.id.tvPropietario)
        val btnDescargar: Button = view.findViewById(R.id.btnDescargar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_archivo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val archivo = archivos[position]
        holder.tvNombreArchivo.text = archivo.nombre
        holder.tvPropietario.text = if (archivo.isPublic) "Público" else "Privado"

        // Seleccionar icono según el tipo de archivo
        holder.imgFileType.setImageResource(obtenerIconoArchivo(archivo.nombre))

        holder.btnDescargar.setOnClickListener {
            onDownloadClick(archivo)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(archivo)
            true
        }
    }

    override fun getItemCount(): Int = archivos.size

    fun actualizarLista(nuevaLista: List<Archivo>) {
        archivos = nuevaLista
        notifyDataSetChanged()
    }

    /**
     * Determina el icono del archivo según su tipo.
     */
    private fun obtenerIconoArchivo(fileType: String): Int {
        return when (fileType) {
            "image" -> R.drawable.imageicon
            "pdf" -> R.drawable.pdficon
            else -> R.drawable.fileicon
        }
    }

}
