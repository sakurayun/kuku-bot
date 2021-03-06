package me.kuku.yuq.controller

import com.IceCreamQAQ.Yu.annotation.*
import com.IceCreamQAQ.Yu.util.IO
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.icecreamqaq.yuq.*
import com.icecreamqaq.yuq.annotation.GroupController
import com.icecreamqaq.yuq.annotation.PathVar
import com.icecreamqaq.yuq.annotation.QMsg
import com.icecreamqaq.yuq.controller.BotActionContext
import com.icecreamqaq.yuq.controller.ContextSession
import com.icecreamqaq.yuq.controller.QQController
import com.icecreamqaq.yuq.message.Face
import com.icecreamqaq.yuq.message.Image
import com.icecreamqaq.yuq.message.Message
import com.icecreamqaq.yuq.message.Text
import com.icecreamqaq.yuq.mirai.MiraiBot
import me.kuku.yuq.entity.QQGroupEntity
import me.kuku.yuq.logic.ToolLogic
import me.kuku.yuq.logic.WeiboLogic
import me.kuku.yuq.service.QQGroupService
import me.kuku.yuq.utils.OkHttpClientUtils
import me.kuku.yuq.utils.removeSuffixLine
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

@GroupController
class ManagerController: QQController() {
    @Config("YuQ.Mirai.bot.master")
    private lateinit var master: String
    @Inject
    private lateinit var qqGroupService: QQGroupService
    @Inject
    private lateinit var miraiBot: MiraiBot
    @Inject
    private lateinit var toolLogic: ToolLogic
    @Inject
    private lateinit var weiboLogic: WeiboLogic

    private val version = "v1.4.7"

    @Before
    fun before(group: Long, qq: Long, actionContext: BotActionContext, message: Message){
        var qqGroupEntity = qqGroupService.findByGroup(group)
        if (qqGroupEntity == null){
            qqGroupEntity = QQGroupEntity(null, group)
            qqGroupService.save(qqGroupEntity)
        }
        actionContext.session["qqGroupEntity"] = qqGroupEntity
        val msg = message.toPath()[0]
        val whiteList = arrayOf("问答", "违规词", "黑名单", "白名单", "开关")
        val adminWhiteList = qqGroupEntity.getAllowedCommandsJsonArray()
        if (!whiteList.contains(msg)) {
            val adminJsonArray = qqGroupEntity.getAdminJsonArray()
            if (!adminJsonArray.contains(qq.toString()) || !adminWhiteList.contains(msg)) {
                if (qq != master.toLong()) throw mif.at(qq).plus("抱歉，您的权限不足，无法执行！！")
            }
        }
    }

    @Action("加管 {qqNo}")
    fun setAdmin(qqNo: Long, qqGroupEntity: QQGroupEntity): Message{
        val jsonArray = qqGroupEntity.getAdminJsonArray()
        jsonArray.add(qqNo.toString())
        qqGroupEntity.adminList = jsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return mif.text("设置").plus(mif.at(qqNo)).plus("为管理员成功！！")
    }

    @Action("去管 {qqNo}")
    fun cancelAdmin(qqNo: Long, qqGroupEntity: QQGroupEntity): Message{
        val jsonArray = qqGroupEntity.getAdminJsonArray()
        jsonArray.remove(qqNo.toString())
        qqGroupEntity.adminList = jsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return mif.text("取消").plus(mif.at(qqNo)).plus("的管理员成功！！")
    }

    @Action("查管")
    fun queryAdmin(qqGroupEntity: QQGroupEntity): String{
        val jsonArray = qqGroupEntity.getAdminJsonArray()
        val sb = StringBuilder().appendln("本群机器人的管理员如下：")
        jsonArray.forEach { sb.appendln(it) }
        return sb.removeSuffixLine().toString()
    }

    @Action("加admin命令 {command}")
    fun addCommands(command: String, qqGroupEntity: QQGroupEntity): String{
        val jsonArray = qqGroupEntity.getAllowedCommandsJsonArray()
        jsonArray.add(command)
        qqGroupEntity.allowedCommandsList = jsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "添加管理员${command}的命令权限成功！！"
    }

    @Action("删admin命令 {command}")
    fun delCommands(command: String, qqGroupEntity: QQGroupEntity): String{
        val jsonArray = qqGroupEntity.getAllowedCommandsJsonArray()
        jsonArray.remove(command)
        qqGroupEntity.allowedCommandsList = jsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "删除管理员${command}的命令权限成功！！"
    }

