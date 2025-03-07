package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.database.dao.HistoryDao
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.util.FileUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.deniscerri.ytdlnis.database.DBManager.SORTING

class HistoryRepository(private val historyDao: HistoryDao) {
    val items : Flow<List<HistoryItem>> = historyDao.getAllHistory()

    enum class HistorySortType {
        DATE, TITLE, AUTHOR, FILESIZE
    }

    fun getItem(id: Long) : HistoryItem {
        return historyDao.getHistoryItem(id)
    }

    fun getAll() : List<HistoryItem> {
        return historyDao.getAllHistoryList()
    }

    fun getAllByURL(url: String) : List<HistoryItem> {
        return historyDao.getAllHistoryByURL(url)
    }

    fun getFiltered(query : String, format : String, site : String, sortType: HistorySortType, sort: SORTING) : List<HistoryItem> {
        return when(sortType){
            HistorySortType.DATE ->  historyDao.getHistorySortedByID(query, format, site, sort.toString())
            HistorySortType.TITLE ->  historyDao.getHistorySortedByTitle(query, format, site, sort.toString())
            HistorySortType.AUTHOR ->  historyDao.getHistorySortedByAuthor(query, format, site, sort.toString())
            HistorySortType.FILESIZE ->  {
                val items = historyDao.getHistorySortedByID(query, format, site, sort.toString())
                when(sort){
                    SORTING.DESC -> items.sortedByDescending { it.format.filesize }
                    SORTING.ASC -> items.sortedBy { it.format.filesize }
                }
            }
        }
    }

    suspend fun insert(item: HistoryItem){
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryItem, deleteFile: Boolean){
        historyDao.delete(item.id)
        if (deleteFile){
            FileUtil.deleteFile(item.downloadPath)
        }
    }

    suspend fun deleteAll(deleteFile: Boolean = false){
        if (deleteFile){
            historyDao.getAllHistoryList().forEach { item ->
                FileUtil.deleteFile(item.downloadPath)
            }
        }
        historyDao.deleteAll()
    }

    suspend fun deleteDuplicates(){
        historyDao.deleteDuplicates()
    }

    suspend fun update(item: HistoryItem){
        historyDao.update(item)
    }

    suspend fun clearDeletedHistory(){
        items.collectLatest {
            it.forEach { item ->
                if (!FileUtil.exists(item.downloadPath)){
                    historyDao.delete(item.id)
                }
            }
        }
    }

}