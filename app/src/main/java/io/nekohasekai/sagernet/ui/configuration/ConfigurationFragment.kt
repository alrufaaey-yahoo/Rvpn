package io.nekohasekai.sagernet.ui.configuration

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.FragmentConfigurationBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressListBinding
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.group.SubscriptionFoundException
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ScannerActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import io.nekohasekai.sagernet.ui.settings.DirectSettingsActivity
import io.nekohasekai.sagernet.ui.settings.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.settings.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.settings.JuicitySettingsActivity
import io.nekohasekai.sagernet.ui.settings.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.settings.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.settings.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.settings.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.settings.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.settings.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.settings.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.settings.VMessSettingsActivity
import io.nekohasekai.sagernet.utils.Logs
import io.nekohasekai.sagernet.utils.ProfileManager
import io.nekohasekai.sagernet.utils.Protocols
import io.nekohasekai.sagernet.utils.onMainDispatcher
import io.nekohasekai.sagernet.utils.readableMessage
import io.nekohasekai.sagernet.utils.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.snackbar

class ConfigurationFragment : ToolbarFragment(), Toolbar.OnMenuItemClickListener {

    private val viewModel by viewModels<ConfigurationViewModel>()
    lateinit var adapter: ConfigurationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentConfigurationBinding.bind(view)

        toolbar.title = getString(R.string.configuration)
        toolbar.inflateMenu(R.menu.add_profile_menu)
        toolbar.setOnMenuItemClickListener(this)