    @Action("查admin命令")
    fun queryCommands(qqGroupEntity: QQGroupEntity): String{
        val jsonArray = qqGroupEntity.getAllowedCommandsJsonArray()
        val sb = StringBuilder().appendln("本群管理员拥有的管理命令使用权限如下：")
        jsonArray.forEach { sb.appendln(it) }
        return sb.removeSuffixLine().toString()
    }

    @Action("机器人 {status}")
    @QMsg(at = true)
    fun switchGroup(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.status = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "机器人开启成功" else "机器人关闭成功"
    }

    @Action("复读 {status}")
    @QMsg(at = true)
    fun repeat(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.repeat = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "复读开启成功" else "复读关闭成功"
    }

    @Action("wbmonitor {name}")
    @Synonym(["微博监控 {name}"])
    @QMsg(at = true)
    fun wbMonitor(name: String, qqGroupEntity: QQGroupEntity): String{
        val commonResult = weiboLogic.getIdByName(name)
        val weiboPojo = commonResult.t?.get(0) ?: return commonResult.msg
        val weiboJsonArray = qqGroupEntity.getWeiboJsonArray()
        val jsonObject = JSONObject()
        jsonObject["name"] = weiboPojo.name
        jsonObject["id"] = weiboPojo.userId
        weiboJsonArray.add(jsonObject)
        qqGroupEntity.weiboList = weiboJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "微博监控添加成功！！！"
    }

    @Action("删微博监控 {name}")
    @QMsg(at = true)
    fun delWbMonitor(name: String, qqGroupEntity: QQGroupEntity): String{
        val weiboJsonArray = qqGroupEntity.getWeiboJsonArray()
        val list = mutableListOf<JSONObject>()
        for (i in weiboJsonArray.indices) {
            val jsonObject = weiboJsonArray.getJSONObject(i)
            if (jsonObject.getString("name") == name) list.add(jsonObject)
        }
        for (jsonObject in list){
            weiboJsonArray.remove(jsonObject)
        }
        qqGroupEntity.weiboList = weiboJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "删除微博监控成功"
    }

    @Action("查微博监控")
    @QMsg(at = true)
    fun queryWbMonitor(qqGroupEntity: QQGroupEntity): String{
        val sb = StringBuilder().appendln("该群微博监控如下：")
        val weiboJsonArray = qqGroupEntity.getWeiboJsonArray()
        for (i in weiboJsonArray.indices){
            val jsonObject = weiboJsonArray.getJSONObject(i)
            sb.appendln("${jsonObject.getString("name")}-${jsonObject.getString("id")}")
        }
        return sb.removeSuffixLine().toString()
    }

    @Action("违规次数 {count}")
    @QMsg(at = true)
    fun maxViolationCount(qqGroupEntity: QQGroupEntity, count: Int): String{
        qqGroupEntity.maxViolationCount = count
        qqGroupService.save(qqGroupEntity)
        return "已设置本群最大违规次数为${count}次"
    }

    @Action("开关")
    @QMsg(at = true)
    fun kai(qqGroupEntity: QQGroupEntity): String{
        val sb = StringBuilder("本群开关情况如下：\n")
        sb.appendln("音乐：${qqGroupEntity.musicType}")
        sb.appendln("色图：" + this.boolToStr(qqGroupEntity.colorPic) + "、" + qqGroupEntity.colorPicType)
        sb.appendln("鉴黄：" + this.boolToStr(qqGroupEntity.pic))
        sb.appendln("嘴臭：" + this.boolToStr(qqGroupEntity.mouthOdor))
        sb.appendln("龙王：" + this.boolToStr(qqGroupEntity.dragonKing))
        sb.appendln("复读：" + this.boolToStr(qqGroupEntity.repeat))
        sb.appendln("欢迎语：" + this.boolToStr(qqGroupEntity.welcomeMsg))
        sb.appendln("qq功能：" + this.boolToStr(qqGroupEntity.qqStatus))
        sb.appendln("退群拉黑：" + this.boolToStr(qqGroupEntity.leaveGroupBlack))
        sb.appendln("萌宠功能：" + this.boolToStr(qqGroupEntity.superCute))
        sb.appendln("自动审核：" + this.boolToStr(qqGroupEntity.autoReview))
        sb.appendln("撤回通知：" + this.boolToStr(qqGroupEntity.recall))
        sb.appendln("整点报时：" + this.boolToStr(qqGroupEntity.onTimeAlarm))
        sb.append("最大违规次数：${qqGroupEntity.maxViolationCount ?: "5次"}")
        return sb.toString()
    }

