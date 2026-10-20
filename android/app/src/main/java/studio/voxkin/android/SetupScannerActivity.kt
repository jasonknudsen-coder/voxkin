package studio.voxkin.android

import android.os.Bundle
import android.view.WindowManager
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

/** Local-only QR capture. No barcode image files, clipboard writes, or payload logging. */
class SetupScannerActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }

    companion object {
        fun options(): ScanOptions = ScanOptions()
            .setCaptureActivity(SetupScannerActivity::class.java)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan a Voxkin setup or invitation QR. You will review it before connecting. Back cancels.")
            .setBeepEnabled(false)
            .setBarcodeImageEnabled(false)
            .setOrientationLocked(false)
    }
}
