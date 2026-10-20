package android.net

import android.os.Parcel

/*
 * Minimal concrete Uri for JVM unit tests.
 *
 * The unit-test runtime ships a stub android.jar where framework methods throw
 * and read-only fields are null, so no real Uri can be produced. Living in the
 * android.net package is the only way to reach Uri's package-private
 * constructor, and every abstract getter is stubbed to a harmless default.
 * Tests must never call these methods (or Song.equals, which would hit them).
 */
class StubUri private constructor() : Uri() {

    override fun toString(): String = "stub-uri"
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = this === other
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int): Unit = throw UnsupportedOperationException()
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = false
    override fun isAbsolute(): Boolean = false

    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getPath(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getLastPathSegment(): String? = null
    override fun getQueryParameter(key: String): String? = null
    override fun getQueryParameterNames(): Set<String> = emptySet()
    override fun getQueryParameters(key: String): List<String> = emptyList()
    override fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun buildUpon(): Uri.Builder = throw UnsupportedOperationException()

    companion object {
        val instance: Uri by lazy { StubUri() }
    }
}