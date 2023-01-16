package com.nkstudio.pc_volume_controller

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


class Controller : AppCompatActivity()
{
    var myAdress = ""

    val TAG = "PPAP"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        val secondIntent = intent
        myAdress = secondIntent.getStringExtra("adress").toString()

        findViewById<Button>(R.id.volumeUp).setOnClickListener {
            onTriggerSend(0,myAdress)
            onTriggerVibrate(400, 150);
        }

        findViewById<Button>(R.id.volumeDown).setOnClickListener {
            onTriggerSend(1,myAdress)
            onTriggerVibrate(400, 150);
        }

        findViewById<Button>(R.id.Mute).setOnClickListener {
            onTriggerSend(2,myAdress)
            onTriggerVibrate(400, 150);
        }

        findViewById<Button>(R.id.NoMute).setOnClickListener {
            onTriggerSend(3,myAdress)
            onTriggerVibrate(400, 150);
        }
    }

    fun onTriggerSend(index: Int, ip: String)
    {
        val udpClientThread = UdpClientThread();
        udpClientThread.msg = index.toString()
        udpClientThread.address = ip
        udpClientThread.start()
    }

    //진동을 처리함
    //갤럭시 워치 최대 세기는 255까지 지원한다.
    @RequiresApi(Build.VERSION_CODES.O)
    fun onTriggerVibrate(duration: Long, power: Int)
    {
        // 1.Vibrator 객체 생성
        val vibrator = application.getSystemService(VIBRATOR_SERVICE) as Vibrator

        // 2.갤럭시 워치는 최대 진동 세기가 255까지만 된다.
        vibrator.vibrate(VibrationEffect.createOneShot(duration, power));
    }

    inner class UdpClientThread : Thread()
    {
        var msg: String? = null
        var address: String? = null

        override fun run()
        {
            try
            {
                val address = InetAddress.getByName(address)
                val socket = DatagramSocket()

                val buf = msg?.toByteArray()

                val packet = DatagramPacket(buf, buf!!.size, address, 9090)
                socket.send(packet)
                socket.close()
            }
            catch (e: Exception)
            {
                e.printStackTrace()
            }
        }
    }
}