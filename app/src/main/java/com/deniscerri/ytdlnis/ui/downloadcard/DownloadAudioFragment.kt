package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.Log
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadAudioFragment(private var resultItem: ResultItem? = null, private var currentDownloadItem: DownloadItem? = null, private var url: String = "") : Fragment(), GUISync {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel : ResultViewModel
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView
    private lateinit var infoUtil: InfoUtil
    private lateinit var genericAudioFormats: MutableList<Format>

    lateinit var downloadItem : DownloadItem
    lateinit var title : TextInputLayout
    lateinit var author : TextInputLayout
    lateinit var preferences: SharedPreferences
    lateinit var shownFields: List<String>
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_download_audio, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        infoUtil = InfoUtil(requireContext())
        genericAudioFormats = infoUtil.getGenericAudioFormats(requireContext().resources)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        shownFields = preferences.getStringSet("modify_download_card", requireContext().getStringArray(R.array.modify_download_card_values).toSet())!!.toList()
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO) {
                if (currentDownloadItem != null){
                    //object cloning
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, url, Type.audio)
                }
            }
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            try {
                title = view.findViewById(R.id.title_textinput)
                title.visibility = if (shownFields.contains("title")) View.VISIBLE else View.GONE
                if (title.editText?.text?.isEmpty() == true){
                    title.editText!!.setText(downloadItem.title)
                    title.endIconMode = END_ICON_NONE
                }
                title.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.title = p0.toString()
                    }
                })

                author = view.findViewById(R.id.author_textinput)
                author.visibility = if (shownFields.contains("author")) View.VISIBLE else View.GONE
                if (author.editText?.text?.isEmpty() == true){
                    author.editText!!.setText(downloadItem.author)
                    author.endIconMode = END_ICON_NONE
                }
                author.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.author = p0.toString()
                    }
                })

                if (savedInstanceState?.containsKey("updated") == true){
                    if (!listOf(resultItem?.title, downloadItem.title).contains(title.editText?.text.toString())){
                        title.endIconMode = TextInputLayout.END_ICON_CUSTOM
                        title.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_refresh)
                        downloadItem.title = title.editText?.text.toString()
                    }

                    if (!listOf(resultItem?.author, downloadItem.author).contains(author.editText?.text.toString())){
                        author.endIconMode = TextInputLayout.END_ICON_CUSTOM
                        author.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_refresh)
                        downloadItem.author = author.editText?.text.toString()
                    }
                }

                title.setEndIconOnClickListener {
                    if (resultItem != null){
                        title.editText?.setText(resultItem?.title)
                    }
                    title.endIconMode = END_ICON_NONE
                }

                author.setEndIconOnClickListener {
                    if (resultItem != null){
                        author.editText?.setText(resultItem?.author)
                    }
                    author.endIconMode = END_ICON_NONE
                }


                saveDir = view.findViewById(R.id.outputPath)
                saveDir.editText!!.setText(
                    FileUtil.formatPath(downloadItem.downloadPath)
                )
                saveDir.editText!!.isFocusable = false
                saveDir.editText!!.isClickable = true
                saveDir.editText!!.setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

                    pathResultLauncher.launch(intent)
                }
                freeSpace = view.findViewById(R.id.freespace)
                val free = FileUtil.convertFileSize(
                    File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
                freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
                if (free == "?") freeSpace.visibility = View.GONE

                var formats = mutableListOf<Format>()
                if (currentDownloadItem == null) {
                    formats.addAll(resultItem?.formats?.filter { it.format_note.contains("audio", ignoreCase = true) } ?: listOf())
                }else{
                    //if its updating a present downloaditem and its the wrong category
                    if (currentDownloadItem!!.type != Type.audio){
                        downloadItem.type = Type.audio
                        runCatching {
                            downloadItem.format = downloadViewModel.getFormat(downloadItem.allFormats, Type.audio)
                        }.onFailure {
                            downloadItem.format = genericAudioFormats.last()
                        }
                    }
                }
                if (formats.isEmpty()) formats.addAll(downloadItem.allFormats.filter { it.format_note.contains("audio", ignoreCase = true) })

                val containers = requireContext().resources.getStringArray(R.array.audio_containers)
                var containerPreference = sharedPreferences.getString("audio_format", "Default")
                if (containerPreference == "Default") containerPreference = getString(R.string.defaultValue)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                container.visibility = if (shownFields.contains("container")) View.VISIBLE else View.GONE
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)

                if (formats.isEmpty()) formats = genericAudioFormats

                val formatCard = view.findViewById<MaterialCardView>(R.id.format_card_constraintLayout)
                val chosenFormat = downloadItem.format
                UiUtil.populateFormatCard(requireContext(), formatCard, chosenFormat, null)
                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(item: List<FormatTuple>) {
                        downloadItem.format = item.first().format
                        UiUtil.populateFormatCard(requireContext(), formatCard, item.first().format, null)
                    }

                    override fun onFormatsUpdated(allFormats: List<List<Format>>) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            resultItem?.apply {
                                this.formats.removeAll(formats.toSet())
                                this.formats.addAll(allFormats.first().filter { !genericAudioFormats.contains(it) })
                                resultViewModel.update(this)
                                kotlin.runCatching {
                                    val f1 = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                                    f1.updateUI(this)
                                }
                            }
                        }
                        formats = allFormats.first().filter { !genericAudioFormats.contains(it) }.toMutableList()
                        formats.removeAll(genericAudioFormats)
                        val preferredFormat = downloadViewModel.getFormat(formats, Type.audio)
                        downloadItem.format = preferredFormat
                        downloadItem.allFormats = formats
                        UiUtil.populateFormatCard(requireContext(), formatCard, preferredFormat, null)
                    }
                }
                formatCard.setOnClickListener{
                    if (parentFragmentManager.findFragmentByTag("formatSheet") == null){
                        val bottomSheet = FormatSelectionBottomSheetDialog(listOf(downloadItem), listOf(formats.ifEmpty { genericAudioFormats }), listener)
                        bottomSheet.show(parentFragmentManager, "formatSheet")
                    }
                }
                formatCard.setOnLongClickListener {
                    UiUtil.showFormatDetails(downloadItem.format, requireActivity())
                    true
                }


                container?.isEnabled = true
                containerAutoCompleteTextView?.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        containers
                    )
                )

                if (currentDownloadItem == null || !containers.contains(downloadItem.container)){
                    downloadItem.container = if (containerPreference == getString(R.string.defaultValue)) "" else containerPreference!!
                }
                containerAutoCompleteTextView.setText(downloadItem.container.ifEmpty { getString(R.string.defaultValue) }, false)

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.container = containers[index]
                        if (containers[index] == getString(R.string.defaultValue)) downloadItem.container = ""
                    }


                view.findViewById<LinearLayout>(R.id.adjust).apply {
                    visibility = if (shownFields.contains("adjust_audio")) View.VISIBLE else View.GONE
                    if (isVisible){
                        UiUtil.configureAudio(
                            view,
                            requireActivity(),
                            listOf(downloadItem),
                            embedThumbClicked = {
                                downloadItem.audioPreferences.embedThumb = it
                            },
                            cropThumbClicked = {
                                downloadItem.audioPreferences.cropThumb = it
                            },
                            splitByChaptersClicked = {
                                downloadItem.audioPreferences.splitByChapters = it
                            },
                            filenameTemplateSet = {
                                downloadItem.customFileNameTemplate = it
                            },
                            sponsorBlockItemsSet = { values, checkedItems ->
                                downloadItem.audioPreferences.sponsorBlockFilters.clear()
                                for (i in checkedItems.indices) {
                                    if (checkedItems[i]) {
                                        downloadItem.audioPreferences.sponsorBlockFilters.add(values[i])
                                    }
                                }
                            },
                            cutClicked = {cutVideoListener ->
                                if (parentFragmentManager.findFragmentByTag("cutVideoSheet") == null){
                                    val bottomSheet = CutVideoBottomSheetDialog(downloadItem, resultItem?.urls ?: "", resultItem?.chapters ?: listOf(), cutVideoListener)
                                    bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                                }
                            },
                            updateDataClicked = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    resultItem?.apply {
                                        resultViewModel.updateItemData(this)
                                    }
                                }
                            },
                            extraCommandsClicked = {
                                val callback = object : ExtraCommandsListener {
                                    override fun onChangeExtraCommand(c: String) {
                                        downloadItem.extraCommands = c
                                    }
                                }

                                val bottomSheetDialog = AddExtraCommandsDialog(downloadItem, callback)
                                bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                            }
                        )
                    }
                }
            }catch (e : Exception){
                e.printStackTrace()
            }
        }
    }

    override fun updateTitleAuthor(t: String, a: String){
        downloadItem.title = t
        downloadItem.author = a
        title.editText?.setText(t)
        title.endIconMode = END_ICON_NONE
        author.editText?.setText(a)
        author.endIconMode = END_ICON_NONE
    }

    override fun updateUI(res: ResultItem?){
        resultItem = res
        val state = Bundle()
        state.putBoolean("updated", true)
        onViewCreated(requireView(),savedInstanceState = state)
    }

    private var pathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            downloadItem.downloadPath = result.data?.data.toString()
            //downloadViewModel.updateDownload(downloadItem)
            saveDir.editText?.setText(FileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)

            val free = FileUtil.convertFileSize(
                File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
            freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
            if (free == "?") freeSpace.visibility = View.GONE

        }
    }
}