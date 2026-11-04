package com.example.data.download

/** A link from a profile's bio ("link in bio"). */
data class BioLink(val title: String, val url: String)

/** Everything an Instagram profile page shows about an account. */
data class ProfileDetails(
    val id: String,
    val username: String,
    val fullName: String,
    val picUrl: String?,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val followers: Long?,
    val following: Long?,
    val posts: Long?,
    val bio: String,
    val externalUrl: String?,
    val bioLinks: List<BioLink>,
    val category: String?,
    val pronouns: List<String>,
    val isBusiness: Boolean
)

enum class PostKind { PHOTO, VIDEO, CAROUSEL, REEL }

/** Which grid of a profile the posts came from (reposts are loaded separately). */
enum class PostsTab { POSTS, REELS, TAGGED }

/** One tile of a profile grid: a photo, a video, a reel, or a carousel with several slides in [media]. */
data class PostItem(
    val id: String,
    val code: String?,
    val kind: PostKind,
    val thumbUrl: String?,
    val caption: String?,
    val likes: Long?,
    val comments: Long?,
    val plays: Long?,
    val takenAtSec: Long,
    val isPinned: Boolean,
    val media: List<InstagramParser.StoryItem>
)

data class PostPage(val items: List<PostItem>, val nextCursor: String?)

/** One row of a followers / following list. */
data class PersonRow(
    val id: String,
    val username: String,
    val fullName: String,
    val picUrl: String?,
    val isVerified: Boolean,
    val isPrivate: Boolean
)

data class PersonPage(val people: List<PersonRow>, val nextCursor: String?)
