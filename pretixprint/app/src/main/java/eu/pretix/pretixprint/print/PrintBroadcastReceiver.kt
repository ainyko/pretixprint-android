package eu.pretix.pretixprint.print

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cups4j.CupsClient
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import java.net.URL
import org.json.JSONObject
import java.io.*
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfReader




class PrintBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {

        val pendingResult = goAsync()
        doAsync {
            val prefs = ctx.defaultSharedPreferences

            val cc = CupsClient(
                    URL(
                            "http://" +
                                    prefs.getString("hardware_ticketprinter_ip", "127.0.0.1") +
                                    ":" +
                                    prefs.getString("hardware_ticketprinter_port", "631")
                    )
            )
            var cp: CupsPrinter? = null
            for (printer in cc.printers) {
                if (printer.name == prefs.getString("hardware_ticketprinter_printername", "PATicket")) {
                    cp = printer
                }
            }
            if (cp == null) {
                cp = cc.defaultPrinter
            }

            if (cp != null && intent != null) {
                val dataInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(0).uri)
                val jsonData = JSONObject(dataInputStream.bufferedReader().use { it.readText() })

                val positions = jsonData.getJSONArray("positions")
                val pages = emptyList<File>().toMutableList()
                for (i in 0..(positions.length() - 1)) {
                    val position = positions.getJSONObject(i)
                    val layout = position.getJSONArray("__layout");

                    val tmpfile = File.createTempFile("print_$i", "pdf", ctx.cacheDir)
                    if (position.has("__file_index")) {
                        val fileIndex = position.getInt("__file_index");

                        val bgInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(fileIndex).uri)
                        bgInputStream.use {
                            Renderer(layout, jsonData, i, it, ctx).writePDF(tmpfile)
                        }
                    } else {
                        Renderer(layout, jsonData, i, null, ctx).writePDF(tmpfile)
                    }
                    pages.add(tmpfile)
                }

                val tmpfile = File.createTempFile("print" , "pdf", ctx.cacheDir)
                val document = Document()
                val copy = PdfCopy(document, FileOutputStream(tmpfile))
                document.open()
                for (page in pages) {
                    val pagedoc = PdfReader(page.absolutePath)
                    copy.addDocument(pagedoc)
                    pagedoc.close()
                }
                document.close()

                val pj = PrintJob.Builder(tmpfile.inputStream()).build()
                cp.print(pj)
            }
            pendingResult.finish()
        }

    }

}