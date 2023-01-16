package com.nkstudio.pc_volume_controller

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


class Controller : AppCompatActivity()
{
    var myAdress = ""

    val TAG = "PPAP"

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        val secondIntent = intent
        myAdress = secondIntent.getStringExtra("adress").toString()

        findViewById<Button>(R.id.volumeUp).setOnClickListener {
            onTriggerSend(0,myAdress)
        }

        findViewById<Button>(R.id.volumeDown).setOnClickListener {
            onTriggerSend(1,myAdress)
        }

        findViewById<Button>(R.id.Mute).setOnClickListener {
            onTriggerSend(2,myAdress)
        }

        findViewById<Button>(R.id.NoMute).setOnClickListener {
            onTriggerSend(3,myAdress)
        }

    }

    fun onTriggerSend(index: Int, ip: String)
    {
        val udpClientThread = UdpClientThread();
        udpClientThread.msg = index.toString()
        udpClientThread.address = ip
        udpClientThread.start()
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