    @Action("重启mirai")
    fun robot(){
        miraiBot.stop()
        miraiBot.init()
        miraiBot.start()
    }

    @Action("涩图切换 {type}")
    @QMsg(at = true)
    fun colorPicType(qqGroupEntity: QQGroupEntity, type: String): String?{
        var colorPicType = qqGroupEntity.colorPicType
        var status = true
        when (type){
            "native", "r-18", "danbooru" -> colorPicType = type
            else -> status = false
        }
        return if (status){
            qqGroupEntity.colorPicType = colorPicType
            qqGroupService.save(qqGroupEntity)
            "涩图切换${type}成功"
        }else {
            qqGroupEntity.colorPic = false
            qqGroupService.save(qqGroupEntity)
            "涩图关闭成功！！"
        }
    }

    @Action("整点报时 {status}")
    @QMsg(at = true)
    fun onTimeAlarm(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.onTimeAlarm = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "整点报时开启成功" else "整点报时关闭成功"
    }

    @Action("自动审核 {status}")
    @QMsg(at = true)
    fun autoReview(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.autoReview = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "自动审核开启成功" else "自动审核关闭成功"
    }

    @Action("#龙王 {status}")
    @QMsg(at = true)
    fun dragonKing(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.dragonKing = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "迫害龙王开启成功" else "迫害龙王关闭成功"
    }

    @Action("#qq {status}")
    @QMsg(at = true)
    fun qqStatus(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.qqStatus = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "qq功能开启成功" else "qq功能关闭成功"
    }

    @Action("欢迎语 {status}")
    @QMsg(at = true)
    fun welcome(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.welcomeMsg = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "欢迎语开启成功" else "欢迎语关闭成功"
    }

    @Action("萌宠 {status}")
    @QMsg(at = true)
    fun superCute(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.superCute = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "萌宠功能开启成功" else "萌宠功能关闭成功"
    }

    @Action("退群拉黑 {status}")
    @QMsg(at = true)
    fun leaveGroupBlack(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.leaveGroupBlack = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "退群拉黑已开启！！" else "退群拉黑已关闭！！"
    }

    @Action("#嘴臭 {status}")
    @QMsg(at = true)
    fun mouthOdor(qqGroupEntity: QQGroupEntity, status: Boolean): String{
        qqGroupEntity.mouthOdor = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "嘴臭（祖安语录）已开启！！" else "嘴臭（祖安语录）已关闭！！"
    }

    @Action("鉴黄 {open}")
    @QMsg(at = true)
    fun pic(qqGroupEntity: QQGroupEntity, open: Boolean): String{
        qqGroupEntity.pic = open
        qqGroupService.save(qqGroupEntity)
        return if (open) "鉴黄已开启！！" else "鉴黄已关闭！！"
    }

    @Action("禁言 {qqNo}")
    @QMsg(at = true)
    fun shutUp(group: Long, qqNo: Long, @PathVar(2) timeStr: String?): String{
        val time = if (timeStr == null) 0
        else {
            if (timeStr.length == 1) return "未发现时间单位！！单位可为（s,m,h,d）"
            val num = timeStr.substring(0, timeStr.length - 1).toInt()
            when (timeStr[timeStr.length - 1]) {
                's' -> num
                'm' -> num * 60
                'h' -> num * 60 * 60
                'd' -> num * 60 * 60 * 24
                else -> return "禁言时间格式不正确"
            }
        }
        yuq.groups[group]?.get(qqNo)?.ban(time)
        return "禁言成功！！"
    }

    @Action("t {qqNo}")
    @QMsg(at = true)
    fun kick(qqNo: Long, group: Long): String{
        yuq.groups[group]?.get(qqNo)?.kick()
        return "踢出成功！！"
    }

    @Action("{act}违规词")
    @QMsg(at = true)
    fun addKey(message: Message, act: String, qqGroupEntity: QQGroupEntity): String?{
        val keywordJsonArray = qqGroupEntity.getKeywordJsonArray()
        val list = message.toPath().toMutableList()
        list.removeAt(0)
        val msg = when (act) {
            "加" -> {
                list.forEach { keywordJsonArray.add(it) }

                "加违规词成功！！"
            }
            "去" -> {
                list.forEach { keywordJsonArray.remove(it) }
                "去违规词成功！！"
            }
            else -> null
        }
        return if (msg != null) {
            qqGroupEntity.keyword = keywordJsonArray.toString()
            qqGroupService.save(qqGroupEntity)
            msg
        }else null
    }

