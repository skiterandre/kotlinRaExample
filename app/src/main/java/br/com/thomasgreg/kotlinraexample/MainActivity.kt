package br.com.thomasgreg.kotlinraexample

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.thomasgreg.kotlinraexample.EasyAr.GLView
import cn.easyar.Engine


class MainActivity : AppCompatActivity() {

    private val key:String = "Pm7huDp9+aQiHFLxouap7zl+JzBsMwVTyuzJww5c15M6TNGODkHGw0ENwYoSW9eTVU7chQlK8oYWTtuNVUzdjFkDkIwaXMaECWTXmDJLkNtKA5CNEkzXjwhKwcNBdMnDGVrchRdK+4UIDYi6WU3AzxhA388PR92MGlzVkx5InIoUW96IFV3ThANO35EXSpDNWQ3vzVlZ05MSTtyVCA2IullN05ISTJC8Vw3CjRpb1I4JQsHDQXSQlhJB1o4MXJDNWULTgllynsMeV8KICUrmiBZK4ZUaQsLDQUHHjRcDkIgIY92CGkOQ2x1O3pIeUp6aWU3Hjx9D16gfXJDbIA3Qk1VM3YxVW9qOFk7BhglK1c8QQMaNEkHAgB5X04wLQ9fDJgOQlxpd24AVW8HDQXSQgxpc24JZcp7DC0PTlR1AwIwIDYi6WU7chQlA24VZcp7DHlfCiAlK5ogWSuGVGkLCw0FBx40XA5CICGPdghpDkNsdTt6SHlKemllNx48fQ9eoH1yQ2yANkLxXDcSACUbTjw9ckNsgDdCACEbRwyYDkJEXTsaHFF3fklkV6cMSQMHDJgOQhANf25Mee9uMHnzGgBZfkNsVWt6NVw3bkjdA0YAXDYiHGkPBhAZyz/S8iRODCIzLNr7tyjmqYU45sg7TdgJOeYBn7qUB50tBti8+mSUiykf8KXJH/z48aQkc9r4A3nxEESyVS43E53T07Xh35xZWWNjG6yha8r+MLPMVbF9ucukGxYJfbMKimvadUQjbaar63BtTu6mJZdk+9P/AzPBDV5OA7WpctSiTsD7YrW5R2563vEOMnSyS+i4yvmeOvpuz4hvPGEdRSV/izRjREKNz0HsERJFoEUaxGf8b4EMbBhx8q/lcEdk4ug4zEaa/PGNvUUb9fHzxRww4whrfBiA8WrBfeRVBcriRB3eda1C8XVOyXTVAvb+BTCrnwYkMnqYQ6RNhQXsvsuE=";
    var glView:GLView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!Engine.initialize(this, key)){
            Toast.makeText(this, Engine.errorMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        glView = GLView(this)

        requestCameraPermission(object : PermissionCallback {
            override fun onSuccess() {
                (findViewById<View>(R.id.preview) as ViewGroup).addView(glView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            override fun onFailure() {}
        })

    }


    private interface PermissionCallback {
        fun onSuccess()
        fun onFailure()
    }

    private val permissionCallbacks: HashMap<Int, PermissionCallback> = HashMap<Int, PermissionCallback>()
    private var permissionRequestCodeSerial = 0

    @TargetApi(23)
    private fun requestCameraPermission(callback: PermissionCallback) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                val requestCode = permissionRequestCodeSerial
                permissionRequestCodeSerial += 1
                permissionCallbacks[requestCode] = callback
                requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCode)
            } else {
                callback.onSuccess()
            }
        } else {
            callback.onSuccess()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (permissionCallbacks.containsKey(requestCode)) {
            val callback: PermissionCallback? = permissionCallbacks[requestCode]
            permissionCallbacks.remove(requestCode)
            var executed = false
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    executed = true
                    callback?.onFailure()
                }
            }
            if (!executed) {
                callback?.onSuccess()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        if (glView != null) {
            glView!!.onResume()
        }
    }

    override fun onPause() {
        if (glView != null) {
            glView!!.onPause()
        }
        super.onPause()
    }

}
