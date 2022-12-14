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
import android.view.View.OnClickListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
            Log.d(TAG, "onMessageReceived ????????? ?????????")
            val takeMessage = String(message.data, StandardCharsets.UTF_8)
            val messageEventPath: String = message.path

            Log.d(
                TAG,
                "onMessageReceived() ???????????? ???????????? ???????????????.: ${message.requestId} $messageEventPath $takeMessage"
            )

            //?????? ????????? ?????? ????????? ?????????
            //?????? ????????? ????????? ?????? ????????? ???????????????.
            if (messageEventPath.isNotEmpty())
            {
                if (messageEventPath == APP_OPEN_WEARABLE_PAYLOAD_PATH)
                {
                    try
                    {
                        // ????????? ???????????? ????????? ????????? ????????? ????????? ?????? ID??? ???????????????.
                        val nodeId: String = message.sourceNodeId

                        // ??????????????? ?????? ???????????? ???????????? ???????????????.
                        val returnPayloadAck = wearableAppCheckPayloadReturn
                        val payload: ByteArray = returnPayloadAck.toByteArray()

                        //?????????
                        val sendMessageTask = Wearable.getMessageClient(activityContext!!)
                            .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload)

                        messageEvent = message
                        mobileNodeUri = message.sourceNodeId

                        sendMessageTask.addOnCompleteListener {
                            if (it.isSuccessful)
                            {
                                Log.d(TAG, "???????????? ??????????????? ????????????")

                                val sbTemp = StringBuilder()
                                sbTemp.append("\n????????? ????????? ?????????????????????.")

                                viewModel.isConnect.value = true

                                Log.d(TAG, " $sbTemp")
                            }
                            else
                            {
                                viewModel.isConnect.value = false
                                Log.d(TAG, "???????????? ??????????????????.")
                            }
                        }
                    }
                    catch (e: Exception)
                    {
                        //Log.d(TAG_MESSAGE_RECEIVED, "?????? ????????? ?????? ???????????? ????????? ??????")
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

                    viewModel.ipAndPort.value = "????????? IP : $IP\n????????? ?????? : $Port"
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
            horizontalAlignment = CenterHorizontally,
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
                Text(text = "??????", fontSize = 20.sp)
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
                Text(text = "??????", fontSize = 20.sp)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InWearApp(viewModel: MainViewModel)
{
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
                    viewModel.onTriggerSend(0, address!!)
                else
                    viewModel.onTriggerSend(1, address!!)

                viewModel.onTriggerVibrate(100, 100);
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.clickable {
                val address = viewModel.address
                val port = viewModel.port


                if (viewModel.isMute.value)
                {
                    viewModel.onTriggerSend(3, address!!)
                    viewModel.isMute.value = false
                }
                else
                {
                    viewModel.onTriggerSend(2, address!!)
                    viewModel.isMute.value = true
                }

                viewModel.onTriggerVibrate(400, 150);
            },
            text = "?????? ?????? : ?????? ???\n????????? ?????? ?????? ??????\n?????? ?????? : ?????? On/Off",
            fontSize = 17.sp,
            color = Color.White,
            lineHeight = 30.5.sp,
            textAlign = TextAlign.Center,
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun SettingApp(viewModel: MainViewModel)
{
    if (viewModel.address.isNullOrEmpty())
        viewModel.ipAndPort.value = "????????? IP : ??????\n????????? ?????? : 9090"
    else
        viewModel.ipAndPort.value = "????????? IP : ${viewModel.address}\n????????? ?????? : 9090"



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),

        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = viewModel.ipAndPort.value,
            textAlign = TextAlign.Center,
            fontSize = 17.sp,
            lineHeight = 25.sp
        )

        if (!viewModel.isConnect.value)
            Text(modifier = Modifier.padding(top = 30.dp), text = "?????? ?????? ???", fontSize = 15.sp)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application)
{
    val ctx = getApplication<Application>().applicationContext

    val ipAndPort = mutableStateOf("????????? IP : ??????\n" + "????????? ?????? : ??????")
    val isConnect = mutableStateOf(false)
    val isMute = mutableStateOf(false)

    var address: String? = null
    var port: Int? = null

    val TAG = "PPAP"

    //????????? ?????????
    //????????? ?????? ?????? ????????? 255?????? ????????????.
    fun onTriggerVibrate(duration: Long, power: Int)
    {
        // 1.Vibrator ?????? ??????
        val vibrator = ctx.getSystemService(VIBRATOR_SERVICE) as Vibrator

        // 2.????????? ????????? ?????? ?????? ????????? 255????????? ??????.
        vibrator.vibrate(VibrationEffect.createOneShot(duration, power));
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