package com.nkstudio.pc_volume_controller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import com.nkstudio.pc_volume_controller.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.HashSet


class MainActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener
{
    companion object
    {
        private const val wearableAppCheckPayloadReturn = "AppOpenWearable"
        private const val APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD"
        private const val APP_DATA_WEARABLE_PAYLOAD_PATH = "/APP_DATA_WEARABLE_PAYLOAD"

        private const val SuccessMessage = "워치와 연결됨"
        private const val FailMessage = "워치와 연결되지 않음"

        private const val TAG = "PPAP"

        // 모바일 앱의 wear.xml에 나열된 기능의 이름입니다.
        // 중요 참고 사항: 이것은 Wear 앱의 기능과 다른 이름을 지정해야 합니다.
        private const val CAPABILITY_WEAR_APP = "pc_volume_controller_wear"

        private const val START_ACTIVITY_PATH = "/message-item-received"

        private val wearableAppCheckPayload = "AppOpenWearable"
    }

    var activityContext: Context? = null

    private var currentWearAppOpenCheck: String? = null

    private lateinit var binding: ActivityMainBinding

    private var wearNodesWithApp: Set<Node>? = null
    private var allConnectedNodes: List<Node>? = null

    private var messageEvent: MessageEvent? = null
    private var wearableNodeUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        activityContext = this;

        //초기 UI 처리
        initUI();

        // UI의 초기 업데이트 수행
        updateUI()

        binding.Send.setOnClickListener {

            val address = binding.IPNumber.text
            val port = binding.PortNumber.text
            SaveData("AddressIP", address.toString())
            SaveData("AddressPort", port.toString())

            val json = JSONObject()
            json.put("AddressIP", address.toString())
            json.put("AddressPort", port.toString())
            val data = json.toString().toByteArray()

            MainScope().launch(Dispatchers.Default)
            {
                sendData(data, APP_DATA_WEARABLE_PAYLOAD_PATH)
            }
        }