    @Action("加黑 {qqNo}")
    @QMsg(at = true)
    fun addBlack(qqNo: Long, qqGroupEntity: QQGroupEntity, group: Long): String{
        val blackJsonArray = qqGroupEntity.getBlackJsonArray()
        blackJsonArray.add(qqNo.toString())
        qqGroupEntity.blackList = blackJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        val members = yuq.groups[group]?.members
        if (members!!.containsKey(qqNo))
            this.kick(qqNo, group)
        return "加黑名单成功！！"
    }

    @Action("去黑 {qqNo}")
    @QMsg(at = true)
    fun delBlack(qqNo: Long, qqGroupEntity: QQGroupEntity): String{
        val blackJsonArray = qqGroupEntity.getBlackJsonArray()
        blackJsonArray.remove(qqNo.toString())
        qqGroupEntity.blackList = blackJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "删除黑名单成功！！"
    }

    @Action("黑名单")
    fun blackList(qqGroupEntity: QQGroupEntity): String{
        val blackJsonArray = qqGroupEntity.getBlackJsonArray()
        val sb = StringBuilder().appendln("本群黑名单如下：")
        blackJsonArray.forEach {
            sb.appendln(it)
        }
        return sb.removeSuffixLine().toString()
    }

    @Action("加白 {qqNo}")
    @QMsg(at = true)
    fun addWhite(qqNo: Long, qqGroupEntity: QQGroupEntity): String{
        val whiteJsonArray = qqGroupEntity.getWhiteJsonArray()
        whiteJsonArray.add(qqNo.toString())
        qqGroupEntity.whiteList = whiteJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "加白名单成功！！"
    }

    @Action("去白 {qqNo}")
    @QMsg(at = true)
    fun delWhite(qqNo: Long, qqGroupEntity: QQGroupEntity): String{
        val whiteJsonArray = qqGroupEntity.getWhiteJsonArray()
        whiteJsonArray.remove(qqNo.toString())
        qqGroupEntity.whiteList = whiteJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "删除白名单成功！！"
    }

    @Action("白名单")
    fun whiteList(qqGroupEntity: QQGroupEntity): String{
        val whiteJsonArray = qqGroupEntity.getWhiteJsonArray()
        val sb = StringBuilder().appendln("本群白名单如下：")
        whiteJsonArray.forEach {
            sb.appendln(it)
        }
        return sb.removeSuffixLine().toString()
    }

    @Action("违规词")
    fun keywords(qqGroupEntity: QQGroupEntity): String{
        val keywordJsonArray = qqGroupEntity.getKeywordJsonArray()
        val sb = StringBuilder().appendln("本群违规词如下：")
        keywordJsonArray.forEach {
            sb.appendln(it)
        }
        return sb.removeSuffixLine().toString()
    }

    @Action("#涩图 {status}")
    @QMsg(at = true)
    fun colorPicSwitch(status: Boolean, qqGroupEntity: QQGroupEntity): String?{
        qqGroupEntity.colorPic = status
        qqGroupService.save(qqGroupEntity)
        return if (status) "涩图功能已开启！" else "涩图功能已关闭！"
    }

    @Action("清屏")
    fun clear(): String{
        val sb = StringBuilder()
        for (i in 0 until 1000) sb.appendln("\n")
        return sb.toString()
    }

    @Action("点歌切换 {type}")
    @QMsg(at = true)
    fun song(type: String, qqGroupEntity: QQGroupEntity): String?{
        var musicType = qqGroupEntity.musicType
        val msg = when (type){
            "qq" -> {
                musicType = "qq"
                "点歌切换为qq源成功"
            }
            "网易" -> {
                musicType = "163"
                "点歌切换为网易源成功"
            }
            else -> null
        }
        return if (msg != null){
            qqGroupEntity.musicType = musicType
            qqGroupService.save(qqGroupEntity)
            msg
        }else null
    }

    @Action("撤回通知 {b}")
    @QMsg(at = true)
    fun recall(qqGroupEntity: QQGroupEntity, b: Boolean): String?{
        qqGroupEntity.recall = b
        qqGroupService.save(qqGroupEntity)
        return if (b) "撤回通知已开启！！" else "撤回通知已关闭!!"
    }

