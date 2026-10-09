package com.report12321.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.report12321.app.R
import com.report12321.app.model.SpamItem
import com.report12321.app.model.SpamType

/**
 * 骚扰信息列表适配器
 *
 * 支持多选模式，用户可以批量选择要举报的骚扰信息
 */
class SpamItemAdapter : RecyclerView.Adapter<SpamItemAdapter.ViewHolder>() {

    private val items = mutableListOf<SpamItem>()
    private val selectedItems = mutableSetOf<Long>()

    var onItemClickListener: ((SpamItem, Boolean) -> Unit)? = null
    var onItemLongClickListener: ((SpamItem) -> Unit)? = null
    var isMultiSelectMode = false

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val iconType: ImageView = view.findViewById(R.id.icon_type)
        val textPhone: TextView = view.findViewById(R.id.text_phone)
        val textContent: TextView = view.findViewById(R.id.text_content)
        val textTime: TextView = view.findViewById(R.id.text_time)
        val textSpamType: TextView = view.findViewById(R.id.text_spam_type)
        val textSpamLevel: TextView = view.findViewById(R.id.text_spam_level)
        val textCallInfo: TextView = view.findViewById(R.id.text_call_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spam, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 类型图标
        when (item.type) {
            SpamType.SMS -> holder.iconType.setImageResource(R.drawable.ic_sms)
            SpamType.CALL -> holder.iconType.setImageResource(R.drawable.ic_call)
        }

        // 号码
        holder.textPhone.text = item.phoneNumber

        // 内容
        holder.textContent.text = item.content

        // 时间
        holder.textTime.text = item.time

        // 骚扰类型标签
        holder.textSpamType.text = item.getSpamTypeName()

        // 骚扰等级
        holder.textSpamLevel.text = item.getSpamLevelText()

        // 通话信息（仅电话类型显示）
        if (item.type == SpamType.CALL && item.callDuration.isNotEmpty()) {
            holder.textCallInfo.visibility = View.VISIBLE
            holder.textCallInfo.text = "${item.callType} | ${item.callDuration}"
        } else {
            holder.textCallInfo.visibility = View.GONE
        }

        // 多选框
        if (isMultiSelectMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.isChecked = selectedItems.contains(item.id)
        } else {
            holder.checkbox.visibility = View.GONE
        }

        // 点击事件
        holder.itemView.setOnClickListener {
            if (isMultiSelectMode) {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
                if (holder.checkbox.isChecked) {
                    selectedItems.add(item.id)
                } else {
                    selectedItems.remove(item.id)
                }
            } else {
                onItemClickListener?.invoke(item, true)
            }
        }

        // 长按进入多选模式
        holder.itemView.setOnLongClickListener {
            if (!isMultiSelectMode) {
                isMultiSelectMode = true
                notifyDataSetChanged()
            }
            holder.checkbox.isChecked = !holder.checkbox.isChecked
            if (holder.checkbox.isChecked) {
                selectedItems.add(item.id)
            } else {
                selectedItems.remove(item.id)
            }
            onItemLongClickListener?.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * 更新数据
     */
    fun updateData(newItems: List<SpamItem>) {
        items.clear()
        items.addAll(newItems)
        selectedItems.clear()
        isMultiSelectMode = false
        notifyDataSetChanged()
    }

    /**
     * 获取选中的项目
     */
    fun getSelectedItems(): List<SpamItem> {
        return items.filter { selectedItems.contains(it.id) }
    }

    /**
     * 全选
     */
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(items.map { it.id })
        notifyDataSetChanged()
    }

    /**
     * 退出多选模式
     */
    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedItems.clear()
        notifyDataSetChanged()
    }

    /**
     * 选中数量
     */
    fun getSelectedCount(): Int = selectedItems.size
}
