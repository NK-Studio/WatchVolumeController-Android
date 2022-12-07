package com.nkstudio.pc_volume_controller

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.nkstudio.pc_volume_controller.databinding.ActivityMainBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), OnCapabilityChangedListener
{
    companion object{
        private const val SuccessMessage = "워치와 연결됨"
        private const val FailMessage = "워치와 연결되지 않음"

        private const val TAG = "PPAP"

        // 모바일 앱의 wear.xml에 나열된 기능의 이름입니다.
        // 중요 참고 사항: 이것은 Wear 앱의 기능과 다른 이름을 지정해야 합니다.
        private const val CAPABILITY_WEAR_APP = "pc_volume_controller_wear"
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var capabilityClient: CapabilityClient
    private lateinit var nodeClient: NodeClient

    private var wearNodesWithApp: Set<Node>? = null
    private var allConnectedNodes: List<Node>? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        capabilityClient = Wearable.getCapabilityClient(this)
        nodeClient = Wearable.getNodeClient(this)

        // UI의 초기 업데이트 수행
        updateUI()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    // 우리의 기능인 Wear 앱이 설치된 장치에 대한 초기 요청입니다.
                    findWearDevicesWithApp()
                }
                launch {
                    // 연결된 모든 Wear 기기에 대한 초기 요청(당사의 기능 유무에 관계없이).
                    // 추가 참고 사항: 네트워크에서 추가된 모든 노드에 대한 수신기가 없기 때문입니다.
                    // 더 이상 사용되지 않는 Google API 클라이언트가 있을 때 전체 목록을 업데이트하기만 하면 됩니다.
                    // onCapabilityChanged() 메서드에서 기능 변경이 발생할 때.
                    findAllWearDevices()
                }
            }
        }
    }

    override fun onPause() {

        super.onPause()
        capabilityClient.removeListener(this, CAPABILITY_WEAR_APP)
    }

    override fun onResume() {

        super.onResume()
        capabilityClient.addListener(this, CAPABILITY_WEAR_APP)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo)
    {
        wearNodesWithApp = capabilityInfo.nodes

        lifecycleScope.launch {
            findAllWearDevices()
        }
    }


    private suspend fun findWearDevicesWithApp() {
        Log.d(TAG, "findWearDevicesWithApp()")

        try {
            val capabilityInfo = capabilityClient
                .getCapability(CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL)
                .await()

            withContext(Dispatchers.Main) {
                Log.d(TAG, "Capability request succeeded.")
                wearNodesWithApp = capabilityInfo.nodes
                Log.d(TAG, "Capable Nodes: $wearNodesWithApp")
                updateUI()
            }
        } catch (cancellationException: CancellationException) {
            // Request was cancelled normally
            throw cancellationException
        } catch (throwable: Throwable) {
            Log.d(TAG, "Capability request failed to return any results.")
        }
    }


    private suspend fun findAllWearDevices() {
        Log.d(TAG, "findAllWearDevices()")

        try {
            val connectedNodes = nodeClient.connectedNodes.await()

            withContext(Dispatchers.Main) {
                allConnectedNodes = connectedNodes
                updateUI()
            }
        } catch (cancellationException: CancellationException) {
            // Request was cancelled normally
        } catch (throwable: Throwable) {
            Log.d(TAG, "Node request failed to return any results.")
        }
    }

    private fun updateUI() {
        Log.d(TAG, "updateUI()")

        val wearNodesWithApp = wearNodesWithApp
        val allConnectedNodes = allConnectedNodes

        when {
            wearNodesWithApp == null || allConnectedNodes == null -> {
                Log.d(TAG, "연결된 노드와 앱이 있는 노드 모두에 대한 결과 대기 중")
                binding.InfoText.isVisible = false;
            }
            allConnectedNodes.isEmpty() -> {
                Log.d(TAG, "기기 없음")
                binding.InfoText.text = FailMessage
                binding.InfoText.isVisible = true
            }
            wearNodesWithApp.isEmpty() -> {
                Log.d(TAG, "모든 기기에서 누락됨")
                binding.InfoText.text = FailMessage
                binding.InfoText.isVisible = true
            }
            wearNodesWithApp.size < allConnectedNodes.size -> {
                Log.d(TAG, "일부 기기에 설치됨")
                binding.InfoText.text = SuccessMessage
                binding.InfoText.isVisible = true
            }
            else -> {
                // TODO: Wear API를 통해 Wear 앱과 통신하는 코드를 추가하세요.
                //       (MessageClient, DataClient, etc.)
                Log.d(TAG, "모든 기기에 설치됨")
                binding.InfoText.text = SuccessMessage
                binding.InfoText.isVisible = true
            }
        }
    }
}