        binding.PCControl.setOnClickListener {

            var controlIntent = Intent(this, Controller::class.java)
            controlIntent.flags =
                FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_NO_ANIMATION
            controlIntent.putExtra("adress", binding.IPNumber.text.toString())
            startActivity(controlIntent)
        }
    }

    private fun initUI()
    {
        val initIP = LoadData("AddressIP", "")
        val initPort = LoadData("AddressPort", "9090")

        binding.IPNumber.setText(initIP)
        binding.PortNumber.setText(initPort)
    }

    private fun sendData(data: ByteArray, path: String)
    {
        val nodeId: String = messageEvent?.sourceNodeId!!
        Wearable.getMessageClient(activityContext!!).sendMessage(nodeId, path, data)
    }

    private fun updateUI()
    {
        val wearNodesWithApp = wearNodesWithApp
        val allConnectedNodes = allConnectedNodes

        when
        {
            wearNodesWithApp == null || allConnectedNodes == null ->
            {
                Log.d(TAG, "연결된 노드와 앱이 있는 노드 모두에 대한 결과 대기 중")
                binding.InfoText.isVisible = false;
            }
            allConnectedNodes.isEmpty() ->
            {
                Log.d(TAG, "기기 없음")
                binding.InfoText.text = FailMessage
                binding.InfoText.isVisible = true
            }
            wearNodesWithApp.isEmpty() ->
            {
                Log.d(TAG, "모든 기기에서 누락됨")
                binding.InfoText.text = FailMessage
                binding.InfoText.isVisible = true
            }
            wearNodesWithApp.size < allConnectedNodes.size ->
            {
                Log.d(TAG, "일부 기기에 설치됨")
                binding.InfoText.text = SuccessMessage
                binding.InfoText.isVisible = true
            }
            else ->
            {
                // TODO: Wear API를 통해 Wear 앱과 통신하는 코드를 추가하세요.
                //       (MessageClient, DataClient, etc.)
                Log.d(TAG, "모든 기기에 설치됨")
                binding.InfoText.text = SuccessMessage
                binding.InfoText.isVisible = true
            }
        }
    }

    private fun LoadData(key: String, initValue: String): String
    {
        var result = initValue

        val settingPreferences = getSharedPreferences("setting", Activity.MODE_PRIVATE)
        if ((settingPreferences != null) && settingPreferences.contains(key))
            result = settingPreferences.getString(key, initValue).toString()

        return result
    }

    private fun SaveData(key: String, value: String)
    {
        val settingPreferences = getSharedPreferences("setting", Activity.MODE_PRIVATE)
        val editor = settingPreferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    /**
     * 워치와 연결을 시도합니다.
     */
    private fun initialiseDevicePairing(activity: Activity)
    {
        MainScope().launch(Dispatchers.Default)
        {

            var getNodesResBool: BooleanArray? = null

            try
            {
                getNodesResBool = getNodes(activity.applicationContext)
            }
            catch (e: Exception)
            {
                e.printStackTrace()
            }

            //UI Thread
            withContext(Dispatchers.Main) {
                if (getNodesResBool!![0])
                {
                    //메시지 확인이 수신된 경우
                    if (getNodesResBool[1])
                    {
                        Toast.makeText(
                            activityContext,
                            "웨어러블 기기가 페어링되고 앱이 열려 있습니다. 웨어러블 기기로 메시지를 보내려면 \"웨어러블에 메시지 보내기\" 버튼을 탭하세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else
                    {
                        Toast.makeText(
                            activityContext,
                            "웨어러블 장치가 페어링되었지만 시계의 웨어러블 앱이 열려 있지 않습니다. 웨어러블 앱을 실행하고 다시 시도하십시오.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                else
                {
                    Toast.makeText(
                        activityContext,
                        "페어링된 웨어러블 기기가 없습니다. Wear OS 앱을 사용하여 웨어러블 기기를 휴대전화에 페어링하고 다시 시도하세요.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     *웨어러블과 연결이 되어있는지? 웨어러블 앱이 켜져있는지?를 반환하는 배열을 반환합니다.
     */
    private fun getNodes(context: Context): BooleanArray
    {
        val nodeResults = HashSet<String>()
        val resBool = BooleanArray(2)

        resBool[0] = false //노드 있음
        resBool[1] = false //웨어러블 반환 승인 수신됨

        val nodeListTask = Wearable.getNodeClient(context).connectedNodes

        try
        {
            // 작업을 차단하고 동기식으로 결과를 가져옵니다(백그라운드 스레드에 있기 때문).
            val nodes = Tasks.await(nodeListTask)

            for (node in nodes)
            {
                //노드 결과에 id를 추가
                nodeResults.add(node.id)

                try
                {
                    val nodeId = node.id

                    //메시지의 데이터를 Uri의 바이트로 설정합니다.

                    val payload: ByteArray = "CheckMessage".toByteArray()

                    // rpc 보내기
                    // 클라이언트는 비용이 저렴하므로 멤버 변수 없이 클라이언트를 인스턴스화합니다.
                    // 만들다. (GoogleApi 인스턴스 간에 캐시 및 공유됩니다.)
                    val sendMessageTask = Wearable.getMessageClient(context)
                        .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload)

                    try
                    {
                        // 작업을 차단하고 동기식으로 결과를 가져옵니다(백그라운드 스레드에 있기 때문).
                        val result = Tasks.await(sendMessageTask)

                        Log.d("PPAP", "메시지 결과 보내기 : $result")

                        resBool[0] = true

                        //확인 메시지를 위해 1000ms 1초 동안 기다립니다.
                        //Wait 1
                        if (currentWearAppOpenCheck != wearableAppCheckPayloadReturn)
                        {
                            Thread.sleep(100)
                        }

                        if (currentWearAppOpenCheck == wearableAppCheckPayloadReturn)
                        {
                            resBool[1] = true
                            return resBool
                        }

                        //Wait 2
                        if (currentWearAppOpenCheck != wearableAppCheckPayloadReturn)
                        {
                            Thread.sleep(150)
                        }

                        if (currentWearAppOpenCheck == wearableAppCheckPayloadReturn)
                        {
                            resBool[1] = true
                            return resBool
                        }

                        //Wait 3
                        if (currentWearAppOpenCheck != wearableAppCheckPayloadReturn)
                        {
                            Thread.sleep(200)
                        }

                        if (currentWearAppOpenCheck == wearableAppCheckPayloadReturn)
                        {
                            resBool[1] = true
                            return resBool
                        }

                        //Wait 4
                        if (currentWearAppOpenCheck != wearableAppCheckPayloadReturn)
                        {
                            Thread.sleep(250)
                        }

                        if (currentWearAppOpenCheck == wearableAppCheckPayloadReturn)
                        {
                            resBool[1] = true
                            return resBool
                        }

                        //Wait 5
                        if (currentWearAppOpenCheck != wearableAppCheckPayloadReturn)
                        {
                            Thread.sleep(350)
                        }

                        if (currentWearAppOpenCheck == wearableAppCheckPayloadReturn)
                        {
                            resBool[1] = true
                            return resBool
                        }

                        resBool[1] = false

                        Log.d("PPAP", "ACK 스레드 시간 초과, 웨어러블에서 수신된 메시지 없음")

                    }
                    catch (exception: Exception)
                    {
                        exception.printStackTrace()
                    }
                }
                catch (e1: Exception)
                {
                    Log.d("PPAP", "메시지 예외 보내기")
                    e1.printStackTrace()
                }
            } //for 루프의 끝
        }
        catch (e1: Exception)
        {
            Log.d("PPAP", "메시지 예외 보내기")
            e1.printStackTrace()
        }

        return resBool
    }

    //    private fun SendMessage(context: Context, msg: String, Path: String)
    //    {
    //        MainScope().launch(Dispatchers.Default)
    //        {
    //            val nodeResults = HashSet<String>()
    //
    //            val nodeListTask = Wearable.getNodeClient(context).connectedNodes
    //
    //            try
    //            {
    //                // 작업을 차단하고 동기식으로 결과를 가져옵니다(백그라운드 스레드에 있기 때문).
    //                val nodes = Tasks.await(nodeListTask)
    //
    //                for (node in nodes)
    //                {
    //                    //노드 결과에 id를 추가
    //                    nodeResults.add(node.id)
    //
    //                    try
    //                    {
    //                        val nodeId = node.id
    //
    //                        //메시지의 데이터를 Uri의 바이트로 설정합니다.
    //                        val payload: ByteArray = msg.toByteArray()
    //
    //                        val sendMessageTask =
    //                            Wearable.getMessageClient(context).sendMessage(nodeId, Path, payload)
    //
    //                        try
    //                        {
    //                            // 작업을 차단하고 동기식으로 결과를 가져옵니다(백그라운드 스레드에 있기 때문).
    //                            val result = Tasks.await(sendMessageTask)
    //
    //                            Log.d("PPAP", "메시지 결과 보내기 : $result")
    //                        }
    //                        catch (e1: Exception)
    //                        {
    //                            e1.printStackTrace()
    //                        }
    //
    //                    }
    //                    catch (e1: Exception)
    //                    {
    //                        Log.d("PPAP", "메시지 예외 보내기")
    //                        e1.printStackTrace()
    //                    }
    //                } //for 루프의 끝
    //            }
    //            catch (e1: Exception)
    //            {
    //                Log.d("PPAP", "메시지 예외 보내기")
    //                e1.printStackTrace()
    //            }
    //        }
    //    }

    override fun onMessageReceived(message: MessageEvent)
    {
        try
        {
            val takeMessage = String(message.data, StandardCharsets.UTF_8)
            val messageEventPath: String = message.path

            //Log.d("PPAP", "onMessageReceived() 시계에서 메시지 수신: ${message.requestId} ${messageEventPath} ${takeMessage}")

            if (messageEventPath == APP_OPEN_WEARABLE_PAYLOAD_PATH)
            {
                //Log.d("PPAP", "Wear에서 앱이 열린다는 확인 메시지를 받았습니다.")

                currentWearAppOpenCheck = takeMessage

                messageEvent = message
                wearableNodeUri = message.sourceNodeId
            }
            //            else if (messageEventPath.isNotEmpty() && messageEventPath == MESSAGE_ITEM_RECEIVED_PATH)
            //            {
            //                try
            //                {
            //                    val sbTemp = StringBuilder()
            //                    sbTemp.append("\n")
            //                    sbTemp.append(takeMessage)
            //                    sbTemp.append(" - (웨어러블에서 수신)")
            //
            //                    Log.d("receive1", " $sbTemp")
            //
            //                } catch (e: Exception)
            //                {
            //                    e.printStackTrace()
            //                }
            //            }
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            Log.d("receive1", "Handled")
        }
    }

    override fun onPause()
    {
        super.onPause()

        try
        {
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    override fun onResume()
    {
        super.onResume()

        try
        {
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }

        val activity: Activity = activityContext as MainActivity

        //워치와 연결을 시도합니다.
        initialiseDevicePairing(activity);
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo)
    {

    }
}