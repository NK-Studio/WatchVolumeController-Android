package com.nkstudio.pc_volume_controller

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tiles.LayoutElementBuilders.VerticalAlignment
import com.google.android.gms.wearable.*
import com.nkstudio.pc_volume_controller.presentation.theme.AndroidWatchTheme
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener
{
    val viewModel by viewModels<MainViewModel>()
    private var activityContext: Context? = null

    private var messageEvent: MessageEvent? = null
    private var mobileNodeUri: String? = null

    private lateinit var capabilityClient: CapabilityClient

    private var androidPhoneNodeWithApp: Node? = null

    companion object
    {
        private const val APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD"
        private const val APP_DATA_WEARABLE_PAYLOAD_PATH = "/APP_DATA_WEARABLE_PAYLOAD"

        private const val wearableAppCheckPayloadReturn = "AppOpenWearable"

        private const val CAPABILITY_PHONE_APP = "gogoPhoneApp"

        private const val TAG = "PPAP"
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        activityContext = this
        initData();

        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "StartApp",
            ) {
                composable("StartApp") {
                    StartApp(navController)
                }

                composable("SettingApp") {
                    SettingApp(viewModel)
                }

                composable("InWearApp") {
                    InWearApp(viewModel)
                }
            }
        }
    }

    override fun onMessageReceived(message: MessageEvent)
    {
        try
        {
            Log.d(TAG, "onMessageReceived 이벤트 수신됨")
            val takeMessage = String(message.data, StandardCharsets.UTF_8)
            val messageEventPath: String = message.path

            Log.d(
                TAG,
                "onMessageReceived() 시계에서 메시지를 받았습니다.: ${message.requestId} $messageEventPath $takeMessage"
            )

            //소스 노드로 다시 메시지 보내기
            //이는 수신자 활동이 열려 있음을 확인합니다.
            if (messageEventPath.isNotEmpty())
            {
                if (messageEventPath == APP_OPEN_WEARABLE_PAYLOAD_PATH)
                {
                    try
                    {
                        // 호스트 부분에서 데이터 항목을 생성한 노드의 노드 ID를 가져옵니다.
                        val nodeId: String = message.sourceNodeId

                        // 모바일에게 보낼 메시지를 바이트로 변경합니다.
                        val returnPayloadAck = wearableAppCheckPayloadReturn
                        val payload: ByteArray = returnPayloadAck.toByteArray()

                        //보내기
                        val sendMessageTask = Wearable.getMessageClient(activityContext!!)
                            .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload)

                        messageEvent = message
                        mobileNodeUri = message.sourceNodeId

                        sendMessageTask.addOnCompleteListener {
                            if (it.isSuccessful)
                            {
                                Log.d(TAG, "메시지가 성공적으로 보내졌다")

                                val sbTemp = StringBuilder()
                                sbTemp.append("\n모바일 장치가 연결되었습니다.")

                                viewModel.isConnect.value = true

                                Log.d(TAG, " $sbTemp")
                            }
                            else
                            {
                                viewModel.isConnect.value = false
                                Log.d(TAG, "메시지가 실패했습니다.")
                            }
                        }
                    }
                    catch (e: Exception)
                    {
                        //Log.d(TAG_MESSAGE_RECEIVED, "송신 노드로 다시 메시지를 보내는 처리")
                        e.printStackTrace()
                    }
                }
                else if (messageEventPath == APP_DATA_WEARABLE_PAYLOAD_PATH)
                {
                    val json: JSONObject?
                    json = JSONObject(takeMessage)
                    val IP = json.getString("AddressIP")
                    val Port = json.getString("AddressPort")

                    SaveData("AddressIP", IP.toString())
                    SaveData("AddressPort", Port.toString())

                    viewModel.ipAndPort.value = "연결된 IP : $IP\n연결된 포트 : $Port"
                    viewModel.address = IP
                    viewModel.port = Port.toInt()
                }
            }
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo)
    {
        androidPhoneNodeWithApp = capabilityInfo.nodes.firstOrNull()
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


    private fun initData()
    {
        val initIP = LoadData("AddressIP", "")
        val initPort = LoadData("AddressPort", "9090")

        viewModel.address = initIP;
        viewModel.port = initPort.toInt();
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
}

@Composable
fun StartApp(navController: NavController)
{
    AndroidWatchTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                modifier = Modifier
                    .width(110.dp)
                    .height(60.dp),
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    navController.navigate("InWearApp")
                }) {
                Text(text = "사용", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                modifier = Modifier
                    .width(110.dp)
                    .height(60.dp),
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    navController.navigate("SettingApp")
                }) {
                Text(text = "설정", fontSize = 20.sp)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InWearApp(viewModel: MainViewModel)
{
    val coroutineScope = rememberCoroutineScope()
    val columnScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(columnScrollState)
            .onRotaryScrollEvent {

                val isVolumeUp = it.verticalScrollPixels.roundToInt() > 0

                val address = viewModel.address
                val port = viewModel.port

                if (isVolumeUp)
                    viewModel.onTriggerSend(0, address!!, port!!)
                else
                    viewModel.onTriggerSend(1, address!!, port!!)

                viewModel.onTriggerVibrate(100, 100);
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "시계 방향 : 볼륨 업\n반시계 방향 볼륨 다운\n글자 터치 : 뮤트 On/Off",
            fontSize = 17.sp,
            color = Color.White,
            lineHeight = 30.5.sp,
            textAlign = TextAlign.Center
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun SettingApp(viewModel: MainViewModel)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        if (!viewModel.isConnect.value)
        {
            Text(text = "앱과 연결 중..")
        }
        else
        {
            Text(
                text = viewModel.ipAndPort.value,
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                lineHeight = 25.sp
            )
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application)
{
    val ctx = getApplication<Application>().applicationContext

    val ipAndPort = mutableStateOf("연결된 IP : 없음\n" + "연결된 포트 : 없음")
    val isConnect = mutableStateOf(false)

    var address: String? = null
    var port: Int? = null

    val TAG = "PPAP"

    //진동을 처리함
    //갤럭시 워치 최대 세기는 255까지 지원한다.
    fun onTriggerVibrate(duration: Long, power: Int)
    {
        // 1.Vibrator 객체 생성
        val vibrator = ctx.getSystemService(VIBRATOR_SERVICE) as Vibrator

        // 2.갤럭시 워치는 최대 진동 세기가 255까지만 된다.
        vibrator.vibrate(VibrationEffect.createOneShot(duration, power));
    }

    fun onTriggerSend(index: Int, ip: String, port: Int)
    {
        val udpClientThread = UdpClientThread();
        udpClientThread.msg = index.toString()
        udpClientThread.address = ip
        udpClientThread.port = port
        udpClientThread.start()
    }

    inner class UdpClientThread : Thread()
    {
        var msg: String? = null
        var address: String? = null
        var port: Int? = null

        override fun run()
        {
            try
            {
                val port = port

                val address = InetAddress.getByName(address)
                val socket = DatagramSocket()

                val buf = msg?.toByteArray()

                val packet = DatagramPacket(buf, buf!!.size, address, port!!)
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