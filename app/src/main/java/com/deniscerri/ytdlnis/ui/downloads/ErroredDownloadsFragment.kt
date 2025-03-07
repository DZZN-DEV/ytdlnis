package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadBottomSheetDialog
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.Extensions.enableFastScroll
import com.deniscerri.ytdlnis.util.Extensions.forceFastScrollMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class ErroredDownloadsFragment : Fragment(), GenericDownloadAdapter.OnItemClickListener, OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var erroredRecyclerView : RecyclerView
    private lateinit var noResults : RelativeLayout
    private lateinit var adapter : GenericDownloadAdapter
    private var actionMode : ActionMode? = null
    private var totalSize: Int = 0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.generic_list, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter =
            GenericDownloadAdapter(
                this,
                requireActivity()
            )

        noResults = view.findViewById(R.id.no_results)
        erroredRecyclerView = view.findViewById(R.id.download_recyclerview)
        erroredRecyclerView.forceFastScrollMode()
        erroredRecyclerView.adapter = adapter
        erroredRecyclerView.enableFastScroll()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("errored")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(erroredRecyclerView)
        }
        erroredRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        lifecycleScope.launch {
            downloadViewModel.erroredDownloads.collectLatest {
                adapter.submitData(it)
            }
        }

        downloadViewModel.getTotalSize(listOf(DownloadRepository.Status.Error)).observe(viewLifecycleOwner){
            totalSize = it
            noResults.visibility = if (it == 0) View.VISIBLE else View.GONE
        }
    }

    override fun onActionButtonClick(itemID: Long) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }
            if (item.logID != null) {
                actionMode?.finish()
                val bundle = Bundle()
                bundle.putLong("logID", item.logID!!)
                findNavController().navigate(
                    R.id.downloadLogFragment,
                    bundle
                )
            }
        }
    }

    override fun onCardClick(itemID: Long) {
       lifecycleScope.launch {
           val item = withContext(Dispatchers.IO){
               downloadViewModel.getItemByID(itemID)
           }

           UiUtil.showDownloadItemDetailsCard(
               item,
               requireActivity(),
               DownloadRepository.Status.valueOf(item.status),
               removeItem = { it: DownloadItem, sheet: BottomSheetDialog ->
                   removeItem(it, sheet)

               },
               downloadItem = {
                   runBlocking{
                       downloadViewModel.queueDownloads(listOf(it))
                   }
               },
               longClickDownloadButton = {
                   findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                       Pair("downloadItem", it),
                       Pair("result", downloadViewModel.createResultItemFromDownload(it)),
                       Pair("type", it.type)
                   ))
               },
               scheduleButtonClick = {}
           )
       }
    }

    override fun onCardSelect(isChecked: Boolean, position: Int) {
        lifecycleScope.launch {
            val selectedObjects = adapter.getSelectedObjectsCount(totalSize)
            if (isChecked) {
                if (actionMode == null){
                    actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)

                }else{
                    actionMode!!.title = "$selectedObjects ${getString(R.string.selected)}"
                }
            }
            else {
                actionMode?.title = "$selectedObjects ${getString(R.string.selected)}"
                if (selectedObjects == 0){
                    actionMode?.finish()
                }
            }
        }
    }

    private fun removeItem(item: DownloadItem, bottomSheet: BottomSheetDialog?){
        bottomSheet?.hide()
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            downloadViewModel.deleteDownload(item.id)
        }
        deleteDialog.show()
    }

    override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        TODO("Not yet implemented")
    }



    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.cancelled_downloads_menu_context, menu)
            mode.title = "${adapter.getSelectedObjectsCount(totalSize)} ${getString(R.string.selected)}"
            return true
        }

        override fun onPrepareActionMode(
            mode: ActionMode?,
            menu: Menu?
        ): Boolean {
            return false
        }

        override fun onActionItemClicked(
            mode: ActionMode?,
            item: MenuItem?
        ): Boolean {
            return when (item!!.itemId) {
                R.id.delete_results -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        lifecycleScope.launch {
                            val selectedObjects = if (adapter.inverted || adapter.checkedItems.isEmpty()){
                                withContext(Dispatchers.IO){
                                    downloadViewModel.getItemIDsNotPresentIn(adapter.checkedItems, listOf(
                                        DownloadRepository.Status.Error))
                                }
                            }else{
                                adapter.checkedItems.toList()
                            }
                            adapter.clearCheckedItems()
                            downloadViewModel.deleteAllWithID(selectedObjects)
                            actionMode?.finish()
                        }
                    }
                    deleteDialog.show()
                    true
                }
                R.id.redownload -> {
                    lifecycleScope.launch {
                        val selectedObjects = if (adapter.inverted || adapter.checkedItems.isEmpty()){
                            withContext(Dispatchers.IO){
                                downloadViewModel.getItemIDsNotPresentIn(adapter.checkedItems, listOf(
                                    DownloadRepository.Status.Error))
                            }
                        }else{
                            adapter.checkedItems.toList()
                        }
                        adapter.clearCheckedItems()
                        withContext(Dispatchers.IO){
                            downloadViewModel.reQueueDownloadItems(selectedObjects)
                        }
                        actionMode?.finish()
                    }
                    true
                }
                R.id.select_all -> {
                    adapter.checkAll()
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    adapter.invertSelected()
                    val selectedObjects = adapter.getSelectedObjectsCount(totalSize)
                    actionMode!!.title = "$selectedObjects ${getString(R.string.selected)}"
                    if (selectedObjects == 0) actionMode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            adapter.clearCheckedItems()
        }
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val itemID = viewHolder.itemView.tag.toString().toLong()
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.RIGHT -> {
                        lifecycleScope.launch {
                            val item = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(itemID)
                            }
                            downloadViewModel.queueDownloads(listOf(item), true)
                            adapter.notifyItemChanged(position)
                        }
                    }
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(itemID)
                            }
                            downloadViewModel.deleteDownload(deletedItem.id)
                            Snackbar.make(erroredRecyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.undo)) {
                                    downloadViewModel.insert(deletedItem)
                                }.show()
                        }

                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    requireContext(),
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .addSwipeRightBackgroundColor(
                        MaterialColors.getColor(
                            requireContext(),
                            R.attr.colorOnSurfaceInverse, Color.TRANSPARENT
                        )
                    )
                    .addSwipeRightActionIcon(R.drawable.ic_refresh)
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

}