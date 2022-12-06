package com.example.androidwatch

import android.app.Activity
import android.os.Bundle
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

    private var isMute = false;

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val infoText = findViewById<TextView>(R.id.InfoText)
        infoText.requestFocus()

        infoText.setOnGenericMotionListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_SCROLL &&
                ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
            )
            {
                // 여기서 부정을 잊지 마세요
                val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(this), this
                        )

                val isVolumeUp = delta.roundToInt() > 0

                if (isVolumeUp){
                    OnTriggerSend(0)
                }else{
                    val udpClientThread = UdpClientThread();
                    OnTriggerSend(1)
                    udpClientThread.start()
                }

                true
            } else
            {
                false
            }
        }
        infoText.setOnClickListener {
            if (isMute) {
                OnTriggerSend(3)
                isMute = false
            }else
            {
                OnTriggerSend(2)
                isMute = true;
            }
        }
    }

    private fun OnTriggerSend(index : Int){
        val udpClientThread = UdpClientThread();
        udpClientThread.msg = index.toString()
        udpClientThread.start()
    }

    inner class UdpClientThread : Thread()
    {
        var msg : String? = null

        override fun run()
        {
            try
            {
                val port = 9090
                val address = InetAddress.getByName("") //IPv4 IP주소를 입력하세요
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
