package io.github.kdroidfilter.nucleus.updater

import kotlin.math.min

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val meta: String,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        when {
            major != other.major -> major - other.major
            minor != other.minor -> minor - other.minor
            patch != other.patch -> patch - other.patch
            else -> compareMeta(meta, other.meta)
        }

    override fun toString(): String =
        buildString {
            append("$major.$minor.$patch")
            if (meta.isNotEmpty()) append("-$meta")
        }

    companion object {
        private val SEMVER_REGEXP = """^(\d+)(?:\.(\d*))?(?:\.(\d*))?(?:-(.*))?${'$'}""".toRegex()

        fun fromString(versionString: String): Version {
            val matchResult =
                SEMVER_REGEXP.matchEntire(versionString.trim())
                    ?: return Version(0, 0, 0, "")
            val major = matchResult.groups[1]?.value?.toInt() ?: 0
            val minor = matchResult.groups[2]?.value?.toInt() ?: 0
            val patch = matchResult.groups[3]?.value?.toInt() ?: 0
            val meta = matchResult.groups[4]?.value ?: ""
            return Version(major, minor, patch, meta)
        }

        private fun compareMeta(
            a: String,
            b: String,
        ): Int {
            if (a.isEmpty() && b.isEmpty()) return 0
            if (a.isEmpty()) return 1 // No pre-release > pre-release
            if (b.isEmpty()) return -1 // Pre-release < no pre-release

            val aParts = a.split(".")
            val bParts = b.split(".")

            for (i in 0 until min(aParts.size, bParts.size)) {
                val aPart = aParts[i]
                val bPart = bParts[i]
                if (aPart == bPart) continue

                val aNum = aPart.toLongOrNull()
                val bNum = bPart.toLongOrNull()
                return when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1 // Numeric < string
                    bNum != null -> 1 // String > numeric
                    else -> aPart.compareTo(bPart)
                }
            }
            return aParts.size.compareTo(bParts.size)
        }
    }
}
