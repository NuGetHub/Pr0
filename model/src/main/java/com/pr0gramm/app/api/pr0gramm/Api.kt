package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import com.pr0gramm.app.model.config.Rule
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import java.util.*

interface Api {
    @GET("/api/items/get")
    suspend fun itemsGetAsync(
            @Query("promoted") promoted: Int?,
            @Query("following") following: Int?,
            @Query("older") older: Long?,
            @Query("newer") newer: Long?,
            @Query("id") around: Long?,
            @Query("flags") flags: Int,
            @Query("tags") tags: String?,
            @Query("likes") likes: String?,
            @Query("self") self: Boolean?,
            @Query("user") user: String?): Feed

    @FormUrlEncoded
    @POST("/api/items/vote")
    suspend fun voteAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Unit

    @FormUrlEncoded
    @POST("/api/tags/vote")
    suspend fun voteTagAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Unit

    @FormUrlEncoded
    @POST("/api/comments/vote")
    suspend fun voteCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Unit

    @FormUrlEncoded
    @POST("/api/user/login")
    suspend fun loginAsync(
            @Field("name") username: String,
            @Field("password") password: String): Response<Login>

    @GET("/api/user/identifier")
    suspend fun identifier(): Identifier

    @FormUrlEncoded
    @POST("/api/tags/add")
    suspend fun addTagsAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") lastId: Long,
            @Field("tags") tags: String): NewTag

    @GET("/api/tags/top")
    suspend fun topTagsAsync(): TagTopList

    @FormUrlEncoded
    @POST("/api/comments/post")
    suspend fun postCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") itemId: Long,
            @Field("parentId") parentId: Long,
            @Field("comment") comment: String): NewComment

    @FormUrlEncoded
    @POST("/api/comments/delete")
    suspend fun hardDeleteCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") commentId: Long,
            @Field("reason") reason: String): Unit

    @FormUrlEncoded
    @POST("/api/comments/softDelete")
    suspend fun softDeleteCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") commentId: Long,
            @Field("reason") reason: String): Unit

    @GET("/api/items/info")
    suspend fun infoAsync(
            @Query("itemId") itemId: Long,
            @Query("bust") bust: Long?): Post

    @GET("/api/user/sync")
    suspend fun syncAsync(
            @Query("offset") offset: Long): Sync

    @GET("/api/user/info")
    suspend fun accountInfoAsync(): AccountInfo

    @GET("/api/profile/info")
    suspend fun infoAsync(
            @Query("name") name: String,
            @Query("flags") flags: Int?): Info

    @GET("/api/inbox/pending")
    suspend fun inboxPendingAsync(): MessageFeed

    @GET("/api/inbox/conversations")
    suspend fun listConversationsAsync(
            @Query("older") older: Long?): Conversations

    @GET("/api/inbox/messages")
    suspend fun messagesWithAsync(
            @Query("with") name: String,
            @Query("older") older: Long?): ConversationMessages

    @GET("/api/inbox/comments")
    suspend fun inboxCommentsAsync(
            @Query("older") older: Long?): MessageFeed

    @GET("/api/profile/comments")
    suspend fun userCommentsAsync(
            @Query("name") user: String,
            @Query("before") before: Long?,
            @Query("flags") flags: Int?): UserComments

    @GET("/api/profile/commentlikes")
    suspend fun userCommentsLikeAsync(
            @Query("name") user: String,
            @Query("before") before: Long,
            @Query("flags") flags: Int?): FavedUserComments

    @FormUrlEncoded
    @POST("/api/inbox/post")
    suspend fun sendMessageAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("comment") text: String,
            @Field("recipientId") recipient: Long): Unit

    @FormUrlEncoded
    @POST("/api/inbox/post")
    suspend fun sendMessageAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("comment") text: String,
            @Field("recipientName") recipient: String): ConversationMessages

    @GET("/api/items/ratelimited")
    suspend fun ratelimitedAsync(): Unit

    @POST("/api/items/upload")
    suspend fun uploadAsync(
            @Body body: RequestBody): Upload

    @FormUrlEncoded
    @POST("/api/items/post")
    suspend fun postAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("sfwstatus") sfwStatus: String,
            @Field("tags") tags: String,
            @Field("checkSimilar") checkSimilar: Int,
            @Field("key") key: String,
            @Field("processAsync") processAsync: Int?): Posted

    @GET("/api/items/queue")
    suspend fun queueAsync(
            @Query("id") id: Long?): QueueState

    @FormUrlEncoded
    @POST("/api/user/invite")
    suspend fun inviteAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("email") email: String): Invited

    // Extra stuff for admins
    @FormUrlEncoded
    @POST("api/items/delete")
    suspend fun deleteItemAsync(
            @Field("_nonce") none: Nonce?,
            @Field("id") id: Long,
            @Field("reason") reason: String,
            @Field("customReason") customReason: String,
            @Field("banUser") banUser: String?,
            @Field("days") days: Float?): Unit

    @FormUrlEncoded
    @POST("backend/admin/?view=users&action=ban")
    suspend fun userBanAsync(
            @Field("name") name: String,
            @Field("reason") reason: String,
            @Field("customReason") reasonCustom: String,
            @Field("days") days: Float,
            @Field("mode") mode: BanMode): Unit

    @GET("api/tags/details")
    suspend fun tagDetailsAsync(
            @Query("itemId") itemId: Long): TagDetails

    @FormUrlEncoded
    @POST("api/tags/delete")
    suspend fun deleteTagAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") itemId: Long,
            @Field("banUsers") banUser: String?,
            @Field("days") days: Float?,
            @Field("tags[]") tagId: List<Long> = listOf()): Unit

    @FormUrlEncoded
    @POST("api/profile/follow")
    suspend fun profileFollowAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") username: String): Unit

    @FormUrlEncoded
    @POST("api/profile/unfollow")
    suspend fun profileUnfollowAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") username: String): Unit

    @GET("api/profile/suggest")
    suspend fun suggestUsersAsync(
            @Query("prefix") prefix: String): Names

    @FormUrlEncoded
    @POST("api/contact/send")
    suspend fun contactSendAsync(
            @Field("subject") subject: String,
            @Field("email") email: String,
            @Field("message") message: String): Unit

    @FormUrlEncoded
    @POST("api/contact/report")
    suspend fun reportAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") item: Long,
            @Field("commentId") commentId: Long,
            @Field("reason") reason: String): Unit

    @FormUrlEncoded
    @POST("api/user/sendpasswordresetmail")
    suspend fun requestPasswordRecoveryAsync(
            @Field("email") email: String): Unit

    @FormUrlEncoded
    @POST("api/user/resetpassword")
    suspend fun resetPasswordAsync(
            @Field("name") name: String,
            @Field("token") token: String,
            @Field("password") password: String): ResetPassword

    @FormUrlEncoded
    @POST("api/user/handoverrequest")
    suspend fun handoverTokenAsync(
            @Field("_nonce") nonce: Nonce?): HandoverToken

    @GET("api/bookmarks/get")
    suspend fun bookmarksAsync(): Bookmarks

    @GET("api/bookmarks/get?default")
    suspend fun defaultBookmarksAsync(): Bookmarks

    @FormUrlEncoded
    @POST("api/bookmarks/add")
    suspend fun bookmarksAddAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") name: String,
            @Field("link") link: String): Bookmarks

    @FormUrlEncoded
    @POST("api/bookmarks/delete")
    suspend fun bookmarksDeleteAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") name: String): Bookmarks

    @GET("media/app-config.json")
    suspend fun remoteConfigAsync(@Query("bust") bust: Long): List<Rule>

    data class Nonce(val value: String) {
        override fun toString(): String = value.take(16)
    }

    @JsonClass(generateAdapter = true)
    data class Error(
            val error: String,
            val code: Int,
            val msg: String)

    @JsonClass(generateAdapter = true)
    data class AccountInfo(
            val account: Account,
            val invited: List<Invite> = listOf()) {

        @JsonClass(generateAdapter = true)
        data class Account(
                val email: String,
                val invites: Int)

        @JsonClass(generateAdapter = true)
        data class Invite(
                val email: String,
                val created: Instant,
                val name: String?,
                val mark: Int?)
    }

    @JsonClass(generateAdapter = true)
    data class Comment(
            val id: Long,
            val confidence: Float,
            val name: String,
            val content: String,
            val created: Instant,
            val parent: Long,
            val up: Int,
            val down: Int,
            val mark: Int) {

        val score: Int get() = up - down
    }

    @JsonClass(generateAdapter = true)
    data class Feed(
            val error: String? = null,
            @Json(name = "items") val _items: List<Item>? = null,
            @Json(name = "atStart") val isAtStart: Boolean = false,
            @Json(name = "atEnd") val isAtEnd: Boolean = false) {

        @Transient
        val items = _items.orEmpty()

        @JsonClass(generateAdapter = true)
        data class Item(
                val id: Long,
                val promoted: Long,
                val image: String,
                val thumb: String,
                val fullsize: String,
                val user: String,
                val up: Int,
                val down: Int,
                val mark: Int,
                val flags: Int,
                val width: Int = 0,
                val height: Int = 0,
                val created: Instant,
                val audio: Boolean = false,
                val deleted: Boolean = false)
    }


    @JsonClass(generateAdapter = true)
    data class Info(
            val user: User,
            val badges: List<Badge> = listOf(),
            val likeCount: Int,
            val uploadCount: Int,
            val commentCount: Int,
            val tagCount: Int,
            val likesArePublic: Boolean,
            val following: Boolean) {

        @JsonClass(generateAdapter = true)
        data class Badge(
                val created: Instant,
                val link: String,
                val image: String,
                val description: String?)

        @JsonClass(generateAdapter = true)
        data class User(
                val id: Int,
                val mark: Int,
                val score: Int,
                val name: String,
                val registered: Instant,
                val banned: Boolean = false,
                val bannedUntil: Instant?,
                val inactive: Boolean = false,
                @Json(name = "commentDelete") val commentDeleteCount: Int,
                @Json(name = "itemDelete") val itemDeleteCount: Int)
    }

    @JsonClass(generateAdapter = true)
    data class Invited(val error: String?)

    @JsonClass(generateAdapter = true)
    data class Identifier(val identifier: String)

    @JsonClass(generateAdapter = true)
    data class Login(
            val success: Boolean,
            val identifier: String?,
            @Json(name = "ban") val banInfo: BanInfo? = null) {

        @JsonClass(generateAdapter = true)
        data class BanInfo(
                val banned: Boolean,
                val reason: String,
                @Json(name = "till") val endTime: Instant?)
    }

    @JsonClass(generateAdapter = true)
    data class Message(
            val id: Long,
            val itemId: Long = 0,
            val mark: Int,
            val message: String,
            val name: String,
            val score: Int,
            val senderId: Int,
            val read: Boolean = true,
            @Json(name = "created") val creationTime: Instant,
            @Json(name = "thumb") val thumbnail: String?) {

        val isComment: Boolean get() = itemId != 0L

        val commentId: Long get() = id
    }

    @JsonClass(generateAdapter = true)
    data class MessageFeed(val messages: List<Message> = listOf())

    @JsonClass(generateAdapter = true)
    data class NewComment(
            val commentId: Long,
            val comments: List<Comment> = listOf())


    @JsonClass(generateAdapter = true)
    data class NewTag(
            val tagIds: List<Long> = listOf(),
            val tags: List<Tag> = listOf())

    @JsonClass(generateAdapter = true)
    data class Post(
            val tags: List<Tag> = listOf(),
            val comments: List<Comment> = listOf())

    @JsonClass(generateAdapter = true)
    data class QueueState(
            val position: Long,
            val item: Posted.PostedItem?,
            val status: String)

    @JsonClass(generateAdapter = true)
    data class Posted(
            val error: String?,
            val item: PostedItem?,
            val similar: List<SimilarItem> = listOf(),
            val report: VideoReport?,
            val queueId: Long?) {

        val itemId: Long = item?.id ?: -1

        @JsonClass(generateAdapter = true)
        data class PostedItem(val id: Long?)

        @JsonClass(generateAdapter = true)
        data class SimilarItem(
                val id: Long,
                val image: String,
                @Json(name = "thumb") val thumbnail: String)

        @JsonClass(generateAdapter = true)
        data class VideoReport(
                val duration: Float = 0f,
                val height: Int = 0,
                val width: Int = 0,
                val format: String?,
                val error: String?,
                val streams: List<MediaStream> = listOf())

        @JsonClass(generateAdapter = true)
        data class MediaStream(
                val codec: String?,
                val type: String)
    }

    @JsonClass(generateAdapter = true)
    data class Sync(
            val logLength: Long,
            val log: String,
            val score: Int,
            val inbox: InboxCounts = InboxCounts())

    @JsonClass(generateAdapter = true)
    data class InboxCounts(
            val comments: Int = 0,
            val mentions: Int = 0,
            val messages: Int = 0) {

        val total: Int get() = comments + mentions + messages
    }

    @JsonClass(generateAdapter = true)
    data class Tag(
            val id: Long,
            val confidence: Float,
            val tag: String) {

        override fun hashCode(): Int = tag.hashCode()

        override fun equals(other: Any?): Boolean = other is Tag && other.tag == tag
    }

    @JsonClass(generateAdapter = true)
    data class Upload(val key: String)

    @JsonClass(generateAdapter = true)
    data class UserComments(
            val user: UserInfo,
            val comments: List<UserComment> = listOf()) {

        @JsonClass(generateAdapter = true)
        data class UserComment(
                val id: Long,
                val itemId: Long,
                val created: Instant,
                val thumb: String,
                val up: Int,
                val down: Int,
                val content: String) {

            val score: Int get() = up - down
        }

        @JsonClass(generateAdapter = true)
        data class UserInfo(
                val id: Int,
                val mark: Int,
                val name: String)
    }

    @JsonClass(generateAdapter = true)
    data class FavedUserComments(
            val user: UserComments.UserInfo,
            val comments: List<FavedUserComment> = listOf())

    @JsonClass(generateAdapter = true)
    data class FavedUserComment(
            val id: Long,
            val itemId: Long,
            val created: Instant,
            val thumb: String,
            val name: String,
            val up: Int,
            val down: Int,
            val mark: Int,
            val content: String,
            @Json(name = "ccreated") val commentCreated: Instant)

    @JsonClass(generateAdapter = true)
    data class ResetPassword(val error: String?)

    @JsonClass(generateAdapter = true)
    data class TagDetails(
            val tags: List<TagInfo> = listOf()) {

        @JsonClass(generateAdapter = true)
        data class TagInfo(
                val id: Long,
                val up: Int,
                val down: Int,
                val confidence: Float,
                val tag: String,
                val user: String,
                val votes: List<Vote> = listOf())

        @JsonClass(generateAdapter = true)
        data class Vote(val vote: Int, val user: String)
    }

    @JsonClass(generateAdapter = true)
    data class HandoverToken(val token: String)

    @JsonClass(generateAdapter = true)
    data class Names(val users: List<String> = listOf())

    @JsonClass(generateAdapter = true)
    data class TagTopList(
            val tags: List<String> = listOf(),
            val blacklist: List<String> = listOf())

    @JsonClass(generateAdapter = true)
    data class Conversations(
            val conversations: List<Conversation>,
            val atEnd: Boolean)

    @JsonClass(generateAdapter = true)
    data class Conversation(
            val lastMessage: Instant,
            val mark: Int,
            val name: String,
            val unreadCount: Int)

    @JsonClass(generateAdapter = true)
    data class ConversationMessages(
            val atEnd: Boolean = true,
            val error: String? = null,
            val messages: List<ConversationMessage> = listOf())

    @JsonClass(generateAdapter = true)
    data class ConversationMessage(
            val id: Long,
            @Json(name = "created") val creationTime: Instant,
            val message: String,
            val sent: Boolean)

    @JsonClass(generateAdapter = true)
    data class Bookmarks(
            val bookmarks: List<Bookmark> = listOf(),
            val trending: List<Bookmark> = listOf(),
            val error: String? = null)

    @JsonClass(generateAdapter = true)
    data class Bookmark(
            val name: String,
            val link: String,
            val velocity: Float = 0.0f)


    enum class BanMode {
        Default, Single, Branch;

        override fun toString(): String = name.toLowerCase(Locale.ROOT)
    }
}
