package com.example.kontrolbot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.kontrolbot.R
import com.example.kontrolbot.auth.AuthManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If not logged in, redirect to sign-in
        if (!AuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        val tvWelcome = findViewById<TextView>(R.id.tv_welcome)
        val btnLogout = findViewById<Button>(R.id.btn_logout)

        tvWelcome.text = "Welcome, ${AuthManager.getEmail(this) ?: "User"}"

        btnLogout.setOnClickListener {
            AuthManager.clearSession(this)
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }
    }
}