        adapter = ConfigurationAdapter(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(ConfigurationTouchHelper(adapter))
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        viewModel.proxies.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                val list = adapter.currentList.toMutableList()
                val from = list.removeAt(fromPosition)
                list.add(toPosition, from)
                runOnDefaultDispatcher {
                    ProfileManager.updateOrder(list)
                }
            }
        })
    }

    private val importFile = registerForActivityResult(MainActivity.GetContent()) {
        val uri = it ?: return@registerForActivityResult
        runOnDefaultDispatcher {
            try {
                val content = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                }
                if (content.isNullOrBlank()) return@runOnDefaultDispatcher
                val proxies = RawUpdater.parseRaw(content)
                if (proxies.isNullOrEmpty()) onMainDispatcher {
                    snackbar(getString(R.string.no_proxies_found_in_file)).show()
                } else import(proxies)
            } catch (e: SubscriptionFoundException) {
                (requireActivity() as MainActivity).importSubscription(e.link.toUri())
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    snackbar(e.readableMessage).show()
                }
            }
        }
    }

    private fun import(proxies: List<ProxyEntity>) {
        runOnDefaultDispatcher {
            val groupId = DataStore.currentGroupId()
            proxies.forEach {
                it.groupId = groupId
            }
            ProfileManager.createProfile(proxies)
        }
        onMainDispatcher {
            snackbar(
                requireContext().resources.getQuantityString(
                    R.plurals.added, proxies.size, proxies.size
                )
            ).show()
        }

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_qr_code -> {
                startActivity(Intent(context, ScannerActivity::class.java))
            }

            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                    return true
                } else if (!text.contains("alrufaaey://")) {
                    snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                    return true
                } else runOnDefaultDispatcher {
                    suspend fun parseSubscription() {
                        try {
                            val proxies = RawUpdater.parseRaw(text)
                            if (proxies.isNullOrEmpty()) onMainDispatcher {
                                snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                            } else import(proxies)
                        } catch (e: SubscriptionFoundException) {
                            (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                        } catch (e: Exception) {
                            Logs.w(e)

                            onMainDispatcher {
                                snackbar(e.readableMessage).show()
                            }
                        }
                    }

                    val singleURI = try {
                        text.toUri()
                    } catch (_: Exception) {
                        null
                    }
                    if (singleURI != null) {
                        // Import as proxy or subscription
                        when (singleURI.scheme) {
                            "http", "https" -> onMainDispatcher {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.subscription_import)
                                    .setMessage(R.string.import_http_url)
                                    .setPositiveButton(R.string.subscription_import) { _, _ ->
                                        runOnDefaultDispatcher {
                                            (requireActivity() as MainActivity).importSubscription(
                                                singleURI
                                            )
                                        }
                                    }
                                    .setNegativeButton(R.string.profile_import) { _, _ ->
                                        runOnDefaultDispatcher {
                                            parseSubscription()
                                        }
                                    }
                                    .show()
                            }

                            else -> parseSubscription()
                        }
                    } else {
                        parseSubscription()
                    }
                }
            }

            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
            }

            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }

            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }

            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }

            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }

            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
                    putExtra("vless", true)
                })
            }

            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }

            R.id.action_new_mieru -> {
                startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
            }

            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }

            R.id.action_new_hysteria -> {
                startActivity(Intent(requireActivity(), HysteriaSettingsActivity::class.java))
            }

            R.id.action_new_tuic -> {
                startActivity(Intent(requireActivity(), TuicSettingsActivity::class.java))
            }

            R.id.action_new_juicity -> {
                startActivity(Intent(requireActivity(), JuicitySettingsActivity::class.java))
            }

            R.id.action_new_direct -> {
                startActivity(Intent(requireActivity(), DirectSettingsActivity::class.java))
            }

            R.id.action_new_ssh -> {
                startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
            }

            R.id.action_new_wg -> {
                // startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
            }

            R.id.action_new_shadowtls -> {
                // startActivity(Intent(requireActivity(), ShadowTLSSettingsActivity::class.java))
            }

            R.id.action_new_anytls -> {
                // startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
            }

            R.id.action_new_config -> {
                // startActivity(Intent(requireActivity(), ConfigSettingsActivity::class.java))
            }

            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }

            R.id.action_clear_traffic_statistics -> runOnDefaultDispatcher {
                val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                val toClear = mutableListOf<ProxyEntity>()
                if (profiles.isNotEmpty()) {
                    for (profile in profiles) {
                        if (profile.tx != 0L || profile.rx != 0L) {
                            profile.tx = 0
                            profile.rx = 0
                            toClear.add(profile)
                        }
                    }
                }
                if (toClear.isNotEmpty()) {
                    ProfileManager.updateProfile(toClear)
                }
            }

            R.id.action_connection_test_clear_results -> runOnDefaultDispatcher {
                val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                val toClear = mutableListOf<ProxyEntity>()
                if (profiles.isNotEmpty()) {
                    for (profile in profiles) {
                        if (profile.status != ProxyEntity.STATUS_INITIAL) {
                            profile.status = ProxyEntity.STATUS_INITIAL
                            profile.ping = 0
                            profile.error = null
                            toClear.add(profile)
                        }
                    }
                }
                if (toClear.isNotEmpty()) {
                    ProfileManager.updateProfile(toClear)
                }
            }

            R.id.action_connection_test_delete_unavailable -> runOnDefaultDispatcher {
                val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                val toClear = profiles.filter { it.status == ProxyEntity.STATUS_ERROR }
                if (toClear.isNotEmpty()) {
                    onMainDispatcher {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.action_connection_test_delete_unavailable)
                            .setMessage(getString(R.string.delete_unavailable_confirm, toClear.size))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                for (profile in toClear) {
                                    adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
                                        val index = configurationIdList.indexOf(profile.id)
                                        if (index >= 0) {
                                            configurationIdList.removeAt(index)
                                            configurationList.remove(profile.id)
                                            notifyItemRemoved(index)
                                        }
                                    }
                                }
                                runOnDefaultDispatcher {
                                    for (profile in toClear) {
                                        ProfileManager.deleteProfile2(
                                            profile.groupId, profile.id
                                        )
                                    }
                                }
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }
                }
            }

            R.id.action_remove_duplicate -> runOnDefaultDispatcher {
                val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                val toClear = mutableListOf<ProxyEntity>()
                val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
                for (profile in profiles) {
                    if (!uniqueProxies.add(Protocols.Deduplication(profile))) {
                        toClear.add(profile)
                    }
                }
                if (toClear.isNotEmpty()) {
                    onMainDispatcher {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.action_remove_duplicate)
                            .setMessage(getString(R.string.remove_duplicate_confirm, toClear.size))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                for (profile in toClear) {
                                    adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
                                        val index = configurationIdList.indexOf(profile.id)
                                        if (index >= 0) {
                                            configurationIdList.removeAt(index)
                                            configurationList.remove(profile.id)
                                            notifyItemRemoved(index)
                                        }
                                    }
                                }
                                runOnDefaultDispatcher {
                                    for (profile in toClear) {
                                        ProfileManager.deleteProfile2(
                                            profile.groupId, profile.id
                                        )
                                    }
                                }
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }
                }
            }

            R.id.action_connection_icmp_ping -> {
                pingTest(true)
            }

            R.id.action_connection_tcp_ping -> {
                pingTest(false)
            }

            R.id.action_connection_url_test -> {
                urlTests()
            }
        }
        return true
    }

    inner class TestDialog {
        val binding = LayoutProgressListBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext()).setView(binding.root)
    }

    private fun pingTest(icmp: Boolean) {
        // Implementation
    }

    private fun urlTests() {
        // Implementation
    }

}
