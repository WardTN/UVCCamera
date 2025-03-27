package com.wardtn.uvccamera

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wardtn.uvccamera.utils.MMKVUtils
import com.wardtn.uvccamera.render.effect.bean.CameraEffect
import com.wardtn.uvccamera.utils.Utils
import com.wardtn.uvccamera.utils.imageloader.ImageLoaders

/**
 * Effect list dialog
 */
@SuppressLint("NotifyDataSetChanged")
class EffectListDialog(activity: Activity) : BaseDialog(activity, portraitWidthRatio = 1f),
    View.OnClickListener {

    private var mListener: OnEffectClickListener? = null
    private var mAdapter: EffectListAdapter? = null
    private var mRecyclerView: RecyclerView? = null
    private var mFilterTabBtn: TextView? = null
    private var mAnimTabBtn: TextView? = null
    private var mEffectList: ArrayList<CameraEffect> = ArrayList()
    private val mEffectMap = HashMap<Int, List<CameraEffect>>()

    override fun getContentLayoutId(): Int = R.layout.dialog_effects

    init {
        mDialog.window?.let {
            it.setGravity(Gravity.BOTTOM)
            it.setWindowAnimations(R.style.camera2_anim_down_to_top)

            it.attributes?.run {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = (200f / 360f * Utils.getScreenWidth(activity)).toInt()
                mDialog.window?.attributes = this
            }
        }

        mDialog.window?.setDimAmount(0f)
        setCanceledOnTouchOutside(true)
        setCancelable(true)
        // recycler view
//        mAdapter = EffectListAdapter().apply {
//            setOnItemChildClickListener { _, _, position ->
//                data.getOrNull(position)?.let {
//                    if (getCurrEffect()?.id == it.id) {
//                        return@setOnItemChildClickListener
//                    }
//                    setCurrEffect(it)
//                    mListener?.onEffectClick(it)
//                    notifyDataSetChanged()
//                }
//            }
//        }

        mAdapter = EffectListAdapter(object : EffectListAdapter.OnEffectClickListener {
            override fun onEffectClick(effect: CameraEffect) {
                // 处理点击事件
                if (mAdapter?.getCurrEffect()?.id == effect.id) {
                    return
                }

                mAdapter?.setCurrEffect(effect)
                mListener?.onEffectClick(effect)
                mAdapter?.notifyDataSetChanged()

            }
        })

        mRecyclerView = mDialog.findViewById(R.id.filterRv)
        mFilterTabBtn = mDialog.findViewById(R.id.tabFilterBtn)
        mAnimTabBtn = mDialog.findViewById(R.id.tabAnimBtn)
        mAnimTabBtn?.setOnClickListener(this)
        mFilterTabBtn?.setOnClickListener(this)
        mRecyclerView?.layoutManager =
            LinearLayoutManager(mDialog.context, LinearLayoutManager.HORIZONTAL, false)
        mRecyclerView?.adapter = mAdapter
    }

    fun setData(list: List<CameraEffect>, listener: OnEffectClickListener) {
        mListener = listener
        mEffectList.clear()
        mEffectList.addAll(list)
        initEffectData()
        initEffectTabs()
    }

    private fun getCurFilterId() = MMKVUtils.getInt(KEY_FILTER, CameraEffect.ID_NONE_FILTER)

    private fun getCurAnimationId() =
        MMKVUtils.getInt(KEY_ANIMATION, CameraEffect.ID_NONE_ANIMATION)

    private fun initEffectTabs() {
        getCurAnimationId().let { curAnimId ->
            if (curAnimId != CameraEffect.ID_NONE_ANIMATION) {
                mAnimTabBtn?.typeface = Typeface.DEFAULT_BOLD
                mAnimTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.black))
                mAnimTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0,
                    0,
                    0,
                    R.drawable.ic_tab_line_blue)
                mFilterTabBtn?.typeface = Typeface.DEFAULT
                mFilterTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.common_a8_black))
                mFilterTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0,
                    0,
                    0,
                    R.drawable.ic_tab_line_white)
                mAdapter?.setNewData(mEffectMap[CameraEffect.CLASSIFY_ID_ANIMATION])
                mAdapter?.setCurrEffect(mAdapter?.data?.find { it.id == curAnimId })
                return
            }
        }
        val curFilterId = getCurFilterId()
        mFilterTabBtn?.typeface = Typeface.DEFAULT_BOLD
        mFilterTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.black))
        mFilterTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.ic_tab_line_blue)
        mAnimTabBtn?.typeface = Typeface.DEFAULT
        mAnimTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.common_a8_black))
        mAnimTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.ic_tab_line_white)
        mAdapter?.setNewData(mEffectMap[CameraEffect.CLASSIFY_ID_FILTER])
        mAdapter?.setCurrEffect(mAdapter?.data?.find { it.id == curFilterId })
    }

    private fun initEffectData() {
        // filter list
        mEffectList.filter {
            it.classifyId == CameraEffect.CLASSIFY_ID_FILTER
        }.let {
            val list = ArrayList<CameraEffect>().apply {
                addAll(it)
            }
            mEffectMap[CameraEffect.CLASSIFY_ID_FILTER] = list
        }
        // animation list
        mEffectList.filter {
            it.classifyId == CameraEffect.CLASSIFY_ID_ANIMATION
        }.let {
            val list = ArrayList<CameraEffect>().apply {
                addAll(it)
            }
            mEffectMap[CameraEffect.CLASSIFY_ID_ANIMATION] = list
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.tabFilterBtn -> {
                mFilterTabBtn?.typeface = Typeface.DEFAULT_BOLD
                mFilterTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.black))
                mFilterTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0,
                    0,
                    0,
                    R.drawable.ic_tab_line_blue)
                mAnimTabBtn?.typeface = Typeface.DEFAULT
                mAnimTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.common_a8_black))
                mAnimTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0,
                    0,
                    0,
                    R.drawable.ic_tab_line_white)
                mAdapter?.setNewData(mEffectMap[CameraEffect.CLASSIFY_ID_FILTER])
                mAdapter?.setCurrEffect(mAdapter?.data?.find { it.id == getCurFilterId() })
            }
            R.id.tabAnimBtn -> {
                mAnimTabBtn?.typeface = Typeface.DEFAULT_BOLD
                mAnimTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.black))
                mAnimTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0,
                    0,
                    0,
                    R.drawable.ic_tab_line_blue)
                mFilterTabBtn?.typeface = Typeface.DEFAULT
                mFilterTabBtn?.setTextColor(getDialog().context.resources.getColor(R.color.common_a8_black))
                mFilterTabBtn?.setCompoundDrawablesWithIntrinsicBounds(0,
                    0,
                    0,
                    R.drawable.ic_tab_line_white)
                mAdapter?.setNewData(mEffectMap[CameraEffect.CLASSIFY_ID_ANIMATION])
                mAdapter?.setCurrEffect(mAdapter?.data?.find { it.id == getCurAnimationId() })
            }
            else -> {
            }
        }
    }

    interface OnEffectClickListener {
        fun onEffectClick(effect: CameraEffect)
    }

    companion object {
        const val KEY_FILTER = "filter"
        const val KEY_ANIMATION = "animation"
    }


    class EffectListAdapter(
        private val mListener: OnEffectClickListener?,
    ) : RecyclerView.Adapter<EffectListAdapter.EffectViewHolder>() {

        private var mCurrEffect: CameraEffect? = null
        var data: List<CameraEffect> = listOf()

        // 更新数据
        fun setNewData(newData: List<CameraEffect>?) {
            newData?.let {
                data = it
                notifyDataSetChanged() // 刷新整个列表
            }
        }

        // 设置当前效果
        fun setCurrEffect(effect: CameraEffect?) {
            val oldPosition = getPosition(mCurrEffect)
            mCurrEffect = effect
            val newPosition = getPosition(effect)
            if (oldPosition != newPosition) {
                if (oldPosition != -1) {
                    notifyItemChanged(oldPosition)
                }
                if (newPosition != -1) {
                    notifyItemChanged(newPosition)
                }
            }
        }

        fun getCurrEffect(): CameraEffect? = mCurrEffect

        private fun getPosition(cameraEffect: CameraEffect?): Int {
            var position = -1
            cameraEffect ?: return position
            data.forEachIndexed { index, filter ->
                if (filter.id == cameraEffect.id) {
                    position = index
                    return@forEachIndexed
                }
            }
            return position
        }

        // 创建 ViewHolder
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_effect_item, parent, false)
            return EffectViewHolder(view)
        }

        // 绑定数据到 ViewHolder
        override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
            val item = data[position]
            holder.bind(item)

            // 更新选中状态
            val isCheck = mCurrEffect?.id == item.id
            val textColor = if (isCheck) 0xFF2E5BFF else 0xFF232325
            holder.effectName.setTextColor(textColor.toInt())
            holder.effectCheckIv.visibility = if (isCheck) View.VISIBLE else View.GONE

            // 点击效果图标处理
            holder.effectIv.setOnClickListener {
                data.getOrNull(position)?.let {
                    if (getCurrEffect()?.id == it.id) {
                        return@setOnClickListener
                    }
                    setCurrEffect(it)
                    mListener?.onEffectClick(it) // 回调点击事件
                    notifyDataSetChanged() // 更新整个列表
                }
            }
        }

        override fun getItemCount(): Int = data.size

        // 自定义 ViewHolder
        class EffectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val effectName: TextView = view.findViewById(R.id.effectName)
            val effectIv: ImageView = view.findViewById(R.id.effectIv)
            val effectCheckIv: ImageView = view.findViewById(R.id.effectCheckIv)

            fun bind(item: CameraEffect) {
                effectName.text = item.name

                // 图片加载逻辑
                item.coverResId?.let {
                    ImageLoaders.of(itemView.context)
                        .loadCircle(effectIv, it, R.drawable.effect_none)
                } ?: run {
                    ImageLoaders.of(itemView.context)
                        .loadCircle(effectIv, item.coverUrl, R.drawable.effect_none)
                }
            }
        }

        // 定义点击回调接口
        interface OnEffectClickListener {
            fun onEffectClick(effect: CameraEffect)
        }
    }
}