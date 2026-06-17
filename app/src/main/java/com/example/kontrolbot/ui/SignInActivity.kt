package com.example.kontrolbot.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.kontrolbot.R
import com.example.kontrolbot.databinding.ActivitySignInBinding
import com.example.kontrolbot.network.RetrofitClient
import com.example.kontrolbot.network.TokenRequest
import com.example.kontrolbot.network.TokenResponse
import com.example.kontrolbot.network.ApiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private lateinit var api: ApiService

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val serverClientId = getString(R.string.server_client_id)
        val backendUrl = getString(R.string.backend_base_url)
        api = RetrofitClient.create(backendUrl)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogleSignIn.setOnClickListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }

        // optional: auto restore signed-in account
        val acct = GoogleSignIn.getLastSignedInAccount(this)
        if (acct != null) {
            binding.tvStatus.text = "Signed in: ${acct.email}"
        }
    }

    private fun handleSignInResult(completedTask: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email

            if (idToken == null) {
                Toast.makeText(this, "ID token is null", Toast.LENGTH_SHORT).show()
                return
            }

            binding.tvStatus.text = "Signing in…"

            // kirim ke server untuk verifikasi
            CoroutineScope(Dispatchers.IO).launch {
                val response = safeVerifyToken(idToken)
                withContext(Dispatchers.Main) {
                    if (response?.ok == true) {
                        binding.tvStatus.text = "Signed in: ${response.email}"
                        Toast.makeText(this@SignInActivity, "Login berhasil: ${response.email}", Toast.LENGTH_SHORT).show()
                        // TODO: lanjut ke main activity / simpan session
                    } else {
                        binding.tvStatus.text = "Sign-in failed"
                        Toast.makeText(this@SignInActivity, "Verifikasi server gagal: ${response?.message ?: "network"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun safeVerifyToken(idToken: String): TokenResponse? {
        return try {
            val resp: Response<TokenResponse> = api.verifyToken(TokenRequest(id_token = idToken))
            if (resp.isSuccessful) resp.body() else TokenResponse(false, null, "HTTP ${resp.code()}")
        } catch (t: Throwable) {
            TokenResponse(false, null, t.message)
        }
    }
}
