package com.example.androidwatch

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import com.example.androidwatch.databinding.ActivityMainBinding
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt


class MainActivity : Activity()
{

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var s = findViewById<TextView>(R.id.hi)
        s.requestFocus()

        s.setOnGenericMotionListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_SCROLL &&
                ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
            )
            {
                // 여기서 부정을 잊지 마세요
                val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(this), this
                        )

                var isVolumeUp = delta.roundToInt() > 0

                if (isVolumeUp){
                    val udpClientThread = UdpClientThread();
                    udpClientThread.msg = "0"
                    udpClientThread.start()
                }else{
                    val udpClientThread = UdpClientThread();
                    udpClientThread.msg = "1"
                    udpClientThread.start()
                }

                true
            } else
            {
                false
            }
        }

    }

    inner class UdpClientThread : Thread()
    {
        var msg : String? = null

        override fun run()
        {
            try
            {
                val port = 9090
                val address = InetAddress.getByName("172.30.1.97")
                val socket = DatagramSocket()

                val buf = msg?.toByteArray()

                val packet = DatagramPacket(buf, buf!!.size, address, port)
                socket.send(packet)
                socket.close()
            } catch (e: Exception)
            {
                e.printStackTrace()
            }
        }
    }
}