    @Action("问")
    @QMsg(at = true)
    fun qa(session: ContextSession, qq: Long, qqGroupEntity: QQGroupEntity): String{
        reply(mif.at(qq).plus("请输入问题！！"))
        val q = session.waitNextMessage(300000)
        val qStr = q.firstString()
        reply(mif.at(qq).plus("请输入回答语句！！"))
        val a = session.waitNextMessage(300000)
        val jsonObject = JSONObject()
        val body = a.body
        val aJsonArray = JSONArray()
        for (messageItem in body){
            val aJsonObject = JSONObject()
            when (messageItem) {
                is Text -> {
                    aJsonObject["type"] = "text"
                    aJsonObject["content"] = messageItem.text
                }
                is Image -> {
                    aJsonObject["type"] = "image"
                    aJsonObject["content"] = messageItem.url
                }
                is Face -> {
                    aJsonObject["type"] = "face"
                    aJsonObject["content"] = messageItem.faceId
                }
            }
            if (aJsonObject.size != 0)
                aJsonArray.add(aJsonObject)
        }
        if (aJsonArray.size == 0) return "回答的语句暂只支持文本和图片和表情！！"
        jsonObject["q"] =  qStr
        jsonObject["a"] = aJsonArray
        val jsonArray = qqGroupEntity.getQaJsonArray()
        jsonArray.add(jsonObject)
        qqGroupEntity.qa = jsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "添加问答成功！！"
    }

    @Action("问答")
    fun qaList(qqGroupEntity: QQGroupEntity): String{
        val sb = StringBuilder().appendln("本群问答列表如下：")
        val qaJsonArray = qqGroupEntity.getQaJsonArray()
        for (i in qaJsonArray.indices){
            val jsonObject = qaJsonArray.getJSONObject(i)
            sb.appendln(jsonObject.getString("q"))
        }
        return sb.removeSuffixLine().toString()
    }

    @Action("删问答")
    @QMsg(at = true)
    fun delQa(qqGroupEntity: QQGroupEntity, message: Message): String{
        val msg = message.firstString()
        if (msg.length <= 4) return "请输入需要删除的问答！！"
        val q = msg.substring(4)
        val qaJsonArray = qqGroupEntity.getQaJsonArray()
        for (i in qaJsonArray.indices){
            val jsonObject = qaJsonArray.getJSONObject(i)
            if (jsonObject.getString("q") == q){
                qaJsonArray.remove(jsonObject)
                qqGroupEntity.qa = qaJsonArray.toString()
                qqGroupService.save(qqGroupEntity)
                return "删除问答成功！！"
            }
        }
        return "没有找到该问答，请检查！！！"
    }

    @Action("拦截 {text}")
    @QMsg(at = true)
    fun addIntercept(qqGroupEntity: QQGroupEntity, text: String): String{
        val interceptJsonArray = qqGroupEntity.getInterceptJsonArray()
        interceptJsonArray.add(text)
        qqGroupEntity.interceptList = interceptJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "${text}指令将不会再响应"
    }

    @Action("删拦截 {text}")
    @QMsg(at = true)
    fun delIntercept(qqGroupEntity: QQGroupEntity, text: String): String{
        val interceptJsonArray = qqGroupEntity.getInterceptJsonArray()
        interceptJsonArray.remove(text)
        qqGroupEntity.interceptList = interceptJsonArray.toString()
        qqGroupService.save(qqGroupEntity)
        return "${text}指令将不会再被拦截"
    }

    @Action("查拦截")
    fun queryIntercept(qqGroupEntity: QQGroupEntity): String{
        val sb = StringBuilder().appendln("本群以下指令不会被响应：")
        val interceptJsonArray = qqGroupEntity.getInterceptJsonArray()
        interceptJsonArray.forEach { sb.appendln(it) }
        return sb.removeSuffixLine().toString()
    }

    @Action("检查更新")
    fun checkUpdate(): String{
        val gitVersion = toolLogic.queryVersion()
        val sb = StringBuilder()
        sb.appendln("当前程序版本：$version")
        sb.appendln("最新程序版本：$gitVersion")
        if (gitVersion > version){
            sb.appendln("更新日志：https://github.com/kukume/kuku-bot/releases/tag/$gitVersion")
            sb.append("发现程序可更新，正在下载中！！！")
            reply(sb.toString())
            val response = OkHttpClientUtils.get("https://u.iheit.com/kuku/bot/kukubot.jar")
            val bytes = OkHttpClientUtils.getBytes(response)
            IO.writeFile(File("${System.getProperty("user.dir")}${File.separator}kukubot.jar"), bytes)
            reply("更新完成，请前往控制台手动启动程序！！")
            exitProcess(0)
        }else sb.append("暂未发现需要更新")
        return sb.toString()
    }

    private fun boolToStr(b: Boolean?) = if (b == true) "开" else "关"
}