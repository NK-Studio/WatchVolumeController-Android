package com.nkstudio.pc_volume_controller

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nkstudio.pc_volume_controller.databinding.ActivityMainBinding
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt


class MainActivity : FragmentActivity(), CapabilityClient.OnCapabilityChangedListener
{
    private lateinit var capabilityClient: CapabilityClient
    private var androidPhoneNodeWithApp: Node? = null

    private lateinit var binding: ActivityMainBinding

    private var isMute = false;

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        capabilityClient = Wearable.getCapabilityClient(this)

        binding.InfoText.requestFocus()

        binding.InfoText.setOnGenericMotionListener { v, ev ->
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

                if (isVolumeUp)
                {
                    OnTriggerSend(0)
                } else
                {
                    OnTriggerSend(1)
                }

                OnTriggerVibrate(100, 100);

                true
            } else
            {
                false
            }
        }
        binding.InfoText.setOnClickListener {
            if (isMute)
            {
                OnTriggerVibrate(400, 150);
                OnTriggerSend(3)
                isMute = false
            } else
            {
                OnTriggerVibrate(400, 150);
                OnTriggerSend(2)
                isMute = true;
            }
        }
    }

    private fun OnTriggerSend(index: Int)
    {
        val udpClientThread = UdpClientThread();
        udpClientThread.msg = index.toString()
        udpClientThread.start()
    }

    ///진동을 처리함
    //갤럭시 워치 최대 세기는 255까지 지원한다.
    private fun OnTriggerVibrate(duration: Long, power: Int)
    {
        // 1. Vibrator 객체 생성
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        //갤럭시 워치는 최대 진동 세기가 255까지만 된다.
        vibrator.vibrate(VibrationEffect.createOneShot(duration, power));
    }

    inner class UdpClientThread : Thread()
    {
        var msg: String? = null

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

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo)
    {
        // 노드 세트에는 하나의 단말기만 있어야 한다.
        // 그래서 첫 번째 항목만 가져온다.
        androidPhoneNodeWithApp = capabilityInfo.nodes.firstOrNull()

        UpdateUI()
    }

    override fun onPause()
    {
        super.onPause()
        Wearable.getCapabilityClient(this).removeListener(this, CAPABILITY_PHONE_APP)
    }

    override fun onResume()
    {
        super.onResume()
        Wearable.getCapabilityClient(this).addListener(this, CAPABILITY_PHONE_APP)

        lifecycleScope.launch {
            checkIfPhoneHasApp()
        }
    }

    private suspend fun checkIfPhoneHasApp()
    {
        try
        {
            val capabilityInfo = capabilityClient
                .getCapability(CAPABILITY_PHONE_APP, CapabilityClient.FILTER_ALL)
                .await()

            Log.d(TAG, "기능 요청이 성공했습니다.")

            withContext(Dispatchers.Main)
            {
                androidPhoneNodeWithApp = capabilityInfo.nodes.firstOrNull()
                UpdateUI()
            }
        } catch (cancellationException: CancellationException)
        {
            // 요청이 정상적으로 취소되었습니다.
        }
        catch (throwable: Throwable)
        {
            Log.d(TAG, "기능 요청이 결과를 반환하지 못했습니다.")
        }
    }

    private fun UpdateUI()
    {
        val androidPhoneNodeWithApp = androidPhoneNodeWithApp

        if (androidPhoneNodeWithApp != null)
            Log.d(TAG, "Installed : 설치가 되어있습니다.")
        else
            Log.d(TAG, "Missing : 설치되어 있지 않습니다.")
    }

    companion object
    {
        private const val TAG = "PPAP"

        // 모바일 앱의 wear.xml에 나열된 기능의 이름입니다.
        // 중요 참고 사항: 이것은 Wear 앱의 기능과 다른 이름을 지정해야 합니다.
        private const val CAPABILITY_PHONE_APP = "pc_volume_controller_mobile"
    }